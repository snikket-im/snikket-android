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

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
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
import eu.siacs.conversations.xmpp.OnStatusChanged;
import eu.siacs.conversations.xmpp.OnTLSExceptionReceived;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.OnJinglePacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;

public class XmppConnectionService extends Service {

	protected static final String LOGTAG = "xmppService";
	public DatabaseBackend databaseBackend;
	private FileBackend fileBackend;

	public long startDate;

	private static final int PING_MAX_INTERVAL = 300;
	private static final int PING_MIN_INTERVAL = 30;
	private static final int PING_TIMEOUT = 10;
	private static final int CONNECT_TIMEOUT = 90;
	public static final long CARBON_GRACE_PERIOD = 60000L;

	private static String ACTION_MERGE_PHONE_CONTACTS = "merge_phone_contacts";

	private MessageParser mMessageParser = new MessageParser(this);
	private PresenceParser mPresenceParser = new PresenceParser(this);
	private IqParser mIqParser = new IqParser(this);
	private MessageGenerator mMessageGenerator = new MessageGenerator();
	private PresenceGenerator mPresenceGenerator = new PresenceGenerator();
	
	private List<Account> accounts;
	private CopyOnWriteArrayList<Conversation> conversations = null;
	private JingleConnectionManager mJingleConnectionManager = new JingleConnectionManager(
			this);

	private OnConversationUpdate mOnConversationUpdate = null;
	private int convChangedListenerCount = 0;
	private OnAccountUpdate mOnAccountUpdate = null;
	private OnRosterUpdate mOnRosterUpdate = null;
	private OnTLSExceptionReceived tlsException = null;
	public OnContactStatusChanged onContactStatusChanged = new OnContactStatusChanged() {

		@Override
		public void onContactStatusChanged(Contact contact, boolean online) {
			Conversation conversation = find(getConversations(),contact);
			if (conversation != null) {
				conversation.endOtrIfNeeded();
				if (online && (contact.getPresences().size() == 1)) {
					sendUnsendMessages(conversation);
				}
			}
		}
	};

	public void setOnTLSExceptionReceivedListener(
			OnTLSExceptionReceived listener) {
		tlsException = listener;
	}

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

	private final IBinder mBinder = new XmppConnectionBinder();
	private OnStatusChanged statusListener = new OnStatusChanged() {

		@Override
		public void onStatusChanged(Account account) {
			if (mOnAccountUpdate != null) {
				mOnAccountUpdate.onAccountUpdate();;
			}
			if (account.getStatus() == Account.STATUS_ONLINE) {
				for(Conversation conversation : account.pendingConferenceLeaves) {
					leaveMuc(conversation);
				}
				for(Conversation conversation : account.pendingConferenceJoins) {
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
				syncDirtyContacts(account);
				scheduleWakeupCall(PING_MAX_INTERVAL, true);
			} else if (account.getStatus() == Account.STATUS_OFFLINE) {
				if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					int timeToReconnect = mRandom.nextInt(50) + 10;
					scheduleWakeupCall(timeToReconnect, false);
				}

			} else if (account.getStatus() == Account.STATUS_REGISTRATION_SUCCESSFULL) {
				databaseBackend.updateAccount(account);
				reconnectAccount(account, true);
			} else if ((account.getStatus() != Account.STATUS_CONNECTING)
					&& (account.getStatus() != Account.STATUS_NO_INTERNET)) {
				int next = account.getXmppConnection().getTimeToNextAttempt();
				Log.d(LOGTAG, account.getJid()
						+ ": error connecting account. try again in " + next
						+ "s for the "
						+ (account.getXmppConnection().getAttempt() + 1)
						+ " time");
				scheduleWakeupCall(next, false);
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
				sendPresencePacket(account, mPresenceGenerator.sendPresence(account));
				connectMultiModeConversations(account);
				updateConversationUi();
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
		if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
			message = new Message(conversation, "",
					Message.ENCRYPTION_DECRYPTED);
		} else {
			message = new Message(conversation, "",
					conversation.getNextEncryption());
		}
		message.setPresence(conversation.getNextPresence());
		message.setType(Message.TYPE_IMAGE);
		message.setStatus(Message.STATUS_OFFERED);
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					getFileBackend().copyImageToPrivateStorage(message, uri);
					if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
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
		return find(bookmark.getAccount(),bookmark.getJid());
	}
	
	public Conversation find(Account account, String jid) {
		return find(getConversations(),account,jid);
	}

	public class XmppConnectionBinder extends Binder {
		public XmppConnectionService getService() {
			return XmppConnectionService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if ((intent != null)
				&& (ACTION_MERGE_PHONE_CONTACTS.equals(intent.getAction()))) {
			mergePhoneContactsWithRoster();
			return START_STICKY;
		} else if ((intent != null)
				&& (Intent.ACTION_SHUTDOWN.equals(intent.getAction()))) {
			logoutAndSave();
			return START_NOT_STICKY;
		}
		this.wakeLock.acquire();
		ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null
				&& activeNetwork.isConnected();

		for (Account account : accounts) {
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {
				if (!isConnected) {
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
						long lastReceived = account.getXmppConnection().lastPaketReceived;
						long lastSent = account.getXmppConnection().lastPingSent;
						if (lastSent - lastReceived >= PING_TIMEOUT * 1000) {
							Log.d(LOGTAG, account.getJid() + ": ping timeout");
							this.reconnectAccount(account, true);
						} else if (SystemClock.elapsedRealtime() - lastReceived >= PING_MIN_INTERVAL * 1000) {
							account.getXmppConnection().sendPing();
							account.getXmppConnection().lastPingSent = SystemClock
									.elapsedRealtime();
							this.scheduleWakeupCall(2, false);
						}
					} else if (account.getStatus() == Account.STATUS_OFFLINE) {
						if (account.getXmppConnection() == null) {
							account.setXmppConnection(this
									.createConnection(account));
						}
						account.getXmppConnection().lastPingSent = SystemClock
								.elapsedRealtime();
						new Thread(account.getXmppConnection()).start();
					} else if ((account.getStatus() == Account.STATUS_CONNECTING)
							&& ((SystemClock.elapsedRealtime() - account
									.getXmppConnection().lastConnect) / 1000 >= CONNECT_TIMEOUT)) {
						Log.d(LOGTAG, account.getJid()
								+ ": time out during connect reconnecting");
						reconnectAccount(account, true);
					} else {
						if (account.getXmppConnection().getTimeToNextAttempt() <= 0) {
							reconnectAccount(account, true);
						}
					}
					// in any case. reschedule wakup call
					this.scheduleWakeupCall(PING_MAX_INTERVAL, true);
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

	@SuppressLint("TrulyRandom")
	@Override
	public void onCreate() {
		ExceptionHelper.init(getApplicationContext());
		PRNGFixes.apply();
		this.mRandom = new SecureRandom();
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
		Log.d(LOGTAG, "good bye");
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
		connection
				.setOnUnregisteredIqPacketReceivedListener(this.mIqParser);
		connection.setOnJinglePacketReceivedListener(this.jingleListener);
		connection
				.setOnTLSExceptionReceivedListener(new OnTLSExceptionReceived() {

					@Override
					public void onTLSExceptionReceived(String fingerprint,
							Account account) {
						Log.d(LOGTAG, "tls exception arrived in service");
						if (tlsException != null) {
							tlsException.onTLSExceptionReceived(fingerprint,
									account);
						}
					}
				});
		connection.setOnBindListener(this.mOnBindListener);
		return connection;
	}

	synchronized public void sendMessage(Message message) {
		Account account = message.getConversation().getAccount();
		Conversation conv = message.getConversation();
		MessagePacket packet = null;
		boolean saveInDb = true;
		boolean send = false;
		if (account.getStatus() == Account.STATUS_ONLINE) {
			if (message.getType() == Message.TYPE_IMAGE) {
				if (message.getPresence() != null) {
					if (message.getEncryption() == Message.ENCRYPTION_OTR) {
						if (!conv.hasValidOtrSession()
								&& (message.getPresence() != null)) {
							conv.startOtrSession(getApplicationContext(),
									message.getPresence(), true);
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
						conv.startOtrSession(getApplicationContext(),
								message.getPresence(), true);
						message.setStatus(Message.STATUS_WAITING);
					} else if (conv.hasValidOtrSession()
							&& conv.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) {
						message.setPresence(conv.getOtrSession().getSessionID()
								.getUserID());
						packet = mMessageGenerator.generateOtrChat(message);
						send = true;
						message.setStatus(Message.STATUS_SEND);
						
					} else if (message.getPresence() == null) {
						message.setStatus(Message.STATUS_WAITING);
					}
				} else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
					message.getConversation().endOtrIfNeeded();
					message.getConversation().failWaitingOtrMessages();
					packet = mMessageGenerator.generatePgpChat(message);
					message.setStatus(Message.STATUS_SEND);
					send = true;
				} else {
					message.getConversation().endOtrIfNeeded();
					message.getConversation().failWaitingOtrMessages();
					if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
						message.setStatus(Message.STATUS_SEND);
					}
					packet = mMessageGenerator.generateChat(message);
					send = true;
				}
			}
		} else {
			message.setStatus(Message.STATUS_WAITING);
			if (message.getType() == Message.TYPE_TEXT) {
				if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
					String pgpBody = message.getEncryptedBody();
					String decryptedBody = message.getBody();
					message.setBody(pgpBody);
					databaseBackend.createMessage(message);
					saveInDb = false;
					message.setBody(decryptedBody);
				} else if (message.getEncryption() == Message.ENCRYPTION_OTR) {
					if (conv.hasValidOtrSession()) {
						message.setPresence(conv.getOtrSession().getSessionID()
								.getUserID());
					} else if (!conv.hasValidOtrSession()
							&& message.getPresence() != null) {
						conv.startOtrSession(getApplicationContext(),
								message.getPresence(), false);
					}
				}
			}

		}
		if (saveInDb) {
			databaseBackend.createMessage(message);
		}
		conv.getMessages().add(message);
		updateConversationUi();
		if ((send) && (packet != null)) {
			sendMessagePacket(account, packet);
		}

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
					message.getConversation().startOtrSession(
							getApplicationContext(), message.getPresence(),
							true);
				} else {
					if (presences.size() == 1) {
						String presence = presences.asStringArray()[0];
						message.getConversation().startOtrSession(
								getApplicationContext(), presence, true);
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
			sendMessagePacket(account,packet);
			markMessage(message, Message.STATUS_SEND);
		}
	}

	public void fetchRosterFromServer(Account account) {
		IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
		if (!"".equals(account.getRosterVersion())) {
			Log.d(LOGTAG, account.getJid() + ": fetching roster version "
					+ account.getRosterVersion());
		} else {
			Log.d(LOGTAG, account.getJid() + ": fetching roster");
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
				List<Bookmark> bookmarks = new ArrayList<Bookmark>();
				Element storage = query.findChild("storage", "storage:bookmarks");
				if (storage!=null) {
					for(Element item : storage.getChildren()) {
						if (item.getName().equals("conference")) {
							Bookmark bookmark = Bookmark.parse(item,account);
							bookmarks.add(bookmark);
							Conversation conversation = find(bookmark);
							if (conversation!=null) {
								conversation.setBookmark(bookmark);
							} else {
								if (bookmark.autojoin()) {
									conversation = findOrCreateConversation(account, bookmark.getJid(), true);
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
		for(Bookmark bookmark : account.getBookmarks()) {
			storage.addChild(bookmark.toElement());
		}
		sendIqPacket(account, iqPacket,null);
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
			}
		}
		
		return this.conversations;
	}
	
	public void populateWithOrderedConversations(List<Conversation> list) {
		list.clear();
		list.addAll(getConversations());
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

	public List<Message> getMoreMessages(Conversation conversation,
			long timestamp) {
		List<Message> messages = databaseBackend.getMessages(conversation, 50,
				timestamp);
		for (Message message : messages) {
			message.setConversation(conversation);
		}
		return messages;
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

	public Conversation find(List<Conversation> haystack, Account account, String jid) {
		for (Conversation conversation : haystack) {
			if ((conversation.getAccount().equals(account))
					&& (conversation.getContactJid().split("/")[0].equals(jid))) {
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
		conversation = databaseBackend.findConversation(account,jid);
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
			Bookmark bookmark = conversation.getBookmark();
			if (bookmark!=null && bookmark.autojoin()) {
				bookmark.setAutojoin(false);
				pushBookmarks(bookmark.getAccount());
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
		for(Conversation conversation : conversations) {
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
		this.mOnConversationUpdate = listener;
		this.convChangedListenerCount++;
	}

	public void removeOnConversationListChangedListener() {
		this.convChangedListenerCount--;
		if (this.convChangedListenerCount == 0) {
			this.mOnConversationUpdate = null;
		}
	}

	public void setOnAccountListChangedListener(OnAccountUpdate listener) {
		this.mOnAccountUpdate = listener;
	}

	public void removeOnAccountListChangedListener() {
		this.mOnAccountUpdate = null;
	}
	
	public void setOnRosterUpdateListener(OnRosterUpdate listener) {
		this.mOnRosterUpdate = listener;
	}

	public void removeOnRosterUpdateListener() {
		this.mOnRosterUpdate = null;
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
			Log.d(LOGTAG,"joining conversation "+conversation.getContactJid());
			String nick = conversation.getMucOptions().getProposedNick();
			conversation.getMucOptions().setJoinNick(nick);
			PresencePacket packet = new PresencePacket();
			String joinJid = conversation.getMucOptions().getJoinJid();
			packet.setAttribute("to",conversation.getMucOptions().getJoinJid());
			Element x = new Element("x");
			x.setAttribute("xmlns", "http://jabber.org/protocol/muc");
			String sig = account.getPgpSignature();
			if (sig != null) {
				packet.addChild("status").setContent("online");
				packet.addChild("x", "jabber:x:signed").setContent(sig);
			}
			if (conversation.getMessages().size() != 0) {
				final SimpleDateFormat mDateFormat = new SimpleDateFormat(
						"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
				mDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
				Date date = new Date(
						conversation.getLatestMessage().getTimeSent() + 1000);
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

	public void setOnRenameListener(OnRenameListener listener) {
		this.renameListener = listener;
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
						conversation.setContactJid(conversation.getMucOptions().getJoinJid());
						databaseBackend.updateConversation(conversation);
						Bookmark bookmark = conversation.getBookmark();
						if (bookmark!=null) {
							bookmark.setNick(nick);
							pushBookmarks(bookmark.getAccount());
						}
					}
				}
			});
			options.flagAboutToRename();
			PresencePacket packet = new PresencePacket();
			packet.setAttribute("to",options.getJoinJid());
			packet.setAttribute("from", conversation.getAccount().getFullJid());

			String sig = account.getPgpSignature();
			if (sig != null) {
				packet.addChild("status").setContent("online");
				packet.addChild("x", "jabber:x:signed").setContent(sig);
			}
			sendPresencePacket(account,packet);
		} else {
			conversation.setContactJid(options.getJoinJid());
			databaseBackend.updateConversation(conversation);
			if (conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
				Bookmark bookmark = conversation.getBookmark();
				if (bookmark!=null) {
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
			sendPresencePacket(conversation.getAccount(),packet);
			conversation.getMucOptions().setOffline();
			conversation.deregisterWithBookmark();
			Log.d(LOGTAG,conversation.getAccount().getJid()+" leaving muc "+conversation.getContactJid());
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
							conversation.endOtrIfNeeded();
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
	}

	protected void syncDirtyContacts(Account account) {
		for (Contact contact : account.getRoster().getContacts()) {
			if (contact.getOption(Contact.Options.DIRTY_PUSH)) {
				pushContactToServer(contact);
			}
			if (contact.getOption(Contact.Options.DIRTY_DELETE)) {
				Log.d(LOGTAG, "dirty delete");
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
		Log.d(LOGTAG, account.getJid() + " otr session established with "
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
						sendMessagePacket(account,outPacket);
					}
				} else if (msg.getType() == Message.TYPE_IMAGE) {
					mJingleConnectionManager.createNewConnection(msg);
				}
			}
		}
		notifyUi(conversation, false);
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
				sendMessagePacket(account,packet);
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
			boolean sendUpdates = contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)
					&& contact.getOption(Contact.Options.PREEMPTIVE_GRANT);
			IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
			iq.query("jabber:iq:roster").addChild(contact.asElement());
			account.getXmppConnection().sendIqPacket(iq, null);
			if (sendUpdates) {
				sendPresencePacket(account, mPresenceGenerator.sendPresenceUpdatesTo(contact));
			}
			if (ask) {
				sendPresencePacket(account, mPresenceGenerator.requestPresenceUpdatesFrom(contact));
			}
		}
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

	public void removeOnTLSExceptionReceivedListener() {
		this.tlsException = null;
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
					scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2), false);
				}
			}
		}).start();
	}

	public void invite(Conversation conversation, String contact) {
		MessagePacket packet = mMessageGenerator.invite(conversation, contact);
		sendMessagePacket(conversation.getAccount(),packet);
	}

	public boolean markMessage(Account account, String recipient, String uuid,
			int status) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getContactJid().equals(recipient)
					&& conversation.getAccount().equals(account)) {
				return markMessage(conversation, uuid, status);
			}
		}
		return false;
	}

	public boolean markMessage(Conversation conversation, String uuid,
			int status) {
		for (Message message : conversation.getMessages()) {
			if (message.getUuid().equals(uuid)) {
				markMessage(message, status);
				return true;
			}
		}
		return false;
	}

	public void markMessage(Message message, int status) {
		message.setStatus(status);
		databaseBackend.updateMessage(message);
		updateConversationUi();
	}

	public SharedPreferences getPreferences() {
		return PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
	}

	public boolean confirmMessages() {
		return getPreferences().getBoolean("confirm_messages", true);
	}

	public void notifyUi(Conversation conversation, boolean notify) {
		if (mOnConversationUpdate != null) {
			mOnConversationUpdate.onConversationUpdate();
		} else {
			UIHelper.updateNotification(getApplicationContext(),
					getConversations(), conversation, notify);
		}
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

	public void markRead(Conversation conversation) {
		conversation.markRead();
		String id = conversation.popLatestMarkableMessageId();
		if (confirmMessages() && id != null) {
			Account account = conversation.getAccount();
			String to = conversation.getContactJid();
			this.sendMessagePacket(conversation.getAccount(), mMessageGenerator.confirm(account, to, id));
		}
	}

	public SecureRandom getRNG() {
		return this.mRandom;
	}

	public PowerManager getPowerManager() {
		return this.pm;
	}

	public void replyWithNotAcceptable(Account account, MessagePacket packet) {
		if (account.getStatus() == Account.STATUS_ONLINE) {
			MessagePacket error = this.mMessageGenerator
					.generateNotAcceptable(packet);
			sendMessagePacket(account,error);
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
				if (server != null) {
					mucServers.add(server);
				}
			}
		}
		return mucServers;
	}
	
	public void sendMessagePacket(Account account, MessagePacket packet) {
		account.getXmppConnection().sendMessagePacket(packet);
	}
	
	public void sendPresencePacket(Account account, PresencePacket packet) {
		account.getXmppConnection().sendPresencePacket(packet);
	}
	
	public void sendIqPacket(Account account, IqPacket packet, OnIqPacketReceived callback) {
		account.getXmppConnection().sendIqPacket(packet, callback);
	}
	
	public MessageGenerator getMessageGenerator() {
		return this.mMessageGenerator;
	}
	
	public PresenceGenerator getPresenceGenerator() {
		return this.mPresenceGenerator;
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
}
