package eu.siacs.conversations.xmpp.jingle;

import android.content.Context;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WebRTCWrapper {

    private final EventCallback eventCallback;

    private final PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

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

    public void initializePeerConnection() {
        final PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        final AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());

        final AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("my-audio-track", audioSource);
        final MediaStream stream = peerConnectionFactory.createLocalMediaStream("my-media-stream");
        stream.addTrack(audioTrack);


        final List<PeerConnection.IceServer> iceServers = ImmutableList.of(
                PeerConnection.IceServer.builder("stun:xmpp.conversations.im:3478").createIceServer()
        );
        final PeerConnection peerConnection = peerConnectionFactory.createPeerConnection(iceServers, peerConnectionObserver);
        if (peerConnection == null) {
            throw new IllegalStateException("Unable to create PeerConnection");
        }
        peerConnection.addStream(stream);
        this.peerConnection = peerConnection;
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
                    future.setException(new IllegalArgumentException("unable to set local session description: "+s));

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
                    future.setException(new IllegalArgumentException("unable to set remote session description: "+s));

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
        final PeerConnection peerConnection = this.peerConnection;
        if (peerConnection == null) {
            throw new IllegalStateException("initialize PeerConnection first");
        }
        peerConnection.addIceCandidate(iceCandidate);
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

    public interface EventCallback {
        void onIceCandidate(IceCandidate iceCandidate);
    }
}
