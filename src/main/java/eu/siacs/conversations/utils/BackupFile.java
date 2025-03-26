package eu.siacs.conversations.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.google.common.collect.Collections2;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.xmpp.Jid;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupFile implements Comparable<BackupFile> {

    private static final ExecutorService BACKUP_FILE_READER_EXECUTOR =
            Executors.newSingleThreadExecutor();

    private final Uri uri;
    private final BackupFileHeader header;

    private BackupFile(Uri uri, BackupFileHeader header) {
        this.uri = uri;
        this.header = header;
    }

    public static ListenableFuture<BackupFile> readAsync(final Context context, final Uri uri) {
        return Futures.submit(() -> read(context, uri), BACKUP_FILE_READER_EXECUTOR);
    }

    private static BackupFile read(final File file) throws IOException {
        final FileInputStream fileInputStream = new FileInputStream(file);
        final DataInputStream dataInputStream = new DataInputStream(fileInputStream);
        BackupFileHeader backupFileHeader = BackupFileHeader.read(dataInputStream);
        fileInputStream.close();
        return new BackupFile(Uri.fromFile(file), backupFileHeader);
    }

    public static BackupFile read(final Context context, final Uri uri) throws IOException {
        final InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new FileNotFoundException();
        }
        final DataInputStream dataInputStream = new DataInputStream(inputStream);
        final BackupFileHeader backupFileHeader = BackupFileHeader.read(dataInputStream);
        inputStream.close();
        return new BackupFile(uri, backupFileHeader);
    }

    public BackupFileHeader getHeader() {
        return header;
    }

    public Uri getUri() {
        return uri;
    }

    public static ListenableFuture<List<BackupFile>> listAsync(final Context context) {
        return Futures.submit(() -> list(context), BACKUP_FILE_READER_EXECUTOR);
    }

    private static List<BackupFile> list(final Context context) {
        final var database = DatabaseBackend.getInstance(context);
        final List<Jid> accounts = database.getAccountJids(false);
        final var backupFiles = new ImmutableList.Builder<BackupFile>();
        final var apps =
                ImmutableSet.of("Conversations", "Quicksy", context.getString(R.string.app_name));
        final List<File> directories = new ArrayList<>();
        for (final String app : apps) {
            directories.add(FileBackend.getLegacyBackupDirectory(app));
        }
        directories.add(FileBackend.getBackupDirectory(context));
        for (final File directory : directories) {
            if (!directory.exists() || !directory.isDirectory()) {
                Log.d(Config.LOGTAG, "directory not found: " + directory.getAbsolutePath());
                continue;
            }
            final File[] files = directory.listFiles();
            if (files == null) {
                continue;
            }
            Log.d(Config.LOGTAG, "looking for backups in " + directory);
            for (final File file : files) {
                if (file.isFile() && file.getName().endsWith(".ceb")) {
                    try {
                        final BackupFile backupFile = BackupFile.read(file);
                        if (accounts.contains(backupFile.getHeader().getJid())) {
                            Log.d(
                                    Config.LOGTAG,
                                    "skipping backup for " + backupFile.getHeader().getJid());
                        } else {
                            backupFiles.add(backupFile);
                        }
                    } catch (final IOException
                            | IllegalArgumentException
                            | BackupFileHeader.OutdatedBackupFileVersion e) {
                        Log.d(Config.LOGTAG, "unable to read backup file ", e);
                    }
                }
            }
        }
        final var list = backupFiles.build();
        if (QuickConversationsService.isQuicksy()) {
            return Ordering.natural()
                    .immutableSortedCopy(
                            Collections2.filter(
                                    list,
                                    b ->
                                            b.header
                                                    .getJid()
                                                    .getDomain()
                                                    .equals(Config.QUICKSY_DOMAIN)));
        }
        return Ordering.natural().immutableSortedCopy(backupFiles.build());
    }

    @Override
    public int compareTo(final BackupFile o) {
        return ComparisonChain.start()
                .compare(header.getJid(), o.header.getJid())
                .compare(header.getTimestamp(), o.header.getTimestamp())
                .result();
    }
}
