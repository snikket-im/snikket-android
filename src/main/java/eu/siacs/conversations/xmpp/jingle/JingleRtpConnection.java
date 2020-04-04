package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;
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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import rocks.xmpp.addr.Jid;

public class JingleRtpConnection extends AbstractJingleConnection {

    private static final Map<State, Collection<State>> VALID_TRANSITIONS;

    static {
        final ImmutableMap.Builder<State, Collection<State>> transitionBuilder = new ImmutableMap.Builder<>();
        transitionBuilder.put(State.NULL, ImmutableList.of(State.PROPOSED, State.SESSION_INITIALIZED));
        transitionBuilder.put(State.PROPOSED, ImmutableList.of(State.ACCEPTED, State.PROCEED));
        transitionBuilder.put(State.PROCEED, ImmutableList.of(State.SESSION_INITIALIZED));
        VALID_TRANSITIONS = transitionBuilder.build();
    }

    private State state = State.NULL;


    public JingleRtpConnection(JingleConnectionManager jingleConnectionManager, Id id, Jid initiator) {
        super(jingleConnectionManager, id, initiator);
    }

    @Override
    void deliverPacket(final JinglePacket jinglePacket) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": packet delivered to JingleRtpConnection");
        switch (jinglePacket.getAction()) {
            case SESSION_INITIATE:
                receiveSessionInitiate(jinglePacket);
                break;
            default:
                Log.d(Config.LOGTAG, String.format("%s: received unhandled jingle action %s", id.account.getJid().asBareJid(), jinglePacket.getAction()));
                break;
        }
    }

    private void receiveSessionInitiate(final JinglePacket jinglePacket) {
        if (isInitiator()) {
            Log.d(Config.LOGTAG, String.format("%s: received session-initiate even though we were initiating", id.account.getJid().asBareJid()));
            //TODO respond with out-of-order
            return;
        }
        final Map<String, DescriptionTransport> contents;
        try {
            contents = DescriptionTransport.of(jinglePacket.getJingleContents());
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": improperly formatted contents", e);
            return;
        }
        Log.d(Config.LOGTAG, "processing session-init with " + contents.size() + " contents");
        final State oldState = this.state;
        if (transition(State.SESSION_INITIALIZED)) {
            if (oldState == State.PROCEED) {
                processContents(contents);
                sendSessionAccept();
            } else {
                //TODO start ringing
            }
        } else {
            Log.d(Config.LOGTAG, String.format("%s: received session-initiate while in state %s", id.account.getJid().asBareJid(), state));
        }
    }

    private void processContents(final Map<String, DescriptionTransport> contents) {
        for (Map.Entry<String, DescriptionTransport> content : contents.entrySet()) {
            final DescriptionTransport descriptionTransport = content.getValue();
            final RtpDescription rtpDescription = descriptionTransport.description;
            Log.d(Config.LOGTAG, "receive content with name " + content.getKey() + " and media=" + rtpDescription.getMedia());
            for (RtpDescription.PayloadType payloadType : rtpDescription.getPayloadTypes()) {
                Log.d(Config.LOGTAG, "payload type: " + payloadType.toString());
            }
            for (RtpDescription.RtpHeaderExtension extension : rtpDescription.getHeaderExtensions()) {
                Log.d(Config.LOGTAG, "extension: " + extension.toString());
            }
            final IceUdpTransportInfo iceUdpTransportInfo = descriptionTransport.transport;
            Log.d(Config.LOGTAG, "transport: " + descriptionTransport.transport);
            Log.d(Config.LOGTAG, "fingerprint " + iceUdpTransportInfo.getFingerprint());
        }
    }

    void deliveryMessage(final Jid from, final Element message) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": delivered message to JingleRtpConnection " + message);
        switch (message.getName()) {
            case "propose":
                receivePropose(from, message);
                break;
            case "proceed":
                receiveProceed(from, message);
            default:
                break;
        }
    }

    private void receivePropose(final Jid from, final Element propose) {
        final boolean originatedFromMyself = from.asBareJid().equals(id.account.getJid().asBareJid());
        if (originatedFromMyself) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": saw proposal from mysql. ignoring");
        } else if (transition(State.PROPOSED)) {
            //TODO start ringing or something
            pickUpCall();
        } else {
            Log.d(Config.LOGTAG, id.account.getJid() + ": ignoring session proposal because already in " + state);
        }
    }

    private void receiveProceed(final Jid from, final Element proceed) {
        if (from.equals(id.with)) {
            if (isInitiator()) {
                if (transition(State.PROCEED)) {
                    this.sendSessionInitiate();
                } else {
                    Log.d(Config.LOGTAG, String.format("%s: ignoring proceed because already in %s", id.account.getJid().asBareJid(), this.state));
                }
            } else {
                Log.d(Config.LOGTAG, String.format("%s: ignoring proceed because we were not initializing", id.account.getJid().asBareJid()));
            }
        } else {
            Log.d(Config.LOGTAG, String.format("%s: ignoring proceed from %s. was expected from %s", id.account.getJid().asBareJid(), from, id.with));
        }
    }

    private void sendSessionInitiate() {
        setupWebRTC();
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": sending session-initiate");
    }

    private void sendSessionAccept() {
        Log.d(Config.LOGTAG, "sending session-accept");
    }

    public void pickUpCall() {
        switch (this.state) {
            case PROPOSED:
                pickupCallFromProposed();
                break;
            case SESSION_INITIALIZED:
                pickupCallFromSessionInitialized();
                break;
            default:
                throw new IllegalStateException("Can not pick up call from " + this.state);
        }
    }

    private void setupWebRTC() {
        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(xmppConnectionService).createInitializationOptions()
        );
        final PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        PeerConnectionFactory peerConnectionFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        final AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());

        final AudioTrack audioTrack = peerConnectionFactory.createAudioTrack("my-audio-track", audioSource);
        final MediaStream stream = peerConnectionFactory.createLocalMediaStream("my-media-stream");
        stream.addTrack(audioTrack);


        PeerConnection peer = peerConnectionFactory.createPeerConnection(Collections.emptyList(), new PeerConnection.Observer() {
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
        });

        peer.addStream(stream);

        peer.createOffer(new SdpObserver() {

            @Override
            public void onCreateSuccess(org.webrtc.SessionDescription description) {
                final SessionDescription sessionDescription = SessionDescription.parse(description.description);
                for (SessionDescription.Media media : sessionDescription.media) {
                    Log.d(Config.LOGTAG, "media: " + media.protocol);
                    for (SessionDescription.Attribute attribute : media.attributes) {
                        Log.d(Config.LOGTAG, "attribute key=" + attribute.key + ", value=" + attribute.value);
                    }
                }
                Log.d(Config.LOGTAG, sessionDescription.toString());
            }

            @Override
            public void onSetSuccess() {

            }

            @Override
            public void onCreateFailure(String s) {

            }

            @Override
            public void onSetFailure(String s) {

            }
        }, new MediaConstraints());
    }

    private void pickupCallFromProposed() {
        transitionOrThrow(State.PROCEED);
        final MessagePacket messagePacket = new MessagePacket();
        messagePacket.setTo(id.with);
        //Note that Movim needs 'accept', correct is 'proceed' https://github.com/movim/movim/issues/916
        messagePacket.addChild("proceed", Namespace.JINGLE_MESSAGE).setAttribute("id", id.sessionId);
        Log.d(Config.LOGTAG, messagePacket.toString());
        xmppConnectionService.sendMessagePacket(id.account, messagePacket);
    }

    private void pickupCallFromSessionInitialized() {

    }

    private synchronized boolean transition(final State target) {
        final Collection<State> validTransitions = VALID_TRANSITIONS.get(this.state);
        if (validTransitions != null && validTransitions.contains(target)) {
            this.state = target;
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": transitioned into " + target);
            return true;
        } else {
            return false;
        }
    }

    public void transitionOrThrow(final State target) {
        if (!transition(target)) {
            throw new IllegalStateException(String.format("Unable to transition from %s to %s", this.state, target));
        }
    }

    public static class DescriptionTransport {
        private final RtpDescription description;
        private final IceUdpTransportInfo transport;

        public DescriptionTransport(final RtpDescription description, final IceUdpTransportInfo transport) {
            this.description = description;
            this.transport = transport;
        }

        public static DescriptionTransport of(final Content content) {
            final GenericDescription description = content.getDescription();
            final GenericTransportInfo transportInfo = content.getTransport();
            final RtpDescription rtpDescription;
            final IceUdpTransportInfo iceUdpTransportInfo;
            if (description instanceof RtpDescription) {
                rtpDescription = (RtpDescription) description;
            } else {
                Log.d(Config.LOGTAG, "description was " + description);
                throw new IllegalArgumentException("Content does not contain RtpDescription");
            }
            if (transportInfo instanceof IceUdpTransportInfo) {
                iceUdpTransportInfo = (IceUdpTransportInfo) transportInfo;
            } else {
                throw new IllegalArgumentException("Content does not contain ICE-UDP transport");
            }
            return new DescriptionTransport(rtpDescription, iceUdpTransportInfo);
        }

        public static Map<String, DescriptionTransport> of(final Map<String, Content> contents) {
            return ImmutableMap.copyOf(Maps.transformValues(contents, new Function<Content, DescriptionTransport>() {
                @NullableDecl
                @Override
                public DescriptionTransport apply(@NullableDecl Content content) {
                    return content == null ? null : of(content);
                }
            }));
        }
    }

}
