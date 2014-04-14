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
	
	private int status = -1;
	private Message message;
	private String sessionId;
	private Account account;
	private String initiator;
	private String responder;
	private List<Element> candidates = new ArrayList<Element>();
	private List<String> candidatesUsedByCounterpart = new ArrayList<String>();
	private HashMap<String, SocksConnection> connections = new HashMap<String, SocksConnection>();
	private Content content = new Content();
	private JingleFile file = null;
	
	private OnIqPacketReceived responseListener = new OnIqPacketReceived() {
		
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.getType() == IqPacket.TYPE_ERROR) {
				mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
				status = STATUS_FAILED;
			}
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
			if (reason.hasChild("cancel")) {
				this.cancel();
			} else if (reason.hasChild("success")) {
				this.finish();
			}
		} else if (packet.isAction("session-accept")) {
			accept(packet);
		} else if (packet.isAction("transport-info")) {
			transportInfo(packet);
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
				public void onPrimaryCandidateFound(boolean success, Element candidate) {
					if (success) {
						mergeCandidate(candidate);
					}
					sendInitRequest();
				}
			});
		}
		
	}
	
	public void init(Account account, JinglePacket packet) {
		this.status = STATUS_INITIATED;
		Conversation conversation = this.mXmppConnectionService.findOrCreateConversation(account, packet.getFrom().split("/")[0], false);
		this.message = new Message(conversation, "receiving image file", Message.ENCRYPTION_NONE);
		this.message.setType(Message.TYPE_IMAGE);
		this.message.setStatus(Message.STATUS_RECIEVING);
		String[] fromParts = packet.getFrom().split("/");
		this.message.setPresence(fromParts[1]);
		this.account = account;
		this.initiator = packet.getFrom();
		this.responder = this.account.getFullJid();
		this.sessionId = packet.getSessionId();
		this.content = packet.getJingleContent();
		this.mergeCandidates(this.content.getCanditates());
		Element fileOffer = packet.getJingleContent().getFileOffer();
		if (fileOffer!=null) {
			this.file = this.mXmppConnectionService.getFileBackend().getJingleFile(message);
			Element fileSize = fileOffer.findChild("size");
			Element fileName = fileOffer.findChild("name");
			this.file.setExpectedSize(Long.parseLong(fileSize.getContent()));
			conversation.getMessages().add(message);
			this.mXmppConnectionService.databaseBackend.createMessage(message);
			if (this.mXmppConnectionService.convChangedListener!=null) {
				this.mXmppConnectionService.convChangedListener.onConversationListChanged();
			}
			if (this.file.getExpectedSize()>=this.mJingleConnectionManager.getAutoAcceptFileSize()) {
				Log.d("xmppService","auto accepting file from "+packet.getFrom());
				this.sendAccept();
			} else {
				Log.d("xmppService","not auto accepting new file offer with size: "+this.file.getExpectedSize()+" allowed size:"+this.mJingleConnectionManager.getAutoAcceptFileSize());
			}
		} else {
			Log.d("xmppService","no file offer was attached. aborting");
		}
	}
	
	private void sendInitRequest() {
		JinglePacket packet = this.bootstrapPacket();
		packet.setAction("session-initiate");
		this.content = new Content();
		if (message.getType() == Message.TYPE_IMAGE) {
			content.setAttribute("creator", "initiator");
			content.setAttribute("name", "a-file-offer");
			this.file = this.mXmppConnectionService.getFileBackend().getJingleFile(message);
			content.setFileOffer(this.file);
			content.setCandidates(this.mJingleConnectionManager.nextRandomId(),this.candidates);
			packet.setContent(content);
			Log.d("xmppService",packet.toString());
			account.getXmppConnection().sendIqPacket(packet, this.responseListener);
			this.status = STATUS_INITIATED;
		}
	}
	
	private void sendAccept() {
		this.mJingleConnectionManager.getPrimaryCandidate(this.account, new OnPrimaryCandidateFound() {
			
			@Override
			public void onPrimaryCandidateFound(boolean success, Element candidate) {
				if (success) {
					if (!equalCandidateExists(candidate)) {
						mergeCandidate(candidate);
						content.addCandidate(candidate);
					}
				}
				JinglePacket packet = bootstrapPacket();
				packet.setAction("session-accept");
				packet.setContent(content);
				account.getXmppConnection().sendIqPacket(packet, new OnIqPacketReceived() {
					
					@Override
					public void onIqPacketReceived(Account account, IqPacket packet) {
						if (packet.getType() != IqPacket.TYPE_ERROR) {
							status = STATUS_ACCEPTED;
							connectNextCandidate();
						}
					}
				});
			}
		});
		
	}
	
	private JinglePacket bootstrapPacket() {
		JinglePacket packet = new JinglePacket();
		packet.setFrom(account.getFullJid());
		packet.setTo(this.message.getCounterpart()); //fixme, not right in all cases;
		packet.setSessionId(this.sessionId);
		packet.setInitiator(this.initiator);
		return packet;
	}
	
	private void accept(JinglePacket packet) {
		Log.d("xmppService","session-accept: "+packet.toString());
		Content content = packet.getJingleContent();
		mergeCandidates(content.getCanditates());
		this.status = STATUS_ACCEPTED;
		this.connectNextCandidate();
		IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
		account.getXmppConnection().sendIqPacket(response, null);
	}

	private void transportInfo(JinglePacket packet) {
		Content content = packet.getJingleContent();
		String cid = content.getUsedCandidate();
		IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
		if (cid!=null) {
			Log.d("xmppService","candidate used by counterpart:"+cid);
			this.candidatesUsedByCounterpart.add(cid);
			if (this.connections.containsKey(cid)) {
				SocksConnection connection = this.connections.get(cid);
				if (connection.isEstablished()) {
					if (status!=STATUS_TRANSMITTING) {
						this.connect(connection);
					} else {
						Log.d("xmppService","ignoring canditate used because we are already transmitting");
					}
				} else {
					Log.d("xmppService","not yet connected. check when callback comes back");
				}
			} else {
				Log.d("xmppService","candidate not yet in list of connections");
			}
		}
		account.getXmppConnection().sendIqPacket(response, null);
	}

	private void connect(final SocksConnection connection) {
		this.status = STATUS_TRANSMITTING;
		final OnFileTransmitted callback = new OnFileTransmitted() {
			
			@Override
			public void onFileTransmitted(JingleFile file) {
				if (initiator.equals(account.getFullJid())) {
					mXmppConnectionService.markMessage(message, Message.STATUS_SEND);
				} else {
					mXmppConnectionService.markMessage(message, Message.STATUS_RECIEVED);
				}
				Log.d("xmppService","sucessfully transmitted file. sha1:"+file.getSha1Sum());
			}
		};
		if ((connection.isProxy()&&(connection.getCid().equals(mJingleConnectionManager.getPrimaryCandidateId(account))))) {
			Log.d("xmppService","candidate "+connection.getCid()+" was our proxy and needs activation");
			IqPacket activation = new IqPacket(IqPacket.TYPE_SET);
			activation.setTo(connection.getJid());
			activation.query("http://jabber.org/protocol/bytestreams").setAttribute("sid", this.getSessionId());
			activation.query().addChild("activate").setContent(this.getResponder());
			this.account.getXmppConnection().sendIqPacket(activation, new OnIqPacketReceived() {
				
				@Override
				public void onIqPacketReceived(Account account, IqPacket packet) {
					Log.d("xmppService","activation result: "+packet.toString());
					if (initiator.equals(account.getFullJid())) {
						Log.d("xmppService","we were initiating. sending file");
						connection.send(file,callback);
					} else {
						connection.receive(file,callback);
						Log.d("xmppService","we were responding. receiving file");
					}
				}
			});
		} else {
			if (initiator.equals(account.getFullJid())) {
				Log.d("xmppService","we were initiating. sending file");
				connection.send(file,callback);
			} else {
				Log.d("xmppService","we were responding. receiving file");
				connection.receive(file,callback);
			}
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
		for(Element candidate : this.candidates) {
			String cid = candidate.getAttribute("cid");
			if (!connections.containsKey(cid)) {
				this.connectWithCandidate(candidate);
				break;
			}
		}
	}
	
	private void connectWithCandidate(Element candidate) {
		final SocksConnection socksConnection = new SocksConnection(this,candidate);
		connections.put(socksConnection.getCid(), socksConnection);
		socksConnection.connect(new OnSocksConnection() {
			
			@Override
			public void failed() {
				connectNextCandidate();
			}
			
			@Override
			public void established() {
				if (candidatesUsedByCounterpart.contains(socksConnection.getCid())) {
					if (status!=STATUS_TRANSMITTING) {
						connect(socksConnection);
					} else {
						Log.d("xmppService","ignoring cuz already transmitting");
					}
				} else {
					sendCandidateUsed(socksConnection.getCid());
				}
			}
		});
	}

	private void disconnect() {
		Iterator<Entry<String, SocksConnection>> it = this.connections.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String, SocksConnection> pairs = it.next();
	        pairs.getValue().disconnect();
	        it.remove();
	    }
	}
	
	private void sendCandidateUsed(final String cid) {
		JinglePacket packet = bootstrapPacket();
		packet.setAction("transport-info");
		Content content = new Content();
		content.setUsedCandidate(this.content.getTransportId(), cid);
		packet.setContent(content);
		Log.d("xmppService","send using candidate: "+cid);
		this.account.getXmppConnection().sendIqPacket(packet, new OnIqPacketReceived() {
			
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				Log.d("xmppService","got ack for our candidate used");
				if (status!=STATUS_TRANSMITTING) {
					connect(connections.get(cid));
				} else {
					Log.d("xmppService","ignoring cuz already transmitting");
				}
			}
		});
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
	
	private boolean equalCandidateExists(Element candidate) {
		for(Element c : this.candidates) {
			if (c.getAttribute("host").equals(candidate.getAttribute("host"))&&(c.getAttribute("port").equals(candidate.getAttribute("port")))) {
				return true;
			}
		}
		return false;
	}
	
	private void mergeCandidate(Element candidate) {
		for(Element c : this.candidates) {
			if (c.getAttribute("cid").equals(candidate.getAttribute("cid"))) {
				return;
			}
		}
		this.candidates.add(candidate);
	}
	
	private void mergeCandidates(List<Element> candidates) {
		for(Element c : candidates) {
			mergeCandidate(c);
		}
	}
	
	private Element getCandidate(String cid) {
		for(Element c : this.candidates) {
			if (c.getAttribute("cid").equals(cid)) {
				return c;
			}
		}
		return null;
	}
}
