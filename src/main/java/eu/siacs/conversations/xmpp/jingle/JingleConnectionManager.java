package eu.siacs.conversations.xmpp.jingle;

import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.FileTransferDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Propose;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import rocks.xmpp.addr.Jid;

public class JingleConnectionManager extends AbstractConnectionManager {
    private final HashMap<RtpSessionProposal, DeviceDiscoveryState> rtpSessionProposals = new HashMap<>();
    private final Map<AbstractJingleConnection.Id, AbstractJingleConnection> connections = new ConcurrentHashMap<>();

    private HashMap<Jid, JingleCandidate> primaryCandidates = new HashMap<>();

    public JingleConnectionManager(XmppConnectionService service) {
        super(service);
    }

    static String nextRandomId() {
        final byte[] id = new byte[16];
        new SecureRandom().nextBytes(id);
        return Base64.encodeToString(id, Base64.NO_WRAP | Base64.NO_PADDING);
    }

    public void deliverPacket(final Account account, final JinglePacket packet) {
        final String sessionId = packet.getSessionId();
        if (sessionId == null) {
            respondWithJingleError(account, packet, "unknown-session", "item-not-found", "cancel");
            return;
        }
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(account, packet);
        final AbstractJingleConnection existingJingleConnection = connections.get(id);
        if (existingJingleConnection != null) {
            existingJingleConnection.deliverPacket(packet);
        } else if (packet.getAction() == JinglePacket.Action.SESSION_INITIATE) {
            final Jid from = packet.getFrom();
            final Content content = packet.getJingleContent();
            final String descriptionNamespace = content == null ? null : content.getDescriptionNamespace();
            final AbstractJingleConnection connection;
            if (FileTransferDescription.NAMESPACES.contains(descriptionNamespace)) {
                connection = new JingleFileTransferConnection(this, id, from);
            } else if (Namespace.JINGLE_APPS_RTP.equals(descriptionNamespace) && !usesTor(account)) {
                if (isBusy()) {
                    mXmppConnectionService.sendIqPacket(account, packet.generateResponse(IqPacket.TYPE.RESULT), null);
                    final JinglePacket sessionTermination = new JinglePacket(JinglePacket.Action.SESSION_TERMINATE, id.sessionId);
                    sessionTermination.setTo(id.with);
                    sessionTermination.setReason(Reason.BUSY, null);
                    mXmppConnectionService.sendIqPacket(account, sessionTermination, null);
                    return;
                }
                connection = new JingleRtpConnection(this, id, from);
            } else {
                respondWithJingleError(account, packet, "unsupported-info", "feature-not-implemented", "cancel");
                return;
            }
            connections.put(id, connection);
            connection.deliverPacket(packet);
        } else {
            Log.d(Config.LOGTAG, "unable to route jingle packet: " + packet);
            respondWithJingleError(account, packet, "unknown-session", "item-not-found", "cancel");
        }
    }

    private boolean usesTor(final Account account) {
        return account.isOnion() || mXmppConnectionService.useTorToConnect();
    }

