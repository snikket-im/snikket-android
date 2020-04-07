package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.FileTransferDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
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

    public void deliverPacket(final Account account, final JinglePacket packet) {
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
            } else if (Namespace.JINGLE_APPS_RTP.equals(descriptionNamespace)) {
                connection = new JingleRtpConnection(this, id, from);
            } else {
                //TODO return feature-not-implemented
                return;
            }
            connections.put(id, connection);
            connection.deliverPacket(packet);
        } else {
            Log.d(Config.LOGTAG, "unable to route jingle packet: " + packet);
            final IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
            final Element error = response.addChild("error");
            error.setAttribute("type", "cancel");
            error.addChild("item-not-found", "urn:ietf:params:xml:ns:xmpp-stanzas");
            error.addChild("unknown-session", "urn:xmpp:jingle:errors:1");
            account.getXmppConnection().sendIqPacket(response, null);
        }
    }

    public void deliverMessage(final Account account, final Jid to, final Jid from, final Element message) {
        Preconditions.checkArgument(Namespace.JINGLE_MESSAGE.equals(message.getNamespace()));
        final String sessionId = message.getAttribute("id");
        if (sessionId == null) {
            return;
        }
        final Jid with;
        if (account.getJid().asBareJid().equals(from.asBareJid())) {
            with = to;
        } else {
            with = from;
        }
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received jingle message from " + from + " with=" + with + " " + message);
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(account, with, sessionId);
        final AbstractJingleConnection existingJingleConnection = connections.get(id);
        if (existingJingleConnection != null) {
            if (existingJingleConnection instanceof JingleRtpConnection) {
                ((JingleRtpConnection) existingJingleConnection).deliveryMessage(from, message);
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": " + existingJingleConnection.getClass().getName() + " does not support jingle messages");
            }
        } else if ("propose".equals(message.getName())) {
            final Element description = message.findChild("description");
            final String namespace = description == null ? null : description.getNamespace();
            if (Namespace.JINGLE_APPS_RTP.equals(namespace)) {
                final JingleRtpConnection rtpConnection = new JingleRtpConnection(this, id, with);
                this.connections.put(id, rtpConnection);
                rtpConnection.deliveryMessage(from, message);
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to react to proposed " + namespace + " session");
            }
        } else if ("proceed".equals(message.getName())) {
            if (!with.equals(from)) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ignore carbon copied proceed");
                return;
            }
            final RtpSessionProposal proposal = new RtpSessionProposal(account, with.asBareJid(), sessionId);
            synchronized (rtpSessionProposals) {
                if (rtpSessionProposals.remove(proposal) != null) {
                    final JingleRtpConnection rtpConnection = new JingleRtpConnection(this, id, account.getJid());
                    this.connections.put(id, rtpConnection);
                    rtpConnection.transitionOrThrow(AbstractJingleConnection.State.PROPOSED);
                    rtpConnection.deliveryMessage(from, message);
                } else {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": no rtp session proposal found for " + with);
                }
            }
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": retrieved out of order jingle message");
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

    public void proposeJingleRtpSession(final Account account, final Contact contact) {
        final RtpSessionProposal proposal = RtpSessionProposal.of(account, contact.getJid().asBareJid());
        synchronized (this.rtpSessionProposals) {
            this.rtpSessionProposals.put(proposal, DeviceDiscoveryState.SEARCHING);
            final MessagePacket messagePacket = mXmppConnectionService.getMessageGenerator().sessionProposal(proposal);
            Log.d(Config.LOGTAG,messagePacket.toString());
            mXmppConnectionService.sendMessagePacket(account, messagePacket);
        }
    }

    static String nextRandomId() {
        return UUID.randomUUID().toString();
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

    public void cancelInTransmission() {
        for (AbstractJingleConnection connection : this.connections.values()) {
            /*if (connection.getJingleStatus() == JingleFileTransferConnection.JINGLE_STATUS_TRANSMITTING) {
                connection.abort("connectivity-error");
            }*/
        }
    }

    public void updateProposedSessionDiscovered(Account account, Jid from, String sessionId, final DeviceDiscoveryState target) {
        final RtpSessionProposal sessionProposal = new RtpSessionProposal(account,from.asBareJid(),sessionId);
        synchronized (this.rtpSessionProposals) {
            final DeviceDiscoveryState currentState = rtpSessionProposals.get(sessionProposal);
            if (currentState == null) {
                Log.d(Config.LOGTAG,"unable to find session proposal for session id "+sessionId);
                return;
            }
            if (currentState == DeviceDiscoveryState.DISCOVERED) {
                Log.d(Config.LOGTAG,"session proposal already at discovered. not going to fall back");
                return;
            }
            this.rtpSessionProposals.put(sessionProposal, target);
            Log.d(Config.LOGTAG,account.getJid().asBareJid()+": flagging session "+sessionId+" as "+target);
        }
    }

    public static class RtpSessionProposal {
        private final Account account;
        public final Jid with;
        public final String sessionId;

        private RtpSessionProposal(Account account, Jid with, String sessionId) {
            this.account = account;
            this.with = with;
            this.sessionId = sessionId;
        }

        public static RtpSessionProposal of(Account account, Jid with) {
            return new RtpSessionProposal(account, with, UUID.randomUUID().toString());
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

    public enum DeviceDiscoveryState {
        SEARCHING, DISCOVERED, FAILED
    }
}
