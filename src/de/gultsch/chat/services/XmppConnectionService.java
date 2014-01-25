package de.gultsch.chat.services;

import java.util.List;

import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.persistance.DatabaseBackend;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class XmppConnectionService extends Service {
	
	protected static final String LOGTAG = "xmppConnection";
	protected DatabaseBackend databaseBackend;

    private final IBinder mBinder = new XmppConnectionBinder();

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
        	return XmppConnectionService.this;
        }
    }
    
    @Override
    public void onCreate() {
    	databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    public void sendMessage(Message message) {
    	Log.d(LOGTAG,"sending message");
    }
    
    public void addConversation(Conversation conversation) {
    	databaseBackend.addConversation(conversation);
    }
    
    public List<Conversation> getConversations(int status) {
    	return databaseBackend.getConversations(status);
    }

}
