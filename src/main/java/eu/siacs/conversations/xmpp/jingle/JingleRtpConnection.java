package eu.siacs.conversations.xmpp.jingle;

import android.content.Intent;
import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ui.RtpSessionActivity;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import rocks.xmpp.addr.Jid;

public class JingleRtpConnection extends AbstractJingleConnection implements WebRTCWrapper.EventCallback {

    private static final Map<State, Collection<State>> VALID_TRANSITIONS;

    static {
        final ImmutableMap.Builder<State, Collection<State>> transitionBuilder = new ImmutableMap.Builder<>();
        transitionBuilder.put(State.NULL, ImmutableList.of(State.PROPOSED, State.SESSION_INITIALIZED));
        transitionBuilder.put(State.PROPOSED, ImmutableList.of(State.ACCEPTED, State.PROCEED));
        transitionBuilder.put(State.PROCEED, ImmutableList.of(State.SESSION_INITIALIZED));
        transitionBuilder.put(State.SESSION_INITIALIZED, ImmutableList.of(State.SESSION_ACCEPTED));
        VALID_TRANSITIONS = transitionBuilder.build();
    }

    private final WebRTCWrapper webRTCWrapper = new WebRTCWrapper(this);
    private final ArrayDeque<IceCandidate> pendingIceCandidates = new ArrayDeque<>();
    private State state = State.NULL;
    private RtpContentMap initiatorRtpContentMap;
    private RtpContentMap responderRtpContentMap;


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
            case TRANSPORT_INFO:
                receiveTransportInfo(jinglePacket);
                break;
            case SESSION_ACCEPT:
                receiveSessionAccept(jinglePacket);
                break;
            default:
                Log.d(Config.LOGTAG, String.format("%s: received unhandled jingle action %s", id.account.getJid().asBareJid(), jinglePacket.getAction()));
                break;
        }
    }

    private void receiveTransportInfo(final JinglePacket jinglePacket) {
        if (isInState(State.SESSION_INITIALIZED, State.SESSION_ACCEPTED)) {
            final RtpContentMap contentMap;
            try {
                contentMap = RtpContentMap.of(jinglePacket);
            } catch (IllegalArgumentException | NullPointerException e) {
                Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": improperly formatted contents", e);
                return;
            }
            final RtpContentMap rtpContentMap = isInitiator() ? this.initiatorRtpContentMap : this.responderRtpContentMap;
            final Group originalGroup = rtpContentMap != null ? rtpContentMap.group : null;
            final List<String> identificationTags = originalGroup == null ? Collections.emptyList() : originalGroup.getIdentificationTags();
            if (identificationTags.size() == 0) {
                Log.w(Config.LOGTAG, id.account.getJid().asBareJid() + ": no identification tags found in initial offer. we won't be able to calculate mLineIndices");
            }
            for (final Map.Entry<String, RtpContentMap.DescriptionTransport> content : contentMap.contents.entrySet()) {
                final String ufrag = content.getValue().transport.getAttribute("ufrag");
                for (final IceUdpTransportInfo.Candidate candidate : content.getValue().transport.getCandidates()) {
                    final String sdp = candidate.toSdpAttribute(ufrag);
                    final String sdpMid = content.getKey();
                    final int mLineIndex = identificationTags.indexOf(sdpMid);
                    final IceCandidate iceCandidate = new IceCandidate(sdpMid, mLineIndex, sdp);
                    Log.d(Config.LOGTAG, "received candidate: " + iceCandidate);
                    if (isInState(State.SESSION_ACCEPTED)) {
                        this.webRTCWrapper.addIceCandidate(iceCandidate);
                    } else {
                        this.pendingIceCandidates.push(iceCandidate);
                    }
                }
            }
        } else {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": received transport info while in state=" + this.state);
        }
    }

    private void receiveSessionInitiate(final JinglePacket jinglePacket) {
        if (isInitiator()) {
            Log.d(Config.LOGTAG, String.format("%s: received session-initiate even though we were initiating", id.account.getJid().asBareJid()));
            //TODO respond with out-of-order
            return;
        }
        final RtpContentMap contentMap;
        try {
            contentMap = RtpContentMap.of(jinglePacket);
            contentMap.requireContentDescriptions();
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": improperly formatted contents", e);
            return;
        }
        Log.d(Config.LOGTAG, "processing session-init with " + contentMap.contents.size() + " contents");
        final State oldState = this.state;
        if (transition(State.SESSION_INITIALIZED)) {
            this.initiatorRtpContentMap = contentMap;
            if (oldState == State.PROCEED) {
                Log.d(Config.LOGTAG, "automatically accepting");
                sendSessionAccept();
            } else {
                Log.d(Config.LOGTAG, "start ringing");
                //TODO start ringing
            }
        } else {
            Log.d(Config.LOGTAG, String.format("%s: received session-initiate while in state %s", id.account.getJid().asBareJid(), state));
        }
    }

    private void receiveSessionAccept(final JinglePacket jinglePacket) {
        if (!isInitiator()) {
            Log.d(Config.LOGTAG, String.format("%s: received session-accept even though we were responding", id.account.getJid().asBareJid()));
            //TODO respond with out-of-order
            return;
        }
        final RtpContentMap contentMap;
        try {
            contentMap = RtpContentMap.of(jinglePacket);
            contentMap.requireContentDescriptions();
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": improperly formatted contents", e);
            return;
        }
        Log.d(Config.LOGTAG, "processing session-accept with " + contentMap.contents.size() + " contents");
        if (transition(State.SESSION_ACCEPTED)) {
            receiveSessionAccept(contentMap);
        } else {
            Log.d(Config.LOGTAG, String.format("%s: received session-accept while in state %s", id.account.getJid().asBareJid(), state));
            //TODO out-of-order
        }
    }

    private void receiveSessionAccept(final RtpContentMap contentMap) {
        this.responderRtpContentMap = contentMap;
        org.webrtc.SessionDescription answer = new org.webrtc.SessionDescription(
                org.webrtc.SessionDescription.Type.ANSWER,
                SessionDescription.of(contentMap).toString()
        );
        try {
            this.webRTCWrapper.setRemoteDescription(answer).get();
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "unable to receive session accept", e);
        }
    }

    private void sendSessionAccept() {
        final RtpContentMap rtpContentMap = this.initiatorRtpContentMap;
        if (rtpContentMap == null) {
            throw new IllegalStateException("initiator RTP Content Map has not been set");
        }
        setupWebRTC();
        final org.webrtc.SessionDescription offer = new org.webrtc.SessionDescription(
                org.webrtc.SessionDescription.Type.OFFER,
                SessionDescription.of(rtpContentMap).toString()
        );
        try {
            this.webRTCWrapper.setRemoteDescription(offer).get();
            org.webrtc.SessionDescription webRTCSessionDescription = this.webRTCWrapper.createAnswer().get();
            final SessionDescription sessionDescription = SessionDescription.parse(webRTCSessionDescription.description);
            final RtpContentMap respondingRtpContentMap = RtpContentMap.of(sessionDescription);
            sendSessionAccept(respondingRtpContentMap);
            this.webRTCWrapper.setLocalDescription(webRTCSessionDescription);
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "unable to send session accept", e);

        }
    }

    private void sendSessionAccept(final RtpContentMap rtpContentMap) {
        this.responderRtpContentMap = rtpContentMap;
        this.transitionOrThrow(State.SESSION_ACCEPTED);
        final JinglePacket sessionAccept = rtpContentMap.toJinglePacket(JinglePacket.Action.SESSION_ACCEPT, id.sessionId);
        Log.d(Config.LOGTAG, sessionAccept.toString());
        send(sessionAccept);
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
        //TODO we can use initiator logic here
        if (originatedFromMyself) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": saw proposal from mysql. ignoring");
        } else if (transition(State.PROPOSED)) {
            startRinging();
        } else {
            Log.d(Config.LOGTAG, id.account.getJid() + ": ignoring session proposal because already in " + state);
        }
    }

    private void startRinging() {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": received call from " + id.with + ". start ringing");
        xmppConnectionService.getNotificationService().showIncomingCallNotification(id);
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
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": prepare session-initiate");
        setupWebRTC();
        try {
            org.webrtc.SessionDescription webRTCSessionDescription = this.webRTCWrapper.createOffer().get();
            final SessionDescription sessionDescription = SessionDescription.parse(webRTCSessionDescription.description);
            Log.d(Config.LOGTAG, "description: " + webRTCSessionDescription.description);
            final RtpContentMap rtpContentMap = RtpContentMap.of(sessionDescription);
            sendSessionInitiate(rtpContentMap);
            this.webRTCWrapper.setLocalDescription(webRTCSessionDescription).get();
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "unable to sendSessionInitiate", e);
        }
    }

    private void sendSessionInitiate(RtpContentMap rtpContentMap) {
        this.initiatorRtpContentMap = rtpContentMap;
        this.transitionOrThrow(State.SESSION_INITIALIZED);
        final JinglePacket sessionInitiate = rtpContentMap.toJinglePacket(JinglePacket.Action.SESSION_INITIATE, id.sessionId);
        Log.d(Config.LOGTAG, sessionInitiate.toString());
        send(sessionInitiate);
    }

    private void sendTransportInfo(final String contentName, IceUdpTransportInfo.Candidate candidate) {
        final RtpContentMap transportInfo;
        try {
            final RtpContentMap rtpContentMap = isInitiator() ? this.initiatorRtpContentMap : this.responderRtpContentMap;
            transportInfo = rtpContentMap.transportInfo(contentName, candidate);
        } catch (Exception e) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": unable to prepare transport-info from candidate for content=" + contentName);
            return;
        }
        final JinglePacket jinglePacket = transportInfo.toJinglePacket(JinglePacket.Action.TRANSPORT_INFO, id.sessionId);
        Log.d(Config.LOGTAG, jinglePacket.toString());
        send(jinglePacket);
    }

    private void send(final JinglePacket jinglePacket) {
        jinglePacket.setTo(id.with);
        //TODO track errors
        xmppConnectionService.sendIqPacket(id.account, jinglePacket, null);
    }

    public RtpEndUserState getEndUserState() {
        switch (this.state) {
            case PROPOSED:
                if (isInitiator()) {
                    return RtpEndUserState.RINGING;
                } else {
                    return RtpEndUserState.INCOMING_CALL;
                }
            case PROCEED:
                if (isInitiator()) {
                    return RtpEndUserState.CONNECTING;
                } else {
                    return RtpEndUserState.ACCEPTING_CALL;
                }
            case SESSION_INITIALIZED:
                return RtpEndUserState.CONNECTING;
            case SESSION_ACCEPTED:
                final PeerConnection.PeerConnectionState state = webRTCWrapper.getState();
                if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                    return RtpEndUserState.CONNECTED;
                } else if (state == PeerConnection.PeerConnectionState.NEW || state == PeerConnection.PeerConnectionState.CONNECTING) {
                    return RtpEndUserState.CONNECTING;
                } else if (state == PeerConnection.PeerConnectionState.CLOSED) {
                    return RtpEndUserState.ENDING_CALL;
                } else {
                    return RtpEndUserState.FAILED;
                }
        }
        return RtpEndUserState.FAILED;
    }


    public void acceptCall() {
        switch (this.state) {
            case PROPOSED:
                acceptCallFromProposed();
                break;
            case SESSION_INITIALIZED:
                acceptCallFromSessionInitialized();
                break;
            default:
                throw new IllegalStateException("Can not pick up call from " + this.state);
        }
    }

    public void rejectCall() {
        Log.d(Config.LOGTAG, "todo rejecting call");
        xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
    }

    public void endCall() {
        if (isInState(State.SESSION_INITIALIZED, State.SESSION_ACCEPTED)) {
            webRTCWrapper.close();
        } else {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": called 'endCall' while in state " + this.state);
        }
    }

    private void setupWebRTC() {
        this.webRTCWrapper.setup(this.xmppConnectionService);
        this.webRTCWrapper.initializePeerConnection();
    }

    private void acceptCallFromProposed() {
        transitionOrThrow(State.PROCEED);
        xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
        final MessagePacket messagePacket = new MessagePacket();
        messagePacket.setTo(id.with);
        //Note that Movim needs 'accept', correct is 'proceed' https://github.com/movim/movim/issues/916
        messagePacket.addChild("proceed", Namespace.JINGLE_MESSAGE).setAttribute("id", id.sessionId);
        Log.d(Config.LOGTAG, messagePacket.toString());
        xmppConnectionService.sendMessagePacket(id.account, messagePacket);
    }

    private void acceptCallFromSessionInitialized() {
        xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
        throw new IllegalStateException("accepting from this state has not been implemented yet");
    }

    private synchronized boolean isInState(State... state) {
        return Arrays.asList(state).contains(this.state);
    }

    private synchronized boolean transition(final State target) {
        final Collection<State> validTransitions = VALID_TRANSITIONS.get(this.state);
        if (validTransitions != null && validTransitions.contains(target)) {
            this.state = target;
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": transitioned into " + target);
            updateEndUserState();
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

    @Override
    public void onIceCandidate(final IceCandidate iceCandidate) {
        final IceUdpTransportInfo.Candidate candidate = IceUdpTransportInfo.Candidate.fromSdpAttribute(iceCandidate.sdp);
        Log.d(Config.LOGTAG, "sending candidate: " + iceCandidate.toString());
        sendTransportInfo(iceCandidate.sdpMid, candidate);
    }

    @Override
    public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
        Log.d(Config.LOGTAG,id.account.getJid().asBareJid()+": PeerConnectionState changed to "+newState);
        updateEndUserState();
    }

    private void updateEndUserState() {
        xmppConnectionService.notifyJingleRtpConnectionUpdate(id.account, id.with, getEndUserState());
    }
}
