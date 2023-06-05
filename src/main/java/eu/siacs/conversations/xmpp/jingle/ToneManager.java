package eu.siacs.conversations.xmpp.jingle;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.util.Log;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import eu.siacs.conversations.Config;

import static java.util.Arrays.asList;

import androidx.core.content.ContextCompat;

class ToneManager {

    private ToneGenerator toneGenerator;
    private final Context context;

    private ToneState state = null;
    private RtpEndUserState endUserState = null;
    private ScheduledFuture<?> currentTone;
    private ScheduledFuture<?> currentResetFuture;
    private boolean appRtcAudioManagerHasControl = false;

    ToneManager(final Context context) {
        this.context = context;
    }

    private static ToneState of(final boolean isInitiator, final RtpEndUserState state, final Set<Media> media) {
        if (isInitiator) {
            if (asList(RtpEndUserState.FINDING_DEVICE, RtpEndUserState.RINGING, RtpEndUserState.CONNECTING).contains(state)) {
                return ToneState.RINGING;
            }
            if (state == RtpEndUserState.DECLINED_OR_BUSY) {
                return ToneState.BUSY;
            }
        }
        if (state == RtpEndUserState.ENDING_CALL) {
            if (media.contains(Media.VIDEO)) {
                return ToneState.NULL;
            } else {
                return ToneState.ENDING_CALL;
            }
        }
        if (Arrays.asList(
                        RtpEndUserState.CONNECTED,
                        RtpEndUserState.RECONNECTING,
                        RtpEndUserState.INCOMING_CONTENT_ADD)
                .contains(state)) {
            if (media.contains(Media.VIDEO)) {
                return ToneState.NULL;
            } else {
                return ToneState.CONNECTED;
            }
        }
        return ToneState.NULL;
    }

    void transition(final RtpEndUserState state, final Set<Media> media) {
        transition(state, of(true, state, media), media);
    }

    void transition(final boolean isInitiator, final RtpEndUserState state, final Set<Media> media) {
        transition(state, of(isInitiator, state, media), media);
    }

    private synchronized void transition(final RtpEndUserState endUserState, final ToneState state, final Set<Media> media) {
        final RtpEndUserState normalizeEndUserState = normalize(endUserState);
        if (this.endUserState == normalizeEndUserState) {
            return;
        }
        this.endUserState = normalizeEndUserState;
        if (this.state == state) {
            return;
        }
        if (state == ToneState.NULL && this.state == ToneState.ENDING_CALL) {
            return;
        }
        cancelCurrentTone();
        Log.d(Config.LOGTAG, getClass().getName() + ".transition(" + state + ")");
        if (state != ToneState.NULL) {
            configureAudioManagerForCall(media);
        }
        switch (state) {
            case RINGING:
                scheduleWaitingTone();
                break;
            case CONNECTED:
                scheduleConnected();
                break;
            case BUSY:
                scheduleBusy();
                break;
            case ENDING_CALL:
                scheduleEnding();
                break;
            case NULL:
                if (noResetScheduled()) {
                    resetAudioManager();
                }
                break;
            default:
                throw new IllegalStateException("Unable to handle transition to "+state);
        }
        this.state = state;
    }

    private static RtpEndUserState normalize(final RtpEndUserState endUserState) {
        if (Arrays.asList(
                        RtpEndUserState.CONNECTED,
                        RtpEndUserState.RECONNECTING,
                        RtpEndUserState.INCOMING_CONTENT_ADD)
                .contains(endUserState)) {
            return RtpEndUserState.CONNECTED;
        } else {
            return endUserState;
        }
    }

    void setAppRtcAudioManagerHasControl(final boolean appRtcAudioManagerHasControl) {
        this.appRtcAudioManagerHasControl = appRtcAudioManagerHasControl;
    }

    private void scheduleConnected() {
        this.currentTone = JingleConnectionManager.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
            startTone(ToneGenerator.TONE_PROP_PROMPT, 200);
        }, 0, TimeUnit.SECONDS);
    }

    private void scheduleEnding() {
        this.currentTone = JingleConnectionManager.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
            startTone(ToneGenerator.TONE_CDMA_CALLDROP_LITE, 375);
        }, 0, TimeUnit.SECONDS);
        this.currentResetFuture = JingleConnectionManager.SCHEDULED_EXECUTOR_SERVICE.schedule(this::resetAudioManager, 375, TimeUnit.MILLISECONDS);
    }

    private void scheduleBusy() {
        this.currentTone = JingleConnectionManager.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
            startTone(ToneGenerator.TONE_CDMA_NETWORK_BUSY, 2500);
        }, 0, TimeUnit.SECONDS);
        this.currentResetFuture = JingleConnectionManager.SCHEDULED_EXECUTOR_SERVICE.schedule(this::resetAudioManager, 2500, TimeUnit.MILLISECONDS);
    }

    private void scheduleWaitingTone() {
        this.currentTone = JingleConnectionManager.SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(() -> {
            startTone(ToneGenerator.TONE_CDMA_DIAL_TONE_LITE, 750);
        }, 0, 3, TimeUnit.SECONDS);
    }

    private boolean noResetScheduled() {
        return this.currentResetFuture == null || this.currentResetFuture.isDone();
    }

    private void cancelCurrentTone() {
        if (currentTone != null) {
            currentTone.cancel(true);
        }
        stopTone(toneGenerator);
    }

    private static void stopTone(final ToneGenerator toneGenerator) {
        if (toneGenerator == null) {
            return;
        }
        try {
            toneGenerator.stopTone();
        } catch (final RuntimeException e) {
            Log.w(Config.LOGTAG,"tone has already stopped");
        }
    }

    private void startTone(final int toneType, final int durationMs) {
        if (this.toneGenerator != null) {
            this.toneGenerator.release();;

        }
        final AudioManager audioManager = ContextCompat.getSystemService(context, AudioManager.class);
        final boolean ringerModeNormal = audioManager == null || audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
        this.toneGenerator = getToneGenerator(ringerModeNormal);
        if (toneGenerator != null) {
            this.toneGenerator.startTone(toneType, durationMs);
        }
    }

    private static ToneGenerator getToneGenerator(final boolean ringerModeNormal) {
        try {
            if (ringerModeNormal) {
                return new ToneGenerator(AudioManager.STREAM_VOICE_CALL,60);
            } else {
                return new ToneGenerator(AudioManager.STREAM_MUSIC,100);
            }
        } catch (final Exception e) {
            Log.d(Config.LOGTAG,"could not create tone generator",e);
            return null;
        }
    }

    private void configureAudioManagerForCall(final Set<Media> media) {
        if (appRtcAudioManagerHasControl) {
            Log.d(Config.LOGTAG, ToneManager.class.getName() + ": do not configure audio manager because RTC has control");
            return;
        }
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        final boolean isSpeakerPhone = media.contains(Media.VIDEO);
        Log.d(Config.LOGTAG, ToneManager.class.getName() + ": putting AudioManager into communication mode. speaker=" + isSpeakerPhone);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(isSpeakerPhone);
    }

    private void resetAudioManager() {
        if (appRtcAudioManagerHasControl) {
            Log.d(Config.LOGTAG, ToneManager.class.getName() + ": do not reset audio manager because RTC has control");
            return;
        }
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        Log.d(Config.LOGTAG, ToneManager.class.getName() + ": putting AudioManager back into normal mode");
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(false);
    }

    private enum ToneState {
        NULL, RINGING, CONNECTED, BUSY, ENDING_CALL
    }
}
