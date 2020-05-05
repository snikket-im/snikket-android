package eu.siacs.conversations.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityRtpSessionBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.AppRTCAudioManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MainThreadExecutor;
import eu.siacs.conversations.utils.PermissionUtils;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import rocks.xmpp.addr.Jid;

import static eu.siacs.conversations.utils.PermissionUtils.getFirstDenied;
import static java.util.Arrays.asList;

public class RtpSessionActivity extends XmppActivity implements XmppConnectionService.OnJingleRtpConnectionUpdate {

    public static final String EXTRA_WITH = "with";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_LAST_REPORTED_STATE = "last_reported_state";
    public static final String EXTRA_LAST_ACTION = "last_action";
    public static final String ACTION_ACCEPT_CALL = "action_accept_call";
    public static final String ACTION_MAKE_VOICE_CALL = "action_make_voice_call";
    public static final String ACTION_MAKE_VIDEO_CALL = "action_make_video_call";
    private static final List<RtpEndUserState> END_CARD = Arrays.asList(
            RtpEndUserState.APPLICATION_ERROR,
            RtpEndUserState.DECLINED_OR_BUSY,
            RtpEndUserState.CONNECTIVITY_ERROR
    );
    private static final String PROXIMITY_WAKE_LOCK_TAG = "conversations:in-rtp-session";
    private static final int REQUEST_ACCEPT_CALL = 0x1111;
    private WeakReference<JingleRtpConnection> rtpConnectionReference;

    private ActivityRtpSessionBinding binding;
    private PowerManager.WakeLock mProximityWakeLock;

    private static Set<Media> actionToMedia(final String action) {
        if (ACTION_MAKE_VIDEO_CALL.equals(action)) {
            return ImmutableSet.of(Media.AUDIO, Media.VIDEO);
        } else {
            return ImmutableSet.of(Media.AUDIO);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_rtp_session);
        setSupportActionBar(binding.toolbar);
    }

    private void endCall(View view) {
        endCall();
    }

    private void endCall() {
        if (this.rtpConnectionReference == null) {
            retractSessionProposal();
            finish();
        } else {
            requireRtpConnection().endCall();
        }
    }

