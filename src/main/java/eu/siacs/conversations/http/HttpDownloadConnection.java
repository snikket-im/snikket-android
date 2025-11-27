package eu.siacs.conversations.http;

import static eu.siacs.conversations.http.HttpConnectionManager.EXECUTOR;

import android.util.Log;
import androidx.annotation.Nullable;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Longs;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.FileWriterException;
import eu.siacs.conversations.utils.MimeUtils;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import javax.net.ssl.SSLHandshakeException;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

public class HttpDownloadConnection implements Transferable {

    private final Message message;
    private final HttpConnectionManager mHttpConnectionManager;
    private final XmppConnectionService mXmppConnectionService;
    private HttpUrl mUrl;
    private DownloadableFile file;
    private int mStatus = Transferable.STATUS_UNKNOWN;
    private boolean acceptedAutomatically = false;
    private int mProgress = 0;
    private Call mostRecentCall;

    HttpDownloadConnection(Message message, HttpConnectionManager manager) {
        this.message = message;
        this.mHttpConnectionManager = manager;
        this.mXmppConnectionService = manager.getXmppConnectionService();
    }

    @Override
    public boolean start() {
        if (mXmppConnectionService.hasInternetConnection()) {
            if (this.mStatus == STATUS_OFFER_CHECK_FILESIZE) {
                checkFileSize(true);
            } else {
                download(true);
            }
            return true;
        } else {
            return false;
        }
    }