    private boolean isBusy() {
        for (AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection) {
                return true;
            }
        }
        synchronized (this.rtpSessionProposals) {
            return this.rtpSessionProposals.containsValue(DeviceDiscoveryState.DISCOVERED) || this.rtpSessionProposals.containsValue(DeviceDiscoveryState.SEARCHING);
        }
    }

    public void respondWithJingleError(final Account account, final IqPacket original, String jingleCondition, String condition, String conditionType) {
        final IqPacket response = original.generateResponse(IqPacket.TYPE.ERROR);
        final Element error = response.addChild("error");
        error.setAttribute("type", conditionType);
        error.addChild(condition, "urn:ietf:params:xml:ns:xmpp-stanzas");
        error.addChild(jingleCondition, "urn:xmpp:jingle:errors:1");
        account.getXmppConnection().sendIqPacket(response, null);
    }

    public void deliverMessage(final Account account, final Jid to, final Jid from, final Element message, String serverMsgId, long timestamp) {
        Preconditions.checkArgument(Namespace.JINGLE_MESSAGE.equals(message.getNamespace()));
        final String sessionId = message.getAttribute("id");
        if (sessionId == null) {
            return;
        }
        if ("accept".equals(message.getName())) {
            for (AbstractJingleConnection connection : connections.values()) {
                if (connection instanceof JingleRtpConnection) {
                    final JingleRtpConnection rtpConnection = (JingleRtpConnection) connection;
                    final AbstractJingleConnection.Id id = connection.getId();
                    if (id.account == account && id.sessionId.equals(sessionId)) {
                        rtpConnection.deliveryMessage(from, message, serverMsgId, timestamp);
                        return;
                    }
                }
            }
            return;
        }
        final boolean fromSelf = from.asBareJid().equals(account.getJid().asBareJid());
        final AbstractJingleConnection.Id id;
        if (fromSelf) {
            if (to.isFullJid()) {
                id = AbstractJingleConnection.Id.of(account, to, sessionId);
            } else {
                return;
            }
        } else {
            id = AbstractJingleConnection.Id.of(account, from, sessionId);
        }
        final AbstractJingleConnection existingJingleConnection = connections.get(id);
        if (existingJingleConnection != null) {
            if (existingJingleConnection instanceof JingleRtpConnection) {
                ((JingleRtpConnection) existingJingleConnection).deliveryMessage(from, message, serverMsgId, timestamp);
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": " + existingJingleConnection.getClass().getName() + " does not support jingle messages");
            }
            return;
        }

        if (fromSelf) {
            if ("proceed".equals(message.getName())) {
                final Conversation c = mXmppConnectionService.findOrCreateConversation(account, id.with, false, false);
                final Message previousBusy = c.findRtpSession(sessionId, Message.STATUS_RECEIVED);
                if (previousBusy != null) {
                    previousBusy.setBody(new RtpSessionStatus(true, 0).toString());
                    if (serverMsgId != null) {
                        previousBusy.setServerMsgId(serverMsgId);
                    }
                    previousBusy.setTime(timestamp);
                    mXmppConnectionService.updateMessage(previousBusy, true);
                    Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": updated previous busy because call got picked up by another device");
                    return;
                }
            }
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ignore jingle message from self");
            return;
        }

        if ("propose".equals(message.getName())) {
            final Propose propose = Propose.upgrade(message);
            final List<GenericDescription> descriptions = propose.getDescriptions();
            final Collection<RtpDescription> rtpDescriptions = Collections2.transform(
                    Collections2.filter(descriptions, d -> d instanceof RtpDescription),
                    input -> (RtpDescription) input
            );
            if (rtpDescriptions.size() > 0 && rtpDescriptions.size() == descriptions.size() && !usesTor(account)) {
                final Collection<Media> media = Collections2.transform(rtpDescriptions, RtpDescription::getMedia);
                if (media.contains(Media.UNKNOWN)) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": encountered unknown media in session proposal. " + propose);
                    return;
                }
                if (isBusy()) {
                    writeLogMissedIncoming(account, id.with.asBareJid(), id.sessionId, serverMsgId, timestamp);
                    final int activeDevices = account.countPresences();
                    Log.d(Config.LOGTAG, "active devices: " + activeDevices);
                    if (activeDevices == 0) {
                        final MessagePacket reject = mXmppConnectionService.getMessageGenerator().sessionReject(from, sessionId);
                        mXmppConnectionService.sendMessagePacket(account, reject);
                    } else {
                        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": ignoring proposal because busy on this device but there are other devices");
                    }
                } else {
                    final JingleRtpConnection rtpConnection = new JingleRtpConnection(this, id, from);
                    this.connections.put(id, rtpConnection);
                    rtpConnection.setProposedMedia(ImmutableSet.copyOf(media));
                    rtpConnection.deliveryMessage(from, message, serverMsgId, timestamp);
                }
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to react to proposed session with " + rtpDescriptions.size() + " rtp descriptions of " + descriptions.size() + " total descriptions");
            }
        } else if ("proceed".equals(message.getName())) {
            synchronized (rtpSessionProposals) {
                final RtpSessionProposal proposal = getRtpSessionProposal(account, from.asBareJid(), sessionId);
                if (proposal != null) {
                    rtpSessionProposals.remove(proposal);
                    final JingleRtpConnection rtpConnection = new JingleRtpConnection(this, id, account.getJid());
                    rtpConnection.setProposedMedia(proposal.media);
                    this.connections.put(id, rtpConnection);
                    rtpConnection.transitionOrThrow(AbstractJingleConnection.State.PROPOSED);
                    rtpConnection.deliveryMessage(from, message, serverMsgId, timestamp);
                } else {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": no rtp session proposal found for " + from + " to deliver proceed");
                }
            }
        } else if ("reject".equals(message.getName())) {
            final RtpSessionProposal proposal = new RtpSessionProposal(account, from.asBareJid(), sessionId);
            synchronized (rtpSessionProposals) {
                if (rtpSessionProposals.remove(proposal) != null) {
                    writeLogMissedOutgoing(account, proposal.with, proposal.sessionId, serverMsgId, timestamp);
                    mXmppConnectionService.notifyJingleRtpConnectionUpdate(account, proposal.with, proposal.sessionId, RtpEndUserState.DECLINED_OR_BUSY);
                } else {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": no rtp session proposal found for " + from + " to deliver reject");
                }
            }
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": retrieved out of order jingle message");
        }

    }

    private RtpSessionProposal getRtpSessionProposal(final Account account, Jid from, String sessionId) {
        for (RtpSessionProposal rtpSessionProposal : rtpSessionProposals.keySet()) {
            if (rtpSessionProposal.sessionId.equals(sessionId) && rtpSessionProposal.with.equals(from) && rtpSessionProposal.account.getJid().equals(account.getJid())) {
                return rtpSessionProposal;
            }
        }
        return null;
    }

    private void writeLogMissedOutgoing(final Account account, Jid with, final String sessionId, String serverMsgId, long timestamp) {
        final Conversation conversation = mXmppConnectionService.findOrCreateConversation(
                account,
                with.asBareJid(),
                false,
                false
        );
        final Message message = new Message(
                conversation,
                Message.STATUS_SEND,
                Message.TYPE_RTP_SESSION,
                sessionId
        );
        message.setBody(new RtpSessionStatus(false, 0).toString());
        message.setServerMsgId(serverMsgId);
        message.setTime(timestamp);
        writeMessage(message);
    }

    private void writeLogMissedIncoming(final Account account, Jid with, final String sessionId, String serverMsgId, long timestamp) {
        final Conversation conversation = mXmppConnectionService.findOrCreateConversation(
                account,
                with.asBareJid(),
                false,
                false
        );
        final Message message = new Message(
                conversation,
                Message.STATUS_RECEIVED,
                Message.TYPE_RTP_SESSION,
                sessionId
        );
        message.setBody(new RtpSessionStatus(false, 0).toString());
        message.setServerMsgId(serverMsgId);
        message.setTime(timestamp);
        writeMessage(message);
    }

    private void writeMessage(final Message message) {
        final Conversational conversational = message.getConversation();
        if (conversational instanceof Conversation) {
            ((Conversation) conversational).add(message);
            mXmppConnectionService.databaseBackend.createMessage(message);
            mXmppConnectionService.updateConversationUi();
        } else {
            throw new IllegalStateException("Somehow the conversation in a message was a stub");
        }
    }

    public void startJingleFileTransfer(final Message message) {
        Preconditions.checkArgument(message.isFileOrImage(), "Message is not of type file or image");
        final Transferable old = message.getTransferable();
        if (old != null) {
            old.cancel();
        }
        final Account account = message.getConversation().getAccount();
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(message);
        final JingleFileTransferConnection connection = new JingleFileTransferConnection(this, id, account.getJid());
        mXmppConnectionService.markMessage(message, Message.STATUS_WAITING);
        this.connections.put(id, connection);
        connection.init(message);
    }

    void finishConnection(final AbstractJingleConnection connection) {
        this.connections.remove(connection.getId());
    }

    void getPrimaryCandidate(final Account account, final boolean initiator, final OnPrimaryCandidateFound listener) {
        if (Config.DISABLE_PROXY_LOOKUP) {
            listener.onPrimaryCandidateFound(false, null);
            return;
        }
        if (!this.primaryCandidates.containsKey(account.getJid().asBareJid())) {
            final Jid proxy = account.getXmppConnection().findDiscoItemByFeature(Namespace.BYTE_STREAMS);
            if (proxy != null) {
                IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
                iq.setTo(proxy);
                iq.query(Namespace.BYTE_STREAMS);
                account.getXmppConnection().sendIqPacket(iq, new OnIqPacketReceived() {

                    @Override
                    public void onIqPacketReceived(Account account, IqPacket packet) {
                        final Element streamhost = packet.query().findChild("streamhost", Namespace.BYTE_STREAMS);
                        final String host = streamhost == null ? null : streamhost.getAttribute("host");
                        final String port = streamhost == null ? null : streamhost.getAttribute("port");
                        if (host != null && port != null) {
                            try {
                                JingleCandidate candidate = new JingleCandidate(nextRandomId(), true);
                                candidate.setHost(host);
                                candidate.setPort(Integer.parseInt(port));
                                candidate.setType(JingleCandidate.TYPE_PROXY);
                                candidate.setJid(proxy);
                                candidate.setPriority(655360 + (initiator ? 30 : 0));
                                primaryCandidates.put(account.getJid().asBareJid(), candidate);
                                listener.onPrimaryCandidateFound(true, candidate);
                            } catch (final NumberFormatException e) {
                                listener.onPrimaryCandidateFound(false, null);
                            }
                        } else {
                            listener.onPrimaryCandidateFound(false, null);
                        }
                    }
                });
            } else {
                listener.onPrimaryCandidateFound(false, null);
            }

        } else {
            listener.onPrimaryCandidateFound(true,
                    this.primaryCandidates.get(account.getJid().asBareJid()));
        }
    }

    public void retractSessionProposal(final Account account, final Jid with) {
        synchronized (this.rtpSessionProposals) {
            RtpSessionProposal matchingProposal = null;
            for (RtpSessionProposal proposal : this.rtpSessionProposals.keySet()) {
                if (proposal.account == account && with.asBareJid().equals(proposal.with)) {
                    matchingProposal = proposal;
                    break;
                }
            }
            if (matchingProposal != null) {
                this.rtpSessionProposals.remove(matchingProposal);
                final MessagePacket messagePacket = mXmppConnectionService.getMessageGenerator().sessionRetract(matchingProposal);
                writeLogMissedOutgoing(account, matchingProposal.with, matchingProposal.sessionId, null, System.currentTimeMillis());
                mXmppConnectionService.sendMessagePacket(account, messagePacket);

            }
        }
    }

    public void proposeJingleRtpSession(final Account account, final Jid with, final Set<Media> media) {
        synchronized (this.rtpSessionProposals) {
            for (Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry : this.rtpSessionProposals.entrySet()) {
                RtpSessionProposal proposal = entry.getKey();
                if (proposal.account == account && with.asBareJid().equals(proposal.with)) {
                    final DeviceDiscoveryState preexistingState = entry.getValue();
                    if (preexistingState != null && preexistingState != DeviceDiscoveryState.FAILED) {
                        mXmppConnectionService.notifyJingleRtpConnectionUpdate(
                                account,
                                with,
                                proposal.sessionId,
                                preexistingState.toEndUserState()
                        );
                        return;
                    }
                }
            }
            final RtpSessionProposal proposal = RtpSessionProposal.of(account, with.asBareJid(), media);
            this.rtpSessionProposals.put(proposal, DeviceDiscoveryState.SEARCHING);
            mXmppConnectionService.notifyJingleRtpConnectionUpdate(
                    account,
                    proposal.with,
                    proposal.sessionId,
                    RtpEndUserState.FINDING_DEVICE
            );
            final MessagePacket messagePacket = mXmppConnectionService.getMessageGenerator().sessionProposal(proposal);
            Log.d(Config.LOGTAG, messagePacket.toString());
            mXmppConnectionService.sendMessagePacket(account, messagePacket);
        }
    }

    public void deliverIbbPacket(Account account, IqPacket packet) {
        final String sid;
        final Element payload;
        if (packet.hasChild("open", Namespace.IBB)) {
            payload = packet.findChild("open", Namespace.IBB);
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("data", Namespace.IBB)) {
            payload = packet.findChild("data", Namespace.IBB);
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("close", Namespace.IBB)) {
            payload = packet.findChild("close", Namespace.IBB);
            sid = payload.getAttribute("sid");
        } else {
            payload = null;
            sid = null;
        }
        if (sid != null) {
            for (final AbstractJingleConnection connection : this.connections.values()) {
                if (connection instanceof JingleFileTransferConnection) {
                    final JingleFileTransferConnection fileTransfer = (JingleFileTransferConnection) connection;
                    final JingleTransport transport = fileTransfer.getTransport();
                    if (transport instanceof JingleInBandTransport) {
                        final JingleInBandTransport inBandTransport = (JingleInBandTransport) transport;
                        if (inBandTransport.matches(account, sid)) {
                            inBandTransport.deliverPayload(packet, payload);
                        }
                        return;
                    }
                }
            }
        }
        Log.d(Config.LOGTAG, "unable to deliver ibb packet: " + packet.toString());
        account.getXmppConnection().sendIqPacket(packet.generateResponse(IqPacket.TYPE.ERROR), null);
    }

    public void notifyRebound() {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            connection.notifyRebound();
        }
    }

    public WeakReference<JingleRtpConnection> findJingleRtpConnection(Account account, Jid with, String sessionId) {
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(account, Jid.ofEscaped(with), sessionId);
        final AbstractJingleConnection connection = connections.get(id);
        if (connection instanceof JingleRtpConnection) {
            return new WeakReference<>((JingleRtpConnection) connection);
        }
        return null;
    }

    public void updateProposedSessionDiscovered(Account account, Jid from, String sessionId, final DeviceDiscoveryState target) {
        synchronized (this.rtpSessionProposals) {
            final RtpSessionProposal sessionProposal = getRtpSessionProposal(account, from.asBareJid(), sessionId);
            final DeviceDiscoveryState currentState = sessionProposal == null ? null : rtpSessionProposals.get(sessionProposal);
            if (currentState == null) {
                Log.d(Config.LOGTAG, "unable to find session proposal for session id " + sessionId);
                return;
            }
            if (currentState == DeviceDiscoveryState.DISCOVERED) {
                Log.d(Config.LOGTAG, "session proposal already at discovered. not going to fall back");
                return;
            }
            this.rtpSessionProposals.put(sessionProposal, target);
            mXmppConnectionService.notifyJingleRtpConnectionUpdate(account, sessionProposal.with, sessionProposal.sessionId, target.toEndUserState());
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": flagging session " + sessionId + " as " + target);
        }
    }

    public void rejectRtpSession(final String sessionId) {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection.getId().sessionId.equals(sessionId)) {
                if (connection instanceof JingleRtpConnection) {
                    ((JingleRtpConnection) connection).rejectCall();
                }
            }
        }
    }

    public void endRtpSession(final String sessionId) {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection.getId().sessionId.equals(sessionId)) {
                if (connection instanceof JingleRtpConnection) {
                    ((JingleRtpConnection) connection).endCall();
                }
            }
        }
    }

    public void failProceed(Account account, final Jid with, String sessionId) {
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(account, with, sessionId);
        final AbstractJingleConnection existingJingleConnection = connections.get(id);
        if (existingJingleConnection instanceof JingleRtpConnection) {
            ((JingleRtpConnection) existingJingleConnection).deliverFailedProceed();
        }
    }

    public enum DeviceDiscoveryState {
        SEARCHING, DISCOVERED, FAILED;

        public RtpEndUserState toEndUserState() {
            switch (this) {
                case SEARCHING:
                    return RtpEndUserState.FINDING_DEVICE;
                case DISCOVERED:
                    return RtpEndUserState.RINGING;
                default:
                    return RtpEndUserState.CONNECTIVITY_ERROR;
            }
        }
    }

    public static class RtpSessionProposal {
        public final Jid with;
        public final String sessionId;
        public final Set<Media> media;
        private final Account account;

        private RtpSessionProposal(Account account, Jid with, String sessionId) {
            this(account, with, sessionId, Collections.emptySet());
        }

        private RtpSessionProposal(Account account, Jid with, String sessionId, Set<Media> media) {
            this.account = account;
            this.with = with;
            this.sessionId = sessionId;
            this.media = media;
        }

        public static RtpSessionProposal of(Account account, Jid with, Set<Media> media) {
            return new RtpSessionProposal(account, with, nextRandomId(), media);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RtpSessionProposal proposal = (RtpSessionProposal) o;
            return Objects.equal(account.getJid(), proposal.account.getJid()) &&
                    Objects.equal(with, proposal.with) &&
                    Objects.equal(sessionId, proposal.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(account.getJid(), with, sessionId);
        }
    }
}
