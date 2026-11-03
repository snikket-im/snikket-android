package eu.siacs.conversations.xmpp.jingle;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import eu.siacs.conversations.Config;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;

class VideoSourceWrapper {

    private static final int CAPTURING_RESOLUTION = 1920;
    private static final int CAPTURING_MAX_FRAME_RATE = 30;

    private final CameraVideoCapturer cameraVideoCapturer;
    private final CameraEvents cameraEvents;
    private final CameraEnumerationAndroid.CaptureFormat captureFormat;
    private final Set<String> availableCameras;
    private boolean isCapturing = false;
    private VideoSource videoSource;

    VideoSourceWrapper(
            CameraVideoCapturer cameraVideoCapturer,
            CameraEvents cameraEvents,
            CameraEnumerationAndroid.CaptureFormat captureFormat,
            Set<String> cameras) {
        this.cameraVideoCapturer = cameraVideoCapturer;
        this.cameraEvents = cameraEvents;
        this.captureFormat = captureFormat;
        this.availableCameras = cameras;
    }

    private int getFrameRate() {
        return Math.max(
                captureFormat.framerate.min,
                Math.min(CAPTURING_MAX_FRAME_RATE, captureFormat.framerate.max));
    }

    public void initialize(
            final PeerConnectionFactory peerConnectionFactory,
            final Context context,
            final EglBase.Context eglBaseContext) {
        final SurfaceTextureHelper surfaceTextureHelper =
                SurfaceTextureHelper.create("webrtc", eglBaseContext);
        if (surfaceTextureHelper == null) {
            throw new IllegalStateException("Could not create SurfaceTextureHelper");
        }
        this.videoSource = peerConnectionFactory.createVideoSource(false);
        this.cameraVideoCapturer.initialize(
                surfaceTextureHelper, context, this.videoSource.getCapturerObserver());
    }

    public VideoSource getVideoSource() {
        final VideoSource videoSource = this.videoSource;
        if (videoSource == null) {
            throw new IllegalStateException("VideoSourceWrapper was not initialized");
        }
        return videoSource;
    }

    public synchronized void startCapture() {
        final int frameRate = getFrameRate();
        Log.d(
                Config.LOGTAG,
                String.format(
                        "start capturing at %dx%d@%d",
                        captureFormat.width, captureFormat.height, frameRate));
        this.cameraEvents.interrupted.set(false);
        this.isCapturing = true;
        this.cameraVideoCapturer.startCapture(captureFormat.width, captureFormat.height, frameRate);
    }

    public void stopCapture() throws InterruptedException {
        // stopping might block until the camera has been opened; so we don’t hold the lock
        synchronized (this) {
            this.isCapturing = false;
        }
        this.cameraVideoCapturer.stopCapture();
    }

    // the capturer stops for good once the camera gets disconnected or runs into an error. This
    // happens for example when Android revokes camera access while the app is in the background
    public synchronized boolean restartCaptureIfInterrupted() {
        if (this.isCapturing && this.cameraEvents.interrupted.compareAndSet(true, false)) {
            Log.d(Config.LOGTAG, "restarting camera capture after it had been interrupted");
            startCapture();
            return true;
        }
        return false;
    }

    public void dispose() {
        this.cameraVideoCapturer.dispose();
        if (this.videoSource != null) {
            dispose(this.videoSource);
        }
    }

    private static void dispose(final VideoSource videoSource) {
        try {
            videoSource.dispose();
        } catch (final IllegalStateException e) {
            Log.e(Config.LOGTAG, "unable to dispose video source", e);
        }
    }

    public ListenableFuture<Boolean> switchCamera() {
        restartCaptureIfInterrupted();
        final SettableFuture<Boolean> future = SettableFuture.create();
        this.cameraVideoCapturer.switchCamera(
                new CameraVideoCapturer.CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(final boolean isFrontCamera) {
                        cameraEvents.frontFacing = isFrontCamera;
                        future.set(isFrontCamera);
                    }

                    @Override
                    public void onCameraSwitchError(final String message) {
                        future.setException(
                                new IllegalStateException(
                                        String.format("Unable to switch camera %s", message)));
                    }
                });
        return future;
    }

    public boolean isFrontCamera() {
        return this.cameraEvents.frontFacing;
    }

    public boolean isCameraSwitchable() {
        return this.availableCameras.size() > 1;
    }

    public static class Factory {
        final Context context;

        public Factory(final Context context) {
            this.context = context;
        }

        public VideoSourceWrapper create() {
            final CameraEnumerator enumerator = new Camera2Enumerator(context);
            final Set<String> deviceNames = ImmutableSet.copyOf(enumerator.getDeviceNames());
            for (final String deviceName : deviceNames) {
                if (isFrontFacing(enumerator, deviceName)) {
                    return of(enumerator, deviceName, deviceNames);
                }
            }
            if (deviceNames.isEmpty()) {
                return null;
            } else {
                return of(enumerator, Iterables.get(deviceNames, 0), deviceNames);
            }
        }

        @Nullable
        private VideoSourceWrapper of(
                final CameraEnumerator enumerator,
                final String deviceName,
                final Set<String> availableCameras) {
            final CameraEvents cameraEvents =
                    new CameraEvents(
                            name -> isFrontFacing(enumerator, name),
                            isFrontFacing(enumerator, deviceName));
            final CameraVideoCapturer capturer =
                    enumerator.createCapturer(deviceName, cameraEvents);
            if (capturer == null) {
                return null;
            }
            final ArrayList<CameraEnumerationAndroid.CaptureFormat> choices =
                    new ArrayList<>(enumerator.getSupportedFormats(deviceName));
            Collections.sort(choices, (a, b) -> b.width - a.width);
            for (final CameraEnumerationAndroid.CaptureFormat captureFormat : choices) {
                if (captureFormat.width <= CAPTURING_RESOLUTION) {
                    return new VideoSourceWrapper(
                            capturer, cameraEvents, captureFormat, availableCameras);
                }
            }
            return null;
        }

        private static boolean isFrontFacing(
                final CameraEnumerator cameraEnumerator, final String deviceName) {
            try {
                return cameraEnumerator.isFrontFacing(deviceName);
            } catch (final NullPointerException e) {
                return false;
            }
        }
    }

    static class CameraEvents implements CameraVideoCapturer.CameraEventsHandler {

        private final AtomicBoolean interrupted = new AtomicBoolean(false);
        private final Predicate<String> isFrontFacing;
        private volatile boolean frontFacing;

        CameraEvents(final Predicate<String> isFrontFacing, final boolean frontFacing) {
            this.isFrontFacing = isFrontFacing;
            this.frontFacing = frontFacing;
        }

        @Override
        public void onCameraError(final String errorDescription) {
            Log.w(Config.LOGTAG, "camera error: " + errorDescription);
            this.interrupted.set(true);
        }

        @Override
        public void onCameraDisconnected() {
            Log.w(Config.LOGTAG, "camera has been disconnected");
            this.interrupted.set(true);
        }

        @Override
        public void onCameraFreezed(final String errorDescription) {
            Log.w(Config.LOGTAG, "camera froze: " + errorDescription);
        }

        // after a failed camera switch the capturer stays on the camera it tried to switch to and
        // restarting will open that one
        @Override
        public void onCameraOpening(final String cameraName) {
            this.frontFacing = this.isFrontFacing.test(cameraName);
        }

        @Override
        public void onFirstFrameAvailable() {}

        @Override
        public void onCameraClosed() {}
    }
}
