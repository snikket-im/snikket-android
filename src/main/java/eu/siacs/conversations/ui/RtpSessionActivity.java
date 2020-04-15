package eu.siacs.conversations.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityRtpSessionBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.AppRTCAudioManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.PermissionUtils;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import rocks.xmpp.addr.Jid;

import static eu.siacs.conversations.utils.PermissionUtils.getFirstDenied;
import static java.util.Arrays.asList;

public class RtpSessionActivity extends XmppActivity implements XmppConnectionService.OnJingleRtpConnectionUpdate {

    private static final String PROXIMITY_WAKE_LOCK_TAG = "conversations:in-rtp-session";

    private static final int REQUEST_ACCEPT_CALL = 0x1111;

    public static final String EXTRA_WITH = "with";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_LAST_REPORTED_STATE = "last_reported_state";

    public static final String ACTION_ACCEPT_CALL = "action_accept_call";
    public static final String ACTION_MAKE_VOICE_CALL = "action_make_voice_call";
    public static final String ACTION_MAKE_VIDEO_CALL = "action_make_video_call";


    private WeakReference<JingleRtpConnection> rtpConnectionReference;

    private ActivityRtpSessionBinding binding;
    private PowerManager.WakeLock mProximityWakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        Log.d(Config.LOGTAG, "RtpSessionActivity.onCreate()");
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_rtp_session);
        setSupportActionBar(binding.toolbar);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(Config.LOGTAG, "RtpSessionActivity.onStart()");
    }

    private void endCall(View view) {
        endCall();
    }

    private void endCall() {
        if (this.rtpConnectionReference == null) {
            final Intent intent = getIntent();
            final Account account = extractAccount(intent);
            final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
            xmppConnectionService.getJingleConnectionManager().retractSessionProposal(account, with.asBareJid());
            finish();
        } else {
            requireRtpConnection().endCall();
        }
    }

    private void rejectCall(View view) {
        requireRtpConnection().rejectCall();
        finish();
    }

    private void acceptCall(View view) {
        requestPermissionsAndAcceptCall();
    }

    private void requestPermissionsAndAcceptCall() {
        final List<String> permissions;
        if (getMedia().contains(Media.VIDEO)) {
            permissions = ImmutableList.of(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO);
        } else {
            permissions = ImmutableList.of(Manifest.permission.RECORD_AUDIO);
        }
        if (PermissionUtils.hasPermission(this, permissions, REQUEST_ACCEPT_CALL)) {
            //TODO like wise the propose; we might just wait here for the audio manager to come up
            putScreenInCallMode();
            requireRtpConnection().acceptCall();
        }
    }

    @SuppressLint("WakelockTimeout")
    private void putScreenInCallMode() {
        //TODO for video calls we actually do want to keep the screen on
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final JingleRtpConnection rtpConnection = rtpConnectionReference != null ? rtpConnectionReference.get() : null;
        final AppRTCAudioManager audioManager = rtpConnection == null ? null : rtpConnection.getAudioManager();
        if (audioManager == null || audioManager.getSelectedAudioDevice() == AppRTCAudioManager.AudioDevice.EARPIECE) {
            acquireProximityWakeLock();
        }
    }

    private void acquireProximityWakeLock() {
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            Log.e(Config.LOGTAG, "power manager not available");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (this.mProximityWakeLock == null) {
                this.mProximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, PROXIMITY_WAKE_LOCK_TAG);
            }
            if (!this.mProximityWakeLock.isHeld()) {
                Log.d(Config.LOGTAG, "acquiring proximity wake lock");
                this.mProximityWakeLock.acquire();
            }
        }
    }

    private void releaseProximityWakeLock() {
        if (this.mProximityWakeLock != null && mProximityWakeLock.isHeld()) {
            Log.d(Config.LOGTAG, "releasing proximity wake lock");
            this.mProximityWakeLock.release();
            this.mProximityWakeLock = null;
        }
    }

    private void putProximityWakeLockInProperState() {
        if (requireRtpConnection().getAudioManager().getSelectedAudioDevice() == AppRTCAudioManager.AudioDevice.EARPIECE) {
            acquireProximityWakeLock();
        } else {
            releaseProximityWakeLock();
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (xmppConnectionService == null) {
            Log.d(Config.LOGTAG, "RtpSessionActivity: background service wasn't bound in onNewIntent()");
            return;
        }
        final Account account = extractAccount(intent);
        final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
        final String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        if (sessionId != null) {
            Log.d(Config.LOGTAG, "reinitializing from onNewIntent()");
            initializeActivityWithRunningRtpSession(account, with, sessionId);
            if (ACTION_ACCEPT_CALL.equals(intent.getAction())) {
                Log.d(Config.LOGTAG, "accepting call from onNewIntent()");
                requestPermissionsAndAcceptCall();
                resetIntent(intent.getExtras());
            }
        } else {
            throw new IllegalStateException("received onNewIntent without sessionId");
        }
    }

    @Override
    void onBackendConnected() {
        final Intent intent = getIntent();
        final String action = intent.getAction();
        final Account account = extractAccount(intent);
        final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
        final String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        if (sessionId != null) {
            initializeActivityWithRunningRtpSession(account, with, sessionId);
            if (ACTION_ACCEPT_CALL.equals(intent.getAction())) {
                Log.d(Config.LOGTAG, "intent action was accept");
                requestPermissionsAndAcceptCall();
                resetIntent(intent.getExtras());
            }
        } else if (asList(ACTION_MAKE_VIDEO_CALL, ACTION_MAKE_VOICE_CALL).contains(action)) {
            proposeJingleRtpSession(account, with, actionToMedia(action));
            binding.with.setText(account.getRoster().getContact(with).getDisplayName());
        } else if (Intent.ACTION_VIEW.equals(action)) {
            final String extraLastState = intent.getStringExtra(EXTRA_LAST_REPORTED_STATE);
            if (extraLastState != null) {
                Log.d(Config.LOGTAG, "restored last state from intent extra");
                RtpEndUserState state = RtpEndUserState.valueOf(extraLastState);
                updateButtonConfiguration(state);
                updateStateDisplay(state);
            }
            binding.with.setText(account.getRoster().getContact(with).getDisplayName());
        }
    }

    private static Set<Media> actionToMedia(final String action) {
        if (ACTION_MAKE_VIDEO_CALL.equals(action)) {
            return ImmutableSet.of(Media.AUDIO, Media.VIDEO);
        } else {
            return ImmutableSet.of(Media.AUDIO);
        }
    }

    private void proposeJingleRtpSession(final Account account, final Jid with, final Set<Media> media) {
        xmppConnectionService.getJingleConnectionManager().proposeJingleRtpSession(account, with, media);
        //TODO maybe we don’t want to acquire a wake lock just yet and wait for audio manager to discover what speaker we are using
        putScreenInCallMode();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtils.allGranted(grantResults)) {
            if (requestCode == REQUEST_ACCEPT_CALL) {
                requireRtpConnection().acceptCall();
            }
        } else {
            @StringRes int res;
            final String firstDenied = getFirstDenied(grantResults, permissions);
            if (Manifest.permission.RECORD_AUDIO.equals(firstDenied)) {
                res = R.string.no_microphone_permission;
            } else if (Manifest.permission.CAMERA.equals(firstDenied)) {
                res = R.string.no_camera_permission;
            } else {
                throw new IllegalStateException("Invalid permission result request");
            }
            Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStop() {
        binding.remoteVideo.release();
        binding.localVideo.release();
        releaseProximityWakeLock();
        //TODO maybe we want to finish if call had ended
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        endCall();
        super.onBackPressed();
    }


    private void initializeActivityWithRunningRtpSession(final Account account, Jid with, String sessionId) {
        final WeakReference<JingleRtpConnection> reference = xmppConnectionService.getJingleConnectionManager()
                .findJingleRtpConnection(account, with, sessionId);
        if (reference == null || reference.get() == null) {
            finish();
            return;
        }
        this.rtpConnectionReference = reference;
        final RtpEndUserState currentState = requireRtpConnection().getEndUserState();
        if (currentState == RtpEndUserState.ENDED) {
            finish();
            return;
        }
        if (currentState == RtpEndUserState.INCOMING_CALL) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (JingleRtpConnection.STATES_SHOWING_ONGOING_CALL.contains(requireRtpConnection().getState())) {
            putScreenInCallMode();
        }
        binding.with.setText(getWith().getDisplayName());
        updateVideoViews();
        updateStateDisplay(currentState);
        updateButtonConfiguration(currentState);
    }

    private void reInitializeActivityWithRunningRapSession(final Account account, Jid with, String sessionId) {
        runOnUiThread(() -> {
            initializeActivityWithRunningRtpSession(account, with, sessionId);
        });
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().toEscapedString());
        intent.putExtra(EXTRA_WITH, with.toEscapedString());
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        setIntent(intent);
    }

    private void ensureSurfaceViewRendererIsSetup(final SurfaceViewRenderer surfaceViewRenderer) {
        surfaceViewRenderer.setVisibility(View.VISIBLE);
        try {
            surfaceViewRenderer.init(requireRtpConnection().getEglBaseContext(), null);
        } catch (IllegalStateException e) {
            Log.d(Config.LOGTAG, "SurfaceViewRenderer was already initialized");
        }
        surfaceViewRenderer.setEnableHardwareScaler(true);
    }

    private void updateStateDisplay(final RtpEndUserState state) {
        switch (state) {
            case INCOMING_CALL:
                if (getMedia().contains(Media.VIDEO)) {
                    setTitle(R.string.rtp_state_incoming_video_call);
                } else {
                    setTitle(R.string.rtp_state_incoming_call);
                }
                break;
            case CONNECTING:
                setTitle(R.string.rtp_state_connecting);
                break;
            case CONNECTED:
                setTitle(R.string.rtp_state_connected);
                break;
            case ACCEPTING_CALL:
                setTitle(R.string.rtp_state_accepting_call);
                break;
            case ENDING_CALL:
                setTitle(R.string.rtp_state_ending_call);
                break;
            case FINDING_DEVICE:
                setTitle(R.string.rtp_state_finding_device);
                break;
            case RINGING:
                setTitle(R.string.rtp_state_ringing);
                break;
            case DECLINED_OR_BUSY:
                setTitle(R.string.rtp_state_declined_or_busy);
                break;
            case CONNECTIVITY_ERROR:
                setTitle(R.string.rtp_state_connectivity_error);
                break;
            case APPLICATION_ERROR:
                setTitle(R.string.rtp_state_application_failure);
                break;
            case ENDED:
                throw new IllegalStateException("Activity should have called finishAndReleaseWakeLock();");
            default:
                throw new IllegalStateException(String.format("State %s has not been handled in UI", state));
        }
    }

    private Set<Media> getMedia() {
        return requireRtpConnection().getMedia();
    }

    @SuppressLint("RestrictedApi")
    private void updateButtonConfiguration(final RtpEndUserState state) {
        if (state == RtpEndUserState.INCOMING_CALL) {
            this.binding.rejectCall.setOnClickListener(this::rejectCall);
            this.binding.rejectCall.setImageResource(R.drawable.ic_call_end_white_48dp);
            this.binding.rejectCall.setVisibility(View.VISIBLE);
            this.binding.endCall.setVisibility(View.INVISIBLE);
            this.binding.acceptCall.setOnClickListener(this::acceptCall);
            this.binding.acceptCall.setImageResource(R.drawable.ic_call_white_48dp);
            this.binding.acceptCall.setVisibility(View.VISIBLE);
        } else if (state == RtpEndUserState.ENDING_CALL) {
            this.binding.rejectCall.setVisibility(View.INVISIBLE);
            this.binding.endCall.setVisibility(View.INVISIBLE);
            this.binding.acceptCall.setVisibility(View.INVISIBLE);
        } else if (state == RtpEndUserState.DECLINED_OR_BUSY) {
            this.binding.rejectCall.setVisibility(View.INVISIBLE);
            this.binding.endCall.setOnClickListener(this::exit);
            this.binding.endCall.setImageResource(R.drawable.ic_clear_white_48dp);
            this.binding.endCall.setVisibility(View.VISIBLE);
            this.binding.acceptCall.setVisibility(View.INVISIBLE);
        } else if (state == RtpEndUserState.CONNECTIVITY_ERROR || state == RtpEndUserState.APPLICATION_ERROR) {
            this.binding.rejectCall.setOnClickListener(this::exit);
            this.binding.rejectCall.setImageResource(R.drawable.ic_clear_white_48dp);
            this.binding.rejectCall.setVisibility(View.VISIBLE);
            this.binding.endCall.setVisibility(View.INVISIBLE);
            this.binding.acceptCall.setOnClickListener(this::retry);
            this.binding.acceptCall.setImageResource(R.drawable.ic_replay_white_48dp);
            this.binding.acceptCall.setVisibility(View.VISIBLE);
        } else {
            this.binding.rejectCall.setVisibility(View.INVISIBLE);
            this.binding.endCall.setOnClickListener(this::endCall);
            this.binding.endCall.setImageResource(R.drawable.ic_call_end_white_48dp);
            this.binding.endCall.setVisibility(View.VISIBLE);
            this.binding.acceptCall.setVisibility(View.INVISIBLE);
        }
        updateInCallButtonConfiguration(state);
    }

    private void updateInCallButtonConfiguration() {
        updateInCallButtonConfiguration(requireRtpConnection().getEndUserState());
    }

    @SuppressLint("RestrictedApi")
    private void updateInCallButtonConfiguration(final RtpEndUserState state) {
        if (state == RtpEndUserState.CONNECTED) {
            if (getMedia().contains(Media.VIDEO)) {
                updateInCallButtonConfigurationVideo(requireRtpConnection().isVideoEnabled());
            } else {
                final AppRTCAudioManager audioManager = requireRtpConnection().getAudioManager();
                updateInCallButtonConfigurationSpeaker(
                        audioManager.getSelectedAudioDevice(),
                        audioManager.getAudioDevices().size()
                );
            }
            updateInCallButtonConfigurationMicrophone(requireRtpConnection().isMicrophoneEnabled());
        } else {
            this.binding.inCallActionLeft.setVisibility(View.GONE);
            this.binding.inCallActionRight.setVisibility(View.GONE);
        }
    }

    @SuppressLint("RestrictedApi")
    private void updateInCallButtonConfigurationSpeaker(final AppRTCAudioManager.AudioDevice selectedAudioDevice, final int numberOfChoices) {
        switch (selectedAudioDevice) {
            case EARPIECE:
                this.binding.inCallActionRight.setImageResource(R.drawable.ic_volume_off_black_24dp);
                if (numberOfChoices >= 2) {
                    this.binding.inCallActionRight.setOnClickListener(this::switchToSpeaker);
                } else {
                    this.binding.inCallActionRight.setOnClickListener(null);
                    this.binding.inCallActionRight.setClickable(false);
                }
                break;
            case WIRED_HEADSET:
                this.binding.inCallActionRight.setImageResource(R.drawable.ic_headset_black_24dp);
                this.binding.inCallActionRight.setOnClickListener(null);
                this.binding.inCallActionRight.setClickable(false);
                break;
            case SPEAKER_PHONE:
                this.binding.inCallActionRight.setImageResource(R.drawable.ic_volume_up_black_24dp);
                if (numberOfChoices >= 2) {
                    this.binding.inCallActionRight.setOnClickListener(this::switchToEarpiece);
                } else {
                    this.binding.inCallActionRight.setOnClickListener(null);
                    this.binding.inCallActionRight.setClickable(false);
                }
                break;
            case BLUETOOTH:
                this.binding.inCallActionRight.setImageResource(R.drawable.ic_bluetooth_audio_black_24dp);
                this.binding.inCallActionRight.setOnClickListener(null);
                this.binding.inCallActionRight.setClickable(false);
                break;
        }
        this.binding.inCallActionRight.setVisibility(View.VISIBLE);
    }

    @SuppressLint("RestrictedApi")
    private void updateInCallButtonConfigurationVideo(final boolean videoEnabled) {
        this.binding.inCallActionRight.setVisibility(View.VISIBLE);
        if (videoEnabled) {
            this.binding.inCallActionRight.setImageResource(R.drawable.ic_videocam_black_24dp);
            this.binding.inCallActionRight.setOnClickListener(this::disableVideo);
        } else {
            this.binding.inCallActionRight.setImageResource(R.drawable.ic_videocam_off_black_24dp);
            this.binding.inCallActionRight.setOnClickListener(this::enableVideo);
        }
    }

    private void enableVideo(View view) {
        requireRtpConnection().setVideoEnabled(true);
        updateInCallButtonConfigurationVideo(true);
    }

    private void disableVideo(View view) {
        requireRtpConnection().setVideoEnabled(false);
        updateInCallButtonConfigurationVideo(false);

    }

    @SuppressLint("RestrictedApi")
    private void updateInCallButtonConfigurationMicrophone(final boolean microphoneEnabled) {
        if (microphoneEnabled) {
            this.binding.inCallActionLeft.setImageResource(R.drawable.ic_mic_black_24dp);
            this.binding.inCallActionLeft.setOnClickListener(this::disableMicrophone);
        } else {
            this.binding.inCallActionLeft.setImageResource(R.drawable.ic_mic_off_black_24dp);
            this.binding.inCallActionLeft.setOnClickListener(this::enableMicrophone);
        }
        this.binding.inCallActionLeft.setVisibility(View.VISIBLE);
    }

    private void updateVideoViews() {
        final Optional<VideoTrack> localVideoTrack = requireRtpConnection().geLocalVideoTrack();
        if (localVideoTrack.isPresent()) {
            ensureSurfaceViewRendererIsSetup(binding.localVideo);
            //paint local view over remote view
            binding.localVideo.setZOrderMediaOverlay(true);
            binding.localVideo.setMirror(true);
            localVideoTrack.get().addSink(binding.localVideo);
        } else {
            binding.localVideo.setVisibility(View.GONE);
        }
        final Optional<VideoTrack> remoteVideoTrack = requireRtpConnection().getRemoteVideoTrack();
        if (remoteVideoTrack.isPresent()) {
            ensureSurfaceViewRendererIsSetup(binding.remoteVideo);
            remoteVideoTrack.get().addSink(binding.remoteVideo);
        } else {
            binding.remoteVideo.setVisibility(View.GONE);
        }
    }

    private void disableMicrophone(View view) {
        JingleRtpConnection rtpConnection = requireRtpConnection();
        rtpConnection.setMicrophoneEnabled(false);
        updateInCallButtonConfiguration();
    }

    private void enableMicrophone(View view) {
        JingleRtpConnection rtpConnection = requireRtpConnection();
        rtpConnection.setMicrophoneEnabled(true);
        updateInCallButtonConfiguration();
    }

    private void switchToEarpiece(View view) {
        requireRtpConnection().getAudioManager().setDefaultAudioDevice(AppRTCAudioManager.AudioDevice.EARPIECE);
        acquireProximityWakeLock();
    }

    private void switchToSpeaker(View view) {
        requireRtpConnection().getAudioManager().setDefaultAudioDevice(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE);
        releaseProximityWakeLock();
    }

    private void retry(View view) {
        Log.d(Config.LOGTAG, "attempting retry");
        final Intent intent = getIntent();
        final Account account = extractAccount(intent);
        final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
        this.rtpConnectionReference = null;
        proposeJingleRtpSession(account, with, ImmutableSet.of(Media.AUDIO, Media.VIDEO));
    }

    private void exit(View view) {
        finish();
    }

    private Contact getWith() {
        final AbstractJingleConnection.Id id = requireRtpConnection().getId();
        final Account account = id.account;
        return account.getRoster().getContact(id.with);
    }

    private JingleRtpConnection requireRtpConnection() {
        final JingleRtpConnection connection = this.rtpConnectionReference != null ? this.rtpConnectionReference.get() : null;
        if (connection == null) {
            throw new IllegalStateException("No RTP connection found");
        }
        return connection;
    }

    @Override
    public void onJingleRtpConnectionUpdate(Account account, Jid with, final String sessionId, RtpEndUserState state) {
        if (Arrays.asList(RtpEndUserState.APPLICATION_ERROR, RtpEndUserState.DECLINED_OR_BUSY, RtpEndUserState.DECLINED_OR_BUSY).contains(state)) {
            releaseProximityWakeLock();
        }
        Log.d(Config.LOGTAG, "onJingleRtpConnectionUpdate(" + state + ")");
        if (with.isBareJid()) {
            updateRtpSessionProposalState(account, with, state);
            return;
        }
        if (this.rtpConnectionReference == null) {
            //this happens when going from proposed session to actual session
            reInitializeActivityWithRunningRapSession(account, with, sessionId);
            return;
        }
        final AbstractJingleConnection.Id id = requireRtpConnection().getId();
        if (account == id.account && id.with.equals(with) && id.sessionId.equals(sessionId)) {
            if (state == RtpEndUserState.ENDED) {
                finish();
                return;
            } else if (asList(RtpEndUserState.APPLICATION_ERROR, RtpEndUserState.DECLINED_OR_BUSY, RtpEndUserState.CONNECTIVITY_ERROR).contains(state)) {
                //todo remember if we were video
                resetIntent(account, with, state);
            }
            runOnUiThread(() -> {
                updateStateDisplay(state);
                updateButtonConfiguration(state);
                //TODO kill video when in final or error stages
                updateVideoViews();
            });
        } else {
            Log.d(Config.LOGTAG, "received update for other rtp session");
            //TODO if we only ever have one; we might just switch over? Maybe!
        }
    }

    @Override
    public void onAudioDeviceChanged(AppRTCAudioManager.AudioDevice selectedAudioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
        Log.d(Config.LOGTAG, "onAudioDeviceChanged in activity: selected:" + selectedAudioDevice + ", available:" + availableAudioDevices);
        try {
            if (requireRtpConnection().getEndUserState() == RtpEndUserState.CONNECTED && !getMedia().contains(Media.VIDEO)) {
                final AppRTCAudioManager audioManager = requireRtpConnection().getAudioManager();
                updateInCallButtonConfigurationSpeaker(
                        audioManager.getSelectedAudioDevice(),
                        audioManager.getAudioDevices().size()
                );
            }
            putProximityWakeLockInProperState();
        } catch (IllegalStateException e) {
            Log.d(Config.LOGTAG, "RTP connection was not available when audio device changed");
        }
    }

    private void updateRtpSessionProposalState(final Account account, final Jid with, final RtpEndUserState state) {
        final Intent currentIntent = getIntent();
        final String withExtra = currentIntent == null ? null : currentIntent.getStringExtra(EXTRA_WITH);
        if (withExtra == null) {
            return;
        }
        if (Jid.ofEscaped(withExtra).asBareJid().equals(with)) {
            runOnUiThread(() -> {
                updateStateDisplay(state);
                updateButtonConfiguration(state);
            });
            resetIntent(account, with, state);
        }
    }

    private void resetIntent(final Bundle extras) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtras(extras);
        setIntent(intent);
    }

    private void resetIntent(final Account account, Jid with, final RtpEndUserState state) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(EXTRA_WITH, with.asBareJid().toEscapedString());
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().toEscapedString());
        intent.putExtra(EXTRA_LAST_REPORTED_STATE, state.toString());
        setIntent(intent);
    }
}
