package eu.siacs.conversations.services;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import de.duenndns.ssl.MemorizingTrustManager;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.parser.PresenceParser;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.OnPhoneContactsLoadedListener;
import eu.siacs.conversations.utils.PRNGFixes;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnBindListener;
import eu.siacs.conversations.xmpp.OnContactStatusChanged;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.OnMessageAcknowledged;
import eu.siacs.conversations.xmpp.OnStatusChanged;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.OnJinglePacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;

public class XmppConnectionService extends Service {

	public DatabaseBackend databaseBackend;
	private FileBackend fileBackend;

	public long startDate;

	private static String ACTION_MERGE_PHONE_CONTACTS = "merge_phone_contacts";
	public static String ACTION_CLEAR_NOTIFICATION = "clear_notification";

	private MemorizingTrustManager mMemorizingTrustManager;

	private NotificationService mNotificationService;

	private MessageParser mMessageParser = new MessageParser(this);
	private PresenceParser mPresenceParser = new PresenceParser(this);
	private IqParser mIqParser = new IqParser(this);
	private MessageGenerator mMessageGenerator = new MessageGenerator(this);
	private PresenceGenerator mPresenceGenerator = new PresenceGenerator(this);

	private List<Account> accounts;
	private CopyOnWriteArrayList<Conversation> conversations = null;
	private JingleConnectionManager mJingleConnectionManager = new JingleConnectionManager(
			this);
	private HttpConnectionManager mHttpConnectionManager = new HttpConnectionManager(
			this);

	private OnConversationUpdate mOnConversationUpdate = null;
	private Integer convChangedListenerCount = 0;
	private OnAccountUpdate mOnAccountUpdate = null;
	private Integer accountChangedListenerCount = 0;
	private OnRosterUpdate mOnRosterUpdate = null;
	private Integer rosterChangedListenerCount = 0;
	public OnContactStatusChanged onContactStatusChanged = new OnContactStatusChanged() {

		@Override
		public void onContactStatusChanged(Contact contact, boolean online) {
			Conversation conversation = find(getConversations(), contact);
			if (conversation != null) {
				conversation.endOtrIfNeeded();
				if (online && (contact.getPresences().size() == 1)) {
					sendUnsendMessages(conversation);
				}
			}
		}
	};

	private SecureRandom mRandom;

