package eu.siacs.conversations.xmpp.jingle.transports;

import static eu.siacs.conversations.xmpp.jingle.WebRTCWrapper.buildConfiguration;
import static eu.siacs.conversations.xmpp.jingle.WebRTCWrapper.logDescription;

import android.content.Context;
import android.util.Log;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.IceServers;
import eu.siacs.conversations.xmpp.jingle.WebRTCWrapper;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.WebRTCDataChannelTransportInfo;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

public class WebRTCDataChannelTransport implements Transport {

    private static final int BUFFER_SIZE = 16_384;
    private static final int MAX_SENT_BUFFER = 256 * 1024;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ExecutorService localDescriptionExecutorService =
            Executors.newSingleThreadExecutor();

    private final AtomicBoolean readyToSentIceCandidates = new AtomicBoolean(false);
    private final Queue<IceCandidate> pendingOutgoingIceCandidates = new LinkedList<>();

    private final PipedOutputStream pipedOutputStream = new PipedOutputStream();
    private final WritableByteChannel writableByteChannel = Channels.newChannel(pipedOutputStream);
    private final PipedInputStream pipedInputStream = new PipedInputStream(BUFFER_SIZE);

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final CountDownLatch terminationLatch = new CountDownLatch(1);

    private final Queue<PeerConnection.PeerConnectionState> stateHistory = new LinkedList<>();

    private final XmppConnection xmppConnection;
    private final Account account;
    private PeerConnectionFactory peerConnectionFactory;
    private ListenableFuture<PeerConnection> peerConnectionFuture;

    private ListenableFuture<SessionDescription> localDescriptionFuture;

    private DataChannel dataChannel;

    private Callback transportCallback;

    private final PeerConnection.Observer peerConnectionObserver =
            new PeerConnection.Observer() {
                @Override
                public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                    Log.d(Config.LOGTAG, "onSignalChange(" + signalingState + ")");
                }