    private void retractSessionProposal() {
        final Intent intent = getIntent();
        final String action = intent.getAction();
        final Account account = extractAccount(intent);
        final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
        final String state = intent.getStringExtra(EXTRA_LAST_REPORTED_STATE);
        if (!Intent.ACTION_VIEW.equals(action) || state == null || !END_CARD.contains(RtpEndUserState.valueOf(state))) {
            resetIntent(account, with, RtpEndUserState.RETRACTED, actionToMedia(intent.getAction()));
        }
        xmppConnectionService.getJingleConnectionManager().retractSessionProposal(account, with.asBareJid());
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
            putScreenInCallMode();
            checkRecorderAndAcceptCall();
        }
    }

    private void checkRecorderAndAcceptCall() {
        checkMicrophoneAvailability();
        try {
            requireRtpConnection().acceptCall();
        } catch (final IllegalStateException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkMicrophoneAvailability() {
        new Thread(() -> {
            final long start = SystemClock.elapsedRealtime();
            final boolean isMicrophoneAvailable = AppRTCAudioManager.isMicrophoneAvailable();
            final long stop = SystemClock.elapsedRealtime();
            Log.d(Config.LOGTAG, "checking microphone availability took " + (stop - start) + "ms");
            if (isMicrophoneAvailable) {
                return;
            }
            runOnUiThread(() -> Toast.makeText(this, R.string.microphone_unavailable, Toast.LENGTH_LONG).show());
        }
        ).start();
    }

    private void putScreenInCallMode() {
        putScreenInCallMode(requireRtpConnection().getMedia());
    }

    private void putScreenInCallMode(final Set<Media> media) {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!media.contains(Media.VIDEO)) {
            final JingleRtpConnection rtpConnection = rtpConnectionReference != null ? rtpConnectionReference.get() : null;
            final AppRTCAudioManager audioManager = rtpConnection == null ? null : rtpConnection.getAudioManager();
            if (audioManager == null || audioManager.getSelectedAudioDevice() == AppRTCAudioManager.AudioDevice.EARPIECE) {
                acquireProximityWakeLock();
            }
        }
    }

    @SuppressLint("WakelockTimeout")
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
        Log.d(Config.LOGTAG, this.getClass().getName() + ".onNewIntent()");
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
            if (initializeActivityWithRunningRtpSession(account, with, sessionId)) {
                return;
            }
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
            if (initializeActivityWithRunningRtpSession(account, with, sessionId)) {
                return;
            }
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
                updateProfilePicture(state);
            }
            binding.with.setText(account.getRoster().getContact(with).getDisplayName());
        }
    }

    private void proposeJingleRtpSession(final Account account, final Jid with, final Set<Media> media) {
        checkMicrophoneAvailability();
        xmppConnectionService.getJingleConnectionManager().proposeJingleRtpSession(account, with, media);
        putScreenInCallMode(media);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtils.allGranted(grantResults)) {
            if (requestCode == REQUEST_ACCEPT_CALL) {
                checkRecorderAndAcceptCall();
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
        final WeakReference<JingleRtpConnection> weakReference = this.rtpConnectionReference;
        final JingleRtpConnection jingleRtpConnection = weakReference == null ? null : weakReference.get();
        if (jingleRtpConnection != null) {
            releaseVideoTracks(jingleRtpConnection);
        } else if (!isChangingConfigurations()) {
            if (xmppConnectionService != null) {
                retractSessionProposal();
            }
        }
        releaseProximityWakeLock();
        super.onStop();
    }

    private void releaseVideoTracks(final JingleRtpConnection jingleRtpConnection) {
        final Optional<VideoTrack> remoteVideo = jingleRtpConnection.getRemoteVideoTrack();
        if (remoteVideo.isPresent()) {
            remoteVideo.get().removeSink(binding.remoteVideo);
        }
        final Optional<VideoTrack> localVideo = jingleRtpConnection.getLocalVideoTrack();
        if (localVideo.isPresent()) {
            localVideo.get().removeSink(binding.localVideo);
        }
    }

    @Override
    public void onBackPressed() {
        endCall();
        super.onBackPressed();
    }

    @Override
    public void onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && deviceSupportsPictureInPicture()) {
            if (shouldBePictureInPicture()) {
                startPictureInPicture();
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startPictureInPicture() {
        try {
            enterPictureInPictureMode(
                    new PictureInPictureParams.Builder()
                            .setAspectRatio(new Rational(10, 16))
                            .build()
            );
        } catch (IllegalStateException e) {
            //this sometimes happens on Samsung phones (possibly when Knox is enabled)
            Log.w(Config.LOGTAG, "unable to enter picture in picture mode", e);
        }
    }

    private boolean deviceSupportsPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
        } else {
            return false;
        }
    }

    private boolean shouldBePictureInPicture() {
        try {
            final JingleRtpConnection rtpConnection = requireRtpConnection();
            return rtpConnection.getMedia().contains(Media.VIDEO) && Arrays.asList(
                    RtpEndUserState.ACCEPTING_CALL,
                    RtpEndUserState.CONNECTING,
                    RtpEndUserState.CONNECTED
            ).contains(rtpConnection.getEndUserState());
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private boolean initializeActivityWithRunningRtpSession(final Account account, Jid with, String sessionId) {
        final WeakReference<JingleRtpConnection> reference = xmppConnectionService.getJingleConnectionManager()
                .findJingleRtpConnection(account, with, sessionId);
        if (reference == null || reference.get() == null) {
            finish();
            return true;
        }
        this.rtpConnectionReference = reference;
        final RtpEndUserState currentState = requireRtpConnection().getEndUserState();
        if (currentState == RtpEndUserState.ENDED) {
            finish();
            return true;
        }
        final Set<Media> media = getMedia();
        if (currentState == RtpEndUserState.INCOMING_CALL) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (JingleRtpConnection.STATES_SHOWING_ONGOING_CALL.contains(requireRtpConnection().getState())) {
            putScreenInCallMode();
        }
        binding.with.setText(getWith().getDisplayName());
        updateVideoViews(currentState);
        updateStateDisplay(currentState, media);
        updateButtonConfiguration(currentState, media);
        updateProfilePicture(currentState);
        return false;
    }

    private void reInitializeActivityWithRunningRapSession(final Account account, Jid with, String sessionId) {
        runOnUiThread(() -> initializeActivityWithRunningRtpSession(account, with, sessionId));
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
        updateStateDisplay(state, Collections.emptySet());
    }

    private void updateStateDisplay(final RtpEndUserState state, final Set<Media> media) {
        switch (state) {
            case INCOMING_CALL:
                Preconditions.checkArgument(media.size() > 0, "Media must not be empty");
                if (media.contains(Media.VIDEO)) {
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
            case RETRACTED:
                setTitle(R.string.rtp_state_retracted);
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

    private void updateProfilePicture(final RtpEndUserState state) {
        updateProfilePicture(state, null);
    }

    private void updateProfilePicture(final RtpEndUserState state, final Contact contact) {
        if (state == RtpEndUserState.INCOMING_CALL || state == RtpEndUserState.ACCEPTING_CALL) {
            final boolean show = getResources().getBoolean(R.bool.show_avatar_incoming_call);
            if (show) {
                binding.contactPhoto.setVisibility(View.VISIBLE);
                if (contact == null) {
                    AvatarWorkerTask.loadAvatar(getWith(), binding.contactPhoto, R.dimen.publish_avatar_size);
                } else {
                    AvatarWorkerTask.loadAvatar(contact, binding.contactPhoto, R.dimen.publish_avatar_size);
                }
            } else {
                binding.contactPhoto.setVisibility(View.GONE);
            }
        } else {
            binding.contactPhoto.setVisibility(View.GONE);
        }
    }

    private Set<Media> getMedia() {
        return requireRtpConnection().getMedia();
    }

    private void updateButtonConfiguration(final RtpEndUserState state) {
        updateButtonConfiguration(state, Collections.emptySet());
    }

    @SuppressLint("RestrictedApi")
    private void updateButtonConfiguration(final RtpEndUserState state, final Set<Media> media) {
        if (state == RtpEndUserState.ENDING_CALL || isPictureInPicture()) {
            this.binding.rejectCall.setVisibility(View.INVISIBLE);
            this.binding.endCall.setVisibility(View.INVISIBLE);
            this.binding.acceptCall.setVisibility(View.INVISIBLE);
        } else if (state == RtpEndUserState.INCOMING_CALL) {
            this.binding.rejectCall.setOnClickListener(this::rejectCall);
            this.binding.rejectCall.setImageResource(R.drawable.ic_call_end_white_48dp);
            this.binding.rejectCall.setVisibility(View.VISIBLE);
            this.binding.endCall.setVisibility(View.INVISIBLE);
            this.binding.acceptCall.setOnClickListener(this::acceptCall);
            this.binding.acceptCall.setImageResource(R.drawable.ic_call_white_48dp);
            this.binding.acceptCall.setVisibility(View.VISIBLE);
        } else if (state == RtpEndUserState.DECLINED_OR_BUSY) {
            this.binding.rejectCall.setVisibility(View.INVISIBLE);
            this.binding.endCall.setOnClickListener(this::exit);
            this.binding.endCall.setImageResource(R.drawable.ic_clear_white_48dp);
            this.binding.endCall.setVisibility(View.VISIBLE);
            this.binding.acceptCall.setVisibility(View.INVISIBLE);
        } else if (asList(RtpEndUserState.CONNECTIVITY_ERROR, RtpEndUserState.APPLICATION_ERROR, RtpEndUserState.RETRACTED).contains(state)) {
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
        updateInCallButtonConfiguration(state, media);
    }

    private boolean isPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return isInPictureInPictureMode();
        } else {
            return false;
        }
    }

    private void updateInCallButtonConfiguration() {
        updateInCallButtonConfiguration(requireRtpConnection().getEndUserState(), requireRtpConnection().getMedia());
    }

    @SuppressLint("RestrictedApi")
    private void updateInCallButtonConfiguration(final RtpEndUserState state, final Set<Media> media) {
        if (state == RtpEndUserState.CONNECTED && !isPictureInPicture()) {
            Preconditions.checkArgument(media.size() > 0, "Media must not be empty");
            if (media.contains(Media.VIDEO)) {
                final JingleRtpConnection rtpConnection = requireRtpConnection();
                updateInCallButtonConfigurationVideo(rtpConnection.isVideoEnabled(), rtpConnection.isCameraSwitchable());
            } else {
                final AppRTCAudioManager audioManager = requireRtpConnection().getAudioManager();
                updateInCallButtonConfigurationSpeaker(
                        audioManager.getSelectedAudioDevice(),
                        audioManager.getAudioDevices().size()
                );
                this.binding.inCallActionFarRight.setVisibility(View.GONE);
            }
            updateInCallButtonConfigurationMicrophone(requireRtpConnection().isMicrophoneEnabled());
        } else {
            this.binding.inCallActionLeft.setVisibility(View.GONE);
            this.binding.inCallActionRight.setVisibility(View.GONE);
            this.binding.inCallActionFarRight.setVisibility(View.GONE);
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
    private void updateInCallButtonConfigurationVideo(final boolean videoEnabled, final boolean isCameraSwitchable) {
        this.binding.inCallActionRight.setVisibility(View.VISIBLE);
        if (isCameraSwitchable) {
            this.binding.inCallActionFarRight.setImageResource(R.drawable.ic_flip_camera_android_black_24dp);
            this.binding.inCallActionFarRight.setVisibility(View.VISIBLE);
            this.binding.inCallActionFarRight.setOnClickListener(this::switchCamera);
        } else {
            this.binding.inCallActionFarRight.setVisibility(View.GONE);
        }
        if (videoEnabled) {
            this.binding.inCallActionRight.setImageResource(R.drawable.ic_videocam_black_24dp);
            this.binding.inCallActionRight.setOnClickListener(this::disableVideo);
        } else {
            this.binding.inCallActionRight.setImageResource(R.drawable.ic_videocam_off_black_24dp);
            this.binding.inCallActionRight.setOnClickListener(this::enableVideo);
        }
    }

    private void switchCamera(final View view) {
        Futures.addCallback(requireRtpConnection().switchCamera(), new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(@NullableDecl Boolean isFrontCamera) {
                binding.localVideo.setMirror(isFrontCamera);
            }

            @Override
            public void onFailure(@NonNull final Throwable throwable) {
                Log.d(Config.LOGTAG,"could not switch camera", Throwables.getRootCause(throwable));
                Toast.makeText(RtpSessionActivity.this, R.string.could_not_switch_camera, Toast.LENGTH_LONG).show();
            }
        }, MainThreadExecutor.getInstance());
    }

    private void enableVideo(View view) {
        requireRtpConnection().setVideoEnabled(true);
        updateInCallButtonConfigurationVideo(true, requireRtpConnection().isCameraSwitchable());
    }

    private void disableVideo(View view) {
        requireRtpConnection().setVideoEnabled(false);
        updateInCallButtonConfigurationVideo(false, requireRtpConnection().isCameraSwitchable());

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

    private void updateVideoViews(final RtpEndUserState state) {
        if (END_CARD.contains(state) || state == RtpEndUserState.ENDING_CALL) {
            binding.localVideo.setVisibility(View.GONE);
            binding.localVideo.release();
            binding.remoteVideo.setVisibility(View.GONE);
            binding.remoteVideo.release();
            binding.pipLocalMicOffIndicator.setVisibility(View.GONE);
            if (isPictureInPicture()) {
                binding.appBarLayout.setVisibility(View.GONE);
                binding.pipPlaceholder.setVisibility(View.VISIBLE);
                if (state == RtpEndUserState.APPLICATION_ERROR || state == RtpEndUserState.CONNECTIVITY_ERROR) {
                    binding.pipWarning.setVisibility(View.VISIBLE);
                    binding.pipWaiting.setVisibility(View.GONE);
                } else {
                    binding.pipWarning.setVisibility(View.GONE);
                    binding.pipWaiting.setVisibility(View.GONE);
                }
            } else {
                binding.appBarLayout.setVisibility(View.VISIBLE);
                binding.pipPlaceholder.setVisibility(View.GONE);
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            return;
        }
        if (isPictureInPicture() && (state == RtpEndUserState.CONNECTING || state == RtpEndUserState.ACCEPTING_CALL)) {
            binding.localVideo.setVisibility(View.GONE);
            binding.remoteVideo.setVisibility(View.GONE);
            binding.appBarLayout.setVisibility(View.GONE);
            binding.pipPlaceholder.setVisibility(View.VISIBLE);
            binding.pipWarning.setVisibility(View.GONE);
            binding.pipWaiting.setVisibility(View.VISIBLE);
            binding.pipLocalMicOffIndicator.setVisibility(View.GONE);
            return;
        }
        final Optional<VideoTrack> localVideoTrack = getLocalVideoTrack();
        if (localVideoTrack.isPresent() && !isPictureInPicture()) {
            ensureSurfaceViewRendererIsSetup(binding.localVideo);
            //paint local view over remote view
            binding.localVideo.setZOrderMediaOverlay(true);
            binding.localVideo.setMirror(requireRtpConnection().isFrontCamera());
            localVideoTrack.get().addSink(binding.localVideo);
        } else {
            binding.localVideo.setVisibility(View.GONE);
        }
        final Optional<VideoTrack> remoteVideoTrack = getRemoteVideoTrack();
        if (remoteVideoTrack.isPresent()) {
            ensureSurfaceViewRendererIsSetup(binding.remoteVideo);
            remoteVideoTrack.get().addSink(binding.remoteVideo);
            if (state == RtpEndUserState.CONNECTED) {
                binding.appBarLayout.setVisibility(View.GONE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                binding.remoteVideo.setVisibility(View.GONE);
            }
            if (isPictureInPicture() && !requireRtpConnection().isMicrophoneEnabled()) {
                binding.pipLocalMicOffIndicator.setVisibility(View.VISIBLE);
            } else {
                binding.pipLocalMicOffIndicator.setVisibility(View.GONE);
            }
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            binding.remoteVideo.setVisibility(View.GONE);
            binding.pipLocalMicOffIndicator.setVisibility(View.GONE);
        }
    }

    private Optional<VideoTrack> getLocalVideoTrack() {
        final JingleRtpConnection connection = this.rtpConnectionReference != null ? this.rtpConnectionReference.get() : null;
        if (connection == null) {
            return Optional.absent();
        }
        return connection.getLocalVideoTrack();
    }

    private Optional<VideoTrack> getRemoteVideoTrack() {
        final JingleRtpConnection connection = this.rtpConnectionReference != null ? this.rtpConnectionReference.get() : null;
        if (connection == null) {
            return Optional.absent();
        }
        return connection.getRemoteVideoTrack();
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
        final String lastAction = intent.getStringExtra(EXTRA_LAST_ACTION);
        final String action = intent.getAction();
        final Set<Media> media = actionToMedia(lastAction == null ? action : lastAction);
        this.rtpConnectionReference = null;
        proposeJingleRtpSession(account, with, media);
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
        Log.d(Config.LOGTAG, "onJingleRtpConnectionUpdate(" + state + ")");
        if (END_CARD.contains(state)) {
            Log.d(Config.LOGTAG, "end card reached");
            releaseProximityWakeLock();
            runOnUiThread(() -> getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        }
        if (with.isBareJid()) {
            updateRtpSessionProposalState(account, with, state);
            return;
        }
        if (this.rtpConnectionReference == null) {
            if (END_CARD.contains(state)) {
                Log.d(Config.LOGTAG, "not reinitializing session");
                return;
            }
            //this happens when going from proposed session to actual session
            reInitializeActivityWithRunningRapSession(account, with, sessionId);
            return;
        }
        final AbstractJingleConnection.Id id = requireRtpConnection().getId();
        final Set<Media> media = getMedia();
        final Contact contact = getWith();
        if (account == id.account && id.with.equals(with) && id.sessionId.equals(sessionId)) {
            if (state == RtpEndUserState.ENDED) {
                finish();
                return;
            }
            runOnUiThread(() -> {
                updateStateDisplay(state, media);
                updateButtonConfiguration(state, media);
                updateVideoViews(state);
                updateProfilePicture(state, contact);
            });
            if (END_CARD.contains(state)) {
                final JingleRtpConnection rtpConnection = requireRtpConnection();
                resetIntent(account, with, state, rtpConnection.getMedia());
                releaseVideoTracks(rtpConnection);
                this.rtpConnectionReference = null;
            }
        } else {
            Log.d(Config.LOGTAG, "received update for other rtp session");
        }
    }

    @Override
    public void onAudioDeviceChanged(AppRTCAudioManager.AudioDevice selectedAudioDevice, Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
        Log.d(Config.LOGTAG, "onAudioDeviceChanged in activity: selected:" + selectedAudioDevice + ", available:" + availableAudioDevices);
        try {
            if (getMedia().contains(Media.VIDEO)) {
                Log.d(Config.LOGTAG, "nothing to do; in video mode");
                return;
            }
            final RtpEndUserState endUserState = requireRtpConnection().getEndUserState();
            if (endUserState == RtpEndUserState.CONNECTED) {
                final AppRTCAudioManager audioManager = requireRtpConnection().getAudioManager();
                updateInCallButtonConfigurationSpeaker(
                        audioManager.getSelectedAudioDevice(),
                        audioManager.getAudioDevices().size()
                );
            } else if (END_CARD.contains(endUserState)) {
                Log.d(Config.LOGTAG, "onAudioDeviceChanged() nothing to do because end card has been reached");
            } else {
                putProximityWakeLockInProperState();
            }
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
                updateProfilePicture(state);
            });
            resetIntent(account, with, state, actionToMedia(currentIntent.getAction()));
        }
    }

    private void resetIntent(final Bundle extras) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtras(extras);
        setIntent(intent);
    }

    private void resetIntent(final Account account, Jid with, final RtpEndUserState state, final Set<Media> media) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(EXTRA_WITH, with.asBareJid().toEscapedString());
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().toEscapedString());
        intent.putExtra(EXTRA_LAST_REPORTED_STATE, state.toString());
        intent.putExtra(EXTRA_LAST_ACTION, media.contains(Media.VIDEO) ? ACTION_MAKE_VIDEO_CALL : ACTION_MAKE_VOICE_CALL);
        setIntent(intent);
    }
}
