package eu.siacs.conversations.crypto;

import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.AsciiArmor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;

public class PgpEngine {
    private final OpenPgpApi api;
    private final XmppConnectionService mXmppConnectionService;

    public PgpEngine(OpenPgpApi api, XmppConnectionService service) {
        this.api = api;
        this.mXmppConnectionService = service;
    }

    private static void logError(Account account, OpenPgpError error) {
        if (error != null) {
            error.describeContents();
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid().toString()
                            + ": OpenKeychain error '"
                            + error.getMessage()
                            + "' code="
                            + error.getErrorId()
                            + " class="
                            + error.getClass().getName());
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid().toString()
                            + ": OpenKeychain error with no message");
        }
    }

    public static ListenableFuture<Void> encryptIfNeeded(
            final PgpEngine pgpEngine, final Message message) {
        final var e = message.getEncryption();
        if (e == Message.ENCRYPTION_PGP
                || (e == Message.ENCRYPTION_DECRYPTED && message.isFileOrImage())) {
            if (pgpEngine == null) {
                return Futures.immediateFailedFuture(
                        new IllegalStateException("PGP service not connected"));
            }
            return pgpEngine.encrypt(message);
        } else {
            return Futures.immediateVoidFuture();
        }
    }

    private ListenableFuture<Void> encrypt(final Message message) {
        final SettableFuture<Void> settableFuture = SettableFuture.create();
        final Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_ENCRYPT);
        final Conversation conversation = (Conversation) message.getConversation();
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            long[] keys = {
                conversation.getContact().getPgpKeyId(), conversation.getAccount().getPgpId()
            };
            params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keys);
        } else {
            params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, conversation.getMucOptions().getPgpKeyIds());
        }

        if (message.needsUploading()) {
            final DownloadableFile inputFile =
                    this.mXmppConnectionService.getFileBackend().getFile(message, true);
            final DownloadableFile outputFile =
                    this.mXmppConnectionService.getFileBackend().getFile(message, false);
            final InputStream is;
            final OutputStream os;
            try {
                final var outputDirectory = outputFile.getParentFile();
                if (outputDirectory != null && outputDirectory.mkdirs()) {
                    Log.d(Config.LOGTAG, "created " + outputDirectory.getAbsolutePath());
                }
                if (outputFile.createNewFile()) {
                    Log.d(Config.LOGTAG, "created " + outputFile.getAbsolutePath());
                }
                is = new FileInputStream(inputFile);
                os = new FileOutputStream(outputFile);
            } catch (final IOException e) {
                return Futures.immediateFailedFuture(e);
            }
            api.executeApiAsync(
                    params,
                    is,
                    os,
                    result -> {
                        switch (result.getIntExtra(
                                OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                            case OpenPgpApi.RESULT_CODE_SUCCESS:
                                try {
                                    os.flush();
                                } catch (IOException ignored) {
                                    // ignored
                                }
                                FileBackend.close(os);
                                settableFuture.set(null);
                                break;
                            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                                settableFuture.setException(
                                        new UserInputRequiredException(
                                                result.getParcelableExtra(
                                                        OpenPgpApi.RESULT_INTENT)));
                                break;
                            case OpenPgpApi.RESULT_CODE_ERROR:
                                OpenPgpError error =
                                        result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                                String errorMessage = error != null ? error.getMessage() : null;
                                logError(conversation.getAccount(), error);
                                settableFuture.setException(
                                        new IllegalStateException(
                                                Strings.nullToEmpty(errorMessage)));
                                break;
                        }
                    });

        } else {
            params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
            String body;
            if (message.hasFileOnRemoteHost()) {
                body = message.getFileParams().url;
            } else {
                body = message.getBody();
            }
            final var is = new ByteArrayInputStream(body.getBytes());
            final var os = new ByteArrayOutputStream();
            api.executeApiAsync(
                    params,
                    is,
                    os,
                    result -> {
                        switch (result.getIntExtra(
                                OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                            case OpenPgpApi.RESULT_CODE_SUCCESS:
                                try {
                                    os.flush();
                                    final ArrayList<String> encryptedMessageBody =
                                            new ArrayList<>();
                                    final String[] lines = os.toString().split("\n");
                                    for (int i = 2; i < lines.length - 1; ++i) {
                                        if (!lines[i].contains("Version")) {
                                            encryptedMessageBody.add(lines[i].trim());
                                        }
                                    }
                                    message.setEncryptedBody(
                                            Joiner.on('\n').join(encryptedMessageBody));
                                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                                    Log.d(
                                            Config.LOGTAG,
                                            "setting future after putting encrypted body in");
                                    settableFuture.set(null);
                                } catch (final IOException e) {
                                    settableFuture.setException(e);
                                }

                                break;
                            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                                settableFuture.setException(
                                        new UserInputRequiredException(
                                                result.getParcelableExtra(
                                                        OpenPgpApi.RESULT_INTENT)));
                                break;
                            case OpenPgpApi.RESULT_CODE_ERROR:
                                OpenPgpError error =
                                        result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                                String errorMessage = error != null ? error.getMessage() : null;
                                logError(conversation.getAccount(), error);
                                settableFuture.setException(
                                        new IllegalStateException(
                                                Strings.nullToEmpty(errorMessage)));
                                break;
                        }
                    });
        }
        return settableFuture;
    }

    public long fetchKeyId(final Account account, final String status, final String signature) {
        if (signature == null || api == null) {
            return 0;
        }
        final Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        try {
            params.putExtra(OpenPgpApi.RESULT_DETACHED_SIGNATURE, AsciiArmor.decode(signature));
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "unable to parse signature", e);
            return 0;
        }
        final InputStream is = new ByteArrayInputStream(Strings.nullToEmpty(status).getBytes());
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final Intent result = api.executeApi(params, is, os);
        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS:
                final OpenPgpSignatureResult sigResult =
                        result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
                // TODO unsure that sigResult.getResult() is either 1, 2 or 3
                if (sigResult != null) {
                    return sigResult.getKeyId();
                } else {
                    return 0;
                }
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                return 0;
            case OpenPgpApi.RESULT_CODE_ERROR:
                logError(account, result.getParcelableExtra(OpenPgpApi.RESULT_ERROR));
                return 0;
        }
        return 0;
    }

    public ListenableFuture<Void> chooseKey(final Account account) {
        final SettableFuture<Void> future = SettableFuture.create();
        final var intent = new Intent(OpenPgpApi.ACTION_GET_SIGN_KEY_ID);
        api.executeApiAsync(
                intent,
                null,
                null,
                result -> {
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            future.set(null);
                            return;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            future.setException(
                                    new UserInputRequiredException(
                                            result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)));
                            return;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                            String errorMessage = error != null ? error.getMessage() : null;
                            logError(account, error);
                            future.setException(
                                    new IllegalStateException(Strings.nullToEmpty(errorMessage)));
                            return;
                    }
                });
        return future;
    }

    public ListenableFuture<String> generateSignature(
            Intent intent, final Account account, final String status) {
        if (account.getPgpId() == 0) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Account has no PGP ID"));
        }
        final SettableFuture<String> future = SettableFuture.create();
        Intent params = intent == null ? new Intent() : intent;
        params.setAction(OpenPgpApi.ACTION_CLEARTEXT_SIGN);
        params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        params.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, account.getPgpId());
        InputStream is = new ByteArrayInputStream(status.getBytes());
        final OutputStream os = new ByteArrayOutputStream();
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid() + ": signing status message \"" + status + "\"");
        api.executeApiAsync(
                params,
                is,
                os,
                result -> {
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            final ArrayList<String> signature = new ArrayList<>();
                            try {
                                os.flush();
                                boolean sig = false;
                                for (final String line : Splitter.on('\n').split(os.toString())) {
                                    if (sig) {
                                        if (line.contains("END PGP SIGNATURE")) {
                                            sig = false;
                                        } else {
                                            if (!line.contains("Version")) {
                                                signature.add(line.trim());
                                            }
                                        }
                                    }
                                    if (line.contains("BEGIN PGP SIGNATURE")) {
                                        sig = true;
                                    }
                                }
                            } catch (IOException e) {
                                future.setException(e);
                                return;
                            }
                            future.set(Joiner.on('\n').join(signature));
                            return;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            future.setException(
                                    new UserInputRequiredException(
                                            result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)));
                            return;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                            String errorMessage = error != null ? error.getMessage() : null;
                            logError(account, error);
                            future.setException(
                                    new IllegalStateException(Strings.nullToEmpty(errorMessage)));
                    }
                });
        return future;
    }

    public ListenableFuture<Boolean> hasKey(final Contact contact) {
        final SettableFuture<Boolean> future = SettableFuture.create();
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
        api.executeApiAsync(
                params,
                null,
                null,
                result -> {
                    switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
                        case OpenPgpApi.RESULT_CODE_SUCCESS:
                            future.set(true);
                            return;
                        case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                            future.setException(
                                    new UserInputRequiredException(
                                            result.getParcelableExtra(OpenPgpApi.RESULT_INTENT)));
                            return;
                        case OpenPgpApi.RESULT_CODE_ERROR:
                            OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                            String errorMessage = error != null ? error.getMessage() : null;
                            logError(contact.getAccount(), error);
                            future.setException(
                                    new IllegalStateException(Strings.nullToEmpty(errorMessage)));
                            return;
                    }
                });
        return future;
    }

    public PendingIntent getIntentForKey(long pgpKeyId) {
        Intent params = new Intent();
        params.setAction(OpenPgpApi.ACTION_GET_KEY);
        params.putExtra(OpenPgpApi.EXTRA_KEY_ID, pgpKeyId);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);
        Intent result = api.executeApi(params, inputStream, outputStream);
        return result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
    }

    public static class UserInputRequiredException extends Exception {
        private final PendingIntent pendingIntent;

        public UserInputRequiredException(PendingIntent pendingIntent) {
            this.pendingIntent = pendingIntent;
        }

        public PendingIntent getPendingIntent() {
            return this.pendingIntent;
        }
    }
}
