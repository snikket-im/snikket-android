package eu.siacs.conversations.xmpp.jingle;

import android.content.Context;
import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Capturer;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import eu.siacs.conversations.Config;

public class WebRTCWrapper {

    private VideoTrack localVideoTrack = null;
    private VideoTrack remoteVideoTrack = null;

    private final EventCallback eventCallback;

    private final PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(Config.LOGTAG, "onSignalingChange(" + signalingState + ")");

        }

        @Override
        public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
            eventCallback.onConnectionChange(newState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
            Log.d(Config.LOGTAG, "remote candidate selected: " + event.remote);
            Log.d(Config.LOGTAG, "local candidate selected: " + event.local);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            eventCallback.onIceCandidate(iceCandidate);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(Config.LOGTAG, "onAddStream");
            for (AudioTrack audioTrack : mediaStream.audioTracks) {
                Log.d(Config.LOGTAG, "remote? - audioTrack enabled:" + audioTrack.enabled() + " state=" + audioTrack.state());
            }
            final List<VideoTrack> videoTracks = mediaStream.videoTracks;
            if (videoTracks.size() > 0) {
                Log.d(Config.LOGTAG, "more than zero remote video tracks found. using first");
                remoteVideoTrack = videoTracks.get(0);
            }
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(Config.LOGTAG, "onAddTrack()");

        }
    };
    @Nullable
    private PeerConnection peerConnection = null;

    public WebRTCWrapper(final EventCallback eventCallback) {
        this.eventCallback = eventCallback;
    }

    public void setup(final Context context) {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        );
    }

    public void initializePeerConnection(final List<PeerConnection.IceServer> iceServers) throws InitializationException {
        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        CameraVideoCapturer capturer = null;
        Camera1Enumerator camera1Enumerator = new Camera1Enumerator();
        for (String deviceName : camera1Enumerator.getDeviceNames()) {
            Log.d(Config.LOGTAG, "camera device name: " + deviceName);
            if (camera1Enumerator.isFrontFacing(deviceName)) {
                capturer = camera1Enumerator.createCapturer(deviceName, new CameraVideoCapturer.CameraEventsHandler() {
                    @Override
                    public void onCameraError(String s) {

                    }

                    @Override
                    public void onCameraDisconnected() {

                    }

                    @Override
                    public void onCameraFreezed(String s) {

                    }

                    @Override
                    public void onCameraOpening(String s) {
                        Log.d(Config.LOGTAG, "onCameraOpening");
                    }

                    @Override
                    public void onFirstFrameAvailable() {
                        Log.d(Config.LOGTAG, "onFirstFrameAvailable");
                    }

                    @Override
                    public void onCameraClosed() {

                    }
                });
            }
        }

        /*if (capturer != null) {
            capturer.initialize();
            Log.d(Config.LOGTAG,"start capturing");
            capturer.startCapture(800,600,30);
        }*/

        final VideoSource videoSource = peerConnectionFactory.createVideoSource(false);
        final VideoTrack videoTrack = peerConnectionFactory.createVideoTrack("my-video-track", videoSource);

        final AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());

        final AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("my-audio-track", audioSource);
        Log.d(Config.LOGTAG, "audioTrack enabled:" + audioTrack.enabled() + " state=" + audioTrack.state());
        final MediaStream stream = peerConnectionFactory.createLocalMediaStream("my-media-stream");
        stream.addTrack(audioTrack);
        //stream.addTrack(videoTrack);

        this.localVideoTrack = videoTrack;

        final PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(iceServers, peerConnectionObserver);
        if (peerConnection == null) {
            throw new InitializationException("Unable to create PeerConnection");
        }
        peerConnection.addStream(stream);
        peerConnection.setAudioPlayout(true);
        peerConnection.setAudioRecording(true);
        this.peerConnection = peerConnection;
    }

    public void closeOrThrow() {
        requirePeerConnection().close();
    }

    public void close() {
        final PeerConnection peerConnection = this.peerConnection;
        if (peerConnection != null) {
            peerConnection.close();
        }
    }


    public ListenableFuture<SessionDescription> createOffer() {
        return Futures.transformAsync(getPeerConnectionFuture(), peerConnection -> {
            final SettableFuture<SessionDescription> future = SettableFuture.create();
            peerConnection.createOffer(new CreateSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    future.set(sessionDescription);
                }

                @Override
                public void onCreateFailure(String s) {
                    future.setException(new IllegalStateException("Unable to create offer: " + s));
                }
            }, new MediaConstraints());
            return future;
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<SessionDescription> createAnswer() {
        return Futures.transformAsync(getPeerConnectionFuture(), peerConnection -> {
            final SettableFuture<SessionDescription> future = SettableFuture.create();
            peerConnection.createAnswer(new CreateSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    future.set(sessionDescription);
                }

                @Override
                public void onCreateFailure(String s) {
                    future.setException(new IllegalStateException("Unable to create answer: " + s));
                }
            }, new MediaConstraints());
            return future;
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> setLocalDescription(final SessionDescription sessionDescription) {
        return Futures.transformAsync(getPeerConnectionFuture(), peerConnection -> {
            final SettableFuture<Void> future = SettableFuture.create();
            peerConnection.setLocalDescription(new SetSdpObserver() {
                @Override
                public void onSetSuccess() {
                    future.set(null);
                }

                @Override
                public void onSetFailure(String s) {
                    future.setException(new IllegalArgumentException("unable to set local session description: " + s));

                }
            }, sessionDescription);
            return future;
        }, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> setRemoteDescription(final SessionDescription sessionDescription) {
        return Futures.transformAsync(getPeerConnectionFuture(), peerConnection -> {
            final SettableFuture<Void> future = SettableFuture.create();
            peerConnection.setRemoteDescription(new SetSdpObserver() {
                @Override
                public void onSetSuccess() {
                    future.set(null);
                }

                @Override
                public void onSetFailure(String s) {
                    future.setException(new IllegalArgumentException("unable to set remote session description: " + s));

                }
            }, sessionDescription);
            return future;
        }, MoreExecutors.directExecutor());
    }

    @Nonnull
    private ListenableFuture<PeerConnection> getPeerConnectionFuture() {
        final PeerConnection peerConnection = this.peerConnection;
        if (peerConnection == null) {
            return Futures.immediateFailedFuture(new IllegalStateException("initialize PeerConnection first"));
        } else {
            return Futures.immediateFuture(peerConnection);
        }
    }

    public void addIceCandidate(IceCandidate iceCandidate) {
        requirePeerConnection().addIceCandidate(iceCandidate);
    }

    public PeerConnection.PeerConnectionState getState() {
        return requirePeerConnection().connectionState();
    }

    private PeerConnection requirePeerConnection() {
        final PeerConnection peerConnection = this.peerConnection;
        if (peerConnection == null) {
            throw new IllegalStateException("initialize PeerConnection first");
        }
        return peerConnection;
    }

    private static abstract class SetSdpObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(org.webrtc.SessionDescription sessionDescription) {
            throw new IllegalStateException("Not able to use SetSdpObserver");
        }

        @Override
        public void onCreateFailure(String s) {
            throw new IllegalStateException("Not able to use SetSdpObserver");
        }

    }

    private static abstract class CreateSdpObserver implements SdpObserver {


        @Override
        public void onSetSuccess() {
            throw new IllegalStateException("Not able to use CreateSdpObserver");
        }


        @Override
        public void onSetFailure(String s) {
            throw new IllegalStateException("Not able to use CreateSdpObserver");
        }
    }

    public static class InitializationException extends Exception {

        private InitializationException(String message) {
            super(message);
        }
    }

    public interface EventCallback {
        void onIceCandidate(IceCandidate iceCandidate);

        void onConnectionChange(PeerConnection.PeerConnectionState newState);
    }
}
