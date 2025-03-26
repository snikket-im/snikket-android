package eu.siacs.conversations.worker;

import static eu.siacs.conversations.utils.Compatibility.s;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CountingInputStream;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.SQLiteAxolotlStore;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.BackupFileHeader;
import eu.siacs.conversations.xmpp.Jid;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;
import javax.crypto.BadPaddingException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.json.JSONException;
import org.json.JSONObject;

public class ImportBackupWorker extends Worker {

    public static final String TAG_IMPORT_BACKUP = "tag-import-backup";

    private static final String DATA_KEY_PASSWORD = "password";
    private static final String DATA_KEY_URI = "uri";
    private static final String DATA_KEY_INCLUDE_OMEMO = "omemo";

    private static final Collection<String> OMEMO_TABLE_LIST =
            Arrays.asList(
                    SQLiteAxolotlStore.PREKEY_TABLENAME,
                    SQLiteAxolotlStore.SIGNED_PREKEY_TABLENAME,
                    SQLiteAxolotlStore.SESSION_TABLENAME,
                    SQLiteAxolotlStore.IDENTITIES_TABLENAME);

    private static final List<String> TABLE_ALLOW_LIST =
            new ImmutableList.Builder<String>()
                    .add(Account.TABLENAME, Conversation.TABLENAME, Message.TABLENAME)
                    .addAll(OMEMO_TABLE_LIST)
                    .build();

    private static final Pattern COLUMN_PATTERN = Pattern.compile("^[a-zA-Z_]+$");

    private static final int NOTIFICATION_ID = 21;

    private final String password;
    private final Uri uri;
    private final boolean includeOmemo;

