package de.gultsch.chat.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;


import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.persistance.DatabaseBackend;
import de.gultsch.chat.ui.OnConversationListChangedListener;
import de.gultsch.chat.ui.OnRosterFetchedListener;
import de.gultsch.chat.xml.Element;
import de.gultsch.chat.xmpp.IqPacket;
import de.gultsch.chat.xmpp.MessagePacket;
import de.gultsch.chat.xmpp.OnIqPacketReceived;
import de.gultsch.chat.xmpp.OnMessagePacketReceived;
import de.gultsch.chat.xmpp.XmppConnection;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

public class XmppConnectionService extends Service {
	
	protected static final String LOGTAG = "xmppService";
	protected DatabaseBackend databaseBackend;
	
	public long startDate;
	
	private List<Account> accounts;
	private List<Conversation> conversations = null;
	
	private Hashtable<Account,XmppConnection> connections = new Hashtable<Account, XmppConnection>();

	private OnConversationListChangedListener convChangedListener = null;
	
    private final IBinder mBinder = new XmppConnectionBinder();
	private OnMessagePacketReceived messageListener = new OnMessagePacketReceived() {
		
		@Override
		public void onMessagePacketReceived(Account account, MessagePacket packet) {
			String fullJid = packet.getFrom();
			String jid = fullJid.split("/")[0];
			String name = jid.split("@")[0];
			Log.d(LOGTAG,"message received for "+account.getJid()+" from "+jid);
			Log.d(LOGTAG,packet.toString());
			Contact contact = new Contact(account,name,jid,null); //dummy contact
			Conversation conversation = findOrCreateConversation(account, contact);
			Message message = new Message(conversation, fullJid, packet.getBody(), Message.ENCRYPTION_NONE, Message.STATUS_RECIEVED);
			conversation.getMessages().add(message);
			databaseBackend.createMessage(message);
			if (convChangedListener != null) {
				convChangedListener.onConversationListChanged();
			}
		}
	};

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
        	return XmppConnectionService.this;
        }
    }
    
    @Override 
    public int onStartCommand(Intent intent, int flags, int startId) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        for(Account account : accounts) {
        	if (!connections.containsKey(account)) {
        		XmppConnection connection = new XmppConnection(account, pm);
        		connection.setOnMessagePacketReceivedListener(this.messageListener );
        		Thread thread = new Thread(connection);
        		thread.start();
        		this.connections.put(account, connection);
        	}
        }
        return START_STICKY;
    }
    
    @Override
    public void onCreate() {
    	databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
    	this.accounts = databaseBackend.getAccounts();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    public void sendMessage(final Account account, final Message message) {
    	new Thread() {
    		@Override
    		public void run() {
    			Log.d(LOGTAG,"sending message for "+account.getJid()+" to: "+message.getCounterpart());
    			databaseBackend.createMessage(message);
    			MessagePacket packet = new MessagePacket();
    			packet.setType(MessagePacket.TYPE_CHAT);
    			packet.setTo(message.getCounterpart());
    			packet.setFrom(account.getJid());
    			packet.setBody(message.getBody());
    			try {
					connections.get(account).sendMessagePacket(packet);
					message.setStatus(Message.STATUS_SEND);
					databaseBackend.updateMessage(message);
				} catch (IOException e) {
					Log.d(LOGTAG,"io exception during send. message is in database. will try again later");
				}
    		}
    	}.start();
    }
    
    public void getRoster(final Account account, final OnRosterFetchedListener listener) {
    	IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
    	Element query = new Element("query");
    	query.setAttribute("xmlns", "jabber:iq:roster");
    	query.setAttribute("ver", "");
    	iqPacket.addChild(query);
    	try {
    		connections.get(account).sendIqPacket(iqPacket, new OnIqPacketReceived() {
				
				@Override
				public void onIqPacketReceived(Account account, IqPacket packet) {
					Element roster = packet.findChild("query");
					Log.d(LOGTAG,roster.toString());
					List<Contact> contacts = new ArrayList<Contact>();
					for(Element item : roster.getChildren()) {
						String name = item.getAttribute("name");
						String jid = item.getAttribute("jid");
						if (name==null) {
							name = jid.split("@")[0];
						}
						Contact contact = new Contact(account, name, jid, null);
						contacts.add(contact);
					}
					if (listener != null) {
						listener.onRosterFetched(contacts);
					}
				}
			});
		} catch (IOException e) {
			Log.d(LOGTAG,"io error during roster fetch");
		}
    }
    
    public void addConversation(Conversation conversation) {
    	databaseBackend.createConversation(conversation);
    }
    
    public List<Conversation> getConversations() {
    	if (this.conversations == null) {
	    	Hashtable<String, Account> accountLookupTable = new Hashtable<String, Account>();
	    	for(Account account : this.accounts) {
	    		accountLookupTable.put(account.getUuid(), account);
	    	}
	    	this.conversations = databaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
	    	for(Conversation conv : this.conversations) {
	    		conv.setAccount(accountLookupTable.get(conv.getAccountUuid()));
	    	}
    	}
    	return this.conversations;
    }
    
    public List<Account> getAccounts() {
    	return this.accounts;
    }
    
    public List<Message> getMessages(Conversation conversation) {
    	return databaseBackend.getMessages(conversation, 100);
    }

    public Conversation findOrCreateConversation(Account account, Contact contact) {
    	Log.d(LOGTAG,"was asked to find conversation for "+contact.getJid());
    	for(Conversation conv : this.getConversations()) {
    		if ((conv.getAccount().equals(account))&&(conv.getContactJid().equals(contact.getJid()))) {
    			Log.d(LOGTAG,"found one in memory");
    			return conv;
    		}
    	}
    	Conversation conversation = databaseBackend.findConversation(account, contact.getJid());
    	if (conversation!=null) {
    		Log.d("gultsch","found one. unarchive it");
    		conversation.setStatus(Conversation.STATUS_AVAILABLE);
    		conversation.setAccount(account);
    		this.databaseBackend.updateConversation(conversation);
    	} else {
    		Log.d(LOGTAG,"didnt find one in archive. create new one");
    		conversation = new Conversation(contact.getDisplayName(), contact.getProfilePhoto(), account, contact.getJid());
    		this.databaseBackend.createConversation(conversation);
    	}
    	this.conversations.add(conversation);
    	if (this.convChangedListener != null) {
    		this.convChangedListener.onConversationListChanged();
    	}
    	return conversation;
    }
    
    public void archiveConversation(Conversation conversation) {
    	this.databaseBackend.updateConversation(conversation);
    	this.conversations.remove(conversation);
    	if (this.convChangedListener != null) {
    		this.convChangedListener.onConversationListChanged();
    	}
    }
    
    public int getConversationCount() {
    	return this.databaseBackend.getConversationCount();
    }

	public void createAccount(Account account) {
		databaseBackend.createAccount(account);
	}
	
	public void updateAccount(Account account) {
		databaseBackend.updateAccount(account);
	}

	public void deleteAccount(Account account) {
		databaseBackend.deleteAccount(account);
	}
	
	public void setOnConversationListChangedListener(OnConversationListChangedListener listener) {
		this.convChangedListener = listener;
	}
	
	public void removeOnConversationListChangedListener() {
		this.convChangedListener = null;
	}
}