                @Override
                public void onConnectionChange(final PeerConnection.PeerConnectionState state) {
                    stateHistory.add(state);
                    Log.d(Config.LOGTAG, "onConnectionChange(" + state + ")");
                    if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                        if (connected.compareAndSet(false, true)) {
                            executorService.execute(() -> onIceConnectionConnected());
                        }
                    }
                    if (state == PeerConnection.PeerConnectionState.FAILED) {
                        final boolean neverConnected =
                                !stateHistory.contains(
                                        PeerConnection.PeerConnectionState.CONNECTED);
                        // we want to terminate the connection a) to properly fail if a connection
                        // drops during a transfer and b) to avoid race conditions if we find a
                        // connection after failure while waiting for the initiator to replace
                        // transport
                        executorService.execute(() -> terminate());
                        if (neverConnected) {
                            executorService.execute(() -> onIceConnectionFailed());
                        }
                    }
                }

                @Override
                public void onIceConnectionChange(
                        final PeerConnection.IceConnectionState newState) {}

                @Override
                public void onIceConnectionReceivingChange(boolean b) {}

                @Override
                public void onIceGatheringChange(
                        final PeerConnection.IceGatheringState iceGatheringState) {
                    Log.d(Config.LOGTAG, "onIceGatheringChange(" + iceGatheringState + ")");
                }

                @Override
                public void onIceCandidate(final IceCandidate iceCandidate) {
                    if (readyToSentIceCandidates.get()) {
                        WebRTCDataChannelTransport.this.onIceCandidate(
                                iceCandidate.sdpMid, iceCandidate.sdp);
                    } else {
                        pendingOutgoingIceCandidates.add(iceCandidate);
                    }
                }

                @Override
                public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

                @Override
                public void onAddStream(MediaStream mediaStream) {}

                @Override
                public void onRemoveStream(MediaStream mediaStream) {}

                @Override
                public void onDataChannel(final DataChannel dataChannel) {
                    Log.d(Config.LOGTAG, "onDataChannel()");
                    WebRTCDataChannelTransport.this.setDataChannel(dataChannel);
                }

                @Override
                public void onRenegotiationNeeded() {
                    Log.d(Config.LOGTAG, "onRenegotiationNeeded");
                }

                @Override
                public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
                    Log.d(Config.LOGTAG, "remote candidate selected: " + event.remote);
                    Log.d(Config.LOGTAG, "local candidate selected: " + event.local);
                }
            };

    private DataChannelWriter dataChannelWriter;

    private void onIceConnectionConnected() {
        this.transportCallback.onTransportEstablished();
    }

    private void onIceConnectionFailed() {
        this.transportCallback.onTransportSetupFailed();
    }

    private void setDataChannel(final DataChannel dataChannel) {
        Log.d(Config.LOGTAG, "the 'receiving' data channel has id " + dataChannel.id());
        this.dataChannel = dataChannel;
        this.dataChannel.registerObserver(
                new OnMessageObserver() {
                    @Override
                    public void onMessage(final DataChannel.Buffer buffer) {
                        Log.d(Config.LOGTAG, "onMessage() (the other one)");
                        try {
                            WebRTCDataChannelTransport.this.writableByteChannel.write(buffer.data);
                        } catch (final IOException e) {
                            Log.d(Config.LOGTAG, "error writing to output stream");
                        }
                    }
                });
    }

    protected void onIceCandidate(final String mid, final String sdp) {
        final var candidate = IceUdpTransportInfo.Candidate.fromSdpAttribute(sdp, null);
        this.transportCallback.onAdditionalCandidate(mid, candidate);
    }

    public WebRTCDataChannelTransport(
            final Context context,
            final XmppConnection xmppConnection,
            final Account account,
            final boolean initiator) {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setFieldTrials("WebRTC-BindUsingInterfaceName/Enabled/")
                        .createInitializationOptions());
        this.peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        this.xmppConnection = xmppConnection;
        this.account = account;
        this.peerConnectionFuture =
                Futures.transform(
                        getIceServers(),
                        iceServers -> createPeerConnection(iceServers, true),
                        MoreExecutors.directExecutor());
        if (initiator) {
            this.localDescriptionFuture = setLocalDescription();
        }
    }

    private ListenableFuture<List<PeerConnection.IceServer>> getIceServers() {
        if (Config.DISABLE_PROXY_LOOKUP) {
            return Futures.immediateFuture(Collections.emptyList());
        }
        if (xmppConnection.getFeatures().externalServiceDiscovery()) {
            final SettableFuture<List<PeerConnection.IceServer>> iceServerFuture =
                    SettableFuture.create();
            final IqPacket request = new IqPacket(IqPacket.TYPE.GET);
            request.setTo(this.account.getDomain());
            request.addChild("services", Namespace.EXTERNAL_SERVICE_DISCOVERY);
            xmppConnection.sendIqPacket(
                    request,
                    (account, response) -> {
                        final var iceServers = IceServers.parse(response);
                        if (iceServers.size() == 0) {
                            Log.w(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": no ICE server found "
                                            + response);
                        }
                        iceServerFuture.set(iceServers);
                    });
            return iceServerFuture;
        } else {
            return Futures.immediateFuture(Collections.emptyList());
        }
    }

    private PeerConnection createPeerConnection(
            final List<PeerConnection.IceServer> iceServers, final boolean trickle) {
        final PeerConnection.RTCConfiguration rtcConfig = buildConfiguration(iceServers, trickle);
        final PeerConnection peerConnection =
                requirePeerConnectionFactory()
                        .createPeerConnection(rtcConfig, peerConnectionObserver);
        if (peerConnection == null) {
            throw new IllegalStateException("Unable to create PeerConnection");
        }
        final var dataChannelInit = new DataChannel.Init();
        dataChannelInit.protocol = "xmpp-jingle";
        final var dataChannel = peerConnection.createDataChannel("test", dataChannelInit);
        this.dataChannelWriter = new DataChannelWriter(this.pipedInputStream, dataChannel);
        Log.d(Config.LOGTAG, "the 'sending' data channel has id " + dataChannel.id());
        new Thread(this.dataChannelWriter).start();
        return peerConnection;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        final var outputStream = new PipedOutputStream();
        this.pipedInputStream.connect(outputStream);
        this.dataChannelWriter.pipedInputStreamLatch.countDown();
        return outputStream;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final var inputStream = new PipedInputStream(BUFFER_SIZE);
        this.pipedOutputStream.connect(inputStream);
        return inputStream;
    }

    @Override
    public ListenableFuture<TransportInfo> asTransportInfo() {
        Preconditions.checkState(
                this.localDescriptionFuture != null,
                "Make sure you are setting initiator description first");
        return Futures.transform(
                asInitialTransportInfo(), info -> info, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<InitialTransportInfo> asInitialTransportInfo() {
        return Futures.transform(
                localDescriptionFuture,
                sdp ->
                        WebRTCDataChannelTransportInfo.of(
                                eu.siacs.conversations.xmpp.jingle.SessionDescription.parse(
                                        sdp.description)),
                MoreExecutors.directExecutor());
    }

    @Override
    public void readyToSentAdditionalCandidates() {
        readyToSentIceCandidates.set(true);
        while (this.pendingOutgoingIceCandidates.peek() != null) {
            final var candidate = pendingOutgoingIceCandidates.poll();
            if (candidate == null) {
                continue;
            }
            onIceCandidate(candidate.sdpMid, candidate.sdp);
        }
    }

    @Override
    public void terminate() {
        terminate(this.dataChannel);
        this.dataChannel = null;
        final var dataChannelWriter = this.dataChannelWriter;
        if (dataChannelWriter != null) {
            dataChannelWriter.close();
        }
        this.dataChannelWriter = null;
        final var future = this.peerConnectionFuture;
        if (future != null) {
            future.cancel(true);
        }
        try {
            final PeerConnection peerConnection = requirePeerConnection();
            terminate(peerConnection);
        } catch (final WebRTCWrapper.PeerConnectionNotInitialized e) {
            Log.d(Config.LOGTAG, "peer connection was not initialized during termination");
        }
        this.peerConnectionFuture = null;
        final var peerConnectionFactory = this.peerConnectionFactory;
        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
        }
        this.peerConnectionFactory = null;
        closeQuietly(this.pipedOutputStream);
        this.terminationLatch.countDown();
        Log.d(Config.LOGTAG, WebRTCDataChannelTransport.class.getSimpleName() + " terminated");
    }

    private static void closeQuietly(final OutputStream outputStream) {
        try {
            outputStream.close();
        } catch (final IOException ignored) {

        }
    }

    private static void terminate(final DataChannel dataChannel) {
        if (dataChannel == null) {
            Log.d(Config.LOGTAG, "nothing to terminate. data channel is already null");
            return;
        }
        try {
            dataChannel.close();
        } catch (final IllegalStateException e) {
            Log.w(Config.LOGTAG, "could not close data channel");
        }
        try {
            dataChannel.dispose();
        } catch (final IllegalStateException e) {
            Log.w(Config.LOGTAG, "could not dispose data channel");
        }
    }

    private static void terminate(final PeerConnection peerConnection) {
        if (peerConnection == null) {
            return;
        }
        try {
            peerConnection.dispose();
            Log.d(Config.LOGTAG, "terminated peer connection!");
        } catch (final IllegalStateException e) {
            Log.w(Config.LOGTAG, "could not dispose of peer connection");
        }
    }

    @Override
    public void setTransportCallback(final Callback callback) {
        this.transportCallback = callback;
    }

    @Override
    public void connect() {}

    @Override
    public CountDownLatch getTerminationLatch() {
        return this.terminationLatch;
    }

    synchronized ListenableFuture<SessionDescription> setLocalDescription() {
        return Futures.transformAsync(
                peerConnectionFuture,
                peerConnection -> {
                    if (peerConnection == null) {
                        return Futures.immediateFailedFuture(
                                new IllegalStateException("PeerConnection was null"));
                    }
                    final SettableFuture<SessionDescription> future = SettableFuture.create();
                    peerConnection.setLocalDescription(
                            new WebRTCWrapper.SetSdpObserver() {
                                @Override
                                public void onSetSuccess() {
                                    future.setFuture(getLocalDescriptionFuture(peerConnection));
                                }

                                @Override
                                public void onSetFailure(final String message) {
                                    future.setException(
                                            new WebRTCWrapper.FailureToSetDescriptionException(
                                                    message));
                                }
                            });
                    return future;
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<SessionDescription> getLocalDescriptionFuture(
            final PeerConnection peerConnection) {
        return Futures.submit(
                () -> {
                    final SessionDescription description = peerConnection.getLocalDescription();
                    WebRTCWrapper.logDescription(description);
                    return description;
                },
                localDescriptionExecutorService);
    }

    @Nonnull
    private PeerConnectionFactory requirePeerConnectionFactory() {
        final PeerConnectionFactory peerConnectionFactory = this.peerConnectionFactory;
        if (peerConnectionFactory == null) {
            throw new IllegalStateException("Make sure PeerConnectionFactory is initialized");
        }
        return peerConnectionFactory;
    }

    @Nonnull
    private PeerConnection requirePeerConnection() {
        final var future = this.peerConnectionFuture;
        if (future != null && future.isDone()) {
            try {
                return future.get();
            } catch (final InterruptedException | ExecutionException e) {
                throw new WebRTCWrapper.PeerConnectionNotInitialized();
            }
        } else {
            throw new WebRTCWrapper.PeerConnectionNotInitialized();
        }
    }

    public static List<IceCandidate> iceCandidatesOf(
            final String contentName,
            final IceUdpTransportInfo.Credentials credentials,
            final List<IceUdpTransportInfo.Candidate> candidates) {
        final ImmutableList.Builder<IceCandidate> iceCandidateBuilder =
                new ImmutableList.Builder<>();
        for (final IceUdpTransportInfo.Candidate candidate : candidates) {
            final String sdp;
            try {
                sdp = candidate.toSdpAttribute(credentials.ufrag);
            } catch (final IllegalArgumentException e) {
                continue;
            }
            // TODO mLneIndex should probably not be hard coded
            iceCandidateBuilder.add(new IceCandidate(contentName, 0, sdp));
        }
        return iceCandidateBuilder.build();
    }

    public void addIceCandidates(final List<IceCandidate> iceCandidates) {
        try {
            for (final var candidate : iceCandidates) {
                requirePeerConnection().addIceCandidate(candidate);
            }
        } catch (WebRTCWrapper.PeerConnectionNotInitialized e) {
            Log.w(Config.LOGTAG, "could not add ice candidate. peer connection is not initialized");
        }
    }

    public void setInitiatorDescription(
            final eu.siacs.conversations.xmpp.jingle.SessionDescription sessionDescription) {
        final var sdp =
                new SessionDescription(
                        SessionDescription.Type.OFFER, sessionDescription.toString());
        final var setFuture = setRemoteDescriptionFuture(sdp);
        this.localDescriptionFuture =
                Futures.transformAsync(
                        setFuture, v -> setLocalDescription(), MoreExecutors.directExecutor());
    }

    public void setResponderDescription(
            final eu.siacs.conversations.xmpp.jingle.SessionDescription sessionDescription) {
        Log.d(Config.LOGTAG, "setResponder description");
        final var sdp =
                new SessionDescription(
                        SessionDescription.Type.ANSWER, sessionDescription.toString());
        logDescription(sdp);
        setRemoteDescriptionFuture(sdp);
    }

    synchronized ListenableFuture<Void> setRemoteDescriptionFuture(
            final SessionDescription sessionDescription) {
        return Futures.transformAsync(
                this.peerConnectionFuture,
                peerConnection -> {
                    if (peerConnection == null) {
                        return Futures.immediateFailedFuture(
                                new IllegalStateException("PeerConnection was null"));
                    }
                    final SettableFuture<Void> future = SettableFuture.create();
                    peerConnection.setRemoteDescription(
                            new WebRTCWrapper.SetSdpObserver() {
                                @Override
                                public void onSetSuccess() {
                                    future.set(null);
                                }

                                @Override
                                public void onSetFailure(final String message) {
                                    future.setException(
                                            new WebRTCWrapper.FailureToSetDescriptionException(
                                                    message));
                                }
                            },
                            sessionDescription);
                    return future;
                },
                MoreExecutors.directExecutor());
    }

    private static class DataChannelWriter implements Runnable {

        private final CountDownLatch pipedInputStreamLatch = new CountDownLatch(1);
        private final CountDownLatch dataChannelLatch = new CountDownLatch(1);
        private final AtomicBoolean isSending = new AtomicBoolean(true);
        private final InputStream inputStream;
        private final DataChannel dataChannel;

        private DataChannelWriter(InputStream inputStream, DataChannel dataChannel) {
            this.inputStream = inputStream;
            this.dataChannel = dataChannel;
            final StateChangeObserver stateChangeObserver =
                    new StateChangeObserver() {

                        @Override
                        public void onStateChange() {
                            if (dataChannel.state() == DataChannel.State.OPEN) {
                                dataChannelLatch.countDown();
                            }
                        }
                    };
            this.dataChannel.registerObserver(stateChangeObserver);
        }

        public void run() {
            try {
                this.pipedInputStreamLatch.await();
                this.dataChannelLatch.await();
                final var buffer = new byte[4096];
                while (isSending.get()) {
                    final long bufferedAmount = dataChannel.bufferedAmount();
                    if (bufferedAmount > MAX_SENT_BUFFER) {
                        Thread.sleep(50);
                        continue;
                    }
                    final int count = this.inputStream.read(buffer);
                    if (count < 0) {
                        Log.d(Config.LOGTAG, "DataChannelWriter reached EOF");
                        return;
                    }
                    dataChannel.send(
                            new DataChannel.Buffer(ByteBuffer.wrap(buffer, 0, count), true));
                }
            } catch (final InterruptedException | InterruptedIOException e) {
                if (isSending.get()) {
                    Log.w(Config.LOGTAG, "DataChannelWriter got interrupted while sending", e);
                }
            } catch (final IOException e) {
                Log.d(Config.LOGTAG, "DataChannelWriter terminated", e);
            } finally {
                Closeables.closeQuietly(inputStream);
            }
        }

        public void close() {
            this.isSending.set(false);
            terminate(this.dataChannel);
        }
    }

    private abstract static class StateChangeObserver implements DataChannel.Observer {

        @Override
        public void onBufferedAmountChange(final long change) {}

        @Override
        public void onMessage(final DataChannel.Buffer buffer) {}
    }

    private abstract static class OnMessageObserver implements DataChannel.Observer {

        @Override
        public void onBufferedAmountChange(long l) {}

        @Override
        public void onStateChange() {}
    }
}