    public ImportBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        final var inputData = workerParams.getInputData();
        this.password = inputData.getString(DATA_KEY_PASSWORD);
        this.uri = Uri.parse(inputData.getString(DATA_KEY_URI));
        this.includeOmemo = inputData.getBoolean(DATA_KEY_INCLUDE_OMEMO, true);
    }

    @NonNull
    @Override
    public Result doWork() {
        setForegroundAsync(
                new ForegroundInfo(NOTIFICATION_ID, createImportBackupNotification(1, 0)));
        final Result result;
        try {
            result = importBackup(this.uri, this.password);
        } catch (final FileNotFoundException e) {
            return failure(Reason.FILE_NOT_FOUND);
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "error restoring backup " + uri, e);
            final Throwable throwable = e.getCause();
            if (throwable instanceof BadPaddingException || e instanceof ZipException) {
                return failure(Reason.DECRYPTION_FAILED);
            } else {
                return failure(Reason.GENERIC);
            }
        } finally {
            getApplicationContext()
                    .getSystemService(NotificationManager.class)
                    .cancel(NOTIFICATION_ID);
        }

        return result;
    }

    private Result importBackup(final Uri uri, final String password)
            throws IOException, InvalidKeySpecException {
        final var context = getApplicationContext();
        final var database = DatabaseBackend.getInstance(context);
        Log.d(Config.LOGTAG, "importing backup from " + uri);
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final SQLiteDatabase db = database.getWritableDatabase();
        final InputStream inputStream;
        final String path = uri.getPath();
        final long fileSize;
        if ("file".equals(uri.getScheme()) && path != null) {
            final File file = new File(path);
            inputStream = new FileInputStream(file);
            fileSize = file.length();
        } else {
            final Cursor returnCursor =
                    context.getContentResolver().query(uri, null, null, null, null);
            if (returnCursor == null) {
                fileSize = 0;
            } else {
                returnCursor.moveToFirst();
                fileSize =
                        returnCursor.getLong(
                                returnCursor.getColumnIndexOrThrow(OpenableColumns.SIZE));
                returnCursor.close();
            }
            inputStream = context.getContentResolver().openInputStream(uri);
        }
        if (inputStream == null) {
            return failure(Reason.FILE_NOT_FOUND);
        }
        final CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        final DataInputStream dataInputStream = new DataInputStream(countingInputStream);
        final BackupFileHeader backupFileHeader = BackupFileHeader.read(dataInputStream);
        Log.d(Config.LOGTAG, backupFileHeader.toString());

        final var accounts = database.getAccountJids(false);

        if (QuickConversationsService.isQuicksy() && !accounts.isEmpty()) {
            return failure(Reason.ACCOUNT_ALREADY_EXISTS);
        }

        if (accounts.contains(backupFileHeader.getJid())) {
            return failure(Reason.ACCOUNT_ALREADY_EXISTS);
        }

        final byte[] key = ExportBackupWorker.getKey(password, backupFileHeader.getSalt());

        final AEADBlockCipher cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(
                false, new AEADParameters(new KeyParameter(key), 128, backupFileHeader.getIv()));
        final CipherInputStream cipherInputStream =
                new CipherInputStream(countingInputStream, cipher);

        final GZIPInputStream gzipInputStream = new GZIPInputStream(cipherInputStream);
        final BufferedReader reader =
                new BufferedReader(new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8));
        final JsonReader jsonReader = new JsonReader(reader);
        if (jsonReader.peek() == JsonToken.BEGIN_ARRAY) {
            jsonReader.beginArray();
        } else {
            throw new IllegalStateException("Backup file did not begin with array");
        }
        db.beginTransaction();
        while (jsonReader.hasNext()) {
            if (jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
                importRow(db, jsonReader, backupFileHeader.getJid(), password);
            } else if (jsonReader.peek() == JsonToken.END_ARRAY) {
                jsonReader.endArray();
                continue;
            }
            updateImportBackupNotification(fileSize, countingInputStream.getCount());
        }
        db.setTransactionSuccessful();
        db.endTransaction();
        final Jid jid = backupFileHeader.getJid();
        final Cursor countCursor =
                db.rawQuery(
                        "select count(messages.uuid) from messages join conversations on"
                                + " conversations.uuid=messages.conversationUuid join accounts on"
                                + " conversations.accountUuid=accounts.uuid where"
                                + " accounts.username=? and accounts.server=?",
                        new String[] {jid.getLocal(), jid.getDomain().toString()});
        countCursor.moveToFirst();
        final int count = countCursor.getInt(0);
        Log.d(Config.LOGTAG, String.format("restored %d messages in %s", count, stopwatch.stop()));
        countCursor.close();
        stopBackgroundService();
        notifySuccess();
        return Result.success();
    }

    private void importRow(
            final SQLiteDatabase db,
            final JsonReader jsonReader,
            final Jid account,
            final String passphrase)
            throws IOException {
        jsonReader.beginObject();
        final String firstParameter = jsonReader.nextName();
        if (!firstParameter.equals("table")) {
            throw new IllegalStateException("Expected key 'table'");
        }
        final String table = jsonReader.nextString();
        if (!TABLE_ALLOW_LIST.contains(table)) {
            throw new IOException(String.format("%s is not recognized for import", table));
        }
        final ContentValues contentValues = new ContentValues();
        final String secondParameter = jsonReader.nextName();
        if (!secondParameter.equals("values")) {
            throw new IllegalStateException("Expected key 'values'");
        }
        jsonReader.beginObject();
        while (jsonReader.peek() != JsonToken.END_OBJECT) {
            final String name = jsonReader.nextName();
            if (COLUMN_PATTERN.matcher(name).matches()) {
                if (jsonReader.peek() == JsonToken.NULL) {
                    jsonReader.nextNull();
                    contentValues.putNull(name);
                } else if (jsonReader.peek() == JsonToken.NUMBER) {
                    contentValues.put(name, jsonReader.nextLong());
                } else {
                    contentValues.put(name, jsonReader.nextString());
                }
            } else {
                throw new IOException(String.format("Unexpected column name %s", name));
            }
        }
        jsonReader.endObject();
        jsonReader.endObject();
        if (Account.TABLENAME.equals(table)) {
            final Jid jid =
                    Jid.of(
                            contentValues.getAsString(Account.USERNAME),
                            contentValues.getAsString(Account.SERVER),
                            null);
            final String password = contentValues.getAsString(Account.PASSWORD);
            if (QuickConversationsService.isQuicksy()) {
                if (!jid.getDomain().equals(Config.QUICKSY_DOMAIN)) {
                    throw new IOException("Trying to restore non Quicksy account on Quicksy");
                }
            }
            if (jid.equals(account) && passphrase.equals(password)) {
                Log.d(Config.LOGTAG, "jid and password from backup header had matching row");
            } else {
                throw new IOException("jid or password in table did not match backup");
            }
            final var keys = Account.parseKeys(contentValues.getAsString(Account.KEYS));
            final var deviceId = keys.optString(SQLiteAxolotlStore.JSONKEY_REGISTRATION_ID);
            final var importReadyKeys = new JSONObject();
            if (!Strings.isNullOrEmpty(deviceId) && this.includeOmemo) {
                try {
                    importReadyKeys.put(SQLiteAxolotlStore.JSONKEY_REGISTRATION_ID, deviceId);
                } catch (final JSONException e) {
                    Log.e(Config.LOGTAG, "error writing omemo registration id", e);
                }
            }
            contentValues.put(Account.KEYS, importReadyKeys.toString());
        }
        if (this.includeOmemo) {
            db.insert(table, null, contentValues);
        } else {
            if (OMEMO_TABLE_LIST.contains(table)) {
                if (SQLiteAxolotlStore.IDENTITIES_TABLENAME.equals(table)
                        && contentValues.getAsInteger(SQLiteAxolotlStore.OWN) == 0) {
                    db.insert(table, null, contentValues);
                } else {
                    Log.d(Config.LOGTAG, "skipping over omemo key material in table " + table);
                }
            } else {
                db.insert(table, null, contentValues);
            }
        }
    }

    private void stopBackgroundService() {
        final var intent = new Intent(getApplicationContext(), XmppConnectionService.class);
        getApplicationContext().stopService(intent);
    }

    private void updateImportBackupNotification(final long total, final long current) {
        final int max;
        final int progress;
        if (total == 0) {
            max = 1;
            progress = 0;
        } else {
            max = 100;
            progress = (int) (current * 100 / total);
        }
        getApplicationContext()
                .getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, createImportBackupNotification(max, progress));
    }

    private Notification createImportBackupNotification(final int max, final int progress) {
        final var context = getApplicationContext();
        final var builder = new NotificationCompat.Builder(getApplicationContext(), "backup");
        builder.setContentTitle(context.getString(R.string.restoring_backup))
                .setSmallIcon(R.drawable.ic_unarchive_24dp)
                .setProgress(max, progress, max == 1 && progress == 0);
        return builder.build();
    }

    private void notifySuccess() {
        final var context = getApplicationContext();
        final var builder = new NotificationCompat.Builder(context, "backup");
        builder.setContentTitle(context.getString(R.string.notification_restored_backup_title))
                .setContentText(context.getString(R.string.notification_restored_backup_subtitle))
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_unarchive_24dp);
        if (QuickConversationsService.isConversations()
                && AccountUtils.MANAGE_ACCOUNT_ACTIVITY != null) {
            builder.setContentText(
                    context.getString(R.string.notification_restored_backup_subtitle));
            builder.setContentIntent(
                    PendingIntent.getActivity(
                            context,
                            145,
                            new Intent(context, AccountUtils.MANAGE_ACCOUNT_ACTIVITY),
                            s()
                                    ? PendingIntent.FLAG_IMMUTABLE
                                            | PendingIntent.FLAG_UPDATE_CURRENT
                                    : PendingIntent.FLAG_UPDATE_CURRENT));
        }
        getApplicationContext()
                .getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID + 2, builder.build());
    }

    public static Data data(final String password, final Uri uri, final boolean includeOmemo) {
        return new Data.Builder()
                .putString(DATA_KEY_PASSWORD, password)
                .putString(DATA_KEY_URI, uri.toString())
                .putBoolean(DATA_KEY_INCLUDE_OMEMO, includeOmemo)
                .build();
    }

    private static Result failure(final Reason reason) {
        return Result.failure(new Data.Builder().putString("reason", reason.toString()).build());
    }

    public enum Reason {
        ACCOUNT_ALREADY_EXISTS,
        DECRYPTION_FAILED,
        FILE_NOT_FOUND,
        GENERIC;

        public static Reason valueOfOrGeneric(final String value) {
            if (Strings.isNullOrEmpty(value)) {
                return GENERIC;
            }
            try {
                return valueOf(value);
            } catch (final IllegalArgumentException e) {
                return GENERIC;
            }
        }
    }
}
