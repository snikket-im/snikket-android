package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.hash.Hashing;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.entities.TransferablePlaceholder;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.stanzas.FileTransferDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.IbbTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.jingle.stanzas.SocksByteStreamsTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.WebRTCDataChannelTransportInfo;
import eu.siacs.conversations.xmpp.jingle.transports.InbandBytestreamsTransport;
import eu.siacs.conversations.xmpp.jingle.transports.SocksByteStreamsTransport;
import eu.siacs.conversations.xmpp.jingle.transports.Transport;
import eu.siacs.conversations.xmpp.jingle.transports.WebRTCDataChannelTransport;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.io.CipherInputStream;
import org.bouncycastle.crypto.io.CipherOutputStream;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.webrtc.IceCandidate;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;

public class JingleFileTransferConnection extends AbstractJingleConnection
        implements Transport.Callback, Transferable {

    private final Message message;

    private FileTransferContentMap initiatorFileTransferContentMap;
    private FileTransferContentMap responderFileTransferContentMap;

    private Transport transport;
    private TransportSecurity transportSecurity;
    private AbstractFileTransceiver fileTransceiver;

    private final Queue<IceCandidate> pendingIncomingIceCandidates = new LinkedList<>();
    private boolean acceptedAutomatically = false;

    public JingleFileTransferConnection(
            final JingleConnectionManager jingleConnectionManager, final Message message) {
        super(
                jingleConnectionManager,
                AbstractJingleConnection.Id.of(message),
                message.getConversation().getAccount().getJid());
        Preconditions.checkArgument(
                message.isFileOrImage(),
                "only file or images messages can be transported via jingle");
        this.message = message;
        this.message.setTransferable(this);
        xmppConnectionService.markMessage(message, Message.STATUS_WAITING);
    }

    public JingleFileTransferConnection(
            final JingleConnectionManager jingleConnectionManager,
            final Id id,
            final Jid initiator) {
        super(jingleConnectionManager, id, initiator);
        final Conversation conversation =
                this.xmppConnectionService.findOrCreateConversation(
                        id.account, id.with.asBareJid(), false, false);
        this.message = new Message(conversation, "", Message.ENCRYPTION_NONE);
        this.message.setStatus(Message.STATUS_RECEIVED);
        this.message.setErrorMessage(null);
        this.message.setTransferable(this);
    }

    @Override
    void deliverPacket(final JinglePacket jinglePacket) {
        switch (jinglePacket.getAction()) {
            case SESSION_ACCEPT -> receiveSessionAccept(jinglePacket);
            case SESSION_INITIATE -> receiveSessionInitiate(jinglePacket);
            case SESSION_INFO -> receiveSessionInfo(jinglePacket);
            case SESSION_TERMINATE -> receiveSessionTerminate(jinglePacket);
            case TRANSPORT_ACCEPT -> receiveTransportAccept(jinglePacket);
            case TRANSPORT_INFO -> receiveTransportInfo(jinglePacket);
            case TRANSPORT_REPLACE -> receiveTransportReplace(jinglePacket);
            default -> {
                respondOk(jinglePacket);
                Log.d(
                        Config.LOGTAG,
                        String.format(
                                "%s: received unhandled jingle action %s",
                                id.account.getJid().asBareJid(), jinglePacket.getAction()));
            }
        }
    }

    public void sendSessionInitialize() {
        final ListenableFuture<Optional<XmppAxolotlMessage>> keyTransportMessage;
        if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
            keyTransportMessage =
                    Futures.transform(
                            id.account
                                    .getAxolotlService()
                                    .prepareKeyTransportMessage(requireConversation()),
                            Optional::of,
                            MoreExecutors.directExecutor());
        } else {
            keyTransportMessage = Futures.immediateFuture(Optional.empty());
        }
        Futures.addCallback(
                keyTransportMessage,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Optional<XmppAxolotlMessage> xmppAxolotlMessage) {
                        sendSessionInitialize(xmppAxolotlMessage.orElse(null));
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        Log.d(Config.LOGTAG, "can not send message");
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void sendSessionInitialize(final XmppAxolotlMessage xmppAxolotlMessage) {
        this.transport = setupTransport();
        this.transport.setTransportCallback(this);
        final File file = xmppConnectionService.getFileBackend().getFile(message);
        final var fileDescription =
                new FileTransferDescription.File(
                        file.length(),
                        file.getName(),
                        message.getMimeType(),
                        Collections.emptyList());
        final var transportInfoFuture = this.transport.asInitialTransportInfo();
        Futures.addCallback(
                transportInfoFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(
                            final Transport.InitialTransportInfo initialTransportInfo) {
                        final FileTransferContentMap contentMap =
                                FileTransferContentMap.of(fileDescription, initialTransportInfo);
                        sendSessionInitialize(xmppAxolotlMessage, contentMap);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {}
                },
                MoreExecutors.directExecutor());
    }

    private Conversation requireConversation() {
        final var conversational = message.getConversation();
        if (conversational instanceof Conversation c) {
            return c;
        } else {
            throw new IllegalStateException("Message had no proper conversation attached");
        }
    }

    private void sendSessionInitialize(
            final XmppAxolotlMessage xmppAxolotlMessage, final FileTransferContentMap contentMap) {
        if (transition(
                State.SESSION_INITIALIZED,
                () -> this.initiatorFileTransferContentMap = contentMap)) {
            final var jinglePacket =
                    contentMap.toJinglePacket(JinglePacket.Action.SESSION_INITIATE, id.sessionId);
            if (xmppAxolotlMessage != null) {
                this.transportSecurity =
                        new TransportSecurity(
                                xmppAxolotlMessage.getInnerKey(), xmppAxolotlMessage.getIV());
                final var contents = jinglePacket.getJingleContents();
                final var rawContent =
                        contents.get(Iterables.getOnlyElement(contentMap.contents.keySet()));
                if (rawContent != null) {
                    rawContent.setSecurity(xmppAxolotlMessage);
                }
            }
            jinglePacket.setTo(id.with);
            xmppConnectionService.sendIqPacket(
                    id.account,
                    jinglePacket,
                    (a, response) -> {
                        if (response.getType() == IqPacket.TYPE.RESULT) {
                            xmppConnectionService.markMessage(message, Message.STATUS_OFFERED);
                            return;
                        }
                        if (response.getType() == IqPacket.TYPE.ERROR) {
                            handleIqErrorResponse(response);
                            return;
                        }
                        if (response.getType() == IqPacket.TYPE.TIMEOUT) {
                            handleIqTimeoutResponse(response);
                        }
                    });
            this.transport.readyToSentAdditionalCandidates();
        }
    }

    private void receiveSessionAccept(final JinglePacket jinglePacket) {
        Log.d(Config.LOGTAG, "receive file transfer session accept");
        if (isResponder()) {
            receiveOutOfOrderAction(jinglePacket, JinglePacket.Action.SESSION_ACCEPT);
            return;
        }
        final FileTransferContentMap contentMap;
        try {
            contentMap = FileTransferContentMap.of(jinglePacket);
            contentMap.requireOnlyFileTransferDescription();
        } catch (final RuntimeException e) {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid() + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        receiveSessionAccept(jinglePacket, contentMap);
    }

    private void receiveSessionAccept(
            final JinglePacket jinglePacket, final FileTransferContentMap contentMap) {
        if (transition(State.SESSION_ACCEPTED, () -> setRemoteContentMap(contentMap))) {
            respondOk(jinglePacket);
            final var transport = this.transport;
            if (configureTransportWithPeerInfo(transport, contentMap)) {
                transport.connect();
            } else {
                Log.e(
                        Config.LOGTAG,
                        "Transport in session accept did not match our session-initialize");
                terminateTransport();
                sendSessionTerminate(
                        Reason.FAILED_APPLICATION,
                        "Transport in session accept did not match our session-initialize");
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid() + ": receive out of order session-accept");
            receiveOutOfOrderAction(jinglePacket, JinglePacket.Action.SESSION_ACCEPT);
        }
    }

    private static boolean configureTransportWithPeerInfo(
            final Transport transport, final FileTransferContentMap contentMap) {
        final GenericTransportInfo transportInfo = contentMap.requireOnlyTransportInfo();
        if (transport instanceof WebRTCDataChannelTransport webRTCDataChannelTransport
                && transportInfo instanceof WebRTCDataChannelTransportInfo) {
            webRTCDataChannelTransport.setResponderDescription(SessionDescription.of(contentMap));
            return true;
        } else if (transport instanceof SocksByteStreamsTransport socksBytestreamsTransport
                && transportInfo
                        instanceof SocksByteStreamsTransportInfo socksBytestreamsTransportInfo) {
            socksBytestreamsTransport.setTheirCandidates(
                    socksBytestreamsTransportInfo.getCandidates());
            return true;
        } else if (transport instanceof InbandBytestreamsTransport inbandBytestreamsTransport
                && transportInfo instanceof IbbTransportInfo ibbTransportInfo) {
            final var peerBlockSize = ibbTransportInfo.getBlockSize();
            if (peerBlockSize != null) {
                inbandBytestreamsTransport.setPeerBlockSize(peerBlockSize);
            }
            return true;
        } else {
            return false;
        }
    }

    private void receiveSessionInitiate(final JinglePacket jinglePacket) {
        if (isInitiator()) {
            receiveOutOfOrderAction(jinglePacket, JinglePacket.Action.SESSION_INITIATE);
            return;
        }
        Log.d(Config.LOGTAG, "receive session initiate " + jinglePacket);
        final FileTransferContentMap contentMap;
        final FileTransferDescription.File file;
        try {
            contentMap = FileTransferContentMap.of(jinglePacket);
            contentMap.requireContentDescriptions();
            file = contentMap.requireOnlyFile();
            // TODO check is offer
        } catch (final RuntimeException e) {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid() + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        final XmppAxolotlMessage.XmppAxolotlKeyTransportMessage keyTransportMessage;
        final var contents = jinglePacket.getJingleContents();
        final var rawContent = contents.get(Iterables.getOnlyElement(contentMap.contents.keySet()));
        final var security =
                rawContent == null ? null : rawContent.getSecurity(jinglePacket.getFrom());
        if (security != null) {
            Log.d(Config.LOGTAG, "found security element!");
            keyTransportMessage =
                    id.account
                            .getAxolotlService()
                            .processReceivingKeyTransportMessage(security, false);
        } else {
            keyTransportMessage = null;
        }
        receiveSessionInitiate(jinglePacket, contentMap, file, keyTransportMessage);
    }

    private void receiveSessionInitiate(
            final JinglePacket jinglePacket,
            final FileTransferContentMap contentMap,
            final FileTransferDescription.File file,
            final XmppAxolotlMessage.XmppAxolotlKeyTransportMessage keyTransportMessage) {

        if (transition(State.SESSION_INITIALIZED, () -> setRemoteContentMap(contentMap))) {
            respondOk(jinglePacket);
            Log.d(
                    Config.LOGTAG,
                    "got file offer " + file + " jet=" + Objects.nonNull(keyTransportMessage));
            setFileOffer(file);
            if (keyTransportMessage != null) {
                this.transportSecurity =
                        new TransportSecurity(
                                keyTransportMessage.getKey(), keyTransportMessage.getIv());
                this.message.setFingerprint(keyTransportMessage.getFingerprint());
                this.message.setEncryption(Message.ENCRYPTION_AXOLOTL);
            } else {
                this.transportSecurity = null;
                this.message.setFingerprint(null);
            }
            final var conversation = (Conversation) message.getConversation();
            conversation.add(message);

            // make auto accept decision
            if (id.account.getRoster().getContact(id.with).showInContactList()
                    && jingleConnectionManager.hasStoragePermission()
                    && file.size <= this.jingleConnectionManager.getAutoAcceptFileSize()
                    && xmppConnectionService.isDataSaverDisabled()) {
                Log.d(Config.LOGTAG, "auto accepting file from " + id.with);
                this.acceptedAutomatically = true;
                this.sendSessionAccept();
            } else {
                Log.d(
                        Config.LOGTAG,
                        "not auto accepting new file offer with size: "
                                + file.size
                                + " allowed size:"
                                + this.jingleConnectionManager.getAutoAcceptFileSize());
                message.markUnread();
                this.xmppConnectionService.updateConversationUi();
                this.xmppConnectionService.getNotificationService().push(message);
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid() + ": receive out of order session-initiate");
            receiveOutOfOrderAction(jinglePacket, JinglePacket.Action.SESSION_INITIATE);
        }
    }

    private void setFileOffer(final FileTransferDescription.File file) {
        final AbstractConnectionManager.Extension extension =
                AbstractConnectionManager.Extension.of(file.name);
        if (VALID_CRYPTO_EXTENSIONS.contains(extension.main)) {
            this.message.setEncryption(Message.ENCRYPTION_PGP);
        } else {
            this.message.setEncryption(Message.ENCRYPTION_NONE);
        }
        final String ext = extension.getExtension();
        final String filename =
                Strings.isNullOrEmpty(ext)
                        ? message.getUuid()
                        : String.format("%s.%s", message.getUuid(), ext);
        xmppConnectionService.getFileBackend().setupRelativeFilePath(message, filename);
    }

    public void sendSessionAccept() {
        final FileTransferContentMap contentMap = this.initiatorFileTransferContentMap;
        final Transport transport;
        try {
            transport = setupTransport(contentMap.requireOnlyTransportInfo());
        } catch (final RuntimeException e) {
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        transitionOrThrow(State.SESSION_ACCEPTED);
        this.transport = transport;
        this.transport.setTransportCallback(this);
        if (this.transport instanceof WebRTCDataChannelTransport webRTCDataChannelTransport) {
            final var sessionDescription = SessionDescription.of(contentMap);
            webRTCDataChannelTransport.setInitiatorDescription(sessionDescription);
        }
        final var transportInfoFuture = transport.asTransportInfo();
        Futures.addCallback(
                transportInfoFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Transport.TransportInfo transportInfo) {
                        final FileTransferContentMap responderContentMap =
                                contentMap.withTransport(transportInfo);
                        sendSessionAccept(responderContentMap);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        failureToAcceptSession(throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void sendSessionAccept(final FileTransferContentMap contentMap) {
        setLocalContentMap(contentMap);
        final var jinglePacket =
                contentMap.toJinglePacket(JinglePacket.Action.SESSION_ACCEPT, id.sessionId);
        send(jinglePacket);
        // this needs to come after session-accept or else our candidate-error might arrive first
        this.transport.connect();
        this.transport.readyToSentAdditionalCandidates();
        if (this.transport instanceof WebRTCDataChannelTransport webRTCDataChannelTransport) {
            drainPendingIncomingIceCandidates(webRTCDataChannelTransport);
        }
    }

    private void drainPendingIncomingIceCandidates(
            final WebRTCDataChannelTransport webRTCDataChannelTransport) {
        while (this.pendingIncomingIceCandidates.peek() != null) {
            final var candidate = this.pendingIncomingIceCandidates.poll();
            if (candidate == null) {
                continue;
            }
            webRTCDataChannelTransport.addIceCandidates(ImmutableList.of(candidate));
        }
    }

    private Transport setupTransport(final GenericTransportInfo transportInfo) {
        final XmppConnection xmppConnection = id.account.getXmppConnection();
        final boolean useTor = id.account.isOnion() || xmppConnectionService.useTorToConnect();
        if (transportInfo instanceof IbbTransportInfo ibbTransportInfo) {
            final String streamId = ibbTransportInfo.getTransportId();
            final Long blockSize = ibbTransportInfo.getBlockSize();
            if (streamId == null || blockSize == null) {
                throw new IllegalStateException("ibb transport is missing sid and/or block-size");
            }
            return new InbandBytestreamsTransport(
                    xmppConnection,
                    id.with,
                    isInitiator(),
                    streamId,
                    Ints.saturatedCast(blockSize));
        } else if (transportInfo
                instanceof SocksByteStreamsTransportInfo socksBytestreamsTransportInfo) {
            final String streamId = socksBytestreamsTransportInfo.getTransportId();
            final String destination = socksBytestreamsTransportInfo.getDestinationAddress();
            final List<SocksByteStreamsTransport.Candidate> candidates =
                    socksBytestreamsTransportInfo.getCandidates();
            Log.d(Config.LOGTAG, "received socks candidates " + candidates);
            return new SocksByteStreamsTransport(
                    xmppConnection, id, isInitiator(), useTor, streamId, candidates);
        } else if (!useTor && transportInfo instanceof WebRTCDataChannelTransportInfo) {
            return new WebRTCDataChannelTransport(
                    xmppConnectionService.getApplicationContext(),
                    xmppConnection,
                    id.account,
                    isInitiator());
        } else {
            throw new IllegalArgumentException("Do not know how to create transport");
        }
    }

    private Transport setupTransport() {
        final XmppConnection xmppConnection = id.account.getXmppConnection();
        final boolean useTor = id.account.isOnion() || xmppConnectionService.useTorToConnect();
        if (!useTor && remoteHasFeature(Namespace.JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL)) {
            return new WebRTCDataChannelTransport(
                    xmppConnectionService.getApplicationContext(),
                    xmppConnection,
                    id.account,
                    isInitiator());
        }
        if (remoteHasFeature(Namespace.JINGLE_TRANSPORTS_S5B)) {
            return new SocksByteStreamsTransport(xmppConnection, id, isInitiator(), useTor);
        }
        return setupLastResortTransport();
    }

    private Transport setupLastResortTransport() {
        final XmppConnection xmppConnection = id.account.getXmppConnection();
        return new InbandBytestreamsTransport(xmppConnection, id.with, isInitiator());
    }

    private void failureToAcceptSession(final Throwable throwable) {
        if (isTerminated()) {
            return;
        }
        final Throwable rootCause = Throwables.getRootCause(throwable);
        Log.d(Config.LOGTAG, "unable to send session accept", rootCause);
        sendSessionTerminate(Reason.ofThrowable(rootCause), rootCause.getMessage());
    }

    private void receiveSessionInfo(final JinglePacket jinglePacket) {
        respondOk(jinglePacket);
        final var sessionInfo = FileTransferDescription.getSessionInfo(jinglePacket);
        if (sessionInfo instanceof FileTransferDescription.Checksum checksum) {
            receiveSessionInfoChecksum(checksum);
        } else if (sessionInfo instanceof FileTransferDescription.Received received) {
            receiveSessionInfoReceived(received);
        }
    }

    private void receiveSessionInfoChecksum(final FileTransferDescription.Checksum checksum) {
        Log.d(Config.LOGTAG, "received checksum " + checksum);
    }

    private void receiveSessionInfoReceived(final FileTransferDescription.Received received) {
        Log.d(Config.LOGTAG, "peer confirmed received " + received);
    }

    private void receiveSessionTerminate(final JinglePacket jinglePacket) {
        respondOk(jinglePacket);
        final JinglePacket.ReasonWrapper wrapper = jinglePacket.getReason();
        final State previous = this.state;
        Log.d(
                Config.LOGTAG,
                id.account.getJid().asBareJid()
                        + ": received session terminate reason="
                        + wrapper.reason
                        + "("
                        + Strings.nullToEmpty(wrapper.text)
                        + ") while in state "
                        + previous);
        if (TERMINATED.contains(previous)) {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid()
                            + ": ignoring session terminate because already in "
                            + previous);
            return;
        }
        if (isInitiator()) {
            this.message.setErrorMessage(
                    Strings.isNullOrEmpty(wrapper.text) ? wrapper.reason.toString() : wrapper.text);
        }
        terminateTransport();
        final State target = reasonToState(wrapper.reason);
        transitionOrThrow(target);
        finish();
    }

    private void receiveTransportAccept(final JinglePacket jinglePacket) {
        if (isResponder()) {
            receiveOutOfOrderAction(jinglePacket, JinglePacket.Action.TRANSPORT_ACCEPT);
            return;
        }
        Log.d(Config.LOGTAG, "receive transport accept " + jinglePacket);
        final GenericTransportInfo transportInfo;
        try {
            transportInfo = FileTransferContentMap.of(jinglePacket).requireOnlyTransportInfo();
        } catch (final RuntimeException e) {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid() + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        if (isInState(State.SESSION_ACCEPTED)) {
            final var group = jinglePacket.getGroup();
            receiveTransportAccept(jinglePacket, new Transport.TransportInfo(transportInfo, group));
        } else {
            receiveOutOfOrderAction(jinglePacket, JinglePacket.Action.TRANSPORT_ACCEPT);
        }
    }

    private void receiveTransportAccept(
            final JinglePacket jinglePacket, final Transport.TransportInfo transportInfo) {
        final FileTransferContentMap remoteContentMap =
                getRemoteContentMap().withTransport(transportInfo);
        setRemoteContentMap(remoteContentMap);
        respondOk(jinglePacket);
        final var transport = this.transport;
        if (configureTransportWithPeerInfo(transport, remoteContentMap)) {
            transport.connect();
        } else {
            Log.e(
                    Config.LOGTAG,
                    "Transport in transport-accept did not match our transport-replace");
            terminateTransport();
            sendSessionTerminate(
                    Reason.FAILED_APPLICATION,
                    "Transport in transport-accept did not match our transport-replace");
        }
    }

    private void receiveTransportInfo(final JinglePacket jinglePacket) {
        final FileTransferContentMap contentMap;
        final GenericTransportInfo transportInfo;
        try {
            contentMap = FileTransferContentMap.of(jinglePacket);
            transportInfo = contentMap.requireOnlyTransportInfo();
        } catch (final RuntimeException e) {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid() + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        respondOk(jinglePacket);
        final var transport = this.transport;
        if (transport instanceof SocksByteStreamsTransport socksBytestreamsTransport
                && transportInfo
                        instanceof SocksByteStreamsTransportInfo socksBytestreamsTransportInfo) {
            receiveTransportInfo(socksBytestreamsTransport, socksBytestreamsTransportInfo);
        } else if (transport instanceof WebRTCDataChannelTransport webRTCDataChannelTransport
                && transportInfo
                        instanceof WebRTCDataChannelTransportInfo webRTCDataChannelTransportInfo) {
            receiveTransportInfo(
                    Iterables.getOnlyElement(contentMap.contents.keySet()),
                    webRTCDataChannelTransport,
                    webRTCDataChannelTransportInfo);
        } else if (transportInfo
                instanceof WebRTCDataChannelTransportInfo webRTCDataChannelTransportInfo) {
            receiveTransportInfo(
                    Iterables.getOnlyElement(contentMap.contents.keySet()),
                    webRTCDataChannelTransportInfo);
        } else {
            Log.d(Config.LOGTAG, "could not deliver transport-info to transport");
        }
    }

    private void receiveTransportInfo(
            final String contentName,
            final WebRTCDataChannelTransport webRTCDataChannelTransport,
            final WebRTCDataChannelTransportInfo webRTCDataChannelTransportInfo) {
        final var credentials = webRTCDataChannelTransportInfo.getCredentials();
        final var iceCandidates =
                WebRTCDataChannelTransport.iceCandidatesOf(
                        contentName, credentials, webRTCDataChannelTransportInfo.getCandidates());
        final var localContentMap = getLocalContentMap();
        if (localContentMap == null) {
            Log.d(Config.LOGTAG, "transport not ready. add pending ice candidate");
            this.pendingIncomingIceCandidates.addAll(iceCandidates);
        } else {
            webRTCDataChannelTransport.addIceCandidates(iceCandidates);
        }
    }

    private void receiveTransportInfo(
            final String contentName,
            final WebRTCDataChannelTransportInfo webRTCDataChannelTransportInfo) {
        final var credentials = webRTCDataChannelTransportInfo.getCredentials();
        final var iceCandidates =
                WebRTCDataChannelTransport.iceCandidatesOf(
                        contentName, credentials, webRTCDataChannelTransportInfo.getCandidates());
        this.pendingIncomingIceCandidates.addAll(iceCandidates);
    }

    private void receiveTransportInfo(
            final SocksByteStreamsTransport socksBytestreamsTransport,
            final SocksByteStreamsTransportInfo socksBytestreamsTransportInfo) {
        final var transportInfo = socksBytestreamsTransportInfo.getTransportInfo();
        if (transportInfo instanceof SocksByteStreamsTransportInfo.CandidateError) {
            socksBytestreamsTransport.setCandidateError();
        } else if (transportInfo
                instanceof SocksByteStreamsTransportInfo.CandidateUsed candidateUsed) {
            if (!socksBytestreamsTransport.setCandidateUsed(candidateUsed.cid)) {
                sendSessionTerminate(
                        Reason.FAILED_TRANSPORT,
                        String.format(
                                "Peer is not connected to our candidate %s", candidateUsed.cid));
            }
        } else if (transportInfo instanceof SocksByteStreamsTransportInfo.Activated activated) {
            socksBytestreamsTransport.setProxyActivated(activated.cid);
        } else if (transportInfo instanceof SocksByteStreamsTransportInfo.ProxyError) {
            socksBytestreamsTransport.setProxyError();
        }
    }

    private void receiveTransportReplace(final JinglePacket jinglePacket) {
        if (isInitiator()) {
            receiveOutOfOrderAction(jinglePacket, JinglePacket.Action.TRANSPORT_REPLACE);
            return;
        }
        final GenericTransportInfo transportInfo;
        try {
            transportInfo = FileTransferContentMap.of(jinglePacket).requireOnlyTransportInfo();
        } catch (final RuntimeException e) {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid() + ": improperly formatted contents",
                    Throwables.getRootCause(e));
            respondOk(jinglePacket);
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        if (isInState(State.SESSION_ACCEPTED)) {
            receiveTransportReplace(jinglePacket, transportInfo);
        } else {
            receiveOutOfOrderAction(jinglePacket, JinglePacket.Action.TRANSPORT_REPLACE);
        }
    }

    private void receiveTransportReplace(
            final JinglePacket jinglePacket, final GenericTransportInfo transportInfo) {
        respondOk(jinglePacket);
        final Transport currentTransport = this.transport;
        if (currentTransport != null) {
            Log.d(
                    Config.LOGTAG,
                    "terminating "
                            + currentTransport.getClass().getSimpleName()
                            + " upon receiving transport-replace");
            currentTransport.setTransportCallback(null);
            currentTransport.terminate();
        }
        final Transport nextTransport;
        try {
            nextTransport = setupTransport(transportInfo);
        } catch (final RuntimeException e) {
            sendSessionTerminate(Reason.of(e), e.getMessage());
            return;
        }
        this.transport = nextTransport;
        Log.d(
                Config.LOGTAG,
                "replacing transport with " + nextTransport.getClass().getSimpleName());
        this.transport.setTransportCallback(this);
        final var transportInfoFuture = nextTransport.asTransportInfo();
        Futures.addCallback(
                transportInfoFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Transport.TransportInfo transportWrapper) {
                        final FileTransferContentMap contentMap =
                                getLocalContentMap().withTransport(transportWrapper);
                        sendTransportAccept(contentMap);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        // transition into application failed (analogues to failureToAccept
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void sendTransportAccept(final FileTransferContentMap contentMap) {
        setLocalContentMap(contentMap);
        final var jinglePacket =
                contentMap
                        .transportInfo()
                        .toJinglePacket(JinglePacket.Action.TRANSPORT_ACCEPT, id.sessionId);
        send(jinglePacket);
        transport.connect();
    }

    protected void sendSessionTerminate(final Reason reason, final String text) {
        if (isInitiator()) {
            this.message.setErrorMessage(Strings.isNullOrEmpty(text) ? reason.toString() : text);
        }
        sendSessionTerminate(reason, text, null);
    }

    private FileTransferContentMap getLocalContentMap() {
        return isInitiator()
                ? this.initiatorFileTransferContentMap
                : this.responderFileTransferContentMap;
    }

    private FileTransferContentMap getRemoteContentMap() {
        return isInitiator()
                ? this.responderFileTransferContentMap
                : this.initiatorFileTransferContentMap;
    }

    private void setLocalContentMap(final FileTransferContentMap contentMap) {
        if (isInitiator()) {
            this.initiatorFileTransferContentMap = contentMap;
        } else {
            this.responderFileTransferContentMap = contentMap;
        }
    }

    private void setRemoteContentMap(final FileTransferContentMap contentMap) {
        if (isInitiator()) {
            this.responderFileTransferContentMap = contentMap;
        } else {
            this.initiatorFileTransferContentMap = contentMap;
        }
    }

    public Transport getTransport() {
        return this.transport;
    }

    @Override
    protected void terminateTransport() {
        final var transport = this.transport;
        if (transport == null) {
            return;
        }
        transport.terminate();
        this.transport = null;
    }

    @Override
    void notifyRebound() {}

    @Override
    public void onTransportEstablished() {
        Log.d(Config.LOGTAG, "on transport established");
        final AbstractFileTransceiver fileTransceiver;
        try {
            fileTransceiver = setupTransceiver(isResponder());
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "failed to set up file transceiver", e);
            sendSessionTerminate(Reason.ofThrowable(e), e.getMessage());
            return;
        }
        this.fileTransceiver = fileTransceiver;
        final var fileTransceiverThread = new Thread(fileTransceiver);
        fileTransceiverThread.start();
        Futures.addCallback(
                fileTransceiver.complete,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final List<FileTransferDescription.Hash> hashes) {
                        onFileTransmissionComplete(hashes);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        onFileTransmissionFailed(throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void onFileTransmissionComplete(final List<FileTransferDescription.Hash> hashes) {
        // TODO if we ever support receiving files this should become isSending(); isReceiving()
        if (isInitiator()) {
            sendSessionInfoChecksum(hashes);
        } else {
            Log.d(Config.LOGTAG, "file transfer complete " + hashes);
            sendFileSessionInfoReceived();
            terminateTransport();
            messageReceivedSuccess();
            sendSessionTerminate(Reason.SUCCESS, null);
        }
    }

    private void messageReceivedSuccess() {
        this.message.setTransferable(null);
        xmppConnectionService.getFileBackend().updateFileParams(message);
        xmppConnectionService.databaseBackend.createMessage(message);
        final File file = xmppConnectionService.getFileBackend().getFile(message);
        if (acceptedAutomatically) {
            message.markUnread();
            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                id.account.getPgpDecryptionService().decrypt(message, true);
            } else {
                xmppConnectionService
                        .getFileBackend()
                        .updateMediaScanner(
                                file,
                                () ->
                                        JingleFileTransferConnection.this
                                                .xmppConnectionService
                                                .getNotificationService()
                                                .push(message));
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            id.account.getPgpDecryptionService().decrypt(message, false);
        } else {
            xmppConnectionService.getFileBackend().updateMediaScanner(file);
        }
    }

    private void onFileTransmissionFailed(final Throwable throwable) {
        if (isTerminated()) {
            Log.d(
                    Config.LOGTAG,
                    "file transfer failed but session is already terminated",
                    throwable);
        } else {
            terminateTransport();
            Log.d(Config.LOGTAG, "on file transmission failed", throwable);
            sendSessionTerminate(Reason.CONNECTIVITY_ERROR, null);
        }
    }

    private AbstractFileTransceiver setupTransceiver(final boolean receiving) throws IOException {
        final var fileDescription = getLocalContentMap().requireOnlyFile();
        final File file = xmppConnectionService.getFileBackend().getFile(message);
        final Runnable updateRunnable = () -> jingleConnectionManager.updateConversationUi(false);
        if (receiving) {
            return new FileReceiver(
                    file,
                    this.transportSecurity,
                    transport.getInputStream(),
                    transport.getTerminationLatch(),
                    fileDescription.size,
                    updateRunnable);
        } else {
            return new FileTransmitter(
                    file,
                    this.transportSecurity,
                    transport.getOutputStream(),
                    transport.getTerminationLatch(),
                    fileDescription.size,
                    updateRunnable);
        }
    }

    private void sendFileSessionInfoReceived() {
        final var contentMap = getLocalContentMap();
        final String name = Iterables.getOnlyElement(contentMap.contents.keySet());
        sendSessionInfo(new FileTransferDescription.Received(name));
    }

    private void sendSessionInfoChecksum(List<FileTransferDescription.Hash> hashes) {
        final var contentMap = getLocalContentMap();
        final String name = Iterables.getOnlyElement(contentMap.contents.keySet());
        sendSessionInfo(new FileTransferDescription.Checksum(name, hashes));
    }

    private void sendSessionInfo(final FileTransferDescription.SessionInfo sessionInfo) {
        final var jinglePacket =
                new JinglePacket(JinglePacket.Action.SESSION_INFO, this.id.sessionId);
        jinglePacket.addJingleChild(sessionInfo.asElement());
        jinglePacket.setTo(this.id.with);
        send(jinglePacket);
    }

    @Override
    public void onTransportSetupFailed() {
        final var transport = this.transport;
        if (transport == null) {
            // this really is not supposed to happen
            sendSessionTerminate(Reason.FAILED_APPLICATION, null);
            return;
        }
        Log.d(Config.LOGTAG, "onTransportSetupFailed");
        final var isTransportInBand = transport instanceof InbandBytestreamsTransport;
        if (isTransportInBand) {
            terminateTransport();
            sendSessionTerminate(Reason.CONNECTIVITY_ERROR, "Failed to setup IBB transport");
            return;
        }
        // terminate the current transport
        transport.terminate();
        if (isInitiator()) {
            this.transport = setupLastResortTransport();
            Log.d(
                    Config.LOGTAG,
                    "replacing transport with " + this.transport.getClass().getSimpleName());
            this.transport.setTransportCallback(this);
            final var transportInfoFuture = this.transport.asTransportInfo();
            Futures.addCallback(
                    transportInfoFuture,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(final Transport.TransportInfo transportWrapper) {
                            final FileTransferContentMap contentMap = getLocalContentMap();
                            sendTransportReplace(contentMap.withTransport(transportWrapper));
                        }

                        @Override
                        public void onFailure(@NonNull Throwable throwable) {
                            // TODO send application failure;
                        }
                    },
                    MoreExecutors.directExecutor());

        } else {
            Log.d(Config.LOGTAG, "transport setup failed. waiting for initiator to replace");
        }
    }

    private void sendTransportReplace(final FileTransferContentMap contentMap) {
        setLocalContentMap(contentMap);
        final var jinglePacket =
                contentMap
                        .transportInfo()
                        .toJinglePacket(JinglePacket.Action.TRANSPORT_REPLACE, id.sessionId);
        send(jinglePacket);
    }

    @Override
    public void onAdditionalCandidate(
            final String contentName, final Transport.Candidate candidate) {
        if (candidate instanceof IceUdpTransportInfo.Candidate iceCandidate) {
            sendTransportInfo(contentName, iceCandidate);
        }
    }

    public void sendTransportInfo(
            final String contentName, final IceUdpTransportInfo.Candidate candidate) {
        final FileTransferContentMap transportInfo;
        try {
            final FileTransferContentMap rtpContentMap = getLocalContentMap();
            transportInfo = rtpContentMap.transportInfo(contentName, candidate);
        } catch (final Exception e) {
            Log.d(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid()
                            + ": unable to prepare transport-info from candidate for content="
                            + contentName);
            return;
        }
        final JinglePacket jinglePacket =
                transportInfo.toJinglePacket(JinglePacket.Action.TRANSPORT_INFO, id.sessionId);
        send(jinglePacket);
    }

    @Override
    public void onCandidateUsed(
            final String streamId, final SocksByteStreamsTransport.Candidate candidate) {
        final FileTransferContentMap contentMap = getLocalContentMap();
        if (contentMap == null) {
            Log.e(Config.LOGTAG, "local content map is null on candidate used");
            return;
        }
        final var jinglePacket =
                contentMap
                        .candidateUsed(streamId, candidate.cid)
                        .toJinglePacket(JinglePacket.Action.TRANSPORT_INFO, id.sessionId);
        Log.d(Config.LOGTAG, "sending candidate used " + jinglePacket);
        send(jinglePacket);
    }

    @Override
    public void onCandidateError(final String streamId) {
        final FileTransferContentMap contentMap = getLocalContentMap();
        if (contentMap == null) {
            Log.e(Config.LOGTAG, "local content map is null on candidate used");
            return;
        }
        final var jinglePacket =
                contentMap
                        .candidateError(streamId)
                        .toJinglePacket(JinglePacket.Action.TRANSPORT_INFO, id.sessionId);
        Log.d(Config.LOGTAG, "sending candidate error " + jinglePacket);
        send(jinglePacket);
    }

    @Override
    public void onProxyActivated(String streamId, SocksByteStreamsTransport.Candidate candidate) {
        final FileTransferContentMap contentMap = getLocalContentMap();
        if (contentMap == null) {
            Log.e(Config.LOGTAG, "local content map is null on candidate used");
            return;
        }
        final var jinglePacket =
                contentMap
                        .proxyActivated(streamId, candidate.cid)
                        .toJinglePacket(JinglePacket.Action.TRANSPORT_INFO, id.sessionId);
        send(jinglePacket);
    }

    @Override
    protected boolean transition(final State target, final Runnable runnable) {
        final boolean transitioned = super.transition(target, runnable);
        if (transitioned && isInitiator()) {
            Log.d(Config.LOGTAG, "running mark message hooks");
            if (target == State.SESSION_ACCEPTED) {
                xmppConnectionService.markMessage(message, Message.STATUS_UNSEND);
            } else if (target == State.TERMINATED_SUCCESS) {
                xmppConnectionService.markMessage(message, Message.STATUS_SEND_RECEIVED);
            } else if (TERMINATED.contains(target)) {
                xmppConnectionService.markMessage(
                        message, Message.STATUS_SEND_FAILED, message.getErrorMessage());
            } else {
                xmppConnectionService.updateConversationUi();
            }
        } else {
            if (Arrays.asList(State.TERMINATED_CANCEL_OR_TIMEOUT, State.TERMINATED_DECLINED_OR_BUSY)
                    .contains(target)) {
                this.message.setTransferable(
                        new TransferablePlaceholder(Transferable.STATUS_CANCELLED));
            } else if (target != State.TERMINATED_SUCCESS && TERMINATED.contains(target)) {
                this.message.setTransferable(
                        new TransferablePlaceholder(Transferable.STATUS_FAILED));
            }
            xmppConnectionService.updateConversationUi();
        }
        return transitioned;
    }

    @Override
    protected void finish() {
        if (transport != null) {
            throw new AssertionError(
                    "finish MUST not be called without terminating the transport first");
        }
        // we don't want to remove TransferablePlaceholder
        if (message.getTransferable() instanceof JingleFileTransferConnection) {
            Log.d(Config.LOGTAG, "nulling transferable on message");
            this.message.setTransferable(null);
        }
        super.finish();
    }

    private int getTransferableStatus() {
        // status in file transfer is a bit weird. for sending it is mostly handled via
        // Message.STATUS_* (offered, unsend (sic) send_received) the transferable status is just
        // uploading
        // for receiving the message status remains at 'received' but Transferable goes through
        // various status
        if (isInitiator()) {
            return Transferable.STATUS_UPLOADING;
        }
        final var state = getState();
        return switch (state) {
            case NULL, SESSION_INITIALIZED, SESSION_INITIALIZED_PRE_APPROVED -> Transferable
                    .STATUS_OFFER;
            case TERMINATED_APPLICATION_FAILURE,
                    TERMINATED_CONNECTIVITY_ERROR,
                    TERMINATED_DECLINED_OR_BUSY,
                    TERMINATED_SECURITY_ERROR -> Transferable.STATUS_FAILED;
            case TERMINATED_CANCEL_OR_TIMEOUT -> Transferable.STATUS_CANCELLED;
            case SESSION_ACCEPTED -> Transferable.STATUS_DOWNLOADING;
            default -> Transferable.STATUS_UNKNOWN;
        };
    }

    // these methods are for interacting with 'Transferable' - we might want to remove the concept
    // at some point

    @Override
    public boolean start() {
        Log.d(Config.LOGTAG, "user pressed start()");
        // TODO there is a 'connected' check apparently?
        if (isInState(State.SESSION_INITIALIZED)) {
            sendSessionAccept();
        }
        return true;
    }

    @Override
    public int getStatus() {
        return getTransferableStatus();
    }

    @Override
    public Long getFileSize() {
        final var transceiver = this.fileTransceiver;
        if (transceiver != null) {
            return transceiver.total;
        }
        final var contentMap = this.initiatorFileTransferContentMap;
        if (contentMap != null) {
            return contentMap.requireOnlyFile().size;
        }
        return null;
    }

    @Override
    public int getProgress() {
        final var transceiver = this.fileTransceiver;
        return transceiver != null ? transceiver.getProgress() : 0;
    }

    @Override
    public void cancel() {
        if (stopFileTransfer()) {
            Log.d(Config.LOGTAG, "user has stopped file transfer");
        } else {
            Log.d(Config.LOGTAG, "user pressed cancel but file transfer was already terminated?");
        }
    }

    private boolean stopFileTransfer() {
        if (isInitiator()) {
            return stopFileTransfer(Reason.CANCEL);
        } else {
            return stopFileTransfer(Reason.DECLINE);
        }
    }

    private boolean stopFileTransfer(final Reason reason) {
        final State target = reasonToState(reason);
        if (transition(target)) {
            // we change state before terminating transport so we don't consume the following
            // IOException and turn it into a connectivity error
            terminateTransport();
            final JinglePacket jinglePacket =
                    new JinglePacket(JinglePacket.Action.SESSION_TERMINATE, id.sessionId);
            jinglePacket.setReason(reason, "User requested to stop file transfer");
            send(jinglePacket);
            finish();
            return true;
        } else {
            return false;
        }
    }

    private abstract static class AbstractFileTransceiver implements Runnable {

        protected final SettableFuture<List<FileTransferDescription.Hash>> complete =
                SettableFuture.create();

        protected final File file;
        protected final TransportSecurity transportSecurity;

        protected final CountDownLatch transportTerminationLatch;
        protected final long total;
        protected long transmitted = 0;
        private int progress = Integer.MIN_VALUE;
        private final Runnable updateRunnable;

        private AbstractFileTransceiver(
                final File file,
                final TransportSecurity transportSecurity,
                final CountDownLatch transportTerminationLatch,
                final long total,
                final Runnable updateRunnable) {
            this.file = file;
            this.transportSecurity = transportSecurity;
            this.transportTerminationLatch = transportTerminationLatch;
            this.total = transportSecurity == null ? total : (total + 16);
            this.updateRunnable = updateRunnable;
        }

        static void closeTransport(final Closeable stream) {
            try {
                stream.close();
            } catch (final IOException e) {
                Log.d(Config.LOGTAG, "transport has already been closed. good");
            }
        }

        public int getProgress() {
            return Ints.saturatedCast(Math.round((1.0 * transmitted / total) * 100));
        }

        public void updateProgress() {
            final int current = getProgress();
            final boolean update;
            synchronized (this) {
                if (this.progress != current) {
                    this.progress = current;
                    update = true;
                } else {
                    update = false;
                }
                if (update) {
                    this.updateRunnable.run();
                }
            }
        }

        protected void awaitTransportTermination() {
            try {
                this.transportTerminationLatch.await();
            } catch (final InterruptedException ignored) {
                return;
            }
            Log.d(Config.LOGTAG, getClass().getSimpleName() + " says Goodbye!");
        }
    }

    private static class FileTransmitter extends AbstractFileTransceiver {

        private final OutputStream outputStream;

        private FileTransmitter(
                final File file,
                final TransportSecurity transportSecurity,
                final OutputStream outputStream,
                final CountDownLatch transportTerminationLatch,
                final long total,
                final Runnable updateRunnable) {
            super(file, transportSecurity, transportTerminationLatch, total, updateRunnable);
            this.outputStream = outputStream;
        }

        private InputStream openFileInputStream() throws FileNotFoundException {
            final var fileInputStream = new FileInputStream(this.file);
            if (this.transportSecurity == null) {
                return fileInputStream;
            } else {
                final AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
                cipher.init(
                        true,
                        new AEADParameters(
                                new KeyParameter(transportSecurity.key),
                                128,
                                transportSecurity.iv));
                Log.d(Config.LOGTAG, "setting up CipherInputStream");
                return new CipherInputStream(fileInputStream, cipher);
            }
        }

        @Override
        public void run() {
            Log.d(Config.LOGTAG, "file transmitter attempting to send " + total + " bytes");
            final var sha1Hasher = Hashing.sha1().newHasher();
            final var sha256Hasher = Hashing.sha256().newHasher();
            try (final var fileInputStream = openFileInputStream()) {
                final var buffer = new byte[4096];
                while (total - transmitted > 0) {
                    final int count = fileInputStream.read(buffer);
                    if (count == -1) {
                        throw new EOFException(
                                String.format("reached EOF after %d/%d", transmitted, total));
                    }
                    outputStream.write(buffer, 0, count);
                    sha1Hasher.putBytes(buffer, 0, count);
                    sha256Hasher.putBytes(buffer, 0, count);
                    transmitted += count;
                    updateProgress();
                }
                outputStream.flush();
                Log.d(
                        Config.LOGTAG,
                        "transmitted " + transmitted + " bytes from " + file.getAbsolutePath());
                final List<FileTransferDescription.Hash> hashes =
                        ImmutableList.of(
                                new FileTransferDescription.Hash(
                                        sha1Hasher.hash().asBytes(),
                                        FileTransferDescription.Algorithm.SHA_1),
                                new FileTransferDescription.Hash(
                                        sha256Hasher.hash().asBytes(),
                                        FileTransferDescription.Algorithm.SHA_256));
                complete.set(hashes);
            } catch (final Exception e) {
                complete.setException(e);
            }
            // the transport implementations backed by PipedOutputStreams do not like it when
            // the writing Thread (this thread) goes away. so we just wait until the other peer
            // has received our file and we are shutting down the transport
            Log.d(Config.LOGTAG, "waiting for transport to terminate before stopping thread");
            awaitTransportTermination();
            closeTransport(outputStream);
        }
    }

    private static class FileReceiver extends AbstractFileTransceiver {

        private final InputStream inputStream;

        private FileReceiver(
                final File file,
                final TransportSecurity transportSecurity,
                final InputStream inputStream,
                final CountDownLatch transportTerminationLatch,
                final long total,
                final Runnable updateRunnable) {
            super(file, transportSecurity, transportTerminationLatch, total, updateRunnable);
            this.inputStream = inputStream;
        }

        private OutputStream openFileOutputStream() throws FileNotFoundException {
            final var directory = this.file.getParentFile();
            if (directory != null && directory.mkdirs()) {
                Log.d(Config.LOGTAG, "created directory " + directory.getAbsolutePath());
            }
            final var fileOutputStream = new FileOutputStream(this.file);
            if (this.transportSecurity == null) {
                return fileOutputStream;
            } else {
                final AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
                cipher.init(
                        false,
                        new AEADParameters(
                                new KeyParameter(transportSecurity.key),
                                128,
                                transportSecurity.iv));
                Log.d(Config.LOGTAG, "setting up CipherOutputStream");
                return new CipherOutputStream(fileOutputStream, cipher);
            }
        }

        @Override
        public void run() {
            Log.d(Config.LOGTAG, "file receiver attempting to receive " + total + " bytes");
            final var sha1Hasher = Hashing.sha1().newHasher();
            final var sha256Hasher = Hashing.sha256().newHasher();
            try (final var fileOutputStream = openFileOutputStream()) {
                final var buffer = new byte[4096];
                while (total - transmitted > 0) {
                    final int count = inputStream.read(buffer);
                    if (count == -1) {
                        throw new EOFException(
                                String.format("reached EOF after %d/%d", transmitted, total));
                    }
                    fileOutputStream.write(buffer, 0, count);
                    sha1Hasher.putBytes(buffer, 0, count);
                    sha256Hasher.putBytes(buffer, 0, count);
                    transmitted += count;
                    updateProgress();
                }
                Log.d(
                        Config.LOGTAG,
                        "written " + transmitted + " bytes to " + file.getAbsolutePath());
                final List<FileTransferDescription.Hash> hashes =
                        ImmutableList.of(
                                new FileTransferDescription.Hash(
                                        sha1Hasher.hash().asBytes(),
                                        FileTransferDescription.Algorithm.SHA_1),
                                new FileTransferDescription.Hash(
                                        sha256Hasher.hash().asBytes(),
                                        FileTransferDescription.Algorithm.SHA_256));
                complete.set(hashes);
            } catch (final Exception e) {
                complete.setException(e);
            }
            Log.d(Config.LOGTAG, "waiting for transport to terminate before stopping thread");
            awaitTransportTermination();
            closeTransport(inputStream);
        }
    }

    private static final class TransportSecurity {
        final byte[] key;
        final byte[] iv;

        private TransportSecurity(byte[] key, byte[] iv) {
            this.key = key;
            this.iv = iv;
        }
    }
}
