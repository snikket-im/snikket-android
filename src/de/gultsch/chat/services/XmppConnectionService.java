package de.gultsch.chat.services;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.persistance.DatabaseBackend;
import de.gultsch.chat.xml.Tag;
import de.gultsch.chat.xml.XmlReader;
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
	
	public boolean connectionRunnig = false;

    private final IBinder mBinder = new XmppConnectionBinder();

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
        	return XmppConnectionService.this;
        }
    }
    
    @Override 
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOGTAG,"recieved start command. been running for "+((System.currentTimeMillis() - startDate) / 1000)+"s");
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (!connectionRunnig) {
	        for(Account account : accounts) {
	        	Log.d(LOGTAG,"connection wasnt running");
	        	XmppConnection connection = new XmppConnection(account, pm);
	        	Thread thread = new Thread(connection);
	        	thread.start();
	        }
	        connectionRunnig = true;
        }
        return START_STICKY;
    }
    
    @Override
    public void onCreate() {
    	databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
    	this.accounts = databaseBackend.getAccounts();
    	startDate = System.currentTimeMillis();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    public void sendMessage(Message message) {
    	databaseBackend.createMessage(message);
    }
    
    public void addConversation(Conversation conversation) {
    	databaseBackend.createConversation(conversation);
    }
    
    public List<Conversation> getConversations(int status) {
    	return databaseBackend.getConversations(status);
    }
    
    public List<Account> getAccounts() {
    	return databaseBackend.getAccounts();
    }
    
    public List<Message> getMessages(Conversation conversation) {
    	return databaseBackend.getMessages(conversation, 100);
    }

    public Conversation findOrCreateConversation(Account account, Contact contact) {
    	Conversation conversation = databaseBackend.findConversation(account, contact);
    	if (conversation!=null) {
    		Log.d("gultsch","found one. unarchive it");
    		conversation.setStatus(Conversation.STATUS_AVAILABLE);
    		this.databaseBackend.updateConversation(conversation);
    	} else {
    		conversation = new Conversation(contact.getDisplayName(), contact.getProfilePhoto(), account, contact.getJid());
    		this.databaseBackend.createConversation(conversation);
    	}
    	return conversation;
    }
    
    public void updateConversation(Conversation conversation) {
    	this.databaseBackend.updateConversation(conversation);
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
}
