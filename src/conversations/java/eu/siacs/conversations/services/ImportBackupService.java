package eu.siacs.conversations.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import javax.crypto.AEADBadTagException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.utils.BackupFileHeader;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.SerialSingleThreadExecutor;

import static eu.siacs.conversations.services.ExportBackupService.CIPHERMODE;
import static eu.siacs.conversations.services.ExportBackupService.KEYTYPE;
import static eu.siacs.conversations.services.ExportBackupService.PROVIDER;

public class ImportBackupService extends Service {

    private static final int NOTIFICATION_ID = 21;

    private final ImportBackupServiceBinder binder = new ImportBackupServiceBinder();
    private final SerialSingleThreadExecutor executor = new SerialSingleThreadExecutor(getClass().getSimpleName());

    private final Set<OnBackupProcessed> mOnBackupProcessedListeners = Collections.newSetFromMap(new WeakHashMap<>());

    private static AtomicBoolean running = new AtomicBoolean(false);
    private DatabaseBackend mDatabaseBackend;
    private NotificationManager notificationManager;

    private static int count(String input, char c) {
        int count = 0;
        for (char aChar : input.toCharArray()) {
            if (aChar == c) {
                ++count;
            }
        }
        return count;
    }

    @Override
    public void onCreate() {
        mDatabaseBackend = DatabaseBackend.getInstance(getBaseContext());
        notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        final String password = intent.getStringExtra("password");
        final String file = intent.getStringExtra("file");
        if (password == null || file == null) {
            return START_NOT_STICKY;
        }
        Log.d(Config.LOGTAG, "on start command");
        if (running.compareAndSet(false, true)) {
            executor.execute(() -> {
                startForegroundService();
                final boolean success = importBackup(new File(file), password);
                stopForeground(true);
                running.set(false);
                if (success) {
                    notifySuccess();
                }
                stopSelf();
            });
        } else {
            Log.d(Config.LOGTAG, "backup already running");
        }
        return START_NOT_STICKY;
    }

    public void loadBackupFiles(OnBackupFilesLoaded onBackupFilesLoaded) {
        executor.execute(() -> {
            final ArrayList<BackupFile> backupFiles = new ArrayList<>();
            for (String app : Arrays.asList("Conversations", "Quicksy")) {
                final File directory = new File(FileBackend.getBackupDirectory(app));
                if (!directory.exists() || !directory.isDirectory()) {
                    Log.d(Config.LOGTAG, "directory not found: " + directory.getAbsolutePath());
                    continue;
                }
                for (File file : directory.listFiles()) {
                    if (file.isFile() && file.getName().endsWith(".ceb")) {
                        try {
                            backupFiles.add(BackupFile.read(file));
                        } catch (IOException e) {
                            Log.d(Config.LOGTAG, "unable to read backup file ", e);
                        }
                    }
                }
            }
            onBackupFilesLoaded.onBackupFilesLoaded(backupFiles);
        });
    }