    public void init(boolean interactive) {
        if (message.isDeleted()) {
            if (message.getType() == Message.TYPE_PRIVATE_FILE) {
                message.setType(Message.TYPE_PRIVATE);
            } else if (message.isFileOrImage()) {
                message.setType(Message.TYPE_TEXT);
            }
            message.setOob(true);
            message.setDeleted(false);
            mXmppConnectionService.updateMessage(message);
        }
        this.message.setTransferable(this);
        try {
            final Message.FileParams fileParams = message.getFileParams();
            if (message.hasFileOnRemoteHost()) {
                mUrl = AesGcmURL.of(fileParams.url);
            } else if (message.isOOb() && fileParams.url != null && fileParams.size != null) {
                mUrl = AesGcmURL.of(fileParams.url);
            } else {
                mUrl = AesGcmURL.of(message.getBody().split("\n")[0]);
            }
            final AbstractConnectionManager.Extension extension =
                    AbstractConnectionManager.Extension.of(mUrl.encodedPath());
            if (VALID_CRYPTO_EXTENSIONS.contains(extension.main)) {
                this.message.setEncryption(Message.ENCRYPTION_PGP);
            } else if (message.getEncryption() != Message.ENCRYPTION_AXOLOTL) {
                this.message.setEncryption(Message.ENCRYPTION_NONE);
            }
            final String ext = extension.getExtension();
            final String filename =
                    Strings.isNullOrEmpty(ext)
                            ? message.getUuid()
                            : String.format("%s.%s", message.getUuid(), ext);
            mXmppConnectionService.getFileBackend().setupRelativeFilePath(message, filename);
            setupFile();
            if (this.message.getEncryption() == Message.ENCRYPTION_AXOLOTL
                    && this.file.getKey() == null) {
                this.message.setEncryption(Message.ENCRYPTION_NONE);
            }
            final Long knownFileSize;
            if (message.getEncryption() == Message.ENCRYPTION_PGP
                    || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                knownFileSize = null;
            } else {
                knownFileSize = message.getFileParams().size;
            }
            if (knownFileSize != null && interactive) {
                if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL
                        && this.file.getKey() != null) {
                    this.file.setExpectedSize(knownFileSize + GCM_AUTHENTICATION_TAG_LENGTH);
                } else {
                    this.file.setExpectedSize(knownFileSize);
                }
                download(true);
            } else {
                checkFileSize(interactive);
            }
        } catch (final IllegalArgumentException e) {
            this.cancel();
        }
    }

    private void setupFile() {
        final String reference = mUrl.fragment();
        if (reference != null && AesGcmURL.IV_KEY.matcher(reference).matches()) {
            this.file =
                    new DownloadableFile(mXmppConnectionService.getCacheDir(), message.getUuid());
            this.file.setKeyAndIv(CryptoHelper.hexToBytes(reference));
            Log.d(
                    Config.LOGTAG,
                    "create temporary OMEMO encrypted file: "
                            + this.file.getAbsolutePath()
                            + "("
                            + message.getMimeType()
                            + ")");
        } else {
            this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
        }
    }

    private void download(final boolean interactive) {
        EXECUTOR.execute(new FileDownloader(interactive));
    }

    private void checkFileSize(final boolean interactive) {
        EXECUTOR.execute(new FileSizeChecker(interactive));
    }

    @Override
    public void cancel() {
        final Call call = this.mostRecentCall;
        if (call != null && !call.isCanceled()) {
            call.cancel();
        }
        mHttpConnectionManager.finishConnection(this);
        message.setTransferable(null);
        if (message.isFileOrImage()) {
            message.setDeleted(true);
        }
        mHttpConnectionManager.updateConversationUi(true);
    }

    private void decryptFile() throws IOException {
        final DownloadableFile outputFile =
                mXmppConnectionService.getFileBackend().getFile(message, true);

        final var directory = outputFile.getParentFile();
        if (directory != null && directory.mkdirs()) {
            Log.d(Config.LOGTAG, "created parent directories for " + outputFile.getAbsolutePath());
        }

        if (!outputFile.createNewFile()) {
            Log.w(Config.LOGTAG, "unable to create output file " + outputFile.getAbsolutePath());
        }
        final var cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(
                false, new AEADParameters(new KeyParameter(this.file.getKey()), 128, file.getIv()));
        try (final InputStream is = new FileInputStream(this.file);
                final CipherOutputStream outputStream =
                        new CipherOutputStream(new FileOutputStream(outputFile), cipher)) {
            ByteStreams.copy(is, outputStream);
        }

        if (file.delete()) {
            Log.w(
                    Config.LOGTAG,
                    "deleted temporary OMEMO encrypted file " + file.getAbsolutePath());
        }
    }

    private void finish() {
        message.setTransferable(null);
        mHttpConnectionManager.finishConnection(this);
        boolean notify = acceptedAutomatically && !message.isRead();
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            notify =
                    message.getConversation()
                            .getAccount()
                            .getPgpDecryptionService()
                            .decrypt(message, notify);
        }
        mHttpConnectionManager.updateConversationUi(true);
        final boolean notifyAfterScan = notify;
        final DownloadableFile file =
                mXmppConnectionService.getFileBackend().getFile(message, true);
        mXmppConnectionService
                .getFileBackend()
                .updateMediaScanner(
                        file,
                        () -> {
                            if (notifyAfterScan) {
                                mXmppConnectionService.getNotificationService().push(message);
                            }
                        });
    }

    private void decryptIfNeeded() throws IOException {
        if (file.getKey() != null && file.getIv() != null) {
            decryptFile();
        }
    }

    private void changeStatus(int status) {
        this.mStatus = status;
        mHttpConnectionManager.updateConversationUi(true);
    }

    private void showToastForException(final Exception e) {
        final Call call = mostRecentCall;
        final boolean cancelled = call != null && call.isCanceled();
        if (e == null || cancelled) {
            return;
        }
        if (e instanceof java.net.UnknownHostException) {
            mXmppConnectionService.showErrorToastInUi(R.string.download_failed_server_not_found);
        } else if (e instanceof java.net.ConnectException) {
            mXmppConnectionService.showErrorToastInUi(R.string.download_failed_could_not_connect);
        } else if (e instanceof FileWriterException) {
            mXmppConnectionService.showErrorToastInUi(
                    R.string.download_failed_could_not_write_file);
        } else if (e instanceof InvalidFileException) {
            mXmppConnectionService.showErrorToastInUi(R.string.download_failed_invalid_file);
        } else {
            mXmppConnectionService.showErrorToastInUi(R.string.download_failed_file_not_found);
        }
    }

    private void updateProgress(long i) {
        this.mProgress = (int) i;
        mHttpConnectionManager.updateConversationUi(false);
    }

    @Override
    public int getStatus() {
        return this.mStatus;
    }

    @Override
    public Long getFileSize() {
        if (this.file != null) {
            return this.file.getExpectedSize();
        } else {
            return null;
        }
    }

    @Override
    public int getProgress() {
        return this.mProgress;
    }

    public Message getMessage() {
        return message;
    }

    private class FileSizeChecker implements Runnable {

        private final boolean interactive;

        FileSizeChecker(boolean interactive) {
            this.interactive = interactive;
        }

        @Override
        public void run() {
            check();
        }

        private void retrieveFailed(@Nullable final Exception e) {
            changeStatus(STATUS_OFFER_CHECK_FILESIZE);
            if (interactive) {
                showToastForException(e);
            } else {
                HttpDownloadConnection.this.acceptedAutomatically = false;
                HttpDownloadConnection.this
                        .mXmppConnectionService
                        .getNotificationService()
                        .push(message);
            }
            cancel();
        }

        private void check() {
            long size;
            try {
                size = retrieveFileSize();
            } catch (final Exception e) {
                Log.d(Config.LOGTAG, "could not retrieve file size", e);
                retrieveFailed(e);
                return;
            }
            persistFileSize(size);
            message.setOob(true);
            mXmppConnectionService.databaseBackend.updateMessage(message, true);
            file.setExpectedSize(size);
            message.resetFileParams();
            if (mHttpConnectionManager.hasStoragePermission()
                    && size <= mHttpConnectionManager.getAutoAcceptFileSize()
                    && mXmppConnectionService.isDataSaverDisabled()) {
                HttpDownloadConnection.this.acceptedAutomatically = true;
                download(interactive);
            } else {
                changeStatus(STATUS_OFFER);
                HttpDownloadConnection.this.acceptedAutomatically = false;
                HttpDownloadConnection.this
                        .mXmppConnectionService
                        .getNotificationService()
                        .push(message);
            }
        }

        private long retrieveFileSize() throws IOException {
            Log.d(Config.LOGTAG, "retrieve file size. interactive:" + interactive);
            changeStatus(STATUS_CHECKING);
            final OkHttpClient client =
                    mHttpConnectionManager.buildHttpClient(
                            mUrl, message.getConversation().getAccount(), interactive);
            final Request request =
                    new Request.Builder()
                            .url(URL.stripFragment(mUrl))
                            .addHeader("Accept-Encoding", "identity")
                            .head()
                            .build();
            mostRecentCall = client.newCall(request);
            try (final Response response = mostRecentCall.execute()) {
                throwOnInvalidCode(response);
                final String contentLength = response.header("Content-Length");
                final String contentType = response.header("Content-Type");
                final AbstractConnectionManager.Extension extension =
                        AbstractConnectionManager.Extension.of(mUrl.encodedPath());
                if (Strings.isNullOrEmpty(extension.getExtension()) && contentType != null) {
                    final String fileExtension = MimeUtils.guessExtensionFromMimeType(contentType);
                    if (fileExtension != null) {
                        mXmppConnectionService
                                .getFileBackend()
                                .setupRelativeFilePath(
                                        message,
                                        String.format("%s.%s", message.getUuid(), fileExtension),
                                        contentType);
                        Log.d(
                                Config.LOGTAG,
                                "rewriting name after not finding extension in url but in content"
                                        + " type");
                        setupFile();
                    }
                }
                final Long size = Longs.tryParse(Strings.nullToEmpty(contentLength));
                if (size == null || size < 0) {
                    throw new IOException("no content-length found in HEAD response");
                }
                return size;
            }
        }
    }

    private void persistFileSize(final long size) {
        final Message.FileParams fileParams = message.getFileParams();
        if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL && file.getKey() != null) {
            // store the file size of the clear text file. If we resume the download we will add the
            // auth tag size again
            // this is equivalent to use updating file params *after* download (which would take the
            // clear text size as well)
            FileBackend.updateFileParams(
                    message, fileParams.url, size - GCM_AUTHENTICATION_TAG_LENGTH);
        } else {
            FileBackend.updateFileParams(message, fileParams.url, size);
        }
    }

    private class FileDownloader implements Runnable {

        private final boolean interactive;

        public FileDownloader(boolean interactive) {
            this.interactive = interactive;
        }

        @Override
        public void run() {
            try {
                changeStatus(STATUS_DOWNLOADING);
                download();
                decryptIfNeeded();
                updateImageBounds();
                finish();
            } catch (final SSLHandshakeException e) {
                changeStatus(STATUS_OFFER);
            } catch (final Exception e) {
                Log.d(
                        Config.LOGTAG,
                        message.getConversation().getAccount().getJid().asBareJid()
                                + ": unable to download file",
                        e);
                if (interactive) {
                    showToastForException(e);
                } else {
                    HttpDownloadConnection.this.acceptedAutomatically = false;
                    HttpDownloadConnection.this
                            .mXmppConnectionService
                            .getNotificationService()
                            .push(message);
                }
                cancel();
            }
        }

        private void download() throws Exception {
            final long expected = file.getExpectedSize();
            final var fileExists = file.exists();
            final var existingFileSize = fileExists ? file.length() : -1L;

            if (fileExists) {
                if (expected > 0 && existingFileSize == expected) {
                    Log.d(Config.LOGTAG, "file already exits (presumably decryption failure)");
                    return;
                }
            }
            final OkHttpClient client =
                    mHttpConnectionManager.buildHttpClient(
                            mUrl, message.getConversation().getAccount(), interactive);

            final Request.Builder requestBuilder =
                    new Request.Builder().url(URL.stripFragment(mUrl));

            final boolean tryResume =
                    fileExists && existingFileSize > 0 && existingFileSize < expected;
            final long resumeSize;
            if (tryResume) {
                resumeSize = existingFileSize;
                Log.d(
                        Config.LOGTAG,
                        "http download trying resume after " + resumeSize + " of " + expected);
                requestBuilder.addHeader(
                        "Range", String.format(Locale.ENGLISH, "bytes=%d-", resumeSize));
            } else {
                resumeSize = 0;
            }
            final Request request = requestBuilder.build();
            mostRecentCall = client.newCall(request);
            try (final Response response = mostRecentCall.execute()) {
                throwOnInvalidCode(response);
                final String contentRange = response.header("Content-Range");
                final boolean serverResumed =
                        tryResume
                                && contentRange != null
                                && contentRange.startsWith("bytes " + resumeSize + "-");
                final var body = response.body();
                if (body == null) {
                    throw new IOException("response body was null");
                }
                final InputStream inputStream = body.byteStream();
                if (tryResume && serverResumed) {
                    Log.d(Config.LOGTAG, "server resumed");
                    final var offset = file.getSize();
                    try (final OutputStream os = new FileOutputStream(file, true)) {
                        copy(inputStream, os, offset, expected);
                    }
                } else {
                    final String contentLength = response.header("Content-Length");
                    final Long size = Longs.tryParse(Strings.nullToEmpty(contentLength));
                    if (size == null) {
                        Log.d(Config.LOGTAG, "no content-length in GET response (probably gzip)");
                    } else {
                        if (expected != size) {
                            if (expected == 0) {
                                // this means we got 0 (unknown) on HEAD. We won't download the file
                                // but we update the file size so the user can try it again now that
                                // the actual file size is known
                                persistFileSize(size);
                            }
                            throw new IOException(
                                    "Content-Length in GET response did not match expected size");
                        }
                    }
                    final var directory = file.getParentFile();
                    if (directory != null && directory.mkdirs()) {
                        Log.d(Config.LOGTAG, "create directory " + directory.getAbsolutePath());
                    }
                    Log.d(Config.LOGTAG, "creating file: " + file.getAbsolutePath());
                    if (!file.exists() && !file.createNewFile()) {
                        throw new FileWriterException(file);
                    }
                    try (final OutputStream os = new FileOutputStream(file)) {
                        copy(inputStream, os, 0, expected);
                    }
                }
            }
        }

        private void copy(
                final InputStream inputStream,
                final OutputStream outputStream,
                final long offset,
                final long expected)
                throws IOException, FileWriterException {
            long transmitted = offset;
            int count;
            final byte[] buffer = new byte[4096];
            updateProgress(Math.round(((double) transmitted / expected) * 100));
            while ((count = inputStream.read(buffer)) != -1) {
                transmitted += count;
                try {
                    outputStream.write(buffer, 0, count);
                } catch (final IOException e) {
                    throw new FileWriterException(file);
                }
                if (transmitted > expected) {
                    throw new InvalidFileException(
                            String.format("File exceeds expected size of %d", expected));
                }
                updateProgress(Math.round(((double) transmitted / expected) * 100));
            }
        }

        private void updateImageBounds() {
            final boolean privateMessage = message.isPrivateMessage();
            message.setType(privateMessage ? Message.TYPE_PRIVATE_FILE : Message.TYPE_FILE);
            final String url;
            final String ref = mUrl.fragment();
            if (ref != null && AesGcmURL.IV_KEY.matcher(ref).matches()) {
                url = AesGcmURL.toAesGcmUrl(mUrl);
            } else {
                url = mUrl.toString();
            }
            mXmppConnectionService.getFileBackend().updateFileParams(message, url);
            mXmppConnectionService.updateMessage(message);
        }
    }

    private static void throwOnInvalidCode(final Response response) throws IOException {
        final int code = response.code();
        if (code < 200 || code >= 300) {
            throw new IOException(String.format(Locale.ENGLISH, "HTTP Status code was %d", code));
        }
    }

    private static class InvalidFileException extends IOException {

        private InvalidFileException(final String message) {
            super(message);
        }
    }
}
