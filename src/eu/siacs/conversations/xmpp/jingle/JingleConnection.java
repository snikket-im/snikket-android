package eu.siacs.conversations.xmpp.jingle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import android.util.Log;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleConnection {

	private JingleConnectionManager mJingleConnectionManager;
	private XmppConnectionService mXmppConnectionService;
	
	public static final int STATUS_INITIATED = 0;
	public static final int STATUS_ACCEPTED = 1;
	public static final int STATUS_TERMINATED = 2;
	public static final int STATUS_CANCELED = 3;
	public static final int STATUS_FINISHED = 4;
	public static final int STATUS_TRANSMITTING = 5;
	public static final int STATUS_FAILED = 99;
	
	private int ibbBlockSize = 4096;
	
	private int status = -1;
	private Message message;
	private String sessionId;
	private Account account;
	private String initiator;
	private String responder;
	private List<JingleCandidate> candidates = new ArrayList<JingleCandidate>();
	private HashMap<String, JingleSocks5Transport> connections = new HashMap<String, JingleSocks5Transport>();
	
	private String transportId;
	private Element fileOffer;
	private JingleFile file = null;
	
	private boolean receivedCandidate = false;
	private boolean sentCandidate = false;
	
	private boolean acceptedAutomatically = false;
	
	private JingleTransport transport = null;
	
	private OnIqPacketReceived responseListener = new OnIqPacketReceived() {
		
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.getType() == IqPacket.TYPE_ERROR) {
				mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
				status = STATUS_FAILED;
			}
		}
	};
	
	final OnFileTransmitted onFileTransmitted = new OnFileTransmitted() {
		
		@Override
		public void onFileTransmitted(JingleFile file) {
			if (responder.equals(account.getFullJid())) {
				sendSuccess();
				if (acceptedAutomatically) {
					message.markUnread();
				}
				mXmppConnectionService.markMessage(message, Message.STATUS_RECIEVED);
			}
			mXmppConnectionService.databaseBackend.createMessage(message);
			Log.d("xmppService","sucessfully transmitted file. sha1:"+file.getSha1Sum());
		}
	};
	
	private OnProxyActivated onProxyActivated = new OnProxyActivated() {
		
		@Override
		public void success() {
			if (initiator.equals(account.getFullJid())) {
				Log.d("xmppService","we were initiating. sending file");
				transport.send(file,onFileTransmitted);
			} else {
				transport.receive(file,onFileTransmitted);
				Log.d("xmppService","we were responding. receiving file");
			}
		}
		
		@Override
		public void failed() {
			Log.d("xmppService","proxy activation failed");
		}
	};
	
	public JingleConnection(JingleConnectionManager mJingleConnectionManager) {
		this.mJingleConnectionManager = mJingleConnectionManager;
		this.mXmppConnectionService = mJingleConnectionManager.getXmppConnectionService();
	}
	
	public String getSessionId() {
		return this.sessionId;
	}
	
	public String getAccountJid() {
		return this.account.getFullJid();
	}
	
	public String getCounterPart() {
		return this.message.getCounterpart();
	}
	
	public void deliverPacket(JinglePacket packet) {
		
		if (packet.isAction("session-terminate")) {
			Reason reason = packet.getReason();
			if (reason!=null) {
				if (reason.hasChild("cancel")) {
					this.cancel();
				} else if (reason.hasChild("success")) {
					this.finish();
				}
			} else {
				Log.d("xmppService","remote terminated for no reason");
				this.cancel();
			}
			} else if (packet.isAction("session-accept")) {
			receiveAccept(packet);
		} else if (packet.isAction("transport-info")) {
			receiveTransportInfo(packet);
		} else if (packet.isAction("transport-replace")) {
			if (packet.getJingleContent().hasIbbTransport()) {
				this.receiveFallbackToIbb(packet);
			} else {
				Log.d("xmppService","trying to fallback to something unknown"+packet.toString());
			}
		} else if (packet.isAction("transport-accept")) {
			this.receiveTransportAccept(packet);
		} else {
			Log.d("xmppService","packet arrived in connection. action was "+packet.getAction());
		}
	}
	
	public void init(Message message) {
		this.message = message;
		this.account = message.getConversation().getAccount();
		this.initiator = this.account.getFullJid();
		this.responder = this.message.getCounterpart();
		this.sessionId = this.mJingleConnectionManager.nextRandomId();
		if (this.candidates.size() > 0) {
			this.sendInitRequest();
		} else {
			this.mJingleConnectionManager.getPrimaryCandidate(account, new OnPrimaryCandidateFound() {
				
				@Override
				public void onPrimaryCandidateFound(boolean success, final JingleCandidate candidate) {
					if (success) {
						final JingleSocks5Transport socksConnection = new JingleSocks5Transport(JingleConnection.this, candidate);
						connections.put(candidate.getCid(), socksConnection);
						socksConnection.connect(new OnTransportConnected() {
							
							@Override
							public void failed() {
								Log.d("xmppService","connection to our own primary candidete failed");
								sendInitRequest();
							}
							
							@Override
							public void established() {
								Log.d("xmppService","succesfully connected to our own primary candidate");
								mergeCandidate(candidate);
								sendInitRequest();
							}
						});
						mergeCandidate(candidate);
					} else {
						Log.d("xmppService","no primary candidate of our own was found");
						sendInitRequest();
					}
				}
			});
		}
		
	}
	
	public void init(Account account, JinglePacket packet) {
		this.status = STATUS_INITIATED;
		Conversation conversation = this.mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().split("/")[0], false);
		this.message = new Message(conversation, "receiving image file", Message.ENCRYPTION_NONE);
		this.message.setType(Message.TYPE_IMAGE);
		this.message.setStatus(Message.STATUS_RECEIVED_OFFER);
		this.message.setJingleConnection(this);
		String[] fromParts = packet.getFrom().split("/");
		this.message.setPresence(fromParts[1]);
		this.account = account;
		this.initiator = packet.getFrom();
		this.responder = this.account.getFullJid();
		this.sessionId = packet.getSessionId();
		Content content = packet.getJingleContent();
		this.transportId = content.getTransportId();
		this.mergeCandidates(JingleCandidate.parse(content.socks5transport().getChildren()));
		this.fileOffer = packet.getJingleContent().getFileOffer();
		if (fileOffer!=null) {
			this.file = this.mXmppConnectionService.getFileBackend().getJingleFile(message);
			Element fileSize = fileOffer.findChild("size");
			Element fileName = fileOffer.findChild("name");
			this.file.setExpectedSize(Long.parseLong(fileSize.getContent()));
			conversation.getMessages().add(message);
			if (this.file.getExpectedSize()<=this.mJingleConnectionManager.getAutoAcceptFileSize()) {
				Log.d("xmppService","auto accepting file from "+packet.getFrom());
				this.acceptedAutomatically = true;
				this.sendAccept();
			} else {
				message.markUnread();
				Log.d("xmppService","not auto accepting new file offer with size: "+this.file.getExpectedSize()+" allowed size:"+this.mJingleConnectionManager.getAutoAcceptFileSize());
				if (this.mXmppConnectionService.convChangedListener!=null) {
					this.mXmppConnectionService.convChangedListener.onConversationListChanged();
				}
			}
		} else {
			Log.d("xmppService","no file offer was attached. aborting");
		}
	}
	
	private void sendInitRequest() {
		JinglePacket packet = this.bootstrapPacket("session-initiate");
		Content content = new Content();
		if (message.getType() == Message.TYPE_IMAGE) {
			content.setAttribute("creator", "initiator");
			content.setAttribute("name", "a-file-offer");
			content.setTransportId(this.transportId);
			this.file = this.mXmppConnectionService.getFileBackend().getJingleFile(message);
			content.setFileOffer(this.file);
			this.transportId = this.mJingleConnectionManager.nextRandomId();
			content.setTransportId(this.transportId);
			content.socks5transport().setChildren(getCandidatesAsElements());
			packet.setContent(content);
			this.sendJinglePacket(packet);
			this.status = STATUS_INITIATED;
		}
	}
	
	private List<Element> getCandidatesAsElements() {
		List<Element> elements = new ArrayList<Element>();
		for(JingleCandidate c : this.candidates) {
			elements.add(c.toElement());
		}
		return elements;
	}
	
	private void sendAccept() {
		status = STATUS_ACCEPTED;
		mXmppConnectionService.markMessage(message, Message.STATUS_RECIEVING);
		this.mJingleConnectionManager.getPrimaryCandidate(this.account, new OnPrimaryCandidateFound() {
			
			@Override
			public void onPrimaryCandidateFound(boolean success,final JingleCandidate candidate) {
				final JinglePacket packet = bootstrapPacket("session-accept");
				final Content content = new Content();
				content.setFileOffer(fileOffer);
				content.setTransportId(transportId);
				if ((success)&&(!equalCandidateExists(candidate))) {
					final JingleSocks5Transport socksConnection = new JingleSocks5Transport(JingleConnection.this, candidate);
					connections.put(candidate.getCid(), socksConnection);
					socksConnection.connect(new OnTransportConnected() {
						
						@Override
						public void failed() {
							Log.d("xmppService","connection to our own primary candidate failed");
							content.socks5transport().setChildren(getCandidatesAsElements());
							packet.setContent(content);
							sendJinglePacket(packet);
							connectNextCandidate();
						}
						
						@Override
						public void established() {
							Log.d("xmppService","connected to primary candidate");
							mergeCandidate(candidate);
							content.socks5transport().setChildren(getCandidatesAsElements());
							packet.setContent(content);
							sendJinglePacket(packet);
							connectNextCandidate();
						}
					});
				} else {
					Log.d("xmppService","did not find a primary candidate for ourself");
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
		packet.setFrom(account.getFullJid());
		packet.setTo(this.message.getCounterpart());
		packet.setSessionId(this.sessionId);
		packet.setInitiator(this.initiator);
		return packet;
	}
	
	private void sendJinglePacket(JinglePacket packet) {
		//Log.d("xmppService",packet.toString());
		account.getXmppConnection().sendIqPacket(packet,responseListener);
	}
	
	private void receiveAccept(JinglePacket packet) {
		Content content = packet.getJingleContent();
		mergeCandidates(JingleCandidate.parse(content.socks5transport().getChildren()));
		this.status = STATUS_ACCEPTED;
		mXmppConnectionService.markMessage(message, Message.STATUS_UNSEND);
		this.connectNextCandidate();
		IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
		account.getXmppConnection().sendIqPacket(response, null);
	}

	private void receiveTransportInfo(JinglePacket packet) {
		Content content = packet.getJingleContent();
		if (content.hasSocks5Transport()) {
			if (content.socks5transport().hasChild("activated")) {
				onProxyActivated.success();
			} else if (content.socks5transport().hasChild("activated")) {
				onProxyActivated.failed();
			} else if (content.socks5transport().hasChild("candidate-error")) {
				Log.d("xmppService","received candidate error");
				this.receivedCandidate = true;
				if (status == STATUS_ACCEPTED) {
					this.connect();
				}
			} else if (content.socks5transport().hasChild("candidate-used")){
				String cid = content.socks5transport().findChild("candidate-used").getAttribute("cid");
				if (cid!=null) {
					Log.d("xmppService","candidate used by counterpart:"+cid);
					JingleCandidate candidate = getCandidate(cid);
					candidate.flagAsUsedByCounterpart();
					this.receivedCandidate = true;
					if ((status == STATUS_ACCEPTED)&&(this.sentCandidate)) {
						this.connect();
					} else {
						Log.d("xmppService","ignoring because file is already in transmission or we havent sent our candidate yet");
					}
				} else {
					Log.d("xmppService","couldn't read used candidate");
				}
			} else {
				Log.d("xmppService","empty transport");
			}
		}
		
		IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
		account.getXmppConnection().sendIqPacket(response, null);
	}

	private void connect() {
		final JingleSocks5Transport connection = chooseConnection();
		this.transport = connection;
		if (connection==null) {
			Log.d("xmppService","could not find suitable candidate");
			this.disconnect();
			if (this.initiator.equals(account.getFullJid())) {
				this.sendFallbackToIbb();
			}
		} else {
			this.status = STATUS_TRANSMITTING;
			if (connection.isProxy()) {
				if (connection.getCandidate().isOurs()) {
					Log.d("xmppService","candidate "+connection.getCandidate().getCid()+" was our proxy and needs activation");
					IqPacket activation = new IqPacket(IqPacket.TYPE_SET);
					activation.setTo(connection.getCandidate().getJid());
					activation.query("http://jabber.org/protocol/bytestreams").setAttribute("sid", this.getSessionId());
					activation.query().addChild("activate").setContent(this.getCounterPart());
					this.account.getXmppConnection().sendIqPacket(activation, new OnIqPacketReceived() {
						
						@Override
						public void onIqPacketReceived(Account account, IqPacket packet) {
							if (packet.getType()==IqPacket.TYPE_ERROR) {
								onProxyActivated.failed();
							} else {
								onProxyActivated.success();
								sendProxyActivated(connection.getCandidate().getCid());
							}
						}
					});
				}
			} else {
				if (initiator.equals(account.getFullJid())) {
					Log.d("xmppService","we were initiating. sending file");
					connection.send(file,onFileTransmitted);
				} else {
					Log.d("xmppService","we were responding. receiving file");
					connection.receive(file,onFileTransmitted);
				}
			}
		}
	}
	
	private JingleSocks5Transport chooseConnection() {
		JingleSocks5Transport connection = null;
		Iterator<Entry<String, JingleSocks5Transport>> it = this.connections.entrySet().iterator();
	    while (it.hasNext()) {
	    	Entry<String, JingleSocks5Transport> pairs = it.next();
	    	JingleSocks5Transport currentConnection = pairs.getValue();
	    	//Log.d("xmppService","comparing candidate: "+currentConnection.getCandidate().toString());
	        if (currentConnection.isEstablished()&&(currentConnection.getCandidate().isUsedByCounterpart()||(!currentConnection.getCandidate().isOurs()))) {
	        	//Log.d("xmppService","is usable");
	        	if (connection==null) {
	        		connection = currentConnection;
	        	} else {
	        		if (connection.getCandidate().getPriority()<currentConnection.getCandidate().getPriority()) {
	        			connection = currentConnection;
	        		} else if (connection.getCandidate().getPriority()==currentConnection.getCandidate().getPriority()) {
	        			//Log.d("xmppService","found two candidates with same priority");
	        			if (initiator.equals(account.getFullJid())) {
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
	        it.remove();
	    }
		return connection;
	}

	private void sendSuccess() {
		JinglePacket packet = bootstrapPacket("session-terminate");
		Reason reason = new Reason();
		reason.addChild("success");
		packet.setReason(reason);
		this.sendJinglePacket(packet);
		this.disconnect();
		this.status = STATUS_FINISHED;
		this.mXmppConnectionService.markMessage(this.message, Message.STATUS_RECIEVED);
	}
	
	private void sendFallbackToIbb() {
		JinglePacket packet = this.bootstrapPacket("transport-replace");
		Content content = new Content("initiator","a-file-offer");
		this.transportId = this.mJingleConnectionManager.nextRandomId();
		content.setTransportId(this.transportId);
		content.ibbTransport().setAttribute("block-size",""+this.ibbBlockSize);
		packet.setContent(content);
		this.sendJinglePacket(packet);
	}
	
	private void receiveFallbackToIbb(JinglePacket packet) {
		String receivedBlockSize = packet.getJingleContent().ibbTransport().getAttribute("block-size");
		if (receivedBlockSize!=null) {
			int bs = Integer.parseInt(receivedBlockSize);
			if (bs>this.ibbBlockSize) {
				this.ibbBlockSize = bs;
			}
		}
		this.transportId = packet.getJingleContent().getTransportId();
		this.transport = new JingleInbandTransport(this.account,this.responder,this.transportId,this.ibbBlockSize);
		this.transport.receive(file, onFileTransmitted);
		JinglePacket answer = bootstrapPacket("transport-accept");
		Content content = new Content("initiator", "a-file-offer");
		content.setTransportId(this.transportId);
		content.ibbTransport().setAttribute("block-size", ""+this.ibbBlockSize);
		answer.setContent(content);
		this.sendJinglePacket(answer);
	}
	
	private void receiveTransportAccept(JinglePacket packet) {
		if (packet.getJingleContent().hasIbbTransport()) {
			String receivedBlockSize = packet.getJingleContent().ibbTransport().getAttribute("block-size");
			if (receivedBlockSize!=null) {
				int bs = Integer.parseInt(receivedBlockSize);
				if (bs>this.ibbBlockSize) {
					this.ibbBlockSize = bs;
				}
			}
			this.transport = new JingleInbandTransport(this.account,this.responder,this.transportId,this.ibbBlockSize);
			this.transport.connect(new OnTransportConnected() {
				
				@Override
				public void failed() {
					Log.d("xmppService","ibb open failed");
				}
				
				@Override
				public void established() {
					JingleConnection.this.transport.send(file, onFileTransmitted);
				}
			});
		} else {
			Log.d("xmppService","invalid transport accept");
		}
	}
	
	private void finish() {
		this.status = STATUS_FINISHED;
		this.mXmppConnectionService.markMessage(this.message, Message.STATUS_SEND);
		this.disconnect();
	}
	
	public void cancel() {
		this.disconnect();
		this.status = STATUS_CANCELED;
		this.mXmppConnectionService.markMessage(this.message, Message.STATUS_SEND_REJECTED);
	}

	private void connectNextCandidate() {
		for(JingleCandidate candidate : this.candidates) {
			if ((!connections.containsKey(candidate.getCid())&&(!candidate.isOurs()))) {
				this.connectWithCandidate(candidate);
				return;
			}
		}
		this.sendCandidateError();
	}
	
	private void connectWithCandidate(final JingleCandidate candidate) {
		final JingleSocks5Transport socksConnection = new JingleSocks5Transport(this,candidate);
		connections.put(candidate.getCid(), socksConnection);
		socksConnection.connect(new OnTransportConnected() {
			
			@Override
			public void failed() {
				Log.d("xmppService", "connection failed with "+candidate.getHost()+":"+candidate.getPort());
				connectNextCandidate();
			}
			
			@Override
			public void established() {
				Log.d("xmppService", "established connection with "+candidate.getHost()+":"+candidate.getPort());
				sendCandidateUsed(candidate.getCid());
			}
		});
	}

	private void disconnect() {
		Iterator<Entry<String, JingleSocks5Transport>> it = this.connections.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String, JingleSocks5Transport> pairs = it.next();
	        pairs.getValue().disconnect();
	        it.remove();
	    }
	}
	
	private void sendProxyActivated(String cid) {
		JinglePacket packet = bootstrapPacket("transport-info");
		Content content = new Content("inititaor","a-file-offer");
		content.setTransportId(this.transportId);
		content.socks5transport().addChild("activated").setAttribute("cid", cid);
		packet.setContent(content);
		this.sendJinglePacket(packet);
	}
	
	private void sendCandidateUsed(final String cid) {
		JinglePacket packet = bootstrapPacket("transport-info");
		Content content = new Content("initiator","a-file-offer");
		content.setTransportId(this.transportId);
		content.socks5transport().addChild("candidate-used").setAttribute("cid", cid);
		packet.setContent(content);
		this.sentCandidate = true;
		if ((receivedCandidate)&&(status == STATUS_ACCEPTED)) {
			connect();
		}
		this.sendJinglePacket(packet);
	}
	
	private void sendCandidateError() {
		JinglePacket packet = bootstrapPacket("transport-info");
		Content content = new Content("initiator","a-file-offer");
		content.setTransportId(this.transportId);
		content.socks5transport().addChild("candidate-error");
		packet.setContent(content);
		this.sentCandidate = true;
		if ((receivedCandidate)&&(status == STATUS_ACCEPTED)) {
			connect();
		}
		this.sendJinglePacket(packet);
	}

	public String getInitiator() {
		return this.initiator;
	}
	
	public String getResponder() {
		return this.responder;
	}
	
	public int getStatus() {
		return this.status;
	}
	
	private boolean equalCandidateExists(JingleCandidate candidate) {
		for(JingleCandidate c : this.candidates) {
			if (c.equalValues(candidate)) {
				return true;
			}
		}
		return false;
	}
	
	private void mergeCandidate(JingleCandidate candidate) {
		for(JingleCandidate c : this.candidates) {
			if (c.equals(candidate)) {
				return;
			}
		}
		this.candidates.add(candidate);
	}
	
	private void mergeCandidates(List<JingleCandidate> candidates) {
		for(JingleCandidate c : candidates) {
			mergeCandidate(c);
		}
	}
	
	private JingleCandidate getCandidate(String cid) {
		for(JingleCandidate c : this.candidates) {
			if (c.getCid().equals(cid)) {
				return c;
			}
		}
		return null;
	}
	
	interface OnProxyActivated {
		public void success();
		public void failed();
	}

	public boolean hasTransportId(String sid) {
		return sid.equals(this.transportId);
	}
	
	public JingleTransport getTransport() {
		return this.transport;
	}

	public void accept() {
		if (status==STATUS_INITIATED) {
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					sendAccept();
				}
			}).start();
		} else {
			Log.d("xmppService","status ("+status+") was not ok");
		}
	}
}
