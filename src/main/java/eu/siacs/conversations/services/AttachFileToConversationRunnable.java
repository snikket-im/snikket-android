package eu.siacs.conversations.services;

import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.otaliastudios.transcoder.Transcoder;
import com.otaliastudios.transcoder.TranscoderListener;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.TranscoderStrategies;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class AttachFileToConversationRunnable implements Runnable, TranscoderListener {

    private final XmppConnectionService mXmppConnectionService;
    private final AppSettings appSettings;
    private final Message message;
    private final Uri uri;
    private final String type;
    private final SettableFuture<Void> callbackHandler = SettableFuture.create();
    private final boolean isVideoMessage;
    private final long originalFileSize;
    private int currentProgress = -1;

    AttachFileToConversationRunnable(
            final XmppConnectionService xmppConnectionService,
            final Uri uri,
            final String type,
            final Message message) {
        this.uri = uri;
        this.type = type;
        this.mXmppConnectionService = xmppConnectionService;
        this.appSettings = new AppSettings(xmppConnectionService.getApplicationContext());
        this.message = message;
        final String mimeType =
                MimeUtils.guessMimeTypeFromUriAndMime(mXmppConnectionService, uri, type);
        final int autoAcceptFileSize =
                mXmppConnectionService.getResources().getInteger(R.integer.auto_accept_filesize);
        this.originalFileSize = FileBackend.getFileSize(mXmppConnectionService, uri);
        this.isVideoMessage =
                (mimeType != null && mimeType.startsWith("video/"))
                        && originalFileSize > autoAcceptFileSize
                        && appSettings.isCompressVideo();
    }

    boolean isVideoMessage() {
        return this.isVideoMessage;
    }

    private void processAsFile() {
        final String path = mXmppConnectionService.getFileBackend().getOriginalPath(uri);
        if (path != null && !FileBackend.isPathBlacklisted(path)) {
            message.setRelativeFilePath(path);
            mXmppConnectionService.getFileBackend().updateFileParams(message);
        } else {
            mXmppConnectionService.getFileBackend().copyFileToPrivateStorage(message, uri, type);
            mXmppConnectionService.getFileBackend().updateFileParams(message);
        }
    }

    private ListenableFuture<Void> fallbackToProcessAsFile() {
        final var file = mXmppConnectionService.getFileBackend().getFile(message);
        if (file.exists() && file.delete()) {
            Log.d(Config.LOGTAG, "deleted preexisting file " + file.getAbsolutePath());
        }
        return Futures.submit(this::processAsFile, XmppConnectionService.FILE_ATTACHMENT_EXECUTOR);
    }

    private void processAsVideo() throws FileNotFoundException {
        Log.d(Config.LOGTAG, "processing file as video");
        mXmppConnectionService.startOngoingVideoTranscodingForegroundNotification();
        mXmppConnectionService
                .getFileBackend()
                .setupRelativeFilePath(message, String.format("%s.%s", message.getUuid(), "mp4"));
        final DownloadableFile file = mXmppConnectionService.getFileBackend().getFile(message);
        if (Objects.requireNonNull(file.getParentFile()).mkdirs()) {
            Log.d(Config.LOGTAG, "created parent directory for video file");
        }

        final boolean highQuality = "720".equals(appSettings.getVideoCompression());

        final Future<Void> transcoderFuture;
        try {
            transcoderFuture =
                    Transcoder.into(file.getAbsolutePath())
                            .addDataSource(mXmppConnectionService, uri)
                            .setVideoTrackStrategy(
                                    highQuality
                                            ? TranscoderStrategies.VIDEO_720P
                                            : TranscoderStrategies.VIDEO_360P)
                            .setAudioTrackStrategy(
                                    highQuality
                                            ? TranscoderStrategies.AUDIO_HQ
                                            : TranscoderStrategies.AUDIO_MQ)
                            .setListener(this)
                            .transcode();
        } catch (final RuntimeException e) {
            // transcode can already throw if there is an invalid file format or a platform bug
            mXmppConnectionService.stopOngoingVideoTranscodingForegroundNotification();
            this.callbackHandler.setFuture(fallbackToProcessAsFile());
            return;
        }
        try {
            transcoderFuture.get();
        } catch (final InterruptedException e) {
            throw new AssertionError(e);
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof Error) {
                mXmppConnectionService.stopOngoingVideoTranscodingForegroundNotification();
                this.callbackHandler.setFuture(fallbackToProcessAsFile());
            } else {
                Log.d(Config.LOGTAG, "ignoring execution exception. Handled by onTranscodeFiled()");
            }
        }
    }

    @Override
    public void onTranscodeProgress(double progress) {
        final int p = (int) Math.round(progress * 100);
        if (p > currentProgress) {
            currentProgress = p;
            mXmppConnectionService
                    .getNotificationService()
                    .updateFileAddingNotification(p, message);
        }
    }

    @Override
    public void onTranscodeCompleted(int successCode) {
        mXmppConnectionService.stopOngoingVideoTranscodingForegroundNotification();
        final File file = mXmppConnectionService.getFileBackend().getFile(message);
        long convertedFileSize = mXmppConnectionService.getFileBackend().getFile(message).getSize();
        Log.d(
                Config.LOGTAG,
                "originalFileSize=" + originalFileSize + " convertedFileSize=" + convertedFileSize);
        if (originalFileSize != 0 && convertedFileSize >= originalFileSize) {
            if (file.delete()) {
                Log.d(
                        Config.LOGTAG,
                        "original file size was smaller. deleting and processing as file");
                this.callbackHandler.setFuture(fallbackToProcessAsFile());
                return;
            } else {
                Log.d(Config.LOGTAG, "unable to delete converted file");
            }
        }
        mXmppConnectionService.getFileBackend().updateFileParams(message);
        this.callbackHandler.set(null);
    }

    @Override
    public void onTranscodeCanceled() {
        mXmppConnectionService.stopOngoingVideoTranscodingForegroundNotification();
        this.callbackHandler.setFuture(fallbackToProcessAsFile());
    }

    @Override
    public void onTranscodeFailed(@NonNull final Throwable exception) {
        mXmppConnectionService.stopOngoingVideoTranscodingForegroundNotification();
        Log.d(Config.LOGTAG, "video transcoding failed", exception);
        this.callbackHandler.setFuture(fallbackToProcessAsFile());
    }

    @Override
    public void run() {
        if (this.isVideoMessage()) {
            try {
                processAsVideo();
                awaitCallbackOrFallback();
            } catch (final FileNotFoundException e) {
                processAsFile();
            }
        } else {
            processAsFile();
        }
    }

    private void awaitCallbackOrFallback() {
        Log.d(Config.LOGTAG, "awaiting callback");
        try {
            callbackHandler.get();
        } catch (final Exception e) {
            Log.e(Config.LOGTAG, "awaiting callback or fallback failed", e);
        }
    }
}
