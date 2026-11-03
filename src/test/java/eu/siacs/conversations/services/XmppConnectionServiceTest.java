package eu.siacs.conversations.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ServiceInfo;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.xmpp.jingle.Media;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class XmppConnectionServiceTest {

    @Test
    public void videoCallWithVideoEnabledUsesCamera() {
        final var ongoingCall =
                new XmppConnectionService.OngoingCall(
                        null, ImmutableSet.of(Media.AUDIO, Media.VIDEO), true, false);
        assertTrue(ongoingCall.usesCamera());
    }

    @Test
    public void videoCallWithVideoDisabledDoesNotUseCamera() {
        final var ongoingCall =
                new XmppConnectionService.OngoingCall(
                        null, ImmutableSet.of(Media.AUDIO, Media.VIDEO), false, false);
        assertFalse(ongoingCall.usesCamera());
    }

    @Test
    public void audioCallDoesNotUseCamera() {
        final var ongoingCall =
                new XmppConnectionService.OngoingCall(
                        null, ImmutableSet.of(Media.AUDIO), true, false);
        assertFalse(ongoingCall.usesCamera());
    }

    @Test
    public void videoCallGetsMicrophoneAndCameraType() {
        assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        | ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
                XmppConnectionService.chooseForegroundServiceType(true, true, true, true, false));
    }

    @Test
    public void videoCallWithoutCameraPermissionGetsMicrophoneType() {
        assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                XmppConnectionService.chooseForegroundServiceType(true, true, true, false, false));
    }

    @Test
    public void audioCallGetsMicrophoneType() {
        assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                XmppConnectionService.chooseForegroundServiceType(true, false, true, true, true));
    }

    @Test
    public void serviceWithoutCallPrefersSystemExemptedType() {
        assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
                XmppConnectionService.chooseForegroundServiceType(false, false, true, true, true));
    }

    @Test
    public void serviceWithoutCallFallsBackToTypesWithGrantedPermission() {
        assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                XmppConnectionService.chooseForegroundServiceType(
                        false, false, true, true, false));
        assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
                XmppConnectionService.chooseForegroundServiceType(
                        false, false, false, true, false));
        assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                XmppConnectionService.chooseForegroundServiceType(
                        false, false, false, false, false));
    }

    @Test
    public void keepsRunningServiceWithCameraWhenCameraTypeIsRefused() {
        assertEquals(
                Optional.absent(),
                XmppConnectionService.getForegroundServiceTypeWithoutCamera(
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                                | ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA));
    }

    @Test
    public void fallsBackToMicrophoneWhenCameraTypeIsRefused() {
        assertEquals(
                Optional.of(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE),
                XmppConnectionService.getForegroundServiceTypeWithoutCamera(
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE));
        // the service was not in the foreground before
        assertEquals(
                Optional.of(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE),
                XmppConnectionService.getForegroundServiceTypeWithoutCamera(
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE));
    }
}