    private void startForegroundService() {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_restore_backup_title))
                .setSmallIcon(R.drawable.ic_unarchive_white_24dp)
                .setProgress(1, 0, true);
        startForeground(NOTIFICATION_ID, mBuilder.build());
    }

    private boolean importBackup(File file, String password) {
        Log.d(Config.LOGTAG, "importing backup from file " + file.getAbsolutePath());
        try {
            SQLiteDatabase db = mDatabaseBackend.getWritableDatabase();
            final FileInputStream fileInputStream = new FileInputStream(file);
            final DataInputStream dataInputStream = new DataInputStream(fileInputStream);
            BackupFileHeader backupFileHeader = BackupFileHeader.read(dataInputStream);
            Log.d(Config.LOGTAG, backupFileHeader.toString());

            final Cipher cipher = Compatibility.twentyEight() ? Cipher.getInstance(CIPHERMODE) : Cipher.getInstance(CIPHERMODE, PROVIDER);
            byte[] key = ExportBackupService.getKey(password, backupFileHeader.getSalt());
            SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
            IvParameterSpec ivSpec = new IvParameterSpec(backupFileHeader.getIv());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            CipherInputStream cipherInputStream = new CipherInputStream(fileInputStream, cipher);

            GZIPInputStream gzipInputStream = new GZIPInputStream(cipherInputStream);
            BufferedReader reader = new BufferedReader(new InputStreamReader(gzipInputStream, "UTF-8"));
            String line;
            StringBuilder multiLineQuery = null;
            while ((line = reader.readLine()) != null) {
                int count = count(line, '\'');
                if (multiLineQuery != null) {
                    multiLineQuery.append(line);
                    if (count % 2 == 1) {
                        db.execSQL(multiLineQuery.toString());
                        multiLineQuery = null;
                    }
                } else {
                    if (count % 2 == 0) {
                        db.execSQL(line);
                    } else {
                        multiLineQuery = new StringBuilder(line);
                    }
                }
            }
            Log.d(Config.LOGTAG, "done reading file");
            stopBackgroundService();
            synchronized (mOnBackupProcessedListeners) {
                for (OnBackupProcessed l : mOnBackupProcessedListeners) {
                    l.onBackupRestored();
                }
            }
            return true;
        } catch (Exception e) {
            Throwable throwable = e.getCause();
            final boolean reasonWasCrypto;
            if (throwable instanceof BadPaddingException) {
                reasonWasCrypto = true;
            } else {
                reasonWasCrypto = false;
            }
            synchronized (mOnBackupProcessedListeners) {
                for (OnBackupProcessed l : mOnBackupProcessedListeners) {
                    if (reasonWasCrypto) {
                        l.onBackupDecryptionFailed();
                    } else {
                        l.onBackupRestoreFailed();
                    }
                }
            }
            Log.d(Config.LOGTAG, "error restoring backup " + file.getAbsolutePath(), e);
            return false;
        }
    }

    private void notifySuccess() {
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext(), "backup");
        mBuilder.setContentTitle(getString(R.string.notification_restored_backup_title))
                .setContentText(getString(R.string.notification_restored_backup_subtitle))
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 145, new Intent(this, ManageAccountActivity.class), PendingIntent.FLAG_UPDATE_CURRENT))
                .setSmallIcon(R.drawable.ic_unarchive_white_24dp);
        notificationManager.notify(NOTIFICATION_ID,mBuilder.build());
    }

    private void stopBackgroundService() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        stopService(intent);
    }

    public void removeOnBackupProcessedListener(OnBackupProcessed listener) {
        synchronized (mOnBackupProcessedListeners) {
            mOnBackupProcessedListeners.remove(listener);
        }
    }

    public void addOnBackupProcessedListener(OnBackupProcessed listener) {
        synchronized (mOnBackupProcessedListeners) {
            mOnBackupProcessedListeners.add(listener);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.binder;
    }

    public static class BackupFile {
        private final File file;
        private final BackupFileHeader header;

        private BackupFile(File file, BackupFileHeader header) {
            this.file = file;
            this.header = header;
        }

        private static BackupFile read(File file) throws IOException {
            final FileInputStream fileInputStream = new FileInputStream(file);
            final DataInputStream dataInputStream = new DataInputStream(fileInputStream);
            BackupFileHeader backupFileHeader = BackupFileHeader.read(dataInputStream);
            fileInputStream.close();
            return new BackupFile(file, backupFileHeader);
        }

        public BackupFileHeader getHeader() {
            return header;
        }

        public File getFile() {
            return file;
        }
    }

    public class ImportBackupServiceBinder extends Binder {
        public ImportBackupService getService() {
            return ImportBackupService.this;
        }
    }

    public interface OnBackupFilesLoaded {
        void onBackupFilesLoaded(List<BackupFile> files);
    }

    public interface OnBackupProcessed {
        void onBackupRestored();
        void onBackupDecryptionFailed();
        void onBackupRestoreFailed();
    }
}