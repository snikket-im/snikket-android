package eu.siacs.conversations.services;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import rocks.xmpp.addr.Jid;

public class ExportLogsService extends Service {

	private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	private static final String DIRECTORY_STRING_FORMAT = FileBackend.getConversationsLogsDirectory() + "/logs/%s";
	private static final String MESSAGE_STRING_FORMAT = "(%s) %s: %s\n";
	private static final int NOTIFICATION_ID = 1;
	private static AtomicBoolean running = new AtomicBoolean(false);
	private DatabaseBackend mDatabaseBackend;
	private List<Account> mAccounts;

	@Override
	public void onCreate() {
		mDatabaseBackend = DatabaseBackend.getInstance(getBaseContext());
		mAccounts = mDatabaseBackend.getAccounts();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (running.compareAndSet(false, true)) {
			new Thread(() -> {
                export();
                stopForeground(true);
                running.set(false);
                stopSelf();
            }).start();
		}
		return START_NOT_STICKY;
	}

	private void export() {
		List<Conversation> conversations = mDatabaseBackend.getConversations(Conversation.STATUS_AVAILABLE);
		conversations.addAll(mDatabaseBackend.getConversations(Conversation.STATUS_ARCHIVED));
		NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getBaseContext());
		mBuilder.setContentTitle(getString(R.string.notification_export_logs_title))
				.setSmallIcon(R.drawable.ic_import_export_white_24dp)
				.setProgress(conversations.size(), 0, false);
		startForeground(NOTIFICATION_ID, mBuilder.build());

		int progress = 0;
		for (Conversation conversation : conversations) {
			writeToFile(conversation);
			progress++;
			mBuilder.setProgress(conversations.size(), progress, false);
			mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
		}
	}

	private void writeToFile(Conversation conversation) {
		Jid accountJid = resolveAccountUuid(conversation.getAccountUuid());
		Jid contactJid = conversation.getJid();

		File dir = new File(String.format(DIRECTORY_STRING_FORMAT,accountJid.asBareJid().toString()));
		dir.mkdirs();

		BufferedWriter bw = null;
		try {
			for (Message message : mDatabaseBackend.getMessagesIterable(conversation)) {
				if (message == null)
					continue;
				if (message.getType() == Message.TYPE_TEXT || message.hasFileOnRemoteHost()) {
					String date = simpleDateFormat.format(new Date(message.getTimeSent()));
					if (bw == null) {
						bw = new BufferedWriter(new FileWriter(
								new File(dir, contactJid.asBareJid().toString() + ".txt")));
					}
					String jid = null;
					switch (message.getStatus()) {
						case Message.STATUS_RECEIVED:
							jid = getMessageCounterpart(message);
							break;
						case Message.STATUS_SEND:
						case Message.STATUS_SEND_RECEIVED:
						case Message.STATUS_SEND_DISPLAYED:
							jid = accountJid.asBareJid().toString();
							break;
					}
					if (jid != null) {
						String body = message.hasFileOnRemoteHost() ? message.getFileParams().url.toString() : message.getBody();
						bw.write(String.format(MESSAGE_STRING_FORMAT, date, jid,
								body.replace("\\\n", "\\ \n").replace("\n", "\\ \n")));
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (bw != null) {
					bw.close();
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	private Jid resolveAccountUuid(String accountUuid) {
		for (Account account : mAccounts) {
			if (account.getUuid().equals(accountUuid)) {
				return account.getJid();
			}
		}
		return null;
	}

	private String getMessageCounterpart(Message message) {
		String trueCounterpart = (String) message.getContentValues().get(Message.TRUE_COUNTERPART);
		if (trueCounterpart != null) {
			return trueCounterpart;
		} else {
			return message.getCounterpart().toString();
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}