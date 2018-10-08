package eu.siacs.conversations.xmpp.jingle;

import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.OnMessageCreatedCallback;
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
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class JingleConnection implements Transferable {

	private JingleConnectionManager mJingleConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private static final int JINGLE_STATUS_OFFERED = -1;
	protected static final int JINGLE_STATUS_INITIATED = 0;
	protected static final int JINGLE_STATUS_ACCEPTED = 1;
	protected static final int JINGLE_STATUS_FINISHED = 4;
	protected static final int JINGLE_STATUS_TRANSMITTING = 5;
	protected static final int JINGLE_STATUS_FAILED = 99;

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

	private String contentName;
	private String contentCreator;

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

	private boolean responding() {
		return responder != null && responder.equals(account.getJid());
	}

	private boolean initiating() {
		return initiator.equals(account.getJid());
	}

	final OnFileTransmissionStatusChanged onFileTransmissionStatusChanged = new OnFileTransmissionStatusChanged() {

		@Override
		public void onFileTransmitted(DownloadableFile file) {
			if (responding()) {
				if (expectedHash.length > 0 && !Arrays.equals(expectedHash,file.getSha1Sum())) {
					Log.d(Config.LOGTAG,account.getJid().asBareJid()+": hashes did not match");
				}
				sendSuccess();
				mXmppConnectionService.getFileBackend().updateFileParams(message);
				mXmppConnectionService.databaseBackend.createMessage(message);
				mXmppConnectionService.markMessage(message,Message.STATUS_RECEIVED);
				if (acceptedAutomatically) {
					message.markUnread();
					if (message.getEncryption() == Message.ENCRYPTION_PGP) {
						account.getPgpDecryptionService().decrypt(message, true);
					} else {
						JingleConnection.this.mXmppConnectionService.getNotificationService().push(message);
					}
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
			Log.d(Config.LOGTAG,"successfully transmitted file:" + file.getAbsolutePath()+" ("+ CryptoHelper.bytesToHex(file.getSha1Sum())+")");
			if (message.getEncryption() != Message.ENCRYPTION_PGP) {
				mXmppConnectionService.getFileBackend().updateMediaScanner(file);
			}
		}

		@Override
		public void onFileTransferAborted() {
			JingleConnection.this.sendCancel();
			JingleConnection.this.fail();
		}
	};

	InputStream getFileInputStream() {
		return this.mFileInputStream;
	}

	OutputStream getFileOutputStream() throws IOException {
		if (this.file == null) {
			Log.d(Config.LOGTAG,"file object was not assigned");
			return null;
		}
		this.file.getParentFile().mkdirs();
		this.file.createNewFile();
		this.mFileOutputStream = AbstractConnectionManager.createOutputStream(this.file);
		return this.mFileOutputStream;
	}

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
			Log.d(Config.LOGTAG, "proxy activation failed");
		}
	};

	public JingleConnection(JingleConnectionManager mJingleConnectionManager) {
		this.mJingleConnectionManager = mJingleConnectionManager;
		this.mXmppConnectionService = mJingleConnectionManager
				.getXmppConnectionService();
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

	public void deliverPacket(JinglePacket packet) {
		boolean returnResult = true;
		if (packet.isAction("session-terminate")) {
			Reason reason = packet.getReason();
			if (reason != null) {
				if (reason.hasChild("cancel")) {
					this.fail();
				} else if (reason.hasChild("success")) {
					this.receiveSuccess();
				} else {
					this.fail();
				}
			} else {
				this.fail();
			}
		} else if (packet.isAction("session-accept")) {
			returnResult = receiveAccept(packet);
		} else if (packet.isAction("session-info")) {
			returnResult = true;
			Element checksum = packet.getChecksum();
			Element file = checksum == null ? null : checksum.findChild("file");
			Element hash = file == null ? null : file.findChild("hash","urn:xmpp:hashes:2");
			if (hash != null && "sha-1".equalsIgnoreCase(hash.getAttribute("algo"))) {
				try {
					this.expectedHash = Base64.decode(hash.getContent(), Base64.DEFAULT);
				} catch (Exception e) {
					this.expectedHash = new byte[0];
				}
			}
		} else if (packet.isAction("transport-info")) {
			returnResult = receiveTransportInfo(packet);
		} else if (packet.isAction("transport-replace")) {
			if (packet.getJingleContent().hasIbbTransport()) {
				returnResult = this.receiveFallbackToIbb(packet);
			} else {
				returnResult = false;
				Log.d(Config.LOGTAG, "trying to fallback to something unknown"
						+ packet.toString());
			}
		} else if (packet.isAction("transport-accept")) {
			returnResult = this.receiveTransportAccept(packet);
		} else {
			Log.d(Config.LOGTAG, "packet arrived in connection. action was "
					+ packet.getAction());
			returnResult = false;
		}
		IqPacket response;
		if (returnResult) {
			response = packet.generateResponse(IqPacket.TYPE.RESULT);

		} else {
			response = packet.generateResponse(IqPacket.TYPE.ERROR);
		}
		mXmppConnectionService.sendIqPacket(account,response,null);
	}

	public void init(final Message message) {
		if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
			Conversation conversation = (Conversation) message.getConversation();
			conversation.getAccount().getAxolotlService().prepareKeyTransportMessage(conversation, new OnMessageCreatedCallback() {
				@Override
				public void run(XmppAxolotlMessage xmppAxolotlMessage) {
					if (xmppAxolotlMessage != null) {
						init(message, xmppAxolotlMessage);
					} else {
						fail();
					}
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
		upgradeNamespace();
		this.message.setTransferable(this);
		this.mStatus = Transferable.STATUS_UPLOADING;
		this.initiator = this.account.getJid();
		this.responder = this.message.getCounterpart();
		this.sessionId = this.mJingleConnectionManager.nextRandomId();
		this.transportId = this.mJingleConnectionManager.nextRandomId();
		if (this.candidates.size() > 0) {
			this.sendInitRequest();
		} else {
			this.mJingleConnectionManager.getPrimaryCandidate(account,
					new OnPrimaryCandidateFound() {

						@Override
						public void onPrimaryCandidateFound(boolean success,
															final JingleCandidate candidate) {
							if (success) {
								final JingleSocks5Transport socksConnection = new JingleSocks5Transport(
										JingleConnection.this, candidate);
								connections.put(candidate.getCid(),
										socksConnection);
								socksConnection
										.connect(new OnTransportConnected() {

											@Override
											public void failed() {
												Log.d(Config.LOGTAG,
														"connection to our own primary candidete failed");
												sendInitRequest();
											}

											@Override
											public void established() {
												Log.d(Config.LOGTAG,
														"successfully connected to our own primary candidate");
												mergeCandidate(candidate);
												sendInitRequest();
											}
										});
								mergeCandidate(candidate);
							} else {
								Log.d(Config.LOGTAG, "no primary candidate of our own was found");
								sendInitRequest();
							}
						}
					});
		}

	}

	private void upgradeNamespace() {
		Jid jid = this.message.getCounterpart();
		String resource = jid != null ?jid.getResource() : null;
		if (resource != null) {
			Presence presence = this.account.getRoster().getContact(jid).getPresences().getPresences().get(resource);
			ServiceDiscoveryResult result = presence != null ? presence.getServiceDiscoveryResult() : null;
			if (result != null) {
				List<String> features = result.getFeatures();
				if (features.contains(Content.Version.FT_5.getNamespace())) {
					this.ftVersion = Content.Version.FT_5;
				} else if (features.contains(Content.Version.FT_4.getNamespace())) {
					this.ftVersion = Content.Version.FT_4;
				}
			}
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
		this.contentName = content.getAttribute("name");
		this.transportId = content.getTransportId();
		this.mergeCandidates(JingleCandidate.parse(content.socks5transport().getChildren()));
		this.ftVersion = content.getVersion();
		if (ftVersion == null) {
			this.sendCancel();
			this.fail();
			return;
		}
		this.fileOffer = content.getFileOffer(this.ftVersion);

		mXmppConnectionService.sendIqPacket(account,packet.generateResponse(IqPacket.TYPE.RESULT),null);

		if (fileOffer != null) {
			Element encrypted = fileOffer.findChild("encrypted", AxolotlService.PEP_PREFIX);
			if (encrypted != null) {
				this.mXmppAxolotlMessage = XmppAxolotlMessage.fromElement(encrypted, packet.getFrom().asBareJid());
			}
			Element fileSize = fileOffer.findChild("size");
			Element fileNameElement = fileOffer.findChild("name");
			if (fileNameElement != null) {
				String[] filename = fileNameElement.getContent()
						.toLowerCase(Locale.US).toLowerCase().split("\\.");
				String extension = filename[filename.length - 1];
				if (VALID_IMAGE_EXTENSIONS.contains(extension)) {
					message.setType(Message.TYPE_IMAGE);
					message.setRelativeFilePath(message.getUuid()+"."+extension);
				} else if (VALID_CRYPTO_EXTENSIONS.contains(
						filename[filename.length - 1])) {
					if (filename.length == 3) {
						extension = filename[filename.length - 2];
						if (VALID_IMAGE_EXTENSIONS.contains(extension)) {
							message.setType(Message.TYPE_IMAGE);
							message.setRelativeFilePath(message.getUuid()+"."+extension);
						} else {
							message.setType(Message.TYPE_FILE);
						}
						message.setEncryption(Message.ENCRYPTION_PGP);
					}
				} else {
					message.setType(Message.TYPE_FILE);
				}
				if (message.getType() == Message.TYPE_FILE) {
					String suffix = "";
					if (!fileNameElement.getContent().isEmpty()) {
						String parts[] = fileNameElement.getContent().split("/");
						suffix = parts[parts.length - 1];
						if (message.getEncryption() == Message.ENCRYPTION_PGP && (suffix.endsWith(".pgp") || suffix.endsWith(".gpg"))) {
							suffix = suffix.substring(0,suffix.length() - 4);
						}
					}
					message.setRelativeFilePath(message.getUuid()+"_"+suffix);
				}
				long size = Long.parseLong(fileSize.getContent());
				message.setBody(Long.toString(size));
				conversation.add(message);
				mJingleConnectionManager.updateConversationUi(true);
				if (mJingleConnectionManager.hasStoragePermission()
						&& size < this.mJingleConnectionManager.getAutoAcceptFileSize()
						&& mXmppConnectionService.isDataSaverDisabled()) {
					Log.d(Config.LOGTAG, "auto accepting file from "+ packet.getFrom());
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
				this.file = this.mXmppConnectionService.getFileBackend().getFile(message, false);
				if (mXmppAxolotlMessage != null) {
					XmppAxolotlMessage.XmppAxolotlKeyTransportMessage transportMessage = account.getAxolotlService().processReceivingKeyTransportMessage(mXmppAxolotlMessage, false);
					if (transportMessage != null) {
						message.setEncryption(Message.ENCRYPTION_AXOLOTL);
						this.file.setKey(transportMessage.getKey());
						this.file.setIv(transportMessage.getIv());
						message.setFingerprint(transportMessage.getFingerprint());
					} else {
						Log.d(Config.LOGTAG,"could not process KeyTransportMessage");
					}
				}
				this.file.setExpectedSize(size);
				message.resetFileParams();
				Log.d(Config.LOGTAG, "receiving file: expecting size of " + this.file.getExpectedSize());
			} else {
				this.sendCancel();
				this.fail();
			}
		} else {
			this.sendCancel();
			this.fail();
		}
	}

	private void sendInitRequest() {
		JinglePacket packet = this.bootstrapPacket("session-initiate");
		Content content = new Content(this.contentCreator, this.contentName);
		if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE) {
			content.setTransportId(this.transportId);
			this.file = this.mXmppConnectionService.getFileBackend().getFile(message, false);
			if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
				this.file.setKey(mXmppAxolotlMessage.getInnerKey());
				this.file.setIv(mXmppAxolotlMessage.getIV());
				this.file.setExpectedSize(file.getSize() + 16);
				content.setFileOffer(this.file, false, this.ftVersion).addChild(mXmppAxolotlMessage.toElement());
			} else {
				this.file.setExpectedSize(file.getSize());
				content.setFileOffer(this.file, false, this.ftVersion);
			}
			message.resetFileParams();
			try {
				this.mFileInputStream = new FileInputStream(file);
			} catch (FileNotFoundException e) {
				abort();
				return;
			}
			content.setTransportId(this.transportId);
			content.socks5transport().setChildren(getCandidatesAsElements());
			packet.setContent(content);
			this.sendJinglePacket(packet,new OnIqPacketReceived() {

				@Override
				public void onIqPacketReceived(Account account, IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE.RESULT) {
						Log.d(Config.LOGTAG,account.getJid().asBareJid()+": other party received offer");
						if (mJingleStatus == JINGLE_STATUS_OFFERED) {
							mJingleStatus = JINGLE_STATUS_INITIATED;
							mXmppConnectionService.markMessage(message, Message.STATUS_OFFERED);
						} else {
							Log.d(Config.LOGTAG,"received ack for offer when status was "+mJingleStatus);
						}
					} else {
						fail(IqParser.extractErrorMessage(packet));
					}
				}
			});

		}
	}

	private void sendHash() {
		JinglePacket packet = this.bootstrapPacket("session-info");
		packet.addChecksum(file.getSha1Sum(),ftVersion.getNamespace());
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
		this.mJingleConnectionManager.getPrimaryCandidate(this.account, new OnPrimaryCandidateFound() {
			@Override
			public void onPrimaryCandidateFound(boolean success, final JingleCandidate candidate) {
				final JinglePacket packet = bootstrapPacket("session-accept");
				final Content content = new Content(contentCreator,contentName);
				content.setFileOffer(fileOffer, ftVersion);
				content.setTransportId(transportId);
				if (success && candidate != null && !equalCandidateExists(candidate)) {
					final JingleSocks5Transport socksConnection = new JingleSocks5Transport(
							JingleConnection.this,
							candidate);
					connections.put(candidate.getCid(), socksConnection);
					socksConnection.connect(new OnTransportConnected() {

						@Override
						public void failed() {
							Log.d(Config.LOGTAG,"connection to our own primary candidate failed");
							content.socks5transport().setChildren(getCandidatesAsElements());
							packet.setContent(content);
							sendJinglePacket(packet);
							connectNextCandidate();
						}

						@Override
						public void established() {
							Log.d(Config.LOGTAG, "connected to primary candidate");
							mergeCandidate(candidate);
							content.socks5transport().setChildren(getCandidatesAsElements());
							packet.setContent(content);
							sendJinglePacket(packet);
							connectNextCandidate();
						}
					});
				} else {
					Log.d(Config.LOGTAG,"did not find a primary candidate for ourself");
					content.socks5transport().setChildren(getCandidatesAsElements());
					packet.setContent(content);
					sendJinglePacket(packet);
					connectNextCandidate();
				}
			}
		});
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
		mXmppConnectionService.sendIqPacket(account,packet,responseListener);
	}

	private void sendJinglePacket(JinglePacket packet, OnIqPacketReceived callback) {
		mXmppConnectionService.sendIqPacket(account,packet,callback);
	}

	private boolean receiveAccept(JinglePacket packet) {
		Content content = packet.getJingleContent();
		mergeCandidates(JingleCandidate.parse(content.socks5transport()
				.getChildren()));
		this.mJingleStatus = JINGLE_STATUS_ACCEPTED;
		mXmppConnectionService.markMessage(message, Message.STATUS_UNSEND);
		this.connectNextCandidate();
		return true;
	}

	private boolean receiveTransportInfo(JinglePacket packet) {
		Content content = packet.getJingleContent();
		if (content.hasSocks5Transport()) {
			if (content.socks5transport().hasChild("activated")) {
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
						this.sendCancel();
						this.fail();
					}
				}
				return true;
			} else if (content.socks5transport().hasChild("proxy-error")) {
				onProxyActivated.failed();
				return true;
			} else if (content.socks5transport().hasChild("candidate-error")) {
				Log.d(Config.LOGTAG, "received candidate error");
				this.receivedCandidate = true;
				if (mJingleStatus == JINGLE_STATUS_ACCEPTED && this.sentCandidate) {
					this.connect();
				}
				return true;
			} else if (content.socks5transport().hasChild("candidate-used")) {
				String cid = content.socks5transport()
						.findChild("candidate-used").getAttribute("cid");
				if (cid != null) {
					Log.d(Config.LOGTAG, "candidate used by counterpart:" + cid);
					JingleCandidate candidate = getCandidate(cid);
					if (candidate == null) {
						Log.d(Config.LOGTAG,"could not find candidate with cid="+cid);
						return false;
					}
					candidate.flagAsUsedByCounterpart();
					this.receivedCandidate = true;
					if (mJingleStatus == JINGLE_STATUS_ACCEPTED && this.sentCandidate) {
						this.connect();
					} else {
						Log.d(Config.LOGTAG, "ignoring because file is already in transmission or we haven't sent our candidate yet status="+mJingleStatus+" sentCandidate="+Boolean.toString(sentCandidate));
					}
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	private void connect() {
		final JingleSocks5Transport connection = chooseConnection();
		this.transport = connection;
		if (connection == null) {
			Log.d(Config.LOGTAG, "could not find suitable candidate");
			this.disconnectSocks5Connections();
			if (initiating()) {
				this.sendFallbackToIbb();
			}
		} else {
			this.mJingleStatus = JINGLE_STATUS_TRANSMITTING;
			if (connection.needsActivation()) {
				if (connection.getCandidate().isOurs()) {
					final String sid;
					if (ftVersion == Content.Version.FT_3) {
						Log.d(Config.LOGTAG,account.getJid().asBareJid()+": use session ID instead of transport ID to activate proxy");
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
					mXmppConnectionService.sendIqPacket(account,activation,
							new OnIqPacketReceived() {

								@Override
								public void onIqPacketReceived(Account account,
										IqPacket packet) {
									if (packet.getType() != IqPacket.TYPE.RESULT) {
										onProxyActivated.failed();
									} else {
										onProxyActivated.success();
										sendProxyActivated(connection.getCandidate().getCid());
									}
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
		JinglePacket packet = bootstrapPacket("session-terminate");
		Reason reason = new Reason();
		reason.addChild("success");
		packet.setReason(reason);
		this.sendJinglePacket(packet);
		this.disconnectSocks5Connections();
		this.mJingleStatus = JINGLE_STATUS_FINISHED;
		this.message.setStatus(Message.STATUS_RECEIVED);
		this.message.setTransferable(null);
		this.mXmppConnectionService.updateMessage(message, false);
		this.mJingleConnectionManager.finishConnection(this);
	}

	private void sendFallbackToIbb() {
		Log.d(Config.LOGTAG, account.getJid().asBareJid()+": sending fallback to ibb");
		JinglePacket packet = this.bootstrapPacket("transport-replace");
		Content content = new Content(this.contentCreator, this.contentName);
		this.transportId = this.mJingleConnectionManager.nextRandomId();
		content.setTransportId(this.transportId);
		content.ibbTransport().setAttribute("block-size",
				Integer.toString(this.ibbBlockSize));
		packet.setContent(content);
		this.sendJinglePacket(packet);
	}

	OnTransportConnected onIbbTransportConnected = new OnTransportConnected() {
		@Override
		public void failed() {
			Log.d(Config.LOGTAG, "ibb open failed");
		}

		@Override
		public void established() {
			JingleConnection.this.transport.send(file, onFileTransmissionStatusChanged);
		}
	};

	private boolean receiveFallbackToIbb(JinglePacket packet) {
		Log.d(Config.LOGTAG, "receiving fallack to ibb");
		String receivedBlockSize = packet.getJingleContent().ibbTransport()
				.getAttribute("block-size");
		if (receivedBlockSize != null) {
			int bs = Integer.parseInt(receivedBlockSize);
			if (bs > this.ibbBlockSize) {
				this.ibbBlockSize = bs;
			}
		}
		this.transportId = packet.getJingleContent().getTransportId();
		this.transport = new JingleInbandTransport(this, this.transportId, this.ibbBlockSize);

		JinglePacket answer = bootstrapPacket("transport-accept");
		Content content = new Content("initiator", "a-file-offer");
		content.setTransportId(this.transportId);
		content.ibbTransport().setAttribute("block-size",this.ibbBlockSize);
		answer.setContent(content);


		if (initiating()) {
			this.sendJinglePacket(answer, new OnIqPacketReceived() {
				@Override
				public void onIqPacketReceived(Account account, IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE.RESULT) {
						Log.d(Config.LOGTAG, account.getJid().asBareJid() + " recipient ACKed our transport-accept. creating ibb");
						transport.connect(onIbbTransportConnected);
					}
				}
			});
		} else {
			this.transport.receive(file, onFileTransmissionStatusChanged);
			this.sendJinglePacket(answer);
		}
		return true;
	}

	private boolean receiveTransportAccept(JinglePacket packet) {
		if (packet.getJingleContent().hasIbbTransport()) {
			String receivedBlockSize = packet.getJingleContent().ibbTransport()
					.getAttribute("block-size");
			if (receivedBlockSize != null) {
				int bs = Integer.parseInt(receivedBlockSize);
				if (bs > this.ibbBlockSize) {
					this.ibbBlockSize = bs;
				}
			}
			this.transport = new JingleInbandTransport(this, this.transportId, this.ibbBlockSize);

			//might be receive instead if we are not initiating
			if (initiating()) {
				this.transport.connect(onIbbTransportConnected);
			} else {
				this.transport.receive(file, onFileTransmissionStatusChanged);
			}
			return true;
		} else {
			return false;
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
			Log.d(Config.LOGTAG,account.getJid().asBareJid()+": received session-terminate/success while responding");
		}
	}
	@Override
	public void cancel() {
		this.cancelled = true;
		abort();
	}

	public void abort() {
		this.disconnectSocks5Connections();
		if (this.transport instanceof JingleInbandTransport) {
			this.transport.disconnect();
		}
		this.sendCancel();
		this.mJingleConnectionManager.finishConnection(this);
		if (responding()) {
			this.message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_FAILED));
			if (this.file!=null) {
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
				this.message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_FAILED));
				if (this.file!=null) {
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

	private void sendCancel() {
		JinglePacket packet = bootstrapPacket("session-terminate");
		Reason reason = new Reason();
		reason.addChild("cancel");
		packet.setReason(reason);
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
		JinglePacket packet = bootstrapPacket("transport-info");
		Content content = new Content(this.contentCreator, this.contentName);
		content.setTransportId(this.transportId);
		content.socks5transport().addChild("activated")
				.setAttribute("cid", cid);
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
		JinglePacket packet = bootstrapPacket("transport-info");
		Content content = new Content(this.contentCreator, this.contentName);
		content.setTransportId(this.transportId);
		content.socks5transport().addChild("candidate-error");
		packet.setContent(content);
		this.sentCandidate = true;
		if (receivedCandidate && mJingleStatus == JINGLE_STATUS_ACCEPTED) {
			connect();
		}
		this.sendJinglePacket(packet);
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

	public void updateProgress(int i) {
		this.mProgress = i;
		mJingleConnectionManager.updateConversationUi(false);
	}

	public String getTransportId() {
		return this.transportId;
	}

	public Content.Version getFtVersion() {
		return this.ftVersion;
	}

	interface OnProxyActivated {
		void success();

		void failed();
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
				new Thread(new Runnable() {

					@Override
					public void run() {
						sendAccept();
					}
				}).start();
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
}
