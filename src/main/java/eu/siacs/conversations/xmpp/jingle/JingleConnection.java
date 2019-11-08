package eu.siacs.conversations.xmpp.jingle;

import android.util.Base64;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.entities.TransferablePlaceholder;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class JingleConnection implements Transferable {

    private static final String JET_OMEMO_CIPHER = "urn:xmpp:ciphers:aes-128-gcm-nopadding";

    private static final int JINGLE_STATUS_INITIATED = 0;
    private static final int JINGLE_STATUS_ACCEPTED = 1;
    private static final int JINGLE_STATUS_FINISHED = 4;
    static final int JINGLE_STATUS_TRANSMITTING = 5;
    private static final int JINGLE_STATUS_FAILED = 99;
    private static final int JINGLE_STATUS_OFFERED = -1;
    private JingleConnectionManager mJingleConnectionManager;
    private XmppConnectionService mXmppConnectionService;
    private Content.Version ftVersion = Content.Version.FT_3;

    private int ibbBlockSize = 8192;

    private int mJingleStatus = JINGLE_STATUS_OFFERED;
    private int mStatus = Transferable.STATUS_UNKNOWN;
    private Message message;
    private String sessionId;
    private Account account;
    private Jid initiator;
    private Jid responder;
    private List<JingleCandidate> candidates = new ArrayList<>();
    private ConcurrentHashMap<String, JingleSocks5Transport> connections = new ConcurrentHashMap<>();

    private String transportId;
    private Element fileOffer;
    private DownloadableFile file = null;

    private boolean proxyActivationFailed = false;

    private String contentName;
    private String contentCreator;
    private Transport initialTransport;
    private boolean remoteSupportsOmemoJet;

    private int mProgress = 0;

    private boolean receivedCandidate = false;
    private boolean sentCandidate = false;

    private boolean acceptedAutomatically = false;
    private boolean cancelled = false;

    private XmppAxolotlMessage mXmppAxolotlMessage;

    private JingleTransport transport = null;

    private OutputStream mFileOutputStream;
    private InputStream mFileInputStream;

    private OnIqPacketReceived responseListener = (account, packet) -> {
        if (packet.getType() != IqPacket.TYPE.RESULT) {
            fail(IqParser.extractErrorMessage(packet));
        }
    };
    private byte[] expectedHash = new byte[0];
    private final OnFileTransmissionStatusChanged onFileTransmissionStatusChanged = new OnFileTransmissionStatusChanged() {

        @Override
        public void onFileTransmitted(DownloadableFile file) {
            if (responding()) {
                if (expectedHash.length > 0 && !Arrays.equals(expectedHash, file.getSha1Sum())) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": hashes did not match");
                }
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": file transmitted(). we are responding");
                sendSuccess();
                mXmppConnectionService.getFileBackend().updateFileParams(message);
                mXmppConnectionService.databaseBackend.createMessage(message);
                mXmppConnectionService.markMessage(message, Message.STATUS_RECEIVED);
                if (acceptedAutomatically) {
                    message.markUnread();
                    if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                        account.getPgpDecryptionService().decrypt(message, true);
                    } else {
                        mXmppConnectionService.getFileBackend().updateMediaScanner(file, () -> JingleConnection.this.mXmppConnectionService.getNotificationService().push(message));

                    }
                    Log.d(Config.LOGTAG, "successfully transmitted file:" + file.getAbsolutePath() + " (" + CryptoHelper.bytesToHex(file.getSha1Sum()) + ")");
                    return;
                } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                    account.getPgpDecryptionService().decrypt(message, true);
                }
            } else {
                if (ftVersion == Content.Version.FT_5) { //older Conversations will break when receiving a session-info
                    sendHash();
                }
                if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                    account.getPgpDecryptionService().decrypt(message, false);
                }
                if (message.getEncryption() == Message.ENCRYPTION_PGP || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
                    file.delete();
                }
            }
            Log.d(Config.LOGTAG, "successfully transmitted file:" + file.getAbsolutePath() + " (" + CryptoHelper.bytesToHex(file.getSha1Sum()) + ")");
            if (message.getEncryption() != Message.ENCRYPTION_PGP) {
                mXmppConnectionService.getFileBackend().updateMediaScanner(file);
            }
        }

        @Override
        public void onFileTransferAborted() {
            JingleConnection.this.sendSessionTerminate("connectivity-error");
            JingleConnection.this.fail();
        }
    };
    private OnTransportConnected onIbbTransportConnected = new OnTransportConnected() {
        @Override
        public void failed() {
            Log.d(Config.LOGTAG, "ibb open failed");
        }

        @Override
        public void established() {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ibb transport connected. sending file");
            mJingleStatus = JINGLE_STATUS_TRANSMITTING;
            JingleConnection.this.transport.send(file, onFileTransmissionStatusChanged);
        }
    };
    private OnProxyActivated onProxyActivated = new OnProxyActivated() {

        @Override
        public void success() {
            if (initiator.equals(account.getJid())) {
                Log.d(Config.LOGTAG, "we were initiating. sending file");
                transport.send(file, onFileTransmissionStatusChanged);
            } else {
                transport.receive(file, onFileTransmissionStatusChanged);
                Log.d(Config.LOGTAG, "we were responding. receiving file");
            }
        }

        @Override
        public void failed() {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": proxy activation failed");
            proxyActivationFailed = true;
            if (initiating()) {
                sendFallbackToIbb();
            }
        }
    };

    public JingleConnection(JingleConnectionManager mJingleConnectionManager) {
        this.mJingleConnectionManager = mJingleConnectionManager;
        this.mXmppConnectionService = mJingleConnectionManager
                .getXmppConnectionService();
    }

    private boolean responding() {
        return responder != null && responder.equals(account.getJid());
    }

    private boolean initiating() {
        return initiator.equals(account.getJid());
    }

    InputStream getFileInputStream() {
        return this.mFileInputStream;
    }

    OutputStream getFileOutputStream() throws IOException {
        if (this.file == null) {
            Log.d(Config.LOGTAG, "file object was not assigned");
            return null;
        }
        this.file.getParentFile().mkdirs();
        this.file.createNewFile();
        this.mFileOutputStream = AbstractConnectionManager.createOutputStream(this.file);
        return this.mFileOutputStream;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public Account getAccount() {
        return this.account;
    }

    public Jid getCounterPart() {
        return this.message.getCounterpart();
    }

    void deliverPacket(JinglePacket packet) {
        if (packet.isAction("session-terminate")) {
            Reason reason = packet.getReason();
            if (reason != null) {
                if (reason.hasChild("cancel")) {
                    this.cancelled = true;
                    this.fail();
                } else if (reason.hasChild("success")) {
                    this.receiveSuccess();
                } else {
                    final List<Element> children = reason.getChildren();
                    if (children.size() == 1) {
                        this.fail(children.get(0).getName());
                    } else {
                        this.fail();
                    }
                }
            } else {
                this.fail();
            }
        } else if (packet.isAction("session-accept")) {
            receiveAccept(packet);
        } else if (packet.isAction("session-info")) {
            final Element checksum = packet.getChecksum();
            final Element file = checksum == null ? null : checksum.findChild("file");
            final Element hash = file == null ? null : file.findChild("hash", "urn:xmpp:hashes:2");
            if (hash != null && "sha-1".equalsIgnoreCase(hash.getAttribute("algo"))) {
                try {
                    this.expectedHash = Base64.decode(hash.getContent(), Base64.DEFAULT);
                } catch (Exception e) {
                    this.expectedHash = new byte[0];
                }
            }
            respondToIq(packet, true);
        } else if (packet.isAction("transport-info")) {
            receiveTransportInfo(packet);
        } else if (packet.isAction("transport-replace")) {
            if (packet.getJingleContent().hasIbbTransport()) {
                receiveFallbackToIbb(packet);
            } else {
                Log.d(Config.LOGTAG, "trying to fallback to something unknown" + packet.toString());
                respondToIq(packet, false);
            }
        } else if (packet.isAction("transport-accept")) {
            receiveTransportAccept(packet);
        } else {
            Log.d(Config.LOGTAG, "packet arrived in connection. action was " + packet.getAction());
            respondToIq(packet, false);
        }
    }

    private void respondToIq(final IqPacket packet, final boolean result) {
        final IqPacket response;
        if (result) {
            response = packet.generateResponse(IqPacket.TYPE.RESULT);
        } else {
            response = packet.generateResponse(IqPacket.TYPE.ERROR);
            final Element error = response.addChild("error").setAttribute("type", "cancel");
            error.addChild("not-acceptable", "urn:ietf:params:xml:ns:xmpp-stanzas");
        }
        mXmppConnectionService.sendIqPacket(account, response, null);
    }

    private void respondToIqWithOutOfOrder(final IqPacket packet) {
        final IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
        final Element error = response.addChild("error").setAttribute("type", "wait");
        error.addChild("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas");
        error.addChild("out-of-order", "urn:xmpp:jingle:errors:1");
        mXmppConnectionService.sendIqPacket(account, response, null);
    }

    public void init(final Message message) {
        if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
            Conversation conversation = (Conversation) message.getConversation();
            conversation.getAccount().getAxolotlService().prepareKeyTransportMessage(conversation, xmppAxolotlMessage -> {
                if (xmppAxolotlMessage != null) {
                    init(message, xmppAxolotlMessage);
                } else {
                    fail();
                }
            });
        } else {
            init(message, null);
        }
    }

    private void init(Message message, XmppAxolotlMessage xmppAxolotlMessage) {
        this.mXmppAxolotlMessage = xmppAxolotlMessage;
        this.contentCreator = "initiator";
        this.contentName = this.mJingleConnectionManager.nextRandomId();
        this.message = message;
        this.account = message.getConversation().getAccount();
        final List<String> remoteFeatures = getRemoteFeatures();
        upgradeNamespace(remoteFeatures);
        this.initialTransport = remoteFeatures.contains(Namespace.JINGLE_TRANSPORTS_S5B) ? Transport.SOCKS : Transport.IBB;
        this.remoteSupportsOmemoJet = remoteFeatures.contains(Namespace.JINGLE_ENCRYPTED_TRANSPORT_OMEMO);
        this.message.setTransferable(this);
        this.mStatus = Transferable.STATUS_UPLOADING;
        this.initiator = this.account.getJid();
        this.responder = this.message.getCounterpart();
        this.sessionId = this.mJingleConnectionManager.nextRandomId();
        this.transportId = this.mJingleConnectionManager.nextRandomId();
        if (this.initialTransport == Transport.IBB) {
            this.sendInitRequest();
        } else {
            gatherAndConnectDirectCandidates();
            this.mJingleConnectionManager.getPrimaryCandidate(account, initiating(), (success, candidate) -> {
                if (success) {
                    final JingleSocks5Transport socksConnection = new JingleSocks5Transport(this, candidate);
                    connections.put(candidate.getCid(), socksConnection);
                    socksConnection.connect(new OnTransportConnected() {

                        @Override
                        public void failed() {
                            Log.d(Config.LOGTAG, String.format("connection to our own proxy65 candidate failed (%s:%d)", candidate.getHost(), candidate.getPort()));
                            sendInitRequest();
                        }

                        @Override
                        public void established() {
                            Log.d(Config.LOGTAG, "successfully connected to our own proxy65 candidate");
                            mergeCandidate(candidate);
                            sendInitRequest();
                        }
                    });
                    mergeCandidate(candidate);
                } else {
                    Log.d(Config.LOGTAG, "no proxy65 candidate of our own was found");
                    sendInitRequest();
                }
            });
        }

    }

    private void gatherAndConnectDirectCandidates() {
        final List<JingleCandidate> directCandidates;
        if (Config.USE_DIRECT_JINGLE_CANDIDATES) {
            if (account.isOnion() || mXmppConnectionService.useTorToConnect()) {
                directCandidates = Collections.emptyList();
            } else {
                directCandidates = DirectConnectionUtils.getLocalCandidates(account.getJid());
            }
        } else {
            directCandidates = Collections.emptyList();
        }
        for (JingleCandidate directCandidate : directCandidates) {
            final JingleSocks5Transport socksConnection = new JingleSocks5Transport(this, directCandidate);
            connections.put(directCandidate.getCid(), socksConnection);
            candidates.add(directCandidate);
        }
    }

    private void upgradeNamespace(List<String> remoteFeatures) {
        if (remoteFeatures.contains(Content.Version.FT_5.getNamespace())) {
            this.ftVersion = Content.Version.FT_5;
        } else if (remoteFeatures.contains(Content.Version.FT_4.getNamespace())) {
            this.ftVersion = Content.Version.FT_4;
        }
    }

    private List<String> getRemoteFeatures() {
        Jid jid = this.message.getCounterpart();
        String resource = jid != null ? jid.getResource() : null;
        if (resource != null) {
            Presence presence = this.account.getRoster().getContact(jid).getPresences().getPresences().get(resource);
            ServiceDiscoveryResult result = presence != null ? presence.getServiceDiscoveryResult() : null;
            return result == null ? Collections.emptyList() : result.getFeatures();
        } else {
            return Collections.emptyList();
        }
    }

    public void init(Account account, JinglePacket packet) {
        this.mJingleStatus = JINGLE_STATUS_INITIATED;
        Conversation conversation = this.mXmppConnectionService
                .findOrCreateConversation(account,
                        packet.getFrom().asBareJid(), false, false);
        this.message = new Message(conversation, "", Message.ENCRYPTION_NONE);
        this.message.setStatus(Message.STATUS_RECEIVED);
        this.mStatus = Transferable.STATUS_OFFER;
        this.message.setTransferable(this);
        final Jid from = packet.getFrom();
        this.message.setCounterpart(from);
        this.account = account;
        this.initiator = packet.getFrom();
        this.responder = this.account.getJid();
        this.sessionId = packet.getSessionId();
        Content content = packet.getJingleContent();
        this.contentCreator = content.getAttribute("creator");
        this.initialTransport = content.hasSocks5Transport() ? Transport.SOCKS : Transport.IBB;
        this.contentName = content.getAttribute("name");
        this.transportId = content.getTransportId();


        if (this.initialTransport == Transport.SOCKS) {
            this.mergeCandidates(JingleCandidate.parse(content.socks5transport().getChildren()));
        } else if (this.initialTransport == Transport.IBB) {
            final String receivedBlockSize = content.ibbTransport().getAttribute("block-size");
            if (receivedBlockSize != null) {
                try {
                    this.ibbBlockSize = Math.min(Integer.parseInt(receivedBlockSize), this.ibbBlockSize);
                } catch (NumberFormatException e) {
                    Log.d(Config.LOGTAG, "number format exception " + e.getMessage());
                    respondToIq(packet, false);
                    this.fail();
                    return;
                }
            } else {
                Log.d(Config.LOGTAG, "received block size was null");
                respondToIq(packet, false);
                this.fail();
                return;
            }
        }
        this.ftVersion = content.getVersion();
        if (ftVersion == null) {
            respondToIq(packet, false);
            this.fail();
            return;
        }
        this.fileOffer = content.getFileOffer(this.ftVersion);


        if (fileOffer != null) {
            boolean remoteIsUsingJet = false;
            Element encrypted = fileOffer.findChild("encrypted", AxolotlService.PEP_PREFIX);
            if (encrypted == null) {
                final Element security = content.findChild("security", Namespace.JINGLE_ENCRYPTED_TRANSPORT);
                if (security != null && AxolotlService.PEP_PREFIX.equals(security.getAttribute("type"))) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received jingle file offer with JET");
                    encrypted = security.findChild("encrypted", AxolotlService.PEP_PREFIX);
                    remoteIsUsingJet = true;
                }
            }
            if (encrypted != null) {
                this.mXmppAxolotlMessage = XmppAxolotlMessage.fromElement(encrypted, packet.getFrom().asBareJid());
            }
            Element fileSize = fileOffer.findChild("size");
            final String path = fileOffer.findChildContent("name");
            if (path != null) {
                AbstractConnectionManager.Extension extension = AbstractConnectionManager.Extension.of(path);
                if (VALID_IMAGE_EXTENSIONS.contains(extension.main)) {
                    message.setType(Message.TYPE_IMAGE);
                    message.setRelativeFilePath(message.getUuid() + "." + extension.main);
                } else if (VALID_CRYPTO_EXTENSIONS.contains(extension.main)) {
                    if (VALID_IMAGE_EXTENSIONS.contains(extension.secondary)) {
                        message.setType(Message.TYPE_IMAGE);
                        message.setRelativeFilePath(message.getUuid() + "." + extension.secondary);
                    } else {
                        message.setType(Message.TYPE_FILE);
                        message.setRelativeFilePath(message.getUuid() + (extension.secondary != null ? ("." + extension.secondary) : ""));
                    }
                    message.setEncryption(Message.ENCRYPTION_PGP);
                } else {
                    message.setType(Message.TYPE_FILE);
                    message.setRelativeFilePath(message.getUuid() + (extension.main != null ? ("." + extension.main) : ""));
                }
                long size = parseLong(fileSize, 0);
                message.setBody(Long.toString(size));
                conversation.add(message);
                mJingleConnectionManager.updateConversationUi(true);
                this.file = this.mXmppConnectionService.getFileBackend().getFile(message, false);
                if (mXmppAxolotlMessage != null) {
                    XmppAxolotlMessage.XmppAxolotlKeyTransportMessage transportMessage = account.getAxolotlService().processReceivingKeyTransportMessage(mXmppAxolotlMessage, false);
                    if (transportMessage != null) {
                        message.setEncryption(Message.ENCRYPTION_AXOLOTL);
                        this.file.setKey(transportMessage.getKey());
                        this.file.setIv(transportMessage.getIv());
                        message.setFingerprint(transportMessage.getFingerprint());
                    } else {
                        Log.d(Config.LOGTAG, "could not process KeyTransportMessage");
                    }
                }
                message.resetFileParams();
                //legacy OMEMO encrypted file transfers reported the file size after encryption
                //JET reports the plain text size. however lower levels of our receiving code still
                //expect the cipher text size. so we just + 16 bytes (auth tag size) here
                this.file.setExpectedSize(size + (remoteIsUsingJet ? 16 : 0));

                respondToIq(packet, true);

                if (mJingleConnectionManager.hasStoragePermission()
                        && size < this.mJingleConnectionManager.getAutoAcceptFileSize()
                        && mXmppConnectionService.isDataSaverDisabled()) {
                    Log.d(Config.LOGTAG, "auto accepting file from " + packet.getFrom());
                    this.acceptedAutomatically = true;
                    this.sendAccept();
                } else {
                    message.markUnread();
                    Log.d(Config.LOGTAG,
                            "not auto accepting new file offer with size: "
                                    + size
                                    + " allowed size:"
                                    + this.mJingleConnectionManager
                                    .getAutoAcceptFileSize());
                    this.mXmppConnectionService.getNotificationService().push(message);
                }
                Log.d(Config.LOGTAG, "receiving file: expecting size of " + this.file.getExpectedSize());
                return;
            }
            respondToIq(packet, false);
        }
    }

    private static long parseLong(final Element element, final long l) {
        final String input = element == null ? null : element.getContent();
        if (input == null) {
            return l;
        }
        try {
            return Long.parseLong(input);
        } catch (Exception e) {
            return l;
        }
    }

    private void sendInitRequest() {
        JinglePacket packet = this.bootstrapPacket("session-initiate");
        Content content = new Content(this.contentCreator, this.contentName);
        if (message.isFileOrImage()) {
            content.setTransportId(this.transportId);
            this.file = this.mXmppConnectionService.getFileBackend().getFile(message, false);
            if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                this.file.setKey(mXmppAxolotlMessage.getInnerKey());
                this.file.setIv(mXmppAxolotlMessage.getIV());
                //legacy OMEMO encrypted file transfer reported file size of the encrypted file
                //JET uses the file size of the plain text file. The difference is only 16 bytes (auth tag)
                this.file.setExpectedSize(file.getSize() + (this.remoteSupportsOmemoJet ? 0 : 16));
                final Element file = content.setFileOffer(this.file, false, this.ftVersion);
                if (remoteSupportsOmemoJet) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": remote announced support for JET");
                    final Element security = new Element("security", Namespace.JINGLE_ENCRYPTED_TRANSPORT);
                    security.setAttribute("name", this.contentName);
                    security.setAttribute("cipher", JET_OMEMO_CIPHER);
                    security.setAttribute("type", AxolotlService.PEP_PREFIX);
                    security.addChild(mXmppAxolotlMessage.toElement());
                    content.addChild(security);
                } else {
                    file.addChild(mXmppAxolotlMessage.toElement());
                }
            } else {
                this.file.setExpectedSize(file.getSize());
                content.setFileOffer(this.file, false, this.ftVersion);
            }
            message.resetFileParams();
            try {
                this.mFileInputStream = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                fail(e.getMessage());
                return;
            }
            content.setTransportId(this.transportId);
            if (this.initialTransport == Transport.IBB) {
                content.ibbTransport().setAttribute("block-size", Integer.toString(this.ibbBlockSize));
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": sending IBB offer");
            } else {
                final List<Element> candidates = getCandidatesAsElements();
                Log.d(Config.LOGTAG, String.format("%s: sending S5B offer with %d candidates", account.getJid().asBareJid(), candidates.size()));
                content.socks5transport().setChildren(candidates);
            }
            packet.setContent(content);
            this.sendJinglePacket(packet, (account, response) -> {
                if (response.getType() == IqPacket.TYPE.RESULT) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": other party received offer");
                    if (mJingleStatus == JINGLE_STATUS_OFFERED) {
                        mJingleStatus = JINGLE_STATUS_INITIATED;
                        mXmppConnectionService.markMessage(message, Message.STATUS_OFFERED);
                    } else {
                        Log.d(Config.LOGTAG, "received ack for offer when status was " + mJingleStatus);
                    }
                } else {
                    fail(IqParser.extractErrorMessage(response));
                }
            });

        }
    }

    private void sendHash() {
        JinglePacket packet = this.bootstrapPacket("session-info");
        packet.addChecksum(file.getSha1Sum(), ftVersion.getNamespace());
        this.sendJinglePacket(packet);
    }

    private List<Element> getCandidatesAsElements() {
        List<Element> elements = new ArrayList<>();
        for (JingleCandidate c : this.candidates) {
            if (c.isOurs()) {
                elements.add(c.toElement());
            }
        }
        return elements;
    }

    private void sendAccept() {
        mJingleStatus = JINGLE_STATUS_ACCEPTED;
        this.mStatus = Transferable.STATUS_DOWNLOADING;
        this.mJingleConnectionManager.updateConversationUi(true);
        if (initialTransport == Transport.SOCKS) {
            sendAcceptSocks();
        } else {
            sendAcceptIbb();
        }
    }

    private void sendAcceptSocks() {
        gatherAndConnectDirectCandidates();
        this.mJingleConnectionManager.getPrimaryCandidate(this.account, initiating(), (success, candidate) -> {
            final JinglePacket packet = bootstrapPacket("session-accept");
            final Content content = new Content(contentCreator, contentName);
            content.setFileOffer(fileOffer, ftVersion);
            content.setTransportId(transportId);
            if (success && candidate != null && !equalCandidateExists(candidate)) {
                final JingleSocks5Transport socksConnection = new JingleSocks5Transport(this, candidate);
                connections.put(candidate.getCid(), socksConnection);
                socksConnection.connect(new OnTransportConnected() {

                    @Override
                    public void failed() {
                        Log.d(Config.LOGTAG, "connection to our own proxy65 candidate failed");
                        content.socks5transport().setChildren(getCandidatesAsElements());
                        packet.setContent(content);
                        sendJinglePacket(packet);
                        connectNextCandidate();
                    }

                    @Override
                    public void established() {
                        Log.d(Config.LOGTAG, "connected to proxy65 candidate");
                        mergeCandidate(candidate);
                        content.socks5transport().setChildren(getCandidatesAsElements());
                        packet.setContent(content);
                        sendJinglePacket(packet);
                        connectNextCandidate();
                    }
                });
            } else {
                Log.d(Config.LOGTAG, "did not find a proxy65 candidate for ourselves");
                content.socks5transport().setChildren(getCandidatesAsElements());
                packet.setContent(content);
                sendJinglePacket(packet);
                connectNextCandidate();
            }
        });
    }

    private void sendAcceptIbb() {
        this.transport = new JingleInbandTransport(this, this.transportId, this.ibbBlockSize);
        final JinglePacket packet = bootstrapPacket("session-accept");
        final Content content = new Content(contentCreator, contentName);
        content.setFileOffer(fileOffer, ftVersion);
        content.setTransportId(transportId);
        content.ibbTransport().setAttribute("block-size", this.ibbBlockSize);
        packet.setContent(content);
        this.transport.receive(file, onFileTransmissionStatusChanged);
        this.sendJinglePacket(packet);
    }

    private JinglePacket bootstrapPacket(String action) {
        JinglePacket packet = new JinglePacket();
        packet.setAction(action);
        packet.setFrom(account.getJid());
        packet.setTo(this.message.getCounterpart());
        packet.setSessionId(this.sessionId);
        packet.setInitiator(this.initiator);
        return packet;
    }

    private void sendJinglePacket(JinglePacket packet) {
        mXmppConnectionService.sendIqPacket(account, packet, responseListener);
    }

    private void sendJinglePacket(JinglePacket packet, OnIqPacketReceived callback) {
        mXmppConnectionService.sendIqPacket(account, packet, callback);
    }

    private void receiveAccept(JinglePacket packet) {
        if (responding()) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received out of order session-accept (we were responding)");
            respondToIqWithOutOfOrder(packet);
            return;
        }
        if (this.mJingleStatus != JINGLE_STATUS_INITIATED) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received out of order session-accept");
            respondToIqWithOutOfOrder(packet);
            return;
        }
        this.mJingleStatus = JINGLE_STATUS_ACCEPTED;
        mXmppConnectionService.markMessage(message, Message.STATUS_UNSEND);
        Content content = packet.getJingleContent();
        if (content.hasSocks5Transport()) {
            respondToIq(packet, true);
            mergeCandidates(JingleCandidate.parse(content.socks5transport().getChildren()));
            this.connectNextCandidate();
        } else if (content.hasIbbTransport()) {
            String receivedBlockSize = packet.getJingleContent().ibbTransport().getAttribute("block-size");
            if (receivedBlockSize != null) {
                try {
                    int bs = Integer.parseInt(receivedBlockSize);
                    if (bs > this.ibbBlockSize) {
                        this.ibbBlockSize = bs;
                    }
                } catch (Exception e) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to parse block size in session-accept");
                }
            }
            respondToIq(packet, true);
            this.transport = new JingleInbandTransport(this, this.transportId, this.ibbBlockSize);
            this.transport.connect(onIbbTransportConnected);
        } else {
            respondToIq(packet, false);
        }
    }

    private void receiveTransportInfo(JinglePacket packet) {
        final Content content = packet.getJingleContent();
        if (content.hasSocks5Transport()) {
            if (content.socks5transport().hasChild("activated")) {
                respondToIq(packet, true);
                if ((this.transport != null) && (this.transport instanceof JingleSocks5Transport)) {
                    onProxyActivated.success();
                } else {
                    String cid = content.socks5transport().findChild("activated").getAttribute("cid");
                    Log.d(Config.LOGTAG, "received proxy activated (" + cid
                            + ")prior to choosing our own transport");
                    JingleSocks5Transport connection = this.connections.get(cid);
                    if (connection != null) {
                        connection.setActivated(true);
                    } else {
                        Log.d(Config.LOGTAG, "activated connection not found");
                        sendSessionTerminate("failed-transport");
                        this.fail();
                    }
                }
            } else if (content.socks5transport().hasChild("proxy-error")) {
                respondToIq(packet, true);
                onProxyActivated.failed();
            } else if (content.socks5transport().hasChild("candidate-error")) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received candidate error");
                respondToIq(packet, true);
                this.receivedCandidate = true;
                if (mJingleStatus == JINGLE_STATUS_ACCEPTED && this.sentCandidate) {
                    this.connect();
                }
            } else if (content.socks5transport().hasChild("candidate-used")) {
                String cid = content.socks5transport().findChild("candidate-used").getAttribute("cid");
                if (cid != null) {
                    Log.d(Config.LOGTAG, "candidate used by counterpart:" + cid);
                    JingleCandidate candidate = getCandidate(cid);
                    if (candidate == null) {
                        Log.d(Config.LOGTAG, "could not find candidate with cid=" + cid);
                        respondToIq(packet, false);
                        return;
                    }
                    respondToIq(packet, true);
                    candidate.flagAsUsedByCounterpart();
                    this.receivedCandidate = true;
                    if (mJingleStatus == JINGLE_STATUS_ACCEPTED && this.sentCandidate) {
                        this.connect();
                    } else {
                        Log.d(Config.LOGTAG, "ignoring because file is already in transmission or we haven't sent our candidate yet status=" + mJingleStatus + " sentCandidate=" + sentCandidate);
                    }
                } else {
                    respondToIq(packet, false);
                }
            } else {
                respondToIq(packet, false);
            }
        } else {
            respondToIq(packet, true);
        }
    }

    private void connect() {
        final JingleSocks5Transport connection = chooseConnection();
        this.transport = connection;
        if (connection == null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": could not find suitable candidate");
            this.disconnectSocks5Connections();
            if (initiating()) {
                this.sendFallbackToIbb();
            }
        } else {
            final JingleCandidate candidate = connection.getCandidate();
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": elected candidate " + candidate.getHost() + ":" + candidate.getPort());
            this.mJingleStatus = JINGLE_STATUS_TRANSMITTING;
            if (connection.needsActivation()) {
                if (connection.getCandidate().isOurs()) {
                    final String sid;
                    if (ftVersion == Content.Version.FT_3) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": use session ID instead of transport ID to activate proxy");
                        sid = getSessionId();
                    } else {
                        sid = getTransportId();
                    }
                    Log.d(Config.LOGTAG, "candidate "
                            + connection.getCandidate().getCid()
                            + " was our proxy. going to activate");
                    IqPacket activation = new IqPacket(IqPacket.TYPE.SET);
                    activation.setTo(connection.getCandidate().getJid());
                    activation.query("http://jabber.org/protocol/bytestreams")
                            .setAttribute("sid", sid);
                    activation.query().addChild("activate")
                            .setContent(this.getCounterPart().toString());
                    mXmppConnectionService.sendIqPacket(account, activation, (account, response) -> {
                        if (response.getType() != IqPacket.TYPE.RESULT) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": " + response.toString());
                            sendProxyError();
                            onProxyActivated.failed();
                        } else {
                            sendProxyActivated(connection.getCandidate().getCid());
                            onProxyActivated.success();
                        }
                    });
                } else {
                    Log.d(Config.LOGTAG,
                            "candidate "
                                    + connection.getCandidate().getCid()
                                    + " was a proxy. waiting for other party to activate");
                }
            } else {
                if (initiating()) {
                    Log.d(Config.LOGTAG, "we were initiating. sending file");
                    connection.send(file, onFileTransmissionStatusChanged);
                } else {
                    Log.d(Config.LOGTAG, "we were responding. receiving file");
                    connection.receive(file, onFileTransmissionStatusChanged);
                }
            }
        }
    }

    private JingleSocks5Transport chooseConnection() {
        JingleSocks5Transport connection = null;
        for (Entry<String, JingleSocks5Transport> cursor : connections
                .entrySet()) {
            JingleSocks5Transport currentConnection = cursor.getValue();
            // Log.d(Config.LOGTAG,"comparing candidate: "+currentConnection.getCandidate().toString());
            if (currentConnection.isEstablished()
                    && (currentConnection.getCandidate().isUsedByCounterpart() || (!currentConnection
                    .getCandidate().isOurs()))) {
                // Log.d(Config.LOGTAG,"is usable");
                if (connection == null) {
                    connection = currentConnection;
                } else {
                    if (connection.getCandidate().getPriority() < currentConnection
                            .getCandidate().getPriority()) {
                        connection = currentConnection;
                    } else if (connection.getCandidate().getPriority() == currentConnection
                            .getCandidate().getPriority()) {
                        // Log.d(Config.LOGTAG,"found two candidates with same priority");
                        if (initiating()) {
                            if (currentConnection.getCandidate().isOurs()) {
                                connection = currentConnection;
                            }
                        } else {
                            if (!currentConnection.getCandidate().isOurs()) {
                                connection = currentConnection;
                            }
                        }
                    }
                }
            }
        }
        return connection;
    }

    private void sendSuccess() {
        sendSessionTerminate("success");
        this.disconnectSocks5Connections();
        this.mJingleStatus = JINGLE_STATUS_FINISHED;
        this.message.setStatus(Message.STATUS_RECEIVED);
        this.message.setTransferable(null);
        this.mXmppConnectionService.updateMessage(message, false);
        this.mJingleConnectionManager.finishConnection(this);
    }

    private void sendFallbackToIbb() {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": sending fallback to ibb");
        JinglePacket packet = this.bootstrapPacket("transport-replace");
        Content content = new Content(this.contentCreator, this.contentName);
        this.transportId = this.mJingleConnectionManager.nextRandomId();
        content.setTransportId(this.transportId);
        content.ibbTransport().setAttribute("block-size",
                Integer.toString(this.ibbBlockSize));
        packet.setContent(content);
        this.sendJinglePacket(packet);
    }


    private void receiveFallbackToIbb(JinglePacket packet) {
        if (initiating()) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received out of order transport-replace (we were initiating)");
            respondToIqWithOutOfOrder(packet);
            return;
        }
        if (mJingleStatus != JINGLE_STATUS_ACCEPTED && !proxyActivationFailed) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received out of order transport-replace");
            respondToIqWithOutOfOrder(packet);
            return;
        }
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": receiving fallback to ibb");
        final String receivedBlockSize = packet.getJingleContent().ibbTransport().getAttribute("block-size");
        if (receivedBlockSize != null) {
            try {
                final int bs = Integer.parseInt(receivedBlockSize);
                if (bs < this.ibbBlockSize) {
                    this.ibbBlockSize = bs;
                }
            } catch (NumberFormatException e) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to parse block size in transport-replace");
            }
        }
        this.transportId = packet.getJingleContent().getTransportId();
        this.transport = new JingleInbandTransport(this, this.transportId, this.ibbBlockSize);

        final JinglePacket answer = bootstrapPacket("transport-accept");

        final Content content = new Content(contentCreator, contentName);
        content.ibbTransport().setAttribute("block-size", this.ibbBlockSize);
        content.ibbTransport().setAttribute("sid", this.transportId);
        answer.setContent(content);

        respondToIq(packet, true);

        if (initiating()) {
            this.sendJinglePacket(answer, (account, response) -> {
                if (response.getType() == IqPacket.TYPE.RESULT) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + " recipient ACKed our transport-accept. creating ibb");
                    transport.connect(onIbbTransportConnected);
                }
            });
        } else {
            this.transport.receive(file, onFileTransmissionStatusChanged);
            this.sendJinglePacket(answer);
        }
    }

    private void receiveTransportAccept(JinglePacket packet) {
        if (responding()) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received out of order transport-accept (we were responding)");
            respondToIqWithOutOfOrder(packet);
            return;
        }
        if (this.mJingleStatus != JINGLE_STATUS_ACCEPTED) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received out of order transport-accept");
            respondToIqWithOutOfOrder(packet);
            return;
        }
        if (packet.getJingleContent().hasIbbTransport()) {
            final Element ibbTransport = packet.getJingleContent().ibbTransport();
            final String receivedBlockSize = ibbTransport.getAttribute("block-size");
            final String sid = ibbTransport.getAttribute("sid");
            if (receivedBlockSize != null) {
                try {
                    int bs = Integer.parseInt(receivedBlockSize);
                    if (bs < this.ibbBlockSize) {
                        this.ibbBlockSize = bs;
                    }
                } catch (NumberFormatException e) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to parse block size in transport-accept");
                }
            }
            this.transport = new JingleInbandTransport(this, this.transportId, this.ibbBlockSize);

            if (sid == null || !sid.equals(this.transportId)) {
                Log.w(Config.LOGTAG, String.format("%s: sid in transport-accept (%s) did not match our sid (%s) ", account.getJid().asBareJid(), sid, transportId));
            }
            respondToIq(packet, true);
            //might be receive instead if we are not initiating
            if (initiating()) {
                this.transport.connect(onIbbTransportConnected);
            }
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received invalid transport-accept");
            respondToIq(packet, false);
        }
    }

    private void receiveSuccess() {
        if (initiating()) {
            this.mJingleStatus = JINGLE_STATUS_FINISHED;
            this.mXmppConnectionService.markMessage(this.message, Message.STATUS_SEND_RECEIVED);
            this.disconnectSocks5Connections();
            if (this.transport instanceof JingleInbandTransport) {
                this.transport.disconnect();
            }
            this.message.setTransferable(null);
            this.mJingleConnectionManager.finishConnection(this);
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received session-terminate/success while responding");
        }
    }

    @Override
    public void cancel() {
        this.cancelled = true;
        abort("cancel");
    }

    void abort(final String reason) {
        this.disconnectSocks5Connections();
        if (this.transport instanceof JingleInbandTransport) {
            this.transport.disconnect();
        }
        sendSessionTerminate(reason);
        this.mJingleConnectionManager.finishConnection(this);
        if (responding()) {
            this.message.setTransferable(new TransferablePlaceholder(cancelled ? Transferable.STATUS_CANCELLED : Transferable.STATUS_FAILED));
            if (this.file != null) {
                file.delete();
            }
            this.mJingleConnectionManager.updateConversationUi(true);
        } else {
            this.mXmppConnectionService.markMessage(this.message, Message.STATUS_SEND_FAILED, cancelled ? Message.ERROR_MESSAGE_CANCELLED : null);
            this.message.setTransferable(null);
        }
    }

    private void fail() {
        fail(null);
    }

    private void fail(String errorMessage) {
        this.mJingleStatus = JINGLE_STATUS_FAILED;
        this.disconnectSocks5Connections();
        if (this.transport instanceof JingleInbandTransport) {
            this.transport.disconnect();
        }
        FileBackend.close(mFileInputStream);
        FileBackend.close(mFileOutputStream);
        if (this.message != null) {
            if (responding()) {
                this.message.setTransferable(new TransferablePlaceholder(cancelled ? Transferable.STATUS_CANCELLED : Transferable.STATUS_FAILED));
                if (this.file != null) {
                    file.delete();
                }
                this.mJingleConnectionManager.updateConversationUi(true);
            } else {
                this.mXmppConnectionService.markMessage(this.message,
                        Message.STATUS_SEND_FAILED,
                        cancelled ? Message.ERROR_MESSAGE_CANCELLED : errorMessage);
                this.message.setTransferable(null);
            }
        }
        this.mJingleConnectionManager.finishConnection(this);
    }

    private void sendSessionTerminate(String reason) {
        final JinglePacket packet = bootstrapPacket("session-terminate");
        final Reason r = new Reason();
        r.addChild(reason);
        packet.setReason(r);
        this.sendJinglePacket(packet);
    }

    private void connectNextCandidate() {
        for (JingleCandidate candidate : this.candidates) {
            if ((!connections.containsKey(candidate.getCid()) && (!candidate
                    .isOurs()))) {
                this.connectWithCandidate(candidate);
                return;
            }
        }
        this.sendCandidateError();
    }

    private void connectWithCandidate(final JingleCandidate candidate) {
        final JingleSocks5Transport socksConnection = new JingleSocks5Transport(
                this, candidate);
        connections.put(candidate.getCid(), socksConnection);
        socksConnection.connect(new OnTransportConnected() {

            @Override
            public void failed() {
                Log.d(Config.LOGTAG,
                        "connection failed with " + candidate.getHost() + ":"
                                + candidate.getPort());
                connectNextCandidate();
            }

            @Override
            public void established() {
                Log.d(Config.LOGTAG,
                        "established connection with " + candidate.getHost()
                                + ":" + candidate.getPort());
                sendCandidateUsed(candidate.getCid());
            }
        });
    }

    private void disconnectSocks5Connections() {
        Iterator<Entry<String, JingleSocks5Transport>> it = this.connections
                .entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, JingleSocks5Transport> pairs = it.next();
            pairs.getValue().disconnect();
            it.remove();
        }
    }

    private void sendProxyActivated(String cid) {
        final JinglePacket packet = bootstrapPacket("transport-info");
        final Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("activated").setAttribute("cid", cid);
        packet.setContent(content);
        this.sendJinglePacket(packet);
    }

    private void sendProxyError() {
        final JinglePacket packet = bootstrapPacket("transport-info");
        final Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("proxy-error");
        packet.setContent(content);
        this.sendJinglePacket(packet);
    }

    private void sendCandidateUsed(final String cid) {
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("candidate-used").setAttribute("cid", cid);
        packet.setContent(content);
        this.sentCandidate = true;
        if ((receivedCandidate) && (mJingleStatus == JINGLE_STATUS_ACCEPTED)) {
            connect();
        }
        this.sendJinglePacket(packet);
    }

    private void sendCandidateError() {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": sending candidate error");
        JinglePacket packet = bootstrapPacket("transport-info");
        Content content = new Content(this.contentCreator, this.contentName);
        content.setTransportId(this.transportId);
        content.socks5transport().addChild("candidate-error");
        packet.setContent(content);
        this.sentCandidate = true;
        this.sendJinglePacket(packet);
        if (receivedCandidate && mJingleStatus == JINGLE_STATUS_ACCEPTED) {
            connect();
        }
    }

    public int getJingleStatus() {
        return this.mJingleStatus;
    }

    private boolean equalCandidateExists(JingleCandidate candidate) {
        for (JingleCandidate c : this.candidates) {
            if (c.equalValues(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void mergeCandidate(JingleCandidate candidate) {
        for (JingleCandidate c : this.candidates) {
            if (c.equals(candidate)) {
                return;
            }
        }
        this.candidates.add(candidate);
    }

    private void mergeCandidates(List<JingleCandidate> candidates) {
        Collections.sort(candidates, (a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        for (JingleCandidate c : candidates) {
            mergeCandidate(c);
        }
    }

    private JingleCandidate getCandidate(String cid) {
        for (JingleCandidate c : this.candidates) {
            if (c.getCid().equals(cid)) {
                return c;
            }
        }
        return null;
    }

    void updateProgress(int i) {
        this.mProgress = i;
        mJingleConnectionManager.updateConversationUi(false);
    }

    public String getTransportId() {
        return this.transportId;
    }

    public Content.Version getFtVersion() {
        return this.ftVersion;
    }

    public boolean hasTransportId(String sid) {
        return sid.equals(this.transportId);
    }

    public JingleTransport getTransport() {
        return this.transport;
    }

    public boolean start() {
        if (account.getStatus() == Account.State.ONLINE) {
            if (mJingleStatus == JINGLE_STATUS_INITIATED) {
                new Thread(this::sendAccept).start();
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int getStatus() {
        return this.mStatus;
    }

    @Override
    public long getFileSize() {
        if (this.file != null) {
            return this.file.getExpectedSize();
        } else {
            return 0;
        }
    }

    @Override
    public int getProgress() {
        return this.mProgress;
    }

    public AbstractConnectionManager getConnectionManager() {
        return this.mJingleConnectionManager;
    }

    interface OnProxyActivated {
        void success();

        void failed();
    }
}
