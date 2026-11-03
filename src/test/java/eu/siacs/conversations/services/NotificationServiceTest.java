package eu.siacs.conversations.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.NotificationManager;
import android.media.AudioManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class NotificationServiceTest {

    @Test
    public void vibratesForIncomingCallWhenNothingPreventsIt() {
        assertTrue(
                NotificationService.isRingingVibrationAllowed(
                        true,
                        NotificationManager.INTERRUPTION_FILTER_ALL,
                        true,
                        NotificationManager.IMPORTANCE_HIGH,
                        AudioManager.MODE_NORMAL));
    }

    @Test
    public void vibratesForIncomingCallOnChannelWithDefaultImportance() {
        assertTrue(
                NotificationService.isRingingVibrationAllowed(
                        true,
                        NotificationManager.INTERRUPTION_FILTER_ALL,
                        true,
                        NotificationManager.IMPORTANCE_DEFAULT,
                        AudioManager.MODE_NORMAL));
    }

    @Test
    public void doesNotVibrateWhenNotificationsAreDisabled() {
        assertFalse(
                NotificationService.isRingingVibrationAllowed(
                        false,
                        NotificationManager.INTERRUPTION_FILTER_ALL,
                        true,
                        NotificationManager.IMPORTANCE_HIGH,
                        AudioManager.MODE_NORMAL));
    }

    @Test
    public void doesNotVibrateDuringDoNotDisturb() {
        for (final int interruptionFilter :
                new int[] {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                    NotificationManager.INTERRUPTION_FILTER_ALARMS,
                    NotificationManager.INTERRUPTION_FILTER_NONE
                }) {
            assertFalse(
                    "interruption filter " + interruptionFilter,
                    NotificationService.isRingingVibrationAllowed(
                            true,
                            interruptionFilter,
                            true,
                            NotificationManager.IMPORTANCE_HIGH,
                            AudioManager.MODE_NORMAL));
        }
    }

    @Test
    public void doesNotVibrateWhenUserTurnedOffVibrationForCalls() {
        assertFalse(
                NotificationService.isRingingVibrationAllowed(
                        true,
                        NotificationManager.INTERRUPTION_FILTER_ALL,
                        false,
                        NotificationManager.IMPORTANCE_HIGH,
                        AudioManager.MODE_NORMAL));
    }

    @Test
    public void doesNotVibrateWhenUserLoweredImportanceOfCalls() {
        for (final int importance :
                new int[] {
                    NotificationManager.IMPORTANCE_LOW,
                    NotificationManager.IMPORTANCE_MIN,
                    NotificationManager.IMPORTANCE_NONE
                }) {
            assertFalse(
                    "importance " + importance,
                    NotificationService.isRingingVibrationAllowed(
                            true,
                            NotificationManager.INTERRUPTION_FILTER_ALL,
                            true,
                            importance,
                            AudioManager.MODE_NORMAL));
        }
    }

    @Test
    public void doesNotVibrateDuringAnotherCall() {
        for (final int audioMode :
                new int[] {AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION}) {
            assertFalse(
                    "audio mode " + audioMode,
                    NotificationService.isRingingVibrationAllowed(
                            true,
                            NotificationManager.INTERRUPTION_FILTER_ALL,
                            true,
                            NotificationManager.IMPORTANCE_HIGH,
                            audioMode));
        }
    }
}
