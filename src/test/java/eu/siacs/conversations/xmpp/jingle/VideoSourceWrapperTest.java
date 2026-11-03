package eu.siacs.conversations.xmpp.jingle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.webrtc.CameraEnumerationAndroid;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.SurfaceTextureHelper;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class VideoSourceWrapperTest {

    private static final String BACK_CAMERA = "0";
    private static final String FRONT_CAMERA = "1";

    private final FakeCameraVideoCapturer capturer = new FakeCameraVideoCapturer();
    private final VideoSourceWrapper.CameraEvents cameraEvents =
            new VideoSourceWrapper.CameraEvents(FRONT_CAMERA::equals, true);
    private final VideoSourceWrapper videoSourceWrapper =
            new VideoSourceWrapper(
                    capturer,
                    cameraEvents,
                    new CameraEnumerationAndroid.CaptureFormat(
                            1280,
                            720,
                            new CameraEnumerationAndroid.CaptureFormat.FramerateRange(
                                    15000, 30000)),
                    ImmutableSet.of(BACK_CAMERA, FRONT_CAMERA));

    @Test
    public void restartsCaptureAfterCameraWasDisconnected() {
        videoSourceWrapper.startCapture();
        // this is what happens when Android revokes camera access from an app in the background
        cameraEvents.onCameraDisconnected();
        assertTrue(videoSourceWrapper.restartCaptureIfInterrupted());
        assertEquals(Arrays.asList("start", "start"), capturer.calls);
    }

    @Test
    public void restartsCaptureAfterCameraError() {
        videoSourceWrapper.startCapture();
        cameraEvents.onCameraError("Camera device has encountered a fatal error");
        assertTrue(videoSourceWrapper.restartCaptureIfInterrupted());
        assertEquals(Arrays.asList("start", "start"), capturer.calls);
    }

    @Test
    public void doesNotRestartCaptureThatIsRunning() {
        videoSourceWrapper.startCapture();
        assertFalse(videoSourceWrapper.restartCaptureIfInterrupted());
        assertEquals(List.of("start"), capturer.calls);
    }

    @Test
    public void restartsCaptureOnlyOncePerInterruption() {
        videoSourceWrapper.startCapture();
        cameraEvents.onCameraDisconnected();
        assertTrue(videoSourceWrapper.restartCaptureIfInterrupted());
        assertFalse(videoSourceWrapper.restartCaptureIfInterrupted());
        assertEquals(Arrays.asList("start", "start"), capturer.calls);
    }

    @Test
    public void startingCaptureClearsInterruption() {
        videoSourceWrapper.startCapture();
        cameraEvents.onCameraDisconnected();
        // for example when the video track gets added again
        videoSourceWrapper.startCapture();
        assertFalse(videoSourceWrapper.restartCaptureIfInterrupted());
        assertEquals(Arrays.asList("start", "start"), capturer.calls);
    }

    @Test
    public void stoppingCaptureStopsCapturerAndPreventsRestart() throws InterruptedException {
        videoSourceWrapper.startCapture();
        videoSourceWrapper.stopCapture();
        cameraEvents.onCameraDisconnected();
        assertFalse(videoSourceWrapper.restartCaptureIfInterrupted());
        assertEquals(Arrays.asList("start", "stop"), capturer.calls);
    }

    @Test
    public void restartsInterruptedCaptureBeforeSwitchingCamera() {
        videoSourceWrapper.startCapture();
        cameraEvents.onCameraDisconnected();
        videoSourceWrapper.switchCamera();
        assertEquals(Arrays.asList("start", "start", "switch"), capturer.calls);
    }

    @Test
    public void reportsFacingOfTheCameraThatIsBeingOpened() {
        assertTrue(videoSourceWrapper.isFrontCamera());
        // after a failed switch to the back camera a restart opens the back camera
        cameraEvents.onCameraOpening(BACK_CAMERA);
        assertFalse(videoSourceWrapper.isFrontCamera());
        cameraEvents.onCameraOpening(FRONT_CAMERA);
        assertTrue(videoSourceWrapper.isFrontCamera());
    }

    private static class FakeCameraVideoCapturer implements CameraVideoCapturer {

        private final List<String> calls = new ArrayList<>();

        @Override
        public void switchCamera(final CameraSwitchHandler cameraSwitchHandler) {
            calls.add("switch");
        }

        @Override
        public void switchCamera(
                final CameraSwitchHandler cameraSwitchHandler, final String cameraName) {
            calls.add("switch");
        }

        @Override
        public void initialize(
                final SurfaceTextureHelper surfaceTextureHelper,
                final Context context,
                final CapturerObserver capturerObserver) {}

        @Override
        public void startCapture(final int width, final int height, final int frameRate) {
            calls.add("start");
        }

        @Override
        public void stopCapture() {
            calls.add("stop");
        }

        @Override
        public void changeCaptureFormat(final int width, final int height, final int frameRate) {}

        @Override
        public void dispose() {}

        @Override
        public boolean isScreencast() {
            return false;
        }
    }
}
