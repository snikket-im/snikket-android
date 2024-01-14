package eu.siacs.conversations.xmpp.jingle;

import android.util.Base64;
import android.util.Log;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Collections2;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.CallIntegration;
import eu.siacs.conversations.services.CallIntegrationConnectionService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Propose;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import eu.siacs.conversations.xmpp.jingle.transports.InbandBytestreamsTransport;
import eu.siacs.conversations.xmpp.jingle.transports.Transport;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class JingleConnectionManager extends AbstractConnectionManager {
    static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE =
            Executors.newSingleThreadScheduledExecutor();
    final ToneManager toneManager;
    private final HashMap<RtpSessionProposal, DeviceDiscoveryState> rtpSessionProposals =
            new HashMap<>();
    private final ConcurrentHashMap<AbstractJingleConnection.Id, AbstractJingleConnection>
            connections = new ConcurrentHashMap<>();

    private final Cache<PersistableSessionId, TerminatedRtpSession> terminatedSessions =
            CacheBuilder.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).build();

    public JingleConnectionManager(XmppConnectionService service) {
        super(service);
        this.toneManager = new ToneManager(service);
    }

    static String nextRandomId() {
        final byte[] id = new byte[16];
        new SecureRandom().nextBytes(id);
        return Base64.encodeToString(id, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
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
            final String descriptionNamespace =
                    content == null ? null : content.getDescriptionNamespace();
            final AbstractJingleConnection connection;
            if (Namespace.JINGLE_APPS_FILE_TRANSFER.equals(descriptionNamespace)) {
                connection = new JingleFileTransferConnection(this, id, from);
            } else if (Namespace.JINGLE_APPS_RTP.equals(descriptionNamespace)
                    && isUsingClearNet(account)) {
                final boolean sessionEnded =
                        this.terminatedSessions.asMap().containsKey(PersistableSessionId.of(id));
                final boolean stranger =
                        isWithStrangerAndStrangerNotificationsAreOff(account, id.with);
                final boolean busy = isBusy();
                if (busy || sessionEnded || stranger) {
                    Log.d(
                            Config.LOGTAG,
                            id.account.getJid().asBareJid()
                                    + ": rejected session with "
                                    + id.with
                                    + " because busy. sessionEnded="
                                    + sessionEnded
                                    + ", stranger="
                                    + stranger);
                    mXmppConnectionService.sendIqPacket(
                            account, packet.generateResponse(IqPacket.TYPE.RESULT), null);
                    final JinglePacket sessionTermination =
                            new JinglePacket(JinglePacket.Action.SESSION_TERMINATE, id.sessionId);
                    sessionTermination.setTo(id.with);
                    sessionTermination.setReason(Reason.BUSY, null);
                    mXmppConnectionService.sendIqPacket(account, sessionTermination, null);
                    if (busy || stranger) {
                        writeLogMissedIncoming(
                                account,
                                id.with,
                                id.sessionId,
                                null,
                                System.currentTimeMillis(),
                                stranger);
                    }
                    return;
                }
                connection = new JingleRtpConnection(this, id, from);
            } else {
                respondWithJingleError(
                        account, packet, "unsupported-info", "feature-not-implemented", "cancel");
                return;
            }
            connections.put(id, connection);

            CallIntegrationConnectionService.addNewIncomingCall(getXmppConnectionService(), id);

            mXmppConnectionService.updateConversationUi();
            connection.deliverPacket(packet);
        } else {
            Log.d(Config.LOGTAG, "unable to route jingle packet: " + packet);
            respondWithJingleError(account, packet, "unknown-session", "item-not-found", "cancel");
        }
    }

    private boolean isUsingClearNet(final Account account) {
        return !account.isOnion() && !mXmppConnectionService.useTorToConnect();
    }

    public boolean isBusy() {
        for (AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection) {
                if (connection.isTerminated()) {
                    continue;
                }
                return true;
            }
        }
        synchronized (this.rtpSessionProposals) {
            return this.rtpSessionProposals.containsValue(DeviceDiscoveryState.DISCOVERED)
                    || this.rtpSessionProposals.containsValue(DeviceDiscoveryState.SEARCHING)
                    || this.rtpSessionProposals.containsValue(
                            DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED);
        }
    }

    public boolean hasJingleRtpConnection(final Account account) {
        for (AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection rtpConnection) {
                if (rtpConnection.isTerminated()) {
                    continue;
                }
                if (rtpConnection.id.account == account) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<RtpSessionProposal> findMatchingSessionProposal(
            final Account account, final Jid with, final Set<Media> media) {
        synchronized (this.rtpSessionProposals) {
            for (Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final RtpSessionProposal proposal = entry.getKey();
                final DeviceDiscoveryState state = entry.getValue();
                final boolean openProposal =
                        state == DeviceDiscoveryState.DISCOVERED
                                || state == DeviceDiscoveryState.SEARCHING
                                || state == DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED;
                if (openProposal
                        && proposal.account == account
                        && proposal.with.equals(with.asBareJid())
                        && proposal.media.equals(media)) {
                    return Optional.of(proposal);
                }
            }
        }
        return Optional.absent();
    }

    private boolean hasMatchingRtpSession(
            final Account account, final Jid with, final Set<Media> media) {
        for (AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection rtpConnection) {
                if (rtpConnection.isTerminated()) {
                    continue;
                }
                if (rtpConnection.getId().account == account
                        && rtpConnection.getId().with.asBareJid().equals(with.asBareJid())
                        && rtpConnection.getMedia().equals(media)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isWithStrangerAndStrangerNotificationsAreOff(final Account account, Jid with) {
        final boolean notifyForStrangers =
                mXmppConnectionService.getNotificationService().notificationsFromStrangers();
        if (notifyForStrangers) {
            return false;
        }
        final Contact contact = account.getRoster().getContact(with);
        return !contact.showInContactList();
    }

    ScheduledFuture<?> schedule(
            final Runnable runnable, final long delay, final TimeUnit timeUnit) {
        return SCHEDULED_EXECUTOR_SERVICE.schedule(runnable, delay, timeUnit);
    }

    void respondWithJingleError(
            final Account account,
            final IqPacket original,
            String jingleCondition,
            String condition,
            String conditionType) {
        final IqPacket response = original.generateResponse(IqPacket.TYPE.ERROR);
        final Element error = response.addChild("error");
        error.setAttribute("type", conditionType);
        error.addChild(condition, "urn:ietf:params:xml:ns:xmpp-stanzas");
        if (jingleCondition != null) {
            error.addChild(jingleCondition, Namespace.JINGLE_ERRORS);
        }
        account.getXmppConnection().sendIqPacket(response, null);
    }

    public void deliverMessage(
            final Account account,
            final Jid to,
            final Jid from,
            final Element message,
            String remoteMsgId,
            String serverMsgId,
            long timestamp) {
        Preconditions.checkArgument(Namespace.JINGLE_MESSAGE.equals(message.getNamespace()));
        final String sessionId = message.getAttribute("id");
        if (sessionId == null) {
            return;
        }
        if ("accept".equals(message.getName())) {
            for (AbstractJingleConnection connection : connections.values()) {
                if (connection instanceof JingleRtpConnection rtpConnection) {
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
        // XEP version 0.6.0 sends proceed, reject, ringing to bare jid
        final boolean addressedDirectly = to != null && to.equals(account.getJid());
        final AbstractJingleConnection.Id id;
        if (fromSelf) {
            if (to != null && to.isFullJid()) {
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
                ((JingleRtpConnection) existingJingleConnection)
                        .deliveryMessage(from, message, serverMsgId, timestamp);
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": "
                                + existingJingleConnection.getClass().getName()
                                + " does not support jingle messages");
            }
            return;
        }

        if (fromSelf) {
            if ("proceed".equals(message.getName())) {
                final Conversation c =
                        mXmppConnectionService.findOrCreateConversation(
                                account, id.with, false, false);
                final Message previousBusy = c.findRtpSession(sessionId, Message.STATUS_RECEIVED);
                if (previousBusy != null) {
                    previousBusy.setBody(new RtpSessionStatus(true, 0).toString());
                    if (serverMsgId != null) {
                        previousBusy.setServerMsgId(serverMsgId);
                    }
                    previousBusy.setTime(timestamp);
                    mXmppConnectionService.updateMessage(previousBusy, true);
                    Log.d(
                            Config.LOGTAG,
                            id.account.getJid().asBareJid()
                                    + ": updated previous busy because call got picked up by another device");
                    mXmppConnectionService.getNotificationService().clearMissedCall(previousBusy);
                    return;
                }
            }
            // TODO handle reject for cases where we don’t have carbon copies (normally reject is to
            // be sent to own bare jid as well)
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": ignore jingle message from self");
            return;
        }

        if ("propose".equals(message.getName())) {
            final Propose propose = Propose.upgrade(message);
            final List<GenericDescription> descriptions = propose.getDescriptions();
            final Collection<RtpDescription> rtpDescriptions =
                    Collections2.transform(
                            Collections2.filter(descriptions, d -> d instanceof RtpDescription),
                            input -> (RtpDescription) input);
            if (rtpDescriptions.size() > 0
                    && rtpDescriptions.size() == descriptions.size()
                    && isUsingClearNet(account)) {
                final Collection<Media> media =
                        Collections2.transform(rtpDescriptions, RtpDescription::getMedia);
                if (media.contains(Media.UNKNOWN)) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": encountered unknown media in session proposal. "
                                    + propose);
                    return;
                }
                final Optional<RtpSessionProposal> matchingSessionProposal =
                        findMatchingSessionProposal(account, id.with, ImmutableSet.copyOf(media));
                if (matchingSessionProposal.isPresent()) {
                    final String ourSessionId = matchingSessionProposal.get().sessionId;
                    final String theirSessionId = id.sessionId;
                    if (ComparisonChain.start()
                                    .compare(ourSessionId, theirSessionId)
                                    .compare(
                                            account.getJid().toEscapedString(),
                                            id.with.toEscapedString())
                                    .result()
                            > 0) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": our session lost tie break. automatically accepting their session. winning Session="
                                        + theirSessionId);
                        // TODO a retract for this reason should probably include some indication of
                        // tie break
                        retractSessionProposal(matchingSessionProposal.get());
                        final JingleRtpConnection rtpConnection =
                                new JingleRtpConnection(this, id, from);
                        this.connections.put(id, rtpConnection);
                        rtpConnection.setProposedMedia(ImmutableSet.copyOf(media));
                        rtpConnection.deliveryMessage(from, message, serverMsgId, timestamp);

                        CallIntegrationConnectionService.addNewIncomingCall(
                                getXmppConnectionService(), id);
                        // TODO actually do the automatic accept?!
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": our session won tie break. waiting for other party to accept. winningSession="
                                        + ourSessionId);
                        // TODO reject their session with <tie-break/>?
                    }
                    return;
                }
                final boolean stranger =
                        isWithStrangerAndStrangerNotificationsAreOff(account, id.with);
                if (isBusy() || stranger) {
                    writeLogMissedIncoming(
                            account,
                            id.with.asBareJid(),
                            id.sessionId,
                            serverMsgId,
                            timestamp,
                            stranger);
                    if (stranger) {
                        Log.d(
                                Config.LOGTAG,
                                id.account.getJid().asBareJid()
                                        + ": ignoring call proposal from stranger "
                                        + id.with);
                        return;
                    }
                    final int activeDevices = account.activeDevicesWithRtpCapability();
                    Log.d(Config.LOGTAG, "active devices with rtp capability: " + activeDevices);
                    if (activeDevices == 0) {
                        final MessagePacket reject =
                                mXmppConnectionService
                                        .getMessageGenerator()
                                        .sessionReject(from, sessionId);
                        mXmppConnectionService.sendMessagePacket(account, reject);
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                id.account.getJid().asBareJid()
                                        + ": ignoring proposal because busy on this device but there are other devices");
                    }
                } else {
                    final JingleRtpConnection rtpConnection =
                            new JingleRtpConnection(this, id, from);
                    this.connections.put(id, rtpConnection);
                    rtpConnection.setProposedMedia(ImmutableSet.copyOf(media));
                    rtpConnection.deliveryMessage(from, message, serverMsgId, timestamp);

                    CallIntegrationConnectionService.addNewIncomingCall(
                            getXmppConnectionService(), id);
                }
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": unable to react to proposed session with "
                                + rtpDescriptions.size()
                                + " rtp descriptions of "
                                + descriptions.size()
                                + " total descriptions");
            }
        } else if (addressedDirectly && "proceed".equals(message.getName())) {
            synchronized (rtpSessionProposals) {
                final RtpSessionProposal proposal =
                        getRtpSessionProposal(account, from.asBareJid(), sessionId);
                if (proposal != null) {
                    rtpSessionProposals.remove(proposal);
                    final JingleRtpConnection rtpConnection =
                            new JingleRtpConnection(
                                    this, id, account.getJid(), proposal.callIntegration);
                    rtpConnection.setProposedMedia(proposal.media);
                    this.connections.put(id, rtpConnection);
                    rtpConnection.transitionOrThrow(AbstractJingleConnection.State.PROPOSED);
                    rtpConnection.deliveryMessage(from, message, serverMsgId, timestamp);
                } else {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": no rtp session ("
                                    + sessionId
                                    + ") proposal found for "
                                    + from
                                    + " to deliver proceed");
                    if (remoteMsgId == null) {
                        return;
                    }
                    final MessagePacket errorMessage = new MessagePacket();
                    errorMessage.setTo(from);
                    errorMessage.setId(remoteMsgId);
                    errorMessage.setType(MessagePacket.TYPE_ERROR);
                    final Element error = errorMessage.addChild("error");
                    error.setAttribute("code", "404");
                    error.setAttribute("type", "cancel");
                    error.addChild("item-not-found", "urn:ietf:params:xml:ns:xmpp-stanzas");
                    mXmppConnectionService.sendMessagePacket(account, errorMessage);
                }
            }
        } else if (addressedDirectly && "reject".equals(message.getName())) {
            final RtpSessionProposal proposal =
                    getRtpSessionProposal(account, from.asBareJid(), sessionId);
            synchronized (rtpSessionProposals) {
                if (proposal != null && rtpSessionProposals.remove(proposal) != null) {
                    proposal.callIntegration.busy();
                    writeLogMissedOutgoing(
                            account, proposal.with, proposal.sessionId, serverMsgId, timestamp);
                    toneManager.transition(RtpEndUserState.DECLINED_OR_BUSY, proposal.media);
                    mXmppConnectionService.notifyJingleRtpConnectionUpdate(
                            account,
                            proposal.with,
                            proposal.sessionId,
                            RtpEndUserState.DECLINED_OR_BUSY);
                } else {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": no rtp session proposal found for "
                                    + from
                                    + " to deliver reject");
                }
            }
        } else if (addressedDirectly && "ringing".equals(message.getName())) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": " + from + " started ringing");
            updateProposedSessionDiscovered(
                    account, from, sessionId, DeviceDiscoveryState.DISCOVERED);
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid()
                            + ": retrieved out of order jingle message from "
                            + from
                            + message
                            + ", addressedDirectly="
                            + addressedDirectly);
        }
    }

    private RtpSessionProposal getRtpSessionProposal(
            final Account account, Jid from, String sessionId) {
        for (RtpSessionProposal rtpSessionProposal : rtpSessionProposals.keySet()) {
            if (rtpSessionProposal.sessionId.equals(sessionId)
                    && rtpSessionProposal.with.equals(from)
                    && rtpSessionProposal.account.getJid().equals(account.getJid())) {
                return rtpSessionProposal;
            }
        }
        return null;
    }

    private void writeLogMissedOutgoing(
            final Account account,
            Jid with,
            final String sessionId,
            String serverMsgId,
            long timestamp) {
        final Conversation conversation =
                mXmppConnectionService.findOrCreateConversation(
                        account, with.asBareJid(), false, false);
        final Message message =
                new Message(conversation, Message.STATUS_SEND, Message.TYPE_RTP_SESSION, sessionId);
        message.setBody(new RtpSessionStatus(false, 0).toString());
        message.setServerMsgId(serverMsgId);
        message.setTime(timestamp);
        writeMessage(message);
    }

    private void writeLogMissedIncoming(
            final Account account,
            final Jid with,
            final String sessionId,
            final String serverMsgId,
            final long timestamp,
            final boolean stranger) {
        final Conversation conversation =
                mXmppConnectionService.findOrCreateConversation(
                        account, with.asBareJid(), false, false);
        final Message message =
                new Message(
                        conversation, Message.STATUS_RECEIVED, Message.TYPE_RTP_SESSION, sessionId);
        message.setBody(new RtpSessionStatus(false, 0).toString());
        message.setServerMsgId(serverMsgId);
        message.setTime(timestamp);
        message.setCounterpart(with);
        writeMessage(message);
        if (stranger) {
            return;
        }
        mXmppConnectionService.getNotificationService().pushMissedCallNow(message);
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
        Preconditions.checkArgument(
                message.isFileOrImage(), "Message is not of type file or image");
        final Transferable old = message.getTransferable();
        if (old != null) {
            old.cancel();
        }
        final JingleFileTransferConnection connection =
                new JingleFileTransferConnection(this, message);
        this.connections.put(connection.getId(), connection);
        connection.sendSessionInitialize();
    }

    public Optional<OngoingRtpSession> getOngoingRtpConnection(final Contact contact) {
        for (final Map.Entry<AbstractJingleConnection.Id, AbstractJingleConnection> entry :
                this.connections.entrySet()) {
            if (entry.getValue() instanceof JingleRtpConnection) {
                final AbstractJingleConnection.Id id = entry.getKey();
                if (id.account == contact.getAccount()
                        && id.with.asBareJid().equals(contact.getJid().asBareJid())) {
                    return Optional.of(id);
                }
            }
        }
        synchronized (this.rtpSessionProposals) {
            for (final Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final RtpSessionProposal proposal = entry.getKey();
                if (proposal.account == contact.getAccount()
                        && contact.getJid().asBareJid().equals(proposal.with)) {
                    final DeviceDiscoveryState preexistingState = entry.getValue();
                    if (preexistingState != null
                            && preexistingState != DeviceDiscoveryState.FAILED) {
                        return Optional.of(proposal);
                    }
                }
            }
        }
        return Optional.absent();
    }

    void finishConnectionOrThrow(final AbstractJingleConnection connection) {
        final AbstractJingleConnection.Id id = connection.getId();
        if (this.connections.remove(id) == null) {
            throw new IllegalStateException(
                    String.format("Unable to finish connection with id=%s", id));
        }
        // update chat UI to remove 'ongoing call' icon
        mXmppConnectionService.updateConversationUi();
    }

    public boolean fireJingleRtpConnectionStateUpdates() {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection jingleRtpConnection) {
                if (jingleRtpConnection.isTerminated()) {
                    continue;
                }
                jingleRtpConnection.fireStateUpdate();
                return true;
            }
        }
        return false;
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
                retractSessionProposal(matchingProposal);
            }
        }
    }

    private void retractSessionProposal(final RtpSessionProposal rtpSessionProposal) {
        final Account account = rtpSessionProposal.account;
        toneManager.transition(RtpEndUserState.ENDED, rtpSessionProposal.media);
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": retracting rtp session proposal with "
                        + rtpSessionProposal.with);
        this.rtpSessionProposals.remove(rtpSessionProposal);
        rtpSessionProposal.callIntegration.retracted();
        mXmppConnectionService.notifyJingleRtpConnectionUpdate(
                account,
                rtpSessionProposal.with,
                rtpSessionProposal.sessionId,
                RtpEndUserState.RETRACTED);
        final MessagePacket messagePacket =
                mXmppConnectionService.getMessageGenerator().sessionRetract(rtpSessionProposal);
        writeLogMissedOutgoing(
                account,
                rtpSessionProposal.with,
                rtpSessionProposal.sessionId,
                null,
                System.currentTimeMillis());
        mXmppConnectionService.sendMessagePacket(account, messagePacket);
    }

    public JingleRtpConnection initializeRtpSession(
            final Account account, final Jid with, final Set<Media> media) {
        final AbstractJingleConnection.Id id = AbstractJingleConnection.Id.of(account, with);
        final JingleRtpConnection rtpConnection =
                new JingleRtpConnection(this, id, account.getJid());
        rtpConnection.setProposedMedia(media);
        this.connections.put(id, rtpConnection);
        rtpConnection.sendSessionInitiate();
        return rtpConnection;
    }

    public RtpSessionProposal proposeJingleRtpSession(
            final Account account, final Jid with, final Set<Media> media) {
        synchronized (this.rtpSessionProposals) {
            for (final Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final RtpSessionProposal proposal = entry.getKey();
                if (proposal.account == account && with.asBareJid().equals(proposal.with)) {
                    final DeviceDiscoveryState preexistingState = entry.getValue();
                    if (preexistingState != null
                            && preexistingState != DeviceDiscoveryState.FAILED) {
                        final RtpEndUserState endUserState = preexistingState.toEndUserState();
                        toneManager.transition(endUserState, media);
                        mXmppConnectionService.notifyJingleRtpConnectionUpdate(
                                account, with, proposal.sessionId, endUserState);
                        return proposal;
                    }
                }
            }
            if (isBusy()) {
                if (hasMatchingRtpSession(account, with, media)) {
                    Log.d(
                            Config.LOGTAG,
                            "ignoring request to propose jingle session because the other party already created one for us");
                    // TODO return something that we can parse the connection of of
                    return null;
                }
                throw new IllegalStateException(
                        "There is already a running RTP session. This should have been caught by the UI");
            }
            final CallIntegration callIntegration =
                    new CallIntegration(mXmppConnectionService.getApplicationContext());
            callIntegration.setInitialAudioDevice(CallIntegration.initialAudioDevice(media));
            final RtpSessionProposal proposal =
                    RtpSessionProposal.of(account, with.asBareJid(), media, callIntegration);
            callIntegration.setCallback(new ProposalStateCallback(proposal));
            this.rtpSessionProposals.put(proposal, DeviceDiscoveryState.SEARCHING);
            mXmppConnectionService.notifyJingleRtpConnectionUpdate(
                    account, proposal.with, proposal.sessionId, RtpEndUserState.FINDING_DEVICE);
            final MessagePacket messagePacket =
                    mXmppConnectionService.getMessageGenerator().sessionProposal(proposal);
            mXmppConnectionService.sendMessagePacket(account, messagePacket);
            return proposal;
        }
    }

    public boolean hasMatchingProposal(final Account account, final Jid with) {
        synchronized (this.rtpSessionProposals) {
            for (Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final var state = entry.getValue();
                final RtpSessionProposal proposal = entry.getKey();
                if (proposal.account == account && with.asBareJid().equals(proposal.with)) {
                    // CallIntegrationConnectionService starts RtpSessionActivity with ACTION_VIEW
                    // and an EXTRA_LAST_REPORTED_STATE of DISCOVERING devices. however due to
                    // possible race conditions the state might have already moved on so we are
                    // going
                    // to update the UI
                    final RtpEndUserState endUserState = state.toEndUserState();
                    mXmppConnectionService.notifyJingleRtpConnectionUpdate(
                            account, proposal.with, proposal.sessionId, endUserState);
                    return true;
                }
            }
        }
        return false;
    }

    public void deliverIbbPacket(final Account account, final IqPacket packet) {
        final String sid;
        final Element payload;
        final InbandBytestreamsTransport.PacketType packetType;
        if (packet.hasChild("open", Namespace.IBB)) {
            packetType = InbandBytestreamsTransport.PacketType.OPEN;
            payload = packet.findChild("open", Namespace.IBB);
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("data", Namespace.IBB)) {
            packetType = InbandBytestreamsTransport.PacketType.DATA;
            payload = packet.findChild("data", Namespace.IBB);
            sid = payload.getAttribute("sid");
        } else if (packet.hasChild("close", Namespace.IBB)) {
            packetType = InbandBytestreamsTransport.PacketType.CLOSE;
            payload = packet.findChild("close", Namespace.IBB);
            sid = payload.getAttribute("sid");
        } else {
            packetType = null;
            payload = null;
            sid = null;
        }
        if (sid == null) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": unable to deliver ibb packet. missing sid");
            account.getXmppConnection()
                    .sendIqPacket(packet.generateResponse(IqPacket.TYPE.ERROR), null);
            return;
        }
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleFileTransferConnection fileTransfer) {
                final Transport transport = fileTransfer.getTransport();
                if (transport instanceof InbandBytestreamsTransport inBandTransport) {
                    if (sid.equals(inBandTransport.getStreamId())) {
                        if (inBandTransport.deliverPacket(packetType, packet.getFrom(), payload)) {
                            account.getXmppConnection()
                                    .sendIqPacket(
                                            packet.generateResponse(IqPacket.TYPE.RESULT), null);
                        } else {
                            account.getXmppConnection()
                                    .sendIqPacket(
                                            packet.generateResponse(IqPacket.TYPE.ERROR), null);
                        }
                        return;
                    }
                }
            }
        }
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid() + ": unable to deliver ibb packet with sid=" + sid);
        account.getXmppConnection()
                .sendIqPacket(packet.generateResponse(IqPacket.TYPE.ERROR), null);
    }

    public void notifyRebound(final Account account) {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            connection.notifyRebound();
        }
        final XmppConnection xmppConnection = account.getXmppConnection();
        if (xmppConnection != null && xmppConnection.getFeatures().sm()) {
            resendSessionProposals(account);
        }
    }

    public WeakReference<JingleRtpConnection> findJingleRtpConnection(
            Account account, Jid with, String sessionId) {
        final AbstractJingleConnection.Id id =
                AbstractJingleConnection.Id.of(account, with, sessionId);
        final AbstractJingleConnection connection = connections.get(id);
        if (connection instanceof JingleRtpConnection) {
            return new WeakReference<>((JingleRtpConnection) connection);
        }
        return null;
    }

    public JingleRtpConnection findJingleRtpConnection(final Account account, final Jid with) {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection instanceof JingleRtpConnection rtpConnection) {
                if (rtpConnection.isTerminated()) {
                    continue;
                }
                final var id = rtpConnection.getId();
                if (id.account == account && account.getJid().equals(with)) {
                    return rtpConnection;
                }
            }
        }
        return null;
    }

    private void resendSessionProposals(final Account account) {
        synchronized (this.rtpSessionProposals) {
            for (final Map.Entry<RtpSessionProposal, DeviceDiscoveryState> entry :
                    this.rtpSessionProposals.entrySet()) {
                final RtpSessionProposal proposal = entry.getKey();
                if (entry.getValue() == DeviceDiscoveryState.SEARCHING
                        && proposal.account == account) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": resending session proposal to "
                                    + proposal.with);
                    final MessagePacket messagePacket =
                            mXmppConnectionService.getMessageGenerator().sessionProposal(proposal);
                    mXmppConnectionService.sendMessagePacket(account, messagePacket);
                }
            }
        }
    }

    public void updateProposedSessionDiscovered(
            Account account, Jid from, String sessionId, final DeviceDiscoveryState target) {
        synchronized (this.rtpSessionProposals) {
            final RtpSessionProposal sessionProposal =
                    getRtpSessionProposal(account, from.asBareJid(), sessionId);
            final DeviceDiscoveryState currentState =
                    sessionProposal == null ? null : rtpSessionProposals.get(sessionProposal);
            if (currentState == null) {
                Log.d(Config.LOGTAG, "unable to find session proposal for session id " + sessionId);
                return;
            }
            if (currentState == DeviceDiscoveryState.DISCOVERED) {
                Log.d(
                        Config.LOGTAG,
                        "session proposal already at discovered. not going to fall back");
                return;
            }
            this.rtpSessionProposals.put(sessionProposal, target);
            final RtpEndUserState endUserState = target.toEndUserState();
            if (endUserState == RtpEndUserState.RINGING) {
                sessionProposal.callIntegration.setDialing();
            }
            // toneManager.transition(endUserState, sessionProposal.media);
            mXmppConnectionService.notifyJingleRtpConnectionUpdate(
                    account, sessionProposal.with, sessionProposal.sessionId, endUserState);
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": flagging session "
                            + sessionId
                            + " as "
                            + target);
        }
    }

    public void rejectRtpSession(final String sessionId) {
        for (final AbstractJingleConnection connection : this.connections.values()) {
            if (connection.getId().sessionId.equals(sessionId)) {
                if (connection instanceof JingleRtpConnection) {
                    try {
                        ((JingleRtpConnection) connection).rejectCall();
                        return;
                    } catch (final IllegalStateException e) {
                        Log.w(
                                Config.LOGTAG,
                                "race condition on rejecting call from notification",
                                e);
                    }
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

    public void failProceed(
            Account account, final Jid with, final String sessionId, final String message) {
        final AbstractJingleConnection.Id id =
                AbstractJingleConnection.Id.of(account, with, sessionId);
        final AbstractJingleConnection existingJingleConnection = connections.get(id);
        if (existingJingleConnection instanceof JingleRtpConnection) {
            ((JingleRtpConnection) existingJingleConnection).deliverFailedProceed(message);
        }
    }

    void ensureConnectionIsRegistered(final AbstractJingleConnection connection) {
        if (connections.containsValue(connection)) {
            return;
        }
        final IllegalStateException e =
                new IllegalStateException(
                        "JingleConnection has not been registered with connection manager");
        Log.e(Config.LOGTAG, "ensureConnectionIsRegistered() failed. Going to throw", e);
        throw e;
    }

    void setTerminalSessionState(
            AbstractJingleConnection.Id id, final RtpEndUserState state, final Set<Media> media) {
        this.terminatedSessions.put(
                PersistableSessionId.of(id), new TerminatedRtpSession(state, media));
    }

    public TerminatedRtpSession getTerminalSessionState(final Jid with, final String sessionId) {
        return this.terminatedSessions.getIfPresent(new PersistableSessionId(with, sessionId));
    }

    private static class PersistableSessionId {
        private final Jid with;
        private final String sessionId;

        private PersistableSessionId(Jid with, String sessionId) {
            this.with = with;
            this.sessionId = sessionId;
        }

        public static PersistableSessionId of(AbstractJingleConnection.Id id) {
            return new PersistableSessionId(id.with, id.sessionId);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PersistableSessionId that = (PersistableSessionId) o;
            return Objects.equal(with, that.with) && Objects.equal(sessionId, that.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(with, sessionId);
        }
    }

    public static class TerminatedRtpSession {
        public final RtpEndUserState state;
        public final Set<Media> media;

        TerminatedRtpSession(RtpEndUserState state, Set<Media> media) {
            this.state = state;
            this.media = media;
        }
    }

    public enum DeviceDiscoveryState {
        SEARCHING,
        SEARCHING_ACKNOWLEDGED,
        DISCOVERED,
        FAILED;

        public RtpEndUserState toEndUserState() {
            return switch (this) {
                case SEARCHING, SEARCHING_ACKNOWLEDGED -> RtpEndUserState.FINDING_DEVICE;
                case DISCOVERED -> RtpEndUserState.RINGING;
                default -> RtpEndUserState.CONNECTIVITY_ERROR;
            };
        }
    }

    public static class RtpSessionProposal implements OngoingRtpSession {
        public final Jid with;
        public final String sessionId;
        public final Set<Media> media;
        private final Account account;
        private final CallIntegration callIntegration;

        private RtpSessionProposal(
                Account account,
                Jid with,
                String sessionId,
                Set<Media> media,
                final CallIntegration callIntegration) {
            this.account = account;
            this.with = with;
            this.sessionId = sessionId;
            this.media = media;
            this.callIntegration = callIntegration;
        }

        public static RtpSessionProposal of(
                Account account,
                Jid with,
                Set<Media> media,
                final CallIntegration callIntegration) {
            return new RtpSessionProposal(account, with, nextRandomId(), media, callIntegration);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RtpSessionProposal proposal = (RtpSessionProposal) o;
            return Objects.equal(account.getJid(), proposal.account.getJid())
                    && Objects.equal(with, proposal.with)
                    && Objects.equal(sessionId, proposal.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(account.getJid(), with, sessionId);
        }

        @Override
        public Account getAccount() {
            return account;
        }

        @Override
        public Jid getWith() {
            return with;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        public CallIntegration getCallIntegration() {
            return this.callIntegration;
        }
    }

    public class ProposalStateCallback implements CallIntegration.Callback {

        private final RtpSessionProposal proposal;

        public ProposalStateCallback(final RtpSessionProposal proposal) {
            this.proposal = proposal;
        }

        @Override
        public void onCallIntegrationShowIncomingCallUi() {}

        @Override
        public void onCallIntegrationDisconnect() {
            Log.d(Config.LOGTAG, "a phone call has just been started. retracting proposal");
            retractSessionProposal(this.proposal);
        }

        @Override
        public void onAudioDeviceChanged(
                CallIntegration.AudioDevice selectedAudioDevice,
                Set<CallIntegration.AudioDevice> availableAudioDevices) {}
    }
}