	private ContentObserver contactObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			Intent intent = new Intent(getApplicationContext(),
					XmppConnectionService.class);
			intent.setAction(ACTION_MERGE_PHONE_CONTACTS);
			startService(intent);
		}
	};
	
	private FileObserver fileObserver = new FileObserver(FileBackend.getConversationsDirectory()) {
		
		@Override
		public void onEvent(int event, String path) {
			if (event == FileObserver.DELETE) {
				markFileDeleted(path.split("\\.")[0]);
			}
		}
	};

	private final IBinder mBinder = new XmppConnectionBinder();
	private OnStatusChanged statusListener = new OnStatusChanged() {

		@Override
		public void onStatusChanged(Account account) {
			XmppConnection connection = account.getXmppConnection();
			if (mOnAccountUpdate != null) {
				mOnAccountUpdate.onAccountUpdate();
				;
			}
			if (account.getStatus() == Account.STATUS_ONLINE) {
				for (Conversation conversation : account.pendingConferenceLeaves) {
					leaveMuc(conversation);
				}
				for (Conversation conversation : account.pendingConferenceJoins) {
					joinMuc(conversation);
				}
				mJingleConnectionManager.cancelInTransmission();
				List<Conversation> conversations = getConversations();
				for (int i = 0; i < conversations.size(); ++i) {
					if (conversations.get(i).getAccount() == account) {
						conversations.get(i).startOtrIfNeeded();
						sendUnsendMessages(conversations.get(i));
					}
				}
				if (connection != null && connection.getFeatures().csi()) {
					if (checkListeners()) {
						Log.d(Config.LOGTAG, account.getJid()
								+ " sending csi//inactive");
						connection.sendInactive();
					} else {
						Log.d(Config.LOGTAG, account.getJid()
								+ " sending csi//active");
						connection.sendActive();
					}
				}
				syncDirtyContacts(account);
				scheduleWakeupCall(Config.PING_MAX_INTERVAL, true);
			} else if (account.getStatus() == Account.STATUS_OFFLINE) {
				resetSendingToWaiting(account);
				if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					int timeToReconnect = mRandom.nextInt(50) + 10;
					scheduleWakeupCall(timeToReconnect, false);
				}
			} else if (account.getStatus() == Account.STATUS_REGISTRATION_SUCCESSFULL) {
				databaseBackend.updateAccount(account);
				reconnectAccount(account, true);
			} else if ((account.getStatus() != Account.STATUS_CONNECTING)
					&& (account.getStatus() != Account.STATUS_NO_INTERNET)) {
				if (connection != null) {
					int next = connection.getTimeToNextAttempt();
					Log.d(Config.LOGTAG, account.getJid()
							+ ": error connecting account. try again in "
							+ next + "s for the "
							+ (connection.getAttempt() + 1) + " time");
					scheduleWakeupCall((int) (next * 1.2), false);
				}
			}
			UIHelper.showErrorNotification(getApplicationContext(),
					getAccounts());
		}
	};

	private OnJinglePacketReceived jingleListener = new OnJinglePacketReceived() {

		@Override
		public void onJinglePacketReceived(Account account, JinglePacket packet) {
			mJingleConnectionManager.deliverPacket(account, packet);
		}
	};

	private OpenPgpServiceConnection pgpServiceConnection;
	private PgpEngine mPgpEngine = null;
	private Intent pingIntent;
	private PendingIntent pendingPingIntent = null;
	private WakeLock wakeLock;
	private PowerManager pm;
	private OnBindListener mOnBindListener = new OnBindListener() {

		@Override
		public void onBind(final Account account) {
			account.getRoster().clearPresences();
			account.clearPresences(); // self presences
			account.pendingConferenceJoins.clear();
			account.pendingConferenceLeaves.clear();
			fetchRosterFromServer(account);
			fetchBookmarks(account);
			sendPresencePacket(account,
					mPresenceGenerator.sendPresence(account));
			connectMultiModeConversations(account);
			updateConversationUi();
		}
	};

	private OnMessageAcknowledged mOnMessageAcknowledgedListener = new OnMessageAcknowledged() {

		@Override
		public void onMessageAcknowledged(Account account, String uuid) {
			for (Conversation conversation : getConversations()) {
				if (conversation.getAccount() == account) {
					for (Message message : conversation.getMessages()) {
						if ((message.getStatus() == Message.STATUS_UNSEND || message
								.getStatus() == Message.STATUS_WAITING)
								&& message.getUuid().equals(uuid)) {
							markMessage(message, Message.STATUS_SEND);
							return;
						}
					}
				}
			}
		}
	};

	public PgpEngine getPgpEngine() {
		if (pgpServiceConnection.isBound()) {
			if (this.mPgpEngine == null) {
				this.mPgpEngine = new PgpEngine(new OpenPgpApi(
						getApplicationContext(),
						pgpServiceConnection.getService()), this);
			}
			return mPgpEngine;
		} else {
			return null;
		}

	}

	public FileBackend getFileBackend() {
		return this.fileBackend;
	}

	public Message attachImageToConversation(final Conversation conversation,
			final Uri uri, final UiCallback<Message> callback) {
		final Message message;
		if (conversation.getNextEncryption(forceEncryption()) == Message.ENCRYPTION_PGP) {
			message = new Message(conversation, "",
					Message.ENCRYPTION_DECRYPTED);
		} else {
			message = new Message(conversation, "",
					conversation.getNextEncryption(forceEncryption()));
		}
		message.setPresence(conversation.getNextPresence());
		message.setType(Message.TYPE_IMAGE);
		message.setStatus(Message.STATUS_OFFERED);
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					getFileBackend().copyImageToPrivateStorage(message, uri);
					if (conversation.getNextEncryption(forceEncryption()) == Message.ENCRYPTION_PGP) {
						getPgpEngine().encrypt(message, callback);
					} else {
						callback.success(message);
					}
				} catch (FileBackend.ImageCopyException e) {
					callback.error(e.getResId(), message);
				}
			}
		}).start();
		return message;
	}

	public Conversation find(Bookmark bookmark) {
		return find(bookmark.getAccount(), bookmark.getJid());
	}

	public Conversation find(Account account, String jid) {
		return find(getConversations(), account, jid);
	}

	public class XmppConnectionBinder extends Binder {
		public XmppConnectionService getService() {
			return XmppConnectionService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null && intent.getAction() != null) {
			if (intent.getAction().equals(ACTION_MERGE_PHONE_CONTACTS)) {
				mergePhoneContactsWithRoster();
				return START_STICKY;
			} else if (intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
				logoutAndSave();
				return START_NOT_STICKY;
			} else if (intent.getAction().equals(ACTION_CLEAR_NOTIFICATION)) {
				mNotificationService.clear();
			}
		}
		this.wakeLock.acquire();
		
		for (Account account : accounts) {
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {
				if (!hasInternetConnection()) {
					account.setStatus(Account.STATUS_NO_INTERNET);
					if (statusListener != null) {
						statusListener.onStatusChanged(account);
					}
				} else {
					if (account.getStatus() == Account.STATUS_NO_INTERNET) {
						account.setStatus(Account.STATUS_OFFLINE);
						if (statusListener != null) {
							statusListener.onStatusChanged(account);
						}
					}
					if (account.getStatus() == Account.STATUS_ONLINE) {
						long lastReceived = account.getXmppConnection()
								.getLastPacketReceived();
						long lastSent = account.getXmppConnection()
								.getLastPingSent();
						if (lastSent - lastReceived >= Config.PING_TIMEOUT * 1000) {
							Log.d(Config.LOGTAG, account.getJid()
									+ ": ping timeout");
							this.reconnectAccount(account, true);
						} else if (SystemClock.elapsedRealtime() - lastReceived >= Config.PING_MIN_INTERVAL * 1000) {
							account.getXmppConnection().sendPing();
							this.scheduleWakeupCall(2, false);
						}
					} else if (account.getStatus() == Account.STATUS_OFFLINE) {
						if (account.getXmppConnection() == null) {
							account.setXmppConnection(this
									.createConnection(account));
						}
						new Thread(account.getXmppConnection()).start();
					} else if ((account.getStatus() == Account.STATUS_CONNECTING)
							&& ((SystemClock.elapsedRealtime() - account
									.getXmppConnection().getLastConnect()) / 1000 >= Config.CONNECT_TIMEOUT)) {
						Log.d(Config.LOGTAG, account.getJid()
								+ ": time out during connect reconnecting");
						reconnectAccount(account, true);
					} else {
						if (account.getXmppConnection().getTimeToNextAttempt() <= 0) {
							reconnectAccount(account, true);
						}
					}
					// in any case. reschedule wakup call
					this.scheduleWakeupCall(Config.PING_MAX_INTERVAL, true);
				}
				if (mOnAccountUpdate != null) {
					mOnAccountUpdate.onAccountUpdate();
				}
			}
		}
		if (wakeLock.isHeld()) {
			try {
				wakeLock.release();
			} catch (RuntimeException re) {
			}
		}
		return START_STICKY;
	}
	
	public boolean hasInternetConnection() {
		ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		return activeNetwork != null && activeNetwork.isConnected();
	}

	@SuppressLint("TrulyRandom")
	@Override
	public void onCreate() {
		ExceptionHelper.init(getApplicationContext());
		PRNGFixes.apply();
		this.mRandom = new SecureRandom();
		this.mMemorizingTrustManager = new MemorizingTrustManager(
				getApplicationContext());
		this.mNotificationService = new NotificationService(this);
		this.databaseBackend = DatabaseBackend
				.getInstance(getApplicationContext());
		this.fileBackend = new FileBackend(getApplicationContext());
		this.accounts = databaseBackend.getAccounts();

		for (Account account : this.accounts) {
			this.databaseBackend.readRoster(account.getRoster());
		}
		this.mergePhoneContactsWithRoster();
		this.getConversations();

		getContentResolver().registerContentObserver(
				ContactsContract.Contacts.CONTENT_URI, true, contactObserver);
		this.fileObserver.startWatching();
		this.pgpServiceConnection = new OpenPgpServiceConnection(
				getApplicationContext(), "org.sufficientlysecure.keychain");
		this.pgpServiceConnection.bindToService();

		this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"XmppConnectionService");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		this.logoutAndSave();
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		this.logoutAndSave();
	}

	private void logoutAndSave() {
		for (Account account : accounts) {
			databaseBackend.writeRoster(account.getRoster());
			if (account.getXmppConnection() != null) {
				disconnect(account, false);
			}
		}
		Context context = getApplicationContext();
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(context, EventReceiver.class);
		alarmManager.cancel(PendingIntent.getBroadcast(context, 0, intent, 0));
		Log.d(Config.LOGTAG, "good bye");
		stopSelf();
	}

	protected void scheduleWakeupCall(int seconds, boolean ping) {
		long timeToWake = SystemClock.elapsedRealtime() + seconds * 1000;
		Context context = getApplicationContext();
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);

		if (ping) {
			if (this.pingIntent == null) {
				this.pingIntent = new Intent(context, EventReceiver.class);
				this.pingIntent.setAction("ping");
				this.pingIntent.putExtra("time", timeToWake);
				this.pendingPingIntent = PendingIntent.getBroadcast(context, 0,
						this.pingIntent, 0);
				alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
						timeToWake, pendingPingIntent);
			} else {
				long scheduledTime = this.pingIntent.getLongExtra("time", 0);
				if (scheduledTime < SystemClock.elapsedRealtime()
						|| (scheduledTime > timeToWake)) {
					this.pingIntent.putExtra("time", timeToWake);
					alarmManager.cancel(this.pendingPingIntent);
					this.pendingPingIntent = PendingIntent.getBroadcast(
							context, 0, this.pingIntent, 0);
					alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
							timeToWake, pendingPingIntent);
				}
			}
		} else {
			Intent intent = new Intent(context, EventReceiver.class);
			intent.setAction("ping_check");
			PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0,
					intent, 0);
			alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake,
					alarmIntent);
		}

	}

	public XmppConnection createConnection(Account account) {
		SharedPreferences sharedPref = getPreferences();
		account.setResource(sharedPref.getString("resource", "mobile")
				.toLowerCase(Locale.getDefault()));
		XmppConnection connection = new XmppConnection(account, this);
		connection.setOnMessagePacketReceivedListener(this.mMessageParser);
		connection.setOnStatusChangedListener(this.statusListener);
		connection.setOnPresencePacketReceivedListener(this.mPresenceParser);
		connection.setOnUnregisteredIqPacketReceivedListener(this.mIqParser);
		connection.setOnJinglePacketReceivedListener(this.jingleListener);
		connection.setOnBindListener(this.mOnBindListener);
		connection
				.setOnMessageAcknowledgeListener(this.mOnMessageAcknowledgedListener);
		return connection;
	}

	synchronized public void sendMessage(Message message) {
		Account account = message.getConversation().getAccount();
		account.deactivateGracePeriod();
		Conversation conv = message.getConversation();
		MessagePacket packet = null;
		boolean saveInDb = true;
		boolean send = false;
		if (account.getStatus() == Account.STATUS_ONLINE
				&& account.getXmppConnection() != null) {
			if (message.getType() == Message.TYPE_IMAGE) {
				if (message.getPresence() != null) {
					if (message.getEncryption() == Message.ENCRYPTION_OTR) {
						if (!conv.hasValidOtrSession()
								&& (message.getPresence() != null)) {
							conv.startOtrSession(this, message.getPresence(),
									true);
							message.setStatus(Message.STATUS_WAITING);
						} else if (conv.hasValidOtrSession()
								&& conv.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) {
							mJingleConnectionManager
									.createNewConnection(message);
						} else if (message.getPresence() == null) {
							message.setStatus(Message.STATUS_WAITING);
						}
					} else {
						mJingleConnectionManager.createNewConnection(message);
					}
				} else {
					message.setStatus(Message.STATUS_WAITING);
				}
			} else {
				if (message.getEncryption() == Message.ENCRYPTION_OTR) {
					if (!conv.hasValidOtrSession()
							&& (message.getPresence() != null)) {
						conv.startOtrSession(this, message.getPresence(), true);
						message.setStatus(Message.STATUS_WAITING);
					} else if (conv.hasValidOtrSession()
							&& conv.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) {
						message.setPresence(conv.getOtrSession().getSessionID()
								.getUserID());
						packet = mMessageGenerator.generateOtrChat(message);
						send = true;

					} else if (message.getPresence() == null) {
						message.setStatus(Message.STATUS_WAITING);
					}
				} else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
					message.getConversation().endOtrIfNeeded();
					failWaitingOtrMessages(message.getConversation());
					packet = mMessageGenerator.generatePgpChat(message);
					send = true;
				} else {
					message.getConversation().endOtrIfNeeded();
					failWaitingOtrMessages(message.getConversation());
					packet = mMessageGenerator.generateChat(message);
					send = true;
				}
			}
			if (!account.getXmppConnection().getFeatures().sm()
					&& conv.getMode() != Conversation.MODE_MULTI) {
				message.setStatus(Message.STATUS_SEND);
			}
		} else {
			message.setStatus(Message.STATUS_WAITING);
			if (message.getType() == Message.TYPE_TEXT) {
				if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
					String pgpBody = message.getEncryptedBody();
					String decryptedBody = message.getBody();
					message.setBody(pgpBody);
					message.setEncryption(Message.ENCRYPTION_PGP);
					databaseBackend.createMessage(message);
					saveInDb = false;
					message.setBody(decryptedBody);
					message.setEncryption(Message.ENCRYPTION_DECRYPTED);
				} else if (message.getEncryption() == Message.ENCRYPTION_OTR) {
					if (conv.hasValidOtrSession()) {
						message.setPresence(conv.getOtrSession().getSessionID()
								.getUserID());
					} else if (!conv.hasValidOtrSession()
							&& message.getPresence() != null) {
						conv.startOtrSession(this, message.getPresence(), false);
					}
				}
			}

		}
		conv.getMessages().add(message);
		if (saveInDb) {
			if (message.getEncryption() == Message.ENCRYPTION_NONE
					|| saveEncryptedMessages()) {
				databaseBackend.createMessage(message);
			}
		}
		if ((send) && (packet != null)) {
			sendMessagePacket(account, packet);
		}
		updateConversationUi();
	}

	private void sendUnsendMessages(Conversation conversation) {
		for (int i = 0; i < conversation.getMessages().size(); ++i) {
			int status = conversation.getMessages().get(i).getStatus();
			if (status == Message.STATUS_WAITING) {
				resendMessage(conversation.getMessages().get(i));
			}
		}
	}

	private void resendMessage(Message message) {
		Account account = message.getConversation().getAccount();
		MessagePacket packet = null;
		if (message.getEncryption() == Message.ENCRYPTION_OTR) {
			Presences presences = message.getConversation().getContact()
					.getPresences();
			if (!message.getConversation().hasValidOtrSession()) {
				if ((message.getPresence() != null)
						&& (presences.has(message.getPresence()))) {
					message.getConversation().startOtrSession(this,
							message.getPresence(), true);
				} else {
					if (presences.size() == 1) {
						String presence = presences.asStringArray()[0];
						message.getConversation().startOtrSession(this,
								presence, true);
					}
				}
			} else {
				if (message.getConversation().getOtrSession()
						.getSessionStatus() == SessionStatus.ENCRYPTED) {
					if (message.getType() == Message.TYPE_TEXT) {
						packet = mMessageGenerator.generateOtrChat(message,
								true);
					} else if (message.getType() == Message.TYPE_IMAGE) {
						mJingleConnectionManager.createNewConnection(message);
					}
				}
			}
		} else if (message.getType() == Message.TYPE_TEXT) {
			if (message.getEncryption() == Message.ENCRYPTION_NONE) {
				packet = mMessageGenerator.generateChat(message, true);
			} else if ((message.getEncryption() == Message.ENCRYPTION_DECRYPTED)
					|| (message.getEncryption() == Message.ENCRYPTION_PGP)) {
				packet = mMessageGenerator.generatePgpChat(message, true);
			}
		} else if (message.getType() == Message.TYPE_IMAGE) {
			Presences presences = message.getConversation().getContact()
					.getPresences();
			if ((message.getPresence() != null)
					&& (presences.has(message.getPresence()))) {
				markMessage(message, Message.STATUS_OFFERED);
				mJingleConnectionManager.createNewConnection(message);
			} else {
				if (presences.size() == 1) {
					String presence = presences.asStringArray()[0];
					message.setPresence(presence);
					markMessage(message, Message.STATUS_OFFERED);
					mJingleConnectionManager.createNewConnection(message);
				}
			}
		}
		if (packet != null) {
			if (!account.getXmppConnection().getFeatures().sm()
					&& message.getConversation().getMode() != Conversation.MODE_MULTI) {
				markMessage(message, Message.STATUS_SEND);
			} else {
				markMessage(message, Message.STATUS_UNSEND);
			}
			sendMessagePacket(account, packet);
		}
	}

	public void fetchRosterFromServer(Account account) {
		IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
		if (!"".equals(account.getRosterVersion())) {
			Log.d(Config.LOGTAG, account.getJid()
					+ ": fetching roster version " + account.getRosterVersion());
		} else {
			Log.d(Config.LOGTAG, account.getJid() + ": fetching roster");
		}
		iqPacket.query("jabber:iq:roster").setAttribute("ver",
				account.getRosterVersion());
		account.getXmppConnection().sendIqPacket(iqPacket,
				new OnIqPacketReceived() {

					@Override
					public void onIqPacketReceived(final Account account,
							IqPacket packet) {
						Element query = packet.findChild("query");
						if (query != null) {
							account.getRoster().markAllAsNotInRoster();
							mIqParser.rosterItems(account, query);
						}
					}
				});
	}

	public void fetchBookmarks(Account account) {
		IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
		Element query = iqPacket.query("jabber:iq:private");
		query.addChild("storage", "storage:bookmarks");
		OnIqPacketReceived callback = new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				Element query = packet.query();
				List<Bookmark> bookmarks = new CopyOnWriteArrayList<Bookmark>();
				Element storage = query.findChild("storage",
						"storage:bookmarks");
				if (storage != null) {
					for (Element item : storage.getChildren()) {
						if (item.getName().equals("conference")) {
							Bookmark bookmark = Bookmark.parse(item, account);
							bookmarks.add(bookmark);
							Conversation conversation = find(bookmark);
							if (conversation != null) {
								conversation.setBookmark(bookmark);
							} else {
								if (bookmark.autojoin()) {
									conversation = findOrCreateConversation(
											account, bookmark.getJid(), true);
									conversation.setBookmark(bookmark);
									joinMuc(conversation);
								}
							}
						}
					}
				}
				account.setBookmarks(bookmarks);
			}
		};
		sendIqPacket(account, iqPacket, callback);

	}

	public void pushBookmarks(Account account) {
		IqPacket iqPacket = new IqPacket(IqPacket.TYPE_SET);
		Element query = iqPacket.query("jabber:iq:private");
		Element storage = query.addChild("storage", "storage:bookmarks");
		for (Bookmark bookmark : account.getBookmarks()) {
			storage.addChild(bookmark);
		}
		sendIqPacket(account, iqPacket, null);
	}

	private void mergePhoneContactsWithRoster() {
		PhoneHelper.loadPhoneContacts(getApplicationContext(),
				new OnPhoneContactsLoadedListener() {
					@Override
					public void onPhoneContactsLoaded(List<Bundle> phoneContacts) {
						for (Account account : accounts) {
							account.getRoster().clearSystemAccounts();
						}
						for (Bundle phoneContact : phoneContacts) {
							for (Account account : accounts) {
								String jid = phoneContact.getString("jid");
								Contact contact = account.getRoster()
										.getContact(jid);
								String systemAccount = phoneContact
										.getInt("phoneid")
										+ "#"
										+ phoneContact.getString("lookup");
								contact.setSystemAccount(systemAccount);
								contact.setPhotoUri(phoneContact
										.getString("photouri"));
								contact.setSystemName(phoneContact
										.getString("displayname"));
							}
						}
					}
				});
	}

	public List<Conversation> getConversations() {
		if (this.conversations == null) {
			Hashtable<String, Account> accountLookupTable = new Hashtable<String, Account>();
			for (Account account : this.accounts) {
				accountLookupTable.put(account.getUuid(), account);
			}
			this.conversations = databaseBackend
					.getConversations(Conversation.STATUS_AVAILABLE);
			for (Conversation conv : this.conversations) {
				Account account = accountLookupTable.get(conv.getAccountUuid());
				conv.setAccount(account);
				conv.setMessages(databaseBackend.getMessages(conv, 50));
				checkDeletedFiles(conv);
			}
		}
		return this.conversations;
	}
	
	private void checkDeletedFiles(Conversation conversation) {
		for(Message message : conversation.getMessages()) {
			if (message.getType() == Message.TYPE_IMAGE && message.getEncryption() != Message.ENCRYPTION_PGP) {
				if (!getFileBackend().isFileAvailable(message)) {
					message.setDownloadable(new DeletedDownloadable());
				}
			}
		}
	}
	
	private void markFileDeleted(String uuid) {
		for(Conversation conversation : getConversations()) {
			for(Message message : conversation.getMessages()) {
				if (message.getType() == Message.TYPE_IMAGE && message.getEncryption() != Message.ENCRYPTION_PGP && message.getUuid().equals(uuid)) {
					if (!getFileBackend().isFileAvailable(message)) {
						message.setDownloadable(new DeletedDownloadable());
						updateConversationUi();
					}
					return;
				}
			}
		}
	}

	public void populateWithOrderedConversations(List<Conversation> list) {
		populateWithOrderedConversations(list, true);
	}

	public void populateWithOrderedConversations(List<Conversation> list,
			boolean includeConferences) {
		list.clear();
		if (includeConferences) {
			list.addAll(getConversations());
		} else {
			for (Conversation conversation : getConversations()) {
				if (conversation.getMode() == Conversation.MODE_SINGLE) {
					list.add(conversation);
				}
			}
		}
		Collections.sort(list, new Comparator<Conversation>() {
			@Override
			public int compare(Conversation lhs, Conversation rhs) {
				Message left = lhs.getLatestMessage();
				Message right = rhs.getLatestMessage();
				if (left.getTimeSent() > right.getTimeSent()) {
					return -1;
				} else if (left.getTimeSent() < right.getTimeSent()) {
					return 1;
				} else {
					return 0;
				}
			}
		});
	}

	public int loadMoreMessages(Conversation conversation, long timestamp) {
		List<Message> messages = databaseBackend.getMessages(conversation, 50,
				timestamp);
		for (Message message : messages) {
			message.setConversation(conversation);
		}
		conversation.getMessages().addAll(0, messages);
		return messages.size();
	}

	public List<Account> getAccounts() {
		return this.accounts;
	}

	public Conversation find(List<Conversation> haystack, Contact contact) {
		for (Conversation conversation : haystack) {
			if (conversation.getContact() == contact) {
				return conversation;
			}
		}
		return null;
	}

	public Conversation find(List<Conversation> haystack, Account account,
			String jid) {
		for (Conversation conversation : haystack) {
			if ((account == null || conversation.getAccount().equals(account))
					&& (conversation.getContactJid().split("/", 2)[0]
							.equals(jid))) {
				return conversation;
			}
		}
		return null;
	}

	public Conversation findOrCreateConversation(Account account, String jid,
			boolean muc) {
		Conversation conversation = find(account, jid);
		if (conversation != null) {
			return conversation;
		}
		conversation = databaseBackend.findConversation(account, jid);
		if (conversation != null) {
			conversation.setStatus(Conversation.STATUS_AVAILABLE);
			conversation.setAccount(account);
			if (muc) {
				conversation.setMode(Conversation.MODE_MULTI);
			} else {
				conversation.setMode(Conversation.MODE_SINGLE);
			}
			conversation.setMessages(databaseBackend.getMessages(conversation,
					50));
			this.databaseBackend.updateConversation(conversation);
		} else {
			String conversationName;
			Contact contact = account.getRoster().getContact(jid);
			if (contact != null) {
				conversationName = contact.getDisplayName();
			} else {
				conversationName = jid.split("@")[0];
			}
			if (muc) {
				conversation = new Conversation(conversationName, account, jid,
						Conversation.MODE_MULTI);
			} else {
				conversation = new Conversation(conversationName, account, jid,
						Conversation.MODE_SINGLE);
			}
			this.databaseBackend.createConversation(conversation);
		}
		this.conversations.add(conversation);
		updateConversationUi();
		return conversation;
	}

	public void archiveConversation(Conversation conversation) {
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			if (conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
				Bookmark bookmark = conversation.getBookmark();
				if (bookmark != null && bookmark.autojoin()) {
					bookmark.setAutojoin(false);
					pushBookmarks(bookmark.getAccount());
				}
			}
			leaveMuc(conversation);
		} else {
			conversation.endOtrIfNeeded();
		}
		this.databaseBackend.updateConversation(conversation);
		this.conversations.remove(conversation);
		updateConversationUi();
	}

	public void clearConversationHistory(Conversation conversation) {
		this.databaseBackend.deleteMessagesInConversation(conversation);
		this.fileBackend.removeFiles(conversation);
		conversation.getMessages().clear();
		updateConversationUi();
	}

	public int getConversationCount() {
		return this.databaseBackend.getConversationCount();
	}

	public void createAccount(Account account) {
		databaseBackend.createAccount(account);
		this.accounts.add(account);
		this.reconnectAccount(account, false);
		updateAccountUi();
	}

	public void updateAccount(Account account) {
		this.statusListener.onStatusChanged(account);
		databaseBackend.updateAccount(account);
		reconnectAccount(account, false);
		updateAccountUi();
		UIHelper.showErrorNotification(getApplicationContext(), getAccounts());
	}

	public void deleteAccount(Account account) {
		for (Conversation conversation : conversations) {
			if (conversation.getAccount() == account) {
				if (conversation.getMode() == Conversation.MODE_MULTI) {
					leaveMuc(conversation);
				} else if (conversation.getMode() == Conversation.MODE_SINGLE) {
					conversation.endOtrIfNeeded();
				}
				conversations.remove(conversation);
			}
		}
		if (account.getXmppConnection() != null) {
			this.disconnect(account, true);
		}
		databaseBackend.deleteAccount(account);
		this.accounts.remove(account);
		updateAccountUi();
		UIHelper.showErrorNotification(getApplicationContext(), getAccounts());
	}

	public void setOnConversationListChangedListener(
			OnConversationUpdate listener) {
		if (!isScreenOn()) {
			Log.d(Config.LOGTAG,
					"ignoring setOnConversationListChangedListener");
			return;
		}
		synchronized (this.convChangedListenerCount) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnConversationUpdate = listener;
			this.mNotificationService.setIsInForeground(true);
			this.convChangedListenerCount++;
		}
	}

	public void removeOnConversationListChangedListener() {
		synchronized (this.convChangedListenerCount) {
			this.convChangedListenerCount--;
			if (this.convChangedListenerCount <= 0) {
				this.convChangedListenerCount = 0;
				this.mOnConversationUpdate = null;
				this.mNotificationService.setIsInForeground(false);
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnAccountListChangedListener(OnAccountUpdate listener) {
		if (!isScreenOn()) {
			Log.d(Config.LOGTAG, "ignoring setOnAccountListChangedListener");
			return;
		}
		synchronized (this.accountChangedListenerCount) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnAccountUpdate = listener;
			this.accountChangedListenerCount++;
		}
	}

	public void removeOnAccountListChangedListener() {
		synchronized (this.accountChangedListenerCount) {
			this.accountChangedListenerCount--;
			if (this.accountChangedListenerCount <= 0) {
				this.mOnAccountUpdate = null;
				this.accountChangedListenerCount = 0;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnRosterUpdateListener(OnRosterUpdate listener) {
		if (!isScreenOn()) {
			Log.d(Config.LOGTAG, "ignoring setOnRosterUpdateListener");
			return;
		}
		synchronized (this.rosterChangedListenerCount) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnRosterUpdate = listener;
			this.rosterChangedListenerCount++;
		}
	}

	public void removeOnRosterUpdateListener() {
		synchronized (this.rosterChangedListenerCount) {
			this.rosterChangedListenerCount--;
			if (this.rosterChangedListenerCount <= 0) {
				this.rosterChangedListenerCount = 0;
				this.mOnRosterUpdate = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	private boolean checkListeners() {
		return (this.mOnAccountUpdate == null
				&& this.mOnConversationUpdate == null && this.mOnRosterUpdate == null);
	}

	private void switchToForeground() {
		for (Account account : getAccounts()) {
			if (account.getStatus() == Account.STATUS_ONLINE) {
				XmppConnection connection = account.getXmppConnection();
				if (connection != null && connection.getFeatures().csi()) {
					connection.sendActive();
				}
			}
		}
		Log.d(Config.LOGTAG,"app switched into foreground");
	}

	private void switchToBackground() {
		for (Account account : getAccounts()) {
			if (account.getStatus() == Account.STATUS_ONLINE) {
				XmppConnection connection = account.getXmppConnection();
				if (connection != null && connection.getFeatures().csi()) {
					connection.sendInactive();
				}
			}
		}
		this.mNotificationService.setIsInForeground(false);
		Log.d(Config.LOGTAG,"app switched into background");
	}

	private boolean isScreenOn() {
		PowerManager pm = (PowerManager) this
				.getSystemService(Context.POWER_SERVICE);
		return pm.isScreenOn();
	}

	public void connectMultiModeConversations(Account account) {
		List<Conversation> conversations = getConversations();
		for (int i = 0; i < conversations.size(); i++) {
			Conversation conversation = conversations.get(i);
			if ((conversation.getMode() == Conversation.MODE_MULTI)
					&& (conversation.getAccount() == account)) {
				joinMuc(conversation);
			}
		}
	}

	public void joinMuc(Conversation conversation) {
		Account account = conversation.getAccount();
		account.pendingConferenceJoins.remove(conversation);
		account.pendingConferenceLeaves.remove(conversation);
		if (account.getStatus() == Account.STATUS_ONLINE) {
			Log.d(Config.LOGTAG,
					"joining conversation " + conversation.getContactJid());
			String nick = conversation.getMucOptions().getProposedNick();
			conversation.getMucOptions().setJoinNick(nick);
			PresencePacket packet = new PresencePacket();
			String joinJid = conversation.getMucOptions().getJoinJid();
			packet.setAttribute("to", conversation.getMucOptions().getJoinJid());
			Element x = new Element("x");
			x.setAttribute("xmlns", "http://jabber.org/protocol/muc");
			if (conversation.getMucOptions().getPassword() != null) {
				Element password = x.addChild("password");
				password.setContent(conversation.getMucOptions().getPassword());
			}
			String sig = account.getPgpSignature();
			if (sig != null) {
				packet.addChild("status").setContent("online");
				packet.addChild("x", "jabber:x:signed").setContent(sig);
			}
			if (conversation.getMessages().size() != 0) {
				final SimpleDateFormat mDateFormat = new SimpleDateFormat(
						"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
				mDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
				Date date = new Date(conversation.getLatestMessage()
						.getTimeSent() + 1000);
				x.addChild("history").setAttribute("since",
						mDateFormat.format(date));
			}
			packet.addChild(x);
			sendPresencePacket(account, packet);
			if (!joinJid.equals(conversation.getContactJid())) {
				conversation.setContactJid(joinJid);
				databaseBackend.updateConversation(conversation);
			}
		} else {
			account.pendingConferenceJoins.add(conversation);
		}
	}

	private OnRenameListener renameListener = null;
	private IqGenerator mIqGenerator = new IqGenerator(this);

	public void setOnRenameListener(OnRenameListener listener) {
		this.renameListener = listener;
	}

	public void providePasswordForMuc(Conversation conversation, String password) {
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			conversation.getMucOptions().setPassword(password);
			if (conversation.getBookmark() != null) {
				conversation.getBookmark().setAutojoin(true);
				pushBookmarks(conversation.getAccount());
			}
			databaseBackend.updateConversation(conversation);
			joinMuc(conversation);
		}
	}

	public void renameInMuc(final Conversation conversation, final String nick) {
		final MucOptions options = conversation.getMucOptions();
		options.setJoinNick(nick);
		if (options.online()) {
			Account account = conversation.getAccount();
			options.setOnRenameListener(new OnRenameListener() {

				@Override
				public void onRename(boolean success) {
					if (renameListener != null) {
						renameListener.onRename(success);
					}
					if (success) {
						conversation.setContactJid(conversation.getMucOptions()
								.getJoinJid());
						databaseBackend.updateConversation(conversation);
						Bookmark bookmark = conversation.getBookmark();
						if (bookmark != null) {
							bookmark.setNick(nick);
							pushBookmarks(bookmark.getAccount());
						}
					}
				}
			});
			options.flagAboutToRename();
			PresencePacket packet = new PresencePacket();
			packet.setAttribute("to", options.getJoinJid());
			packet.setAttribute("from", conversation.getAccount().getFullJid());

			String sig = account.getPgpSignature();
			if (sig != null) {
				packet.addChild("status").setContent("online");
				packet.addChild("x", "jabber:x:signed").setContent(sig);
			}
			sendPresencePacket(account, packet);
		} else {
			conversation.setContactJid(options.getJoinJid());
			databaseBackend.updateConversation(conversation);
			if (conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
				Bookmark bookmark = conversation.getBookmark();
				if (bookmark != null) {
					bookmark.setNick(nick);
					pushBookmarks(bookmark.getAccount());
				}
				joinMuc(conversation);
			}
		}
	}

	public void leaveMuc(Conversation conversation) {
		Account account = conversation.getAccount();
		account.pendingConferenceJoins.remove(conversation);
		account.pendingConferenceLeaves.remove(conversation);
		if (account.getStatus() == Account.STATUS_ONLINE) {
			PresencePacket packet = new PresencePacket();
			packet.setAttribute("to", conversation.getMucOptions().getJoinJid());
			packet.setAttribute("from", conversation.getAccount().getFullJid());
			packet.setAttribute("type", "unavailable");
			sendPresencePacket(conversation.getAccount(), packet);
			conversation.getMucOptions().setOffline();
			conversation.deregisterWithBookmark();
			Log.d(Config.LOGTAG, conversation.getAccount().getJid()
					+ ": leaving muc " + conversation.getContactJid());
		} else {
			account.pendingConferenceLeaves.add(conversation);
		}
	}

	public void disconnect(Account account, boolean force) {
		if ((account.getStatus() == Account.STATUS_ONLINE)
				|| (account.getStatus() == Account.STATUS_DISABLED)) {
			if (!force) {
				List<Conversation> conversations = getConversations();
				for (int i = 0; i < conversations.size(); i++) {
					Conversation conversation = conversations.get(i);
					if (conversation.getAccount() == account) {
						if (conversation.getMode() == Conversation.MODE_MULTI) {
							leaveMuc(conversation);
						} else {
							if (conversation.endOtrIfNeeded()) {
								Log.d(Config.LOGTAG,account.getJid()+": ended otr session with "+conversation.getContactJid());
							}
						}
					}
				}
			}
			account.getXmppConnection().disconnect(force);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public void updateMessage(Message message) {
		databaseBackend.updateMessage(message);
		updateConversationUi();
	}

	protected void syncDirtyContacts(Account account) {
		for (Contact contact : account.getRoster().getContacts()) {
			if (contact.getOption(Contact.Options.DIRTY_PUSH)) {
				pushContactToServer(contact);
			}
			if (contact.getOption(Contact.Options.DIRTY_DELETE)) {
				deleteContactOnServer(contact);
			}
		}
	}

	public void createContact(Contact contact) {
		SharedPreferences sharedPref = getPreferences();
		boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
		if (autoGrant) {
			contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
			contact.setOption(Contact.Options.ASKING);
		}
		pushContactToServer(contact);
	}

	public void onOtrSessionEstablished(Conversation conversation) {
		Account account = conversation.getAccount();
		List<Message> messages = conversation.getMessages();
		Session otrSession = conversation.getOtrSession();
		Log.d(Config.LOGTAG,
				account.getJid() + " otr session established with "
						+ conversation.getContactJid() + "/"
						+ otrSession.getSessionID().getUserID());
		for (int i = 0; i < messages.size(); ++i) {
			Message msg = messages.get(i);
			if ((msg.getStatus() == Message.STATUS_UNSEND || msg.getStatus() == Message.STATUS_WAITING)
					&& (msg.getEncryption() == Message.ENCRYPTION_OTR)) {
				msg.setPresence(otrSession.getSessionID().getUserID());
				if (msg.getType() == Message.TYPE_TEXT) {
					MessagePacket outPacket = mMessageGenerator
							.generateOtrChat(msg, true);
					if (outPacket != null) {
						msg.setStatus(Message.STATUS_SEND);
						databaseBackend.updateMessage(msg);
						sendMessagePacket(account, outPacket);
					}
				} else if (msg.getType() == Message.TYPE_IMAGE) {
					mJingleConnectionManager.createNewConnection(msg);
				}
			}
		}
		updateConversationUi();
	}

	public boolean renewSymmetricKey(Conversation conversation) {
		Account account = conversation.getAccount();
		byte[] symmetricKey = new byte[32];
		this.mRandom.nextBytes(symmetricKey);
		Session otrSession = conversation.getOtrSession();
		if (otrSession != null) {
			MessagePacket packet = new MessagePacket();
			packet.setType(MessagePacket.TYPE_CHAT);
			packet.setFrom(account.getFullJid());
			packet.addChild("private", "urn:xmpp:carbons:2");
			packet.addChild("no-copy", "urn:xmpp:hints");
			packet.setTo(otrSession.getSessionID().getAccountID() + "/"
					+ otrSession.getSessionID().getUserID());
			try {
				packet.setBody(otrSession
						.transformSending(CryptoHelper.FILETRANSFER
								+ CryptoHelper.bytesToHex(symmetricKey)));
				sendMessagePacket(account, packet);
				conversation.setSymmetricKey(symmetricKey);
				return true;
			} catch (OtrException e) {
				return false;
			}
		}
		return false;
	}

	public void pushContactToServer(Contact contact) {
		contact.resetOption(Contact.Options.DIRTY_DELETE);
		contact.setOption(Contact.Options.DIRTY_PUSH);
		Account account = contact.getAccount();
		if (account.getStatus() == Account.STATUS_ONLINE) {
			boolean ask = contact.getOption(Contact.Options.ASKING);
			boolean sendUpdates = contact
					.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)
					&& contact.getOption(Contact.Options.PREEMPTIVE_GRANT);
			IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
			iq.query("jabber:iq:roster").addChild(contact.asElement());
			account.getXmppConnection().sendIqPacket(iq, null);
			if (sendUpdates) {
				sendPresencePacket(account,
						mPresenceGenerator.sendPresenceUpdatesTo(contact));
			}
			if (ask) {
				sendPresencePacket(account,
						mPresenceGenerator.requestPresenceUpdatesFrom(contact));
			}
		}
	}

	public void publishAvatar(Account account, Uri image,
			final UiCallback<Avatar> callback) {
		final Bitmap.CompressFormat format = Config.AVATAR_FORMAT;
		final int size = Config.AVATAR_SIZE;
		final Avatar avatar = getFileBackend()
				.getPepAvatar(image, size, format);
		if (avatar != null) {
			avatar.height = size;
			avatar.width = size;
			if (format.equals(Bitmap.CompressFormat.WEBP)) {
				avatar.type = "image/webp";
			} else if (format.equals(Bitmap.CompressFormat.JPEG)) {
				avatar.type = "image/jpeg";
			} else if (format.equals(Bitmap.CompressFormat.PNG)) {
				avatar.type = "image/png";
			}
			if (!getFileBackend().save(avatar)) {
				callback.error(R.string.error_saving_avatar, avatar);
				return;
			}
			IqPacket packet = this.mIqGenerator.publishAvatar(avatar);
			this.sendIqPacket(account, packet, new OnIqPacketReceived() {

				@Override
				public void onIqPacketReceived(Account account, IqPacket result) {
					if (result.getType() == IqPacket.TYPE_RESULT) {
						IqPacket packet = XmppConnectionService.this.mIqGenerator
								.publishAvatarMetadata(avatar);
						sendIqPacket(account, packet, new OnIqPacketReceived() {

							@Override
							public void onIqPacketReceived(Account account,
									IqPacket result) {
								if (result.getType() == IqPacket.TYPE_RESULT) {
									if (account.setAvatar(avatar.getFilename())) {
										databaseBackend.updateAccount(account);
									}
									callback.success(avatar);
								} else {
									callback.error(
											R.string.error_publish_avatar_server_reject,
											avatar);
								}
							}
						});
					} else {
						callback.error(
								R.string.error_publish_avatar_server_reject,
								avatar);
					}
				}
			});
		} else {
			callback.error(R.string.error_publish_avatar_converting, null);
		}
	}

	public void fetchAvatar(Account account, Avatar avatar) {
		fetchAvatar(account, avatar, null);
	}

	public void fetchAvatar(Account account, final Avatar avatar,
			final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.retrieveAvatar(avatar);
		sendIqPacket(account, packet, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket result) {
				final String ERROR = account.getJid()
						+ ": fetching avatar for " + avatar.owner + " failed ";
				if (result.getType() == IqPacket.TYPE_RESULT) {
					avatar.image = mIqParser.avatarData(result);
					if (avatar.image != null) {
						if (getFileBackend().save(avatar)) {
							if (account.getJid().equals(avatar.owner)) {
								if (account.setAvatar(avatar.getFilename())) {
									databaseBackend.updateAccount(account);
								}
							} else {
								Contact contact = account.getRoster()
										.getContact(avatar.owner);
								contact.setAvatar(avatar.getFilename());
							}
							if (callback != null) {
								callback.success(avatar);
							}
							Log.d(Config.LOGTAG, account.getJid()
									+ ": succesfully fetched avatar for "
									+ avatar.owner);
							return;
						}
					} else {

						Log.d(Config.LOGTAG, ERROR + "(parsing error)");
					}
				} else {
					Element error = result.findChild("error");
					if (error == null) {
						Log.d(Config.LOGTAG, ERROR + "(server error)");
					} else {
						Log.d(Config.LOGTAG, ERROR + error.toString());
					}
				}
				if (callback != null) {
					callback.error(0, null);
				}

			}
		});
	}

	public void checkForAvatar(Account account,
			final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.retrieveAvatarMetaData(null);
		this.sendIqPacket(account, packet, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE_RESULT) {
					Element pubsub = packet.findChild("pubsub",
							"http://jabber.org/protocol/pubsub");
					if (pubsub != null) {
						Element items = pubsub.findChild("items");
						if (items != null) {
							Avatar avatar = Avatar.parseMetadata(items);
							if (avatar != null) {
								avatar.owner = account.getJid();
								if (fileBackend.isAvatarCached(avatar)) {
									if (account.setAvatar(avatar.getFilename())) {
										databaseBackend.updateAccount(account);
									}
									callback.success(avatar);
								} else {
									fetchAvatar(account, avatar, callback);
								}
								return;
							}
						}
					}
				}
				callback.error(0, null);
			}
		});
	}

	public void deleteContactOnServer(Contact contact) {
		contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
		contact.resetOption(Contact.Options.DIRTY_PUSH);
		contact.setOption(Contact.Options.DIRTY_DELETE);
		Account account = contact.getAccount();
		if (account.getStatus() == Account.STATUS_ONLINE) {
			IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
			Element item = iq.query("jabber:iq:roster").addChild("item");
			item.setAttribute("jid", contact.getJid());
			item.setAttribute("subscription", "remove");
			account.getXmppConnection().sendIqPacket(iq, null);
		}
	}

	public void updateConversation(Conversation conversation) {
		this.databaseBackend.updateConversation(conversation);
	}

	public void reconnectAccount(final Account account, final boolean force) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (account.getXmppConnection() != null) {
					disconnect(account, force);
				}
				if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					if (account.getXmppConnection() == null) {
						account.setXmppConnection(createConnection(account));
					}
					Thread thread = new Thread(account.getXmppConnection());
					thread.start();
					scheduleWakeupCall((int) (Config.CONNECT_TIMEOUT * 1.2),
							false);
				} else {
					account.getRoster().clearPresences();
					account.setXmppConnection(null);
				}
			}
		}).start();
	}

	public void invite(Conversation conversation, String contact) {
		MessagePacket packet = mMessageGenerator.invite(conversation, contact);
		sendMessagePacket(conversation.getAccount(), packet);
	}

	public void resetSendingToWaiting(Account account) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getAccount() == account) {
				for (Message message : conversation.getMessages()) {
					if (message.getType() != Message.TYPE_IMAGE
							&& message.getStatus() == Message.STATUS_UNSEND) {
						markMessage(message, Message.STATUS_WAITING);
					}
				}
			}
		}
	}

	public boolean markMessage(Account account, String recipient, String uuid,
			int status) {
		if (uuid == null) {
			return false;
		} else {
			for (Conversation conversation : getConversations()) {
				if (conversation.getContactJid().equals(recipient)
						&& conversation.getAccount().equals(account)) {
					return markMessage(conversation, uuid, status);
				}
			}
			return false;
		}
	}

	public boolean markMessage(Conversation conversation, String uuid,
			int status) {
		if (uuid == null) {
			return false;
		} else {
			for (Message message : conversation.getMessages()) {
				if (uuid.equals(message.getUuid())
						|| (message.getStatus() >= Message.STATUS_SEND && uuid
								.equals(message.getRemoteMsgId()))) {
					markMessage(message, status);
					return true;
				}
			}
			return false;
		}
	}

	public void markMessage(Message message, int status) {
		if (status == Message.STATUS_SEND_FAILED
				&& (message.getStatus() == Message.STATUS_SEND_RECEIVED || message
						.getStatus() == Message.STATUS_SEND_DISPLAYED)) {
			return;
		}
		message.setStatus(status);
		databaseBackend.updateMessage(message);
		updateConversationUi();
	}

	public SharedPreferences getPreferences() {
		return PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
	}

	public boolean forceEncryption() {
		return getPreferences().getBoolean("force_encryption", false);
	}

	public boolean confirmMessages() {
		return getPreferences().getBoolean("confirm_messages", true);
	}

	public boolean saveEncryptedMessages() {
		return !getPreferences().getBoolean("dont_save_encrypted", false);
	}

	public boolean indicateReceived() {
		return getPreferences().getBoolean("indicate_received", false);
	}

	public void updateConversationUi() {
		if (mOnConversationUpdate != null) {
			mOnConversationUpdate.onConversationUpdate();
		}
	}

	public void updateAccountUi() {
		if (mOnAccountUpdate != null) {
			mOnAccountUpdate.onAccountUpdate();
		}
	}

	public void updateRosterUi() {
		if (mOnRosterUpdate != null) {
			mOnRosterUpdate.onRosterUpdate();
		}
	}

	public Account findAccountByJid(String accountJid) {
		for (Account account : this.accounts) {
			if (account.getJid().equals(accountJid)) {
				return account;
			}
		}
		return null;
	}

	public Conversation findConversationByUuid(String uuid) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getUuid().equals(uuid)) {
				return conversation;
			}
		}
		return null;
	}

	public void markRead(Conversation conversation, boolean calledByUi) {
		mNotificationService.clear(conversation);
		String id = conversation.getLatestMarkableMessageId();
		conversation.markRead();
		if (confirmMessages() && id != null && calledByUi) {
			Log.d(Config.LOGTAG, conversation.getAccount().getJid()
					+ ": sending read marker for " + conversation.getName());
			Account account = conversation.getAccount();
			String to = conversation.getContactJid();
			this.sendMessagePacket(conversation.getAccount(),
					mMessageGenerator.confirm(account, to, id));
		}
		if (!calledByUi) {
			updateConversationUi();
		}
	}

	public void failWaitingOtrMessages(Conversation conversation) {
		for (Message message : conversation.getMessages()) {
			if (message.getEncryption() == Message.ENCRYPTION_OTR
					&& message.getStatus() == Message.STATUS_WAITING) {
				markMessage(message, Message.STATUS_SEND_FAILED);
			}
		}
	}

	public SecureRandom getRNG() {
		return this.mRandom;
	}

	public MemorizingTrustManager getMemorizingTrustManager() {
		return this.mMemorizingTrustManager;
	}

	public PowerManager getPowerManager() {
		return this.pm;
	}

	public void replyWithNotAcceptable(Account account, MessagePacket packet) {
		if (account.getStatus() == Account.STATUS_ONLINE) {
			MessagePacket error = this.mMessageGenerator
					.generateNotAcceptable(packet);
			sendMessagePacket(account, error);
		}
	}

	public void syncRosterToDisk(final Account account) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				databaseBackend.writeRoster(account.getRoster());
			}
		}).start();

	}

	public List<String> getKnownHosts() {
		List<String> hosts = new ArrayList<String>();
		for (Account account : getAccounts()) {
			if (!hosts.contains(account.getServer())) {
				hosts.add(account.getServer());
			}
			for (Contact contact : account.getRoster().getContacts()) {
				if (contact.showInRoster()) {
					String server = contact.getServer();
					if (server != null && !hosts.contains(server)) {
						hosts.add(server);
					}
				}
			}
		}
		return hosts;
	}

	public List<String> getKnownConferenceHosts() {
		ArrayList<String> mucServers = new ArrayList<String>();
		for (Account account : accounts) {
			if (account.getXmppConnection() != null) {
				String server = account.getXmppConnection().getMucServer();
				if (server != null && !mucServers.contains(server)) {
					mucServers.add(server);
				}
			}
		}
		return mucServers;
	}

	public void sendMessagePacket(Account account, MessagePacket packet) {
		XmppConnection connection = account.getXmppConnection();
		if (connection != null) {
			connection.sendMessagePacket(packet);
		}
	}

	public void sendPresencePacket(Account account, PresencePacket packet) {
		XmppConnection connection = account.getXmppConnection();
		if (connection != null) {
			connection.sendPresencePacket(packet);
		}
	}

	public void sendIqPacket(Account account, IqPacket packet,
			OnIqPacketReceived callback) {
		XmppConnection connection = account.getXmppConnection();
		if (connection != null) {
			connection.sendIqPacket(packet, callback);
		}
	}

	public MessageGenerator getMessageGenerator() {
		return this.mMessageGenerator;
	}

	public PresenceGenerator getPresenceGenerator() {
		return this.mPresenceGenerator;
	}

	public IqGenerator getIqGenerator() {
		return this.mIqGenerator;
	}

	public JingleConnectionManager getJingleConnectionManager() {
		return this.mJingleConnectionManager;
	}

	public interface OnConversationUpdate {
		public void onConversationUpdate();
	}

	public interface OnAccountUpdate {
		public void onAccountUpdate();
	}

	public interface OnRosterUpdate {
		public void onRosterUpdate();
	}

	public List<Contact> findContacts(String jid) {
		ArrayList<Contact> contacts = new ArrayList<Contact>();
		for (Account account : getAccounts()) {
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {
				Contact contact = account.getRoster().getContactFromRoster(jid);
				if (contact != null) {
					contacts.add(contact);
				}
			}
		}
		return contacts;
	}

	public NotificationService getNotificationService() {
		return this.mNotificationService;
	}

	public HttpConnectionManager getHttpConnectionManager() {
		return this.mHttpConnectionManager;
	}
	
	private class DeletedDownloadable implements Downloadable {

		@Override
		public boolean start() {
			return false;
		}

		@Override
		public int getStatus() {
			return Downloadable.STATUS_DELETED;
		}

		@Override
		public long getFileSize() {
			return 0;
		}
		
	}
}
