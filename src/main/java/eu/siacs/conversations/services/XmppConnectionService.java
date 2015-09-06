package eu.siacs.conversations.services;

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
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;
import android.util.LruCache;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import de.duenndns.ssl.MemorizingTrustManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.entities.TransferablePlaceholder;
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
import eu.siacs.conversations.utils.SerialSingleThreadExecutor;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnBindListener;
import eu.siacs.conversations.xmpp.OnContactStatusChanged;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.OnMessageAcknowledged;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.OnStatusChanged;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.OnJinglePacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import me.leolin.shortcutbadger.ShortcutBadger;

public class XmppConnectionService extends Service implements OnPhoneContactsLoadedListener {

	public static final String ACTION_CLEAR_NOTIFICATION = "clear_notification";
	public static final String ACTION_DISABLE_FOREGROUND = "disable_foreground";
	private static final String ACTION_MERGE_PHONE_CONTACTS = "merge_phone_contacts";
	public static final String ACTION_TRY_AGAIN = "try_again";
	public static final String ACTION_DISABLE_ACCOUNT = "disable_account";
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

	private final SerialSingleThreadExecutor mFileAddingExecutor = new SerialSingleThreadExecutor();
	private final SerialSingleThreadExecutor mDatabaseExecutor = new SerialSingleThreadExecutor();

	private final IBinder mBinder = new XmppConnectionBinder();
	private final List<Conversation> conversations = new CopyOnWriteArrayList<>();
	private final FileObserver fileObserver = new FileObserver(
			FileBackend.getConversationsImageDirectory()) {

		@Override
		public void onEvent(int event, String path) {
			if (event == FileObserver.DELETE) {
				markFileDeleted(path.split("\\.")[0]);
			}
		}
	};
	private final OnJinglePacketReceived jingleListener = new OnJinglePacketReceived() {

		@Override
		public void onJinglePacketReceived(Account account, JinglePacket packet) {
			mJingleConnectionManager.deliverPacket(account, packet);
		}
	};
	private final OnBindListener mOnBindListener = new OnBindListener() {

		@Override
		public void onBind(final Account account) {
			account.getRoster().clearPresences();
			fetchRosterFromServer(account);
			fetchBookmarks(account);
			sendPresence(account);
			connectMultiModeConversations(account);
			for (Conversation conversation : account.pendingConferenceLeaves) {
				leaveMuc(conversation);
			}
			account.pendingConferenceLeaves.clear();
			for (Conversation conversation : account.pendingConferenceJoins) {
				joinMuc(conversation);
			}
			account.pendingConferenceJoins.clear();
			mMessageArchiveService.executePendingQueries(account);
			mJingleConnectionManager.cancelInTransmission();
			syncDirtyContacts(account);
			account.getAxolotlService().publishBundlesIfNeeded(true);
		}
	};
	private final OnMessageAcknowledged mOnMessageAcknowledgedListener = new OnMessageAcknowledged() {

		@Override
		public void onMessageAcknowledged(Account account, String uuid) {
			for (final Conversation conversation : getConversations()) {
				if (conversation.getAccount() == account) {
					Message message = conversation.findUnsentMessageWithUuid(uuid);
					if (message != null) {
						markMessage(message, Message.STATUS_SEND);
						if (conversation.setLastMessageTransmitted(System.currentTimeMillis())) {
							databaseBackend.updateConversation(conversation);
						}
					}
				}
			}
		}
	};
	private final IqGenerator mIqGenerator = new IqGenerator(this);
	public DatabaseBackend databaseBackend;
	public OnContactStatusChanged onContactStatusChanged = new OnContactStatusChanged() {

		@Override
		public void onContactStatusChanged(Contact contact, boolean online) {
			Conversation conversation = find(getConversations(), contact);
			if (conversation != null) {
				if (online) {
					conversation.endOtrIfNeeded();
					if (contact.getPresences().size() == 1) {
						sendUnsentMessages(conversation);
					}
				} else {
					if (contact.getPresences().size() >= 1) {
						if (conversation.hasValidOtrSession()) {
							String otrResource = conversation.getOtrSession().getSessionID().getUserID();
							if (!(Arrays.asList(contact.getPresences().asStringArray()).contains(otrResource))) {
								conversation.endOtrIfNeeded();
							}
						}
					} else {
						conversation.endOtrIfNeeded();
					}
				}
			}
		}
	};
	private FileBackend fileBackend = new FileBackend(this);
	private MemorizingTrustManager mMemorizingTrustManager;
	private NotificationService mNotificationService = new NotificationService(
			this);
	private OnMessagePacketReceived mMessageParser = new MessageParser(this);
	private OnPresencePacketReceived mPresenceParser = new PresenceParser(this);
	private IqParser mIqParser = new IqParser(this);
	private OnIqPacketReceived mDefaultIqHandler = new OnIqPacketReceived() {
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.getType() != IqPacket.TYPE.RESULT) {
				Element error = packet.findChild("error");
				String text = error != null ? error.findChildContent("text") : null;
				if (text != null) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": received iq error - "+text);
				}
			}
		}
	};
	private MessageGenerator mMessageGenerator = new MessageGenerator(this);
	private PresenceGenerator mPresenceGenerator = new PresenceGenerator(this);
	private List<Account> accounts;
	private JingleConnectionManager mJingleConnectionManager = new JingleConnectionManager(
			this);
	private HttpConnectionManager mHttpConnectionManager = new HttpConnectionManager(
			this);
	private AvatarService mAvatarService = new AvatarService(this);
	private final List<String> mInProgressAvatarFetches = new ArrayList<>();
	private MessageArchiveService mMessageArchiveService = new MessageArchiveService(this);
	private OnConversationUpdate mOnConversationUpdate = null;
	private int convChangedListenerCount = 0;
	private OnShowErrorToast mOnShowErrorToast = null;
	private int showErrorToastListenerCount = 0;
	private int unreadCount = -1;
	private OnAccountUpdate mOnAccountUpdate = null;
	private OnStatusChanged statusListener = new OnStatusChanged() {

		@Override
		public void onStatusChanged(Account account) {
			XmppConnection connection = account.getXmppConnection();
			if (mOnAccountUpdate != null) {
				mOnAccountUpdate.onAccountUpdate();
			}
			if (account.getStatus() == Account.State.ONLINE) {
				if (connection != null && connection.getFeatures().csi()) {
					if (checkListeners()) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid()+ " sending csi//inactive");
						connection.sendInactive();
					} else {
						Log.d(Config.LOGTAG, account.getJid().toBareJid()+ " sending csi//active");
						connection.sendActive();
					}
				}
				List<Conversation> conversations = getConversations();
				for (Conversation conversation : conversations) {
					if (conversation.getAccount() == account) {
						conversation.startOtrIfNeeded();
						sendUnsentMessages(conversation);
					}
				}
				scheduleWakeUpCall(Config.PING_MAX_INTERVAL, account.getUuid().hashCode());
			} else if (account.getStatus() == Account.State.OFFLINE) {
				resetSendingToWaiting(account);
				if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					int timeToReconnect = mRandom.nextInt(20) + 10;
					scheduleWakeUpCall(timeToReconnect,account.getUuid().hashCode());
				}
			} else if (account.getStatus() == Account.State.REGISTRATION_SUCCESSFUL) {
				databaseBackend.updateAccount(account);
				reconnectAccount(account, true);
			} else if ((account.getStatus() != Account.State.CONNECTING)
					&& (account.getStatus() != Account.State.NO_INTERNET)) {
				if (connection != null) {
					int next = connection.getTimeToNextAttempt();
					Log.d(Config.LOGTAG, account.getJid().toBareJid()
							+ ": error connecting account. try again in "
							+ next + "s for the "
							+ (connection.getAttempt() + 1) + " time");
					scheduleWakeUpCall(next,account.getUuid().hashCode());
				}
					}
			getNotificationService().updateErrorNotification();
		}
	};
	private int accountChangedListenerCount = 0;
	private OnRosterUpdate mOnRosterUpdate = null;
	private OnUpdateBlocklist mOnUpdateBlocklist = null;
	private int updateBlocklistListenerCount = 0;
	private int rosterChangedListenerCount = 0;
	private OnMucRosterUpdate mOnMucRosterUpdate = null;
	private int mucRosterChangedListenerCount = 0;
	private OnKeyStatusUpdated mOnKeyStatusUpdated = null;
	private int keyStatusUpdatedListenerCount = 0;
	private SecureRandom mRandom;
	private OpenPgpServiceConnection pgpServiceConnection;
	private PgpEngine mPgpEngine = null;
	private WakeLock wakeLock;
	private PowerManager pm;
	private LruCache<String, Bitmap> mBitmapCache;
	private Thread mPhoneContactMergerThread;

	private boolean mRestoredFromDatabase = false;
	public boolean areMessagesInitialized() {
		return this.mRestoredFromDatabase;
	}

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

	public AvatarService getAvatarService() {
		return this.mAvatarService;
	}

	public void attachLocationToConversation(final Conversation conversation,
											 final Uri uri,
											 final UiCallback<Message> callback) {
		int encryption = conversation.getNextEncryption();
		if (encryption == Message.ENCRYPTION_PGP) {
			encryption = Message.ENCRYPTION_DECRYPTED;
		}
		Message message = new Message(conversation,uri.toString(),encryption);
		if (conversation.getNextCounterpart() != null) {
			message.setCounterpart(conversation.getNextCounterpart());
		}
		if (encryption == Message.ENCRYPTION_DECRYPTED) {
			getPgpEngine().encrypt(message, callback);
		} else {
			callback.success(message);
		}
	}

	public void attachFileToConversation(final Conversation conversation,
			final Uri uri,
			final UiCallback<Message> callback) {
		final Message message;
		if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
			message = new Message(conversation, "", Message.ENCRYPTION_DECRYPTED);
		} else {
			message = new Message(conversation, "", conversation.getNextEncryption());
		}
		message.setCounterpart(conversation.getNextCounterpart());
		message.setType(Message.TYPE_FILE);
		String path = getFileBackend().getOriginalPath(uri);
		if (path!=null) {
			message.setRelativeFilePath(path);
			getFileBackend().updateFileParams(message);
			if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
				getPgpEngine().encrypt(message, callback);
			} else {
				callback.success(message);
			}
		} else {
			mFileAddingExecutor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						getFileBackend().copyFileToPrivateStorage(message, uri);
						getFileBackend().updateFileParams(message);
						if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
							getPgpEngine().encrypt(message, callback);
						} else {
							callback.success(message);
						}
					} catch (FileBackend.FileCopyException e) {
						callback.error(e.getResId(), message);
					}
				}
			});
		}
	}

	public void attachImageToConversation(final Conversation conversation, final Uri uri, final UiCallback<Message> callback) {
		if (getFileBackend().useImageAsIs(uri)) {
			Log.d(Config.LOGTAG,"using image as is");
			attachFileToConversation(conversation, uri, callback);
			return;
		}
		final Message message;
		if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
			message = new Message(conversation, "", Message.ENCRYPTION_DECRYPTED);
		} else {
			message = new Message(conversation, "",conversation.getNextEncryption());
		}
		message.setCounterpart(conversation.getNextCounterpart());
		message.setType(Message.TYPE_IMAGE);
		mFileAddingExecutor.execute(new Runnable() {

			@Override
			public void run() {
				try {
					getFileBackend().copyImageToPrivateStorage(message, uri);
					if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
						getPgpEngine().encrypt(message, callback);
					} else {
						callback.success(message);
					}
				} catch (final FileBackend.FileCopyException e) {
					callback.error(e.getResId(), message);
				}
			}
		});
	}

	public Conversation find(Bookmark bookmark) {
		return find(bookmark.getAccount(), bookmark.getJid());
	}

	public Conversation find(final Account account, final Jid jid) {
		return find(getConversations(), account, jid);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String action = intent == null ? null : intent.getAction();
		if (action != null) {
			switch (action) {
				case ConnectivityManager.CONNECTIVITY_ACTION:
					if (hasInternetConnection() && Config.RESET_ATTEMPT_COUNT_ON_NETWORK_CHANGE) {
						resetAllAttemptCounts(true);
					}
					break;
				case ACTION_MERGE_PHONE_CONTACTS:
					if (mRestoredFromDatabase) {
						PhoneHelper.loadPhoneContacts(getApplicationContext(),
								new CopyOnWriteArrayList<Bundle>(),
								this);
					}
					return START_STICKY;
				case Intent.ACTION_SHUTDOWN:
					logoutAndSave();
					return START_NOT_STICKY;
				case ACTION_CLEAR_NOTIFICATION:
					mNotificationService.clear();
					break;
				case ACTION_DISABLE_FOREGROUND:
					getPreferences().edit().putBoolean("keep_foreground_service",false).commit();
					toggleForegroundService();
					break;
				case ACTION_TRY_AGAIN:
					resetAllAttemptCounts(false);
					break;
				case ACTION_DISABLE_ACCOUNT:
					try {
						String jid = intent.getStringExtra("account");
						Account account = jid == null ? null : findAccountByJid(Jid.fromString(jid));
						if (account != null) {
							account.setOption(Account.OPTION_DISABLED,true);
							updateAccount(account);
						}
					} catch (final InvalidJidException ignored) {
						break;
					}
					break;
			}
		}
		this.wakeLock.acquire();

		for (Account account : accounts) {
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {
				if (!hasInternetConnection()) {
					account.setStatus(Account.State.NO_INTERNET);
					if (statusListener != null) {
						statusListener.onStatusChanged(account);
					}
				} else {
					if (account.getStatus() == Account.State.NO_INTERNET) {
						account.setStatus(Account.State.OFFLINE);
						if (statusListener != null) {
							statusListener.onStatusChanged(account);
						}
					}
					if (account.getStatus() == Account.State.ONLINE) {
						long lastReceived = account.getXmppConnection().getLastPacketReceived();
						long lastSent = account.getXmppConnection().getLastPingSent();
						long pingInterval = "ui".equals(action) ? Config.PING_MIN_INTERVAL * 1000 : Config.PING_MAX_INTERVAL * 1000;
						long msToNextPing = (Math.max(lastReceived,lastSent) + pingInterval) - SystemClock.elapsedRealtime();
						long pingTimeoutIn = (lastSent + Config.PING_TIMEOUT * 1000) - SystemClock.elapsedRealtime();
						if (lastSent > lastReceived) {
							if (pingTimeoutIn < 0) {
								Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": ping timeout");
								this.reconnectAccount(account, true);
							} else {
								int secs = (int) (pingTimeoutIn / 1000);
								this.scheduleWakeUpCall(secs,account.getUuid().hashCode());
							}
						} else if (msToNextPing <= 0) {
							account.getXmppConnection().sendPing();
							Log.d(Config.LOGTAG, account.getJid().toBareJid()+" send ping");
							this.scheduleWakeUpCall(Config.PING_TIMEOUT,account.getUuid().hashCode());
						} else {
							this.scheduleWakeUpCall((int) (msToNextPing / 1000), account.getUuid().hashCode());
						}
					} else if (account.getStatus() == Account.State.OFFLINE) {
						reconnectAccount(account,true);
					} else if (account.getStatus() == Account.State.CONNECTING) {
						long timeout = Config.CONNECT_TIMEOUT - ((SystemClock.elapsedRealtime() - account.getXmppConnection().getLastConnect()) / 1000);
						if (timeout < 0) {
							Log.d(Config.LOGTAG, account.getJid() + ": time out during connect reconnecting");
							reconnectAccount(account, true);
						} else {
							scheduleWakeUpCall((int) timeout,account.getUuid().hashCode());
						}
					} else {
						if (account.getXmppConnection().getTimeToNextAttempt() <= 0) {
							reconnectAccount(account, true);
						}
					}

				}
				if (mOnAccountUpdate != null) {
					mOnAccountUpdate.onAccountUpdate();
				}
			}
		}
		/*PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
			if (!pm.isScreenOn()) {
			removeStaleListeners();
			}*/
		if (wakeLock.isHeld()) {
			try {
				wakeLock.release();
			} catch (final RuntimeException ignored) {
			}
		}
		return START_STICKY;
	}

	private void resetAllAttemptCounts(boolean reallyAll) {
		Log.d(Config.LOGTAG,"resetting all attepmt counts");
		for(Account account : accounts) {
			if (account.hasErrorStatus() || reallyAll) {
				final XmppConnection connection = account.getXmppConnection();
				if (connection != null) {
					connection.resetAttemptCount();
				}
			}
		}
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
		updateMemorizingTrustmanager();
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
		final int cacheSize = maxMemory / 8;
		this.mBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(final String key, final Bitmap bitmap) {
				return bitmap.getByteCount() / 1024;
			}
		};

		this.databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
		this.accounts = databaseBackend.getAccounts();

		restoreFromDatabase();

		getContentResolver().registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactObserver);
		this.fileObserver.startWatching();
		this.pgpServiceConnection = new OpenPgpServiceConnection(getApplicationContext(), "org.sufficientlysecure.keychain");
		this.pgpServiceConnection.bindToService();

		this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"XmppConnectionService");
		toggleForegroundService();
		updateUnreadCountBadge();
	}

	public void toggleForegroundService() {
		if (getPreferences().getBoolean("keep_foreground_service",false)) {
			startForeground(NotificationService.FOREGROUND_NOTIFICATION_ID, this.mNotificationService.createForegroundNotification());
		} else {
			stopForeground(true);
		}
	}

	@Override
	public void onTaskRemoved(final Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		if (!getPreferences().getBoolean("keep_foreground_service",false)) {
			this.logoutAndSave();
		}
	}

	private void logoutAndSave() {
		for (final Account account : accounts) {
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

	protected void scheduleWakeUpCall(int seconds, int requestCode) {
		final long timeToWake = SystemClock.elapsedRealtime() + (seconds < 0 ? 1 : seconds + 1) * 1000;

		Context context = getApplicationContext();
		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		Intent intent = new Intent(context, EventReceiver.class);
		intent.setAction("ping");
		PendingIntent alarmIntent = PendingIntent.getBroadcast(context, requestCode, intent, 0);
		alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, alarmIntent);
	}

	public XmppConnection createConnection(final Account account) {
		final SharedPreferences sharedPref = getPreferences();
		account.setResource(sharedPref.getString("resource", "mobile")
				.toLowerCase(Locale.getDefault()));
		final XmppConnection connection = new XmppConnection(account, this);
		connection.setOnMessagePacketReceivedListener(this.mMessageParser);
		connection.setOnStatusChangedListener(this.statusListener);
		connection.setOnPresencePacketReceivedListener(this.mPresenceParser);
		connection.setOnUnregisteredIqPacketReceivedListener(this.mIqParser);
		connection.setOnJinglePacketReceivedListener(this.jingleListener);
		connection.setOnBindListener(this.mOnBindListener);
		connection.setOnMessageAcknowledgeListener(this.mOnMessageAcknowledgedListener);
		connection.addOnAdvancedStreamFeaturesAvailableListener(this.mMessageArchiveService);
		return connection;
	}

	public void sendChatState(Conversation conversation) {
		if (sendChatStates()) {
			MessagePacket packet = mMessageGenerator.generateChatState(conversation);
			sendMessagePacket(conversation.getAccount(), packet);
		}
	}

	private void sendFileMessage(final Message message, final boolean delay) {
		Log.d(Config.LOGTAG, "send file message");
		final Account account = message.getConversation().getAccount();
		final XmppConnection connection = account.getXmppConnection();
		if (connection != null && connection.getFeatures().httpUpload()) {
			mHttpConnectionManager.createNewUploadConnection(message, delay);
		} else {
			mJingleConnectionManager.createNewConnection(message);
		}
	}

	public void sendMessage(final Message message) {
		sendMessage(message, false, false);
	}

	private void sendMessage(final Message message, final boolean resend, final boolean delay) {
		final Account account = message.getConversation().getAccount();
		final Conversation conversation = message.getConversation();
		account.deactivateGracePeriod();
		MessagePacket packet = null;
		boolean saveInDb = true;
		message.setStatus(Message.STATUS_WAITING);

		if (!resend && message.getEncryption() != Message.ENCRYPTION_OTR) {
			message.getConversation().endOtrIfNeeded();
			message.getConversation().findUnsentMessagesWithEncryption(Message.ENCRYPTION_OTR,
					new Conversation.OnMessageFound() {
				@Override
				public void onMessageFound(Message message) {
					markMessage(message,Message.STATUS_SEND_FAILED);
				}
			});
		}

		if (account.isOnlineAndConnected()) {
			switch (message.getEncryption()) {
				case Message.ENCRYPTION_NONE:
					if (message.needsUploading()) {
						if (account.httpUploadAvailable() || message.fixCounterpart()) {
							this.sendFileMessage(message,delay);
						} else {
							break;
						}
					} else {
						packet = mMessageGenerator.generateChat(message);
					}
					break;
				case Message.ENCRYPTION_PGP:
				case Message.ENCRYPTION_DECRYPTED:
					if (message.needsUploading()) {
						if (account.httpUploadAvailable() || message.fixCounterpart()) {
							this.sendFileMessage(message,delay);
						} else {
							break;
						}
					} else {
						packet = mMessageGenerator.generatePgpChat(message);
					}
					break;
				case Message.ENCRYPTION_OTR:
					SessionImpl otrSession = conversation.getOtrSession();
					if (otrSession != null && otrSession.getSessionStatus() == SessionStatus.ENCRYPTED) {
						try {
							message.setCounterpart(Jid.fromSessionID(otrSession.getSessionID()));
						} catch (InvalidJidException e) {
							break;
						}
						if (message.needsUploading()) {
							mJingleConnectionManager.createNewConnection(message);
						} else {
							packet = mMessageGenerator.generateOtrChat(message);
						}
					} else if (otrSession == null) {
						if (message.fixCounterpart()) {
							conversation.startOtrSession(message.getCounterpart().getResourcepart(), true);
						} else {
							break;
						}
					}
					break;
				case Message.ENCRYPTION_AXOLOTL:
					message.setAxolotlFingerprint(account.getAxolotlService().getOwnFingerprint());
					if (message.needsUploading()) {
						if (account.httpUploadAvailable() || message.fixCounterpart()) {
							this.sendFileMessage(message,delay);
						} else {
							break;
						}
					} else {
						XmppAxolotlMessage axolotlMessage = account.getAxolotlService().fetchAxolotlMessageFromCache(message);
						if (axolotlMessage == null) {
							account.getAxolotlService().preparePayloadMessage(message, delay);
						} else {
							packet = mMessageGenerator.generateAxolotlChat(message, axolotlMessage);
						}
					}
					break;

			}
			if (packet != null) {
				if (account.getXmppConnection().getFeatures().sm() || conversation.getMode() == Conversation.MODE_MULTI) {
					message.setStatus(Message.STATUS_UNSEND);
				} else {
					message.setStatus(Message.STATUS_SEND);
				}
			}
		} else {
			switch(message.getEncryption()) {
				case Message.ENCRYPTION_DECRYPTED:
					if (!message.needsUploading()) {
						String pgpBody = message.getEncryptedBody();
						String decryptedBody = message.getBody();
						message.setBody(pgpBody);
						message.setEncryption(Message.ENCRYPTION_PGP);
						databaseBackend.createMessage(message);
						saveInDb = false;
						message.setBody(decryptedBody);
						message.setEncryption(Message.ENCRYPTION_DECRYPTED);
					}
					break;
				case Message.ENCRYPTION_OTR:
					if (!conversation.hasValidOtrSession() && message.getCounterpart() != null) {
						conversation.startOtrSession(message.getCounterpart().getResourcepart(), false);
					}
					break;
				case Message.ENCRYPTION_AXOLOTL:
					message.setAxolotlFingerprint(account.getAxolotlService().getOwnFingerprint());
					break;
			}
		}

		if (resend) {
			if (packet != null) {
				if (account.getXmppConnection().getFeatures().sm() || conversation.getMode() == Conversation.MODE_MULTI) {
					markMessage(message,Message.STATUS_UNSEND);
				} else {
					markMessage(message,Message.STATUS_SEND);
				}
			}
		} else {
			conversation.add(message);
			if (saveInDb && (message.getEncryption() == Message.ENCRYPTION_NONE || saveEncryptedMessages())) {
				databaseBackend.createMessage(message);
			}
			updateConversationUi();
		}
		if (packet != null) {
			if (delay) {
				mMessageGenerator.addDelay(packet,message.getTimeSent());
			}
			if (conversation.setOutgoingChatState(Config.DEFAULT_CHATSTATE)) {
				if (this.sendChatStates()) {
					packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
				}
			}
			sendMessagePacket(account, packet);
		}
	}

	private void sendUnsentMessages(final Conversation conversation) {
		conversation.findWaitingMessages(new Conversation.OnMessageFound() {

			@Override
			public void onMessageFound(Message message) {
				resendMessage(message, true);
			}
		});
	}

	public void resendMessage(final Message message, final boolean delay) {
		sendMessage(message, true, delay);
	}

	public void fetchRosterFromServer(final Account account) {
		final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
		if (!"".equals(account.getRosterVersion())) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid()
					+ ": fetching roster version " + account.getRosterVersion());
		} else {
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": fetching roster");
		}
		iqPacket.query(Xmlns.ROSTER).setAttribute("ver", account.getRosterVersion());
		sendIqPacket(account, iqPacket, mIqParser);
	}

	public void fetchBookmarks(final Account account) {
		final IqPacket iqPacket = new IqPacket(IqPacket.TYPE.GET);
		final Element query = iqPacket.query("jabber:iq:private");
		query.addChild("storage", "storage:bookmarks");
		final OnIqPacketReceived callback = new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					final Element query = packet.query();
					final List<Bookmark> bookmarks = new CopyOnWriteArrayList<>();
					final Element storage = query.findChild("storage", "storage:bookmarks");
					if (storage != null) {
						for (final Element item : storage.getChildren()) {
							if (item.getName().equals("conference")) {
								final Bookmark bookmark = Bookmark.parse(item, account);
								bookmarks.add(bookmark);
								Conversation conversation = find(bookmark);
								if (conversation != null) {
									conversation.setBookmark(bookmark);
								} else if (bookmark.autojoin() && bookmark.getJid() != null) {
									conversation = findOrCreateConversation(
											account, bookmark.getJid(), true);
									conversation.setBookmark(bookmark);
									joinMuc(conversation);
								}
							}
						}
					}
					account.setBookmarks(bookmarks);
				} else {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": could not fetch bookmarks");
				}
			}
		};
		sendIqPacket(account, iqPacket, callback);
	}

	public void pushBookmarks(Account account) {
		IqPacket iqPacket = new IqPacket(IqPacket.TYPE.SET);
		Element query = iqPacket.query("jabber:iq:private");
		Element storage = query.addChild("storage", "storage:bookmarks");
		for (Bookmark bookmark : account.getBookmarks()) {
			storage.addChild(bookmark);
		}
		sendIqPacket(account, iqPacket, mDefaultIqHandler);
	}

	public void onPhoneContactsLoaded(final List<Bundle> phoneContacts) {
		if (mPhoneContactMergerThread != null) {
			mPhoneContactMergerThread.interrupt();
		}
		mPhoneContactMergerThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Log.d(Config.LOGTAG,"start merging phone contacts with roster");
				for (Account account : accounts) {
					List<Contact> withSystemAccounts = account.getRoster().getWithSystemAccounts();
					for (Bundle phoneContact : phoneContacts) {
						if (Thread.interrupted()) {
							Log.d(Config.LOGTAG,"interrupted merging phone contacts");
							return;
						}
						Jid jid;
						try {
							jid = Jid.fromString(phoneContact.getString("jid"));
						} catch (final InvalidJidException e) {
							continue;
						}
						final Contact contact = account.getRoster().getContact(jid);
						String systemAccount = phoneContact.getInt("phoneid")
							+ "#"
							+ phoneContact.getString("lookup");
						contact.setSystemAccount(systemAccount);
						if (contact.setPhotoUri(phoneContact.getString("photouri"))) {
							getAvatarService().clear(contact);
						}
						contact.setSystemName(phoneContact.getString("displayname"));
						withSystemAccounts.remove(contact);
					}
					for(Contact contact : withSystemAccounts) {
						contact.setSystemAccount(null);
						contact.setSystemName(null);
						if (contact.setPhotoUri(null)) {
							getAvatarService().clear(contact);
						}
					}
				}
				Log.d(Config.LOGTAG,"finished merging phone contacts");
				updateAccountUi();
			}
		});
		mPhoneContactMergerThread.start();
	}

	private void restoreFromDatabase() {
		synchronized (this.conversations) {
			final Map<String, Account> accountLookupTable = new Hashtable<>();
			for (Account account : this.accounts) {
				accountLookupTable.put(account.getUuid(), account);
			}
			this.conversations.addAll(databaseBackend.getConversations(Conversation.STATUS_AVAILABLE));
			for (Conversation conversation : this.conversations) {
				Account account = accountLookupTable.get(conversation.getAccountUuid());
				conversation.setAccount(account);
			}
			Runnable runnable =new Runnable() {
				@Override
				public void run() {
					Log.d(Config.LOGTAG,"restoring roster");
					for(Account account : accounts) {
						databaseBackend.readRoster(account.getRoster());
						account.initAccountServices(XmppConnectionService.this);
					}
					getBitmapCache().evictAll();
					Looper.prepare();
					PhoneHelper.loadPhoneContacts(getApplicationContext(),
							new CopyOnWriteArrayList<Bundle>(),
							XmppConnectionService.this);
					Log.d(Config.LOGTAG,"restoring messages");
					for (Conversation conversation : conversations) {
						conversation.addAll(0, databaseBackend.getMessages(conversation, Config.PAGE_SIZE));
						checkDeletedFiles(conversation);
					}
					mRestoredFromDatabase = true;
					Log.d(Config.LOGTAG,"restored all messages");
					updateConversationUi();
				}
			};
			mDatabaseExecutor.execute(runnable);
		}
	}

	public List<Conversation> getConversations() {
		return this.conversations;
	}

	private void checkDeletedFiles(Conversation conversation) {
		conversation.findMessagesWithFiles(new Conversation.OnMessageFound() {

			@Override
			public void onMessageFound(Message message) {
				if (!getFileBackend().isFileAvailable(message)) {
					message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_DELETED));
					final int s = message.getStatus();
					if(s == Message.STATUS_WAITING || s == Message.STATUS_OFFERED || s == Message.STATUS_UNSEND) {
						markMessage(message,Message.STATUS_SEND_FAILED);
					}
				}
			}
		});
	}

	private void markFileDeleted(String uuid) {
		for (Conversation conversation : getConversations()) {
			Message message = conversation.findMessageWithFileAndUuid(uuid);
			if (message != null) {
				if (!getFileBackend().isFileAvailable(message)) {
					message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_DELETED));
					final int s = message.getStatus();
					if(s == Message.STATUS_WAITING || s == Message.STATUS_OFFERED || s == Message.STATUS_UNSEND) {
						markMessage(message,Message.STATUS_SEND_FAILED);
					} else {
						updateConversationUi();
					}
				}
				return;
			}
		}
	}

	public void populateWithOrderedConversations(final List<Conversation> list) {
		populateWithOrderedConversations(list, true);
	}

	public void populateWithOrderedConversations(final List<Conversation> list, boolean includeNoFileUpload) {
		list.clear();
		if (includeNoFileUpload) {
			list.addAll(getConversations());
		} else {
			for (Conversation conversation : getConversations()) {
				if (conversation.getMode() == Conversation.MODE_SINGLE
						|| conversation.getAccount().httpUploadAvailable()) {
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

	public void loadMoreMessages(final Conversation conversation, final long timestamp, final OnMoreMessagesLoaded callback) {
		Log.d(Config.LOGTAG, "load more messages for " + conversation.getName() + " prior to " + MessageGenerator.getTimestamp(timestamp));
		if (XmppConnectionService.this.getMessageArchiveService().queryInProgress(conversation,callback)) {
			return;
		}
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				final Account account = conversation.getAccount();
				List<Message> messages = databaseBackend.getMessages(conversation, 50,timestamp);
				if (messages.size() > 0) {
					conversation.addAll(0, messages);
					checkDeletedFiles(conversation);
					callback.onMoreMessagesLoaded(messages.size(), conversation);
				} else if (conversation.hasMessagesLeftOnServer()
						&& account.isOnlineAndConnected()
						&& account.getXmppConnection().getFeatures().mam()) {
					MessageArchiveService.Query query = getMessageArchiveService().query(conversation,0,timestamp - 1);
					if (query != null) {
						query.setCallback(callback);
					}
					callback.informUser(R.string.fetching_history_from_server);
				}
			}
		};
		mDatabaseExecutor.execute(runnable);
	}

	public List<Account> getAccounts() {
		return this.accounts;
	}

	public Conversation find(final Iterable<Conversation> haystack, final Contact contact) {
		for (final Conversation conversation : haystack) {
			if (conversation.getContact() == contact) {
				return conversation;
			}
		}
		return null;
	}

	public Conversation find(final Iterable<Conversation> haystack, final Account account, final Jid jid) {
		if (jid == null) {
			return null;
		}
		for (final Conversation conversation : haystack) {
			if ((account == null || conversation.getAccount() == account)
					&& (conversation.getJid().toBareJid().equals(jid.toBareJid()))) {
				return conversation;
			}
		}
		return null;
	}

	public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc) {
		return this.findOrCreateConversation(account, jid, muc, null);
	}

	public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc, final MessageArchiveService.Query query) {
		synchronized (this.conversations) {
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
					conversation.setContactJid(jid);
				} else {
					conversation.setMode(Conversation.MODE_SINGLE);
					conversation.setContactJid(jid.toBareJid());
				}
				conversation.setNextEncryption(-1);
				conversation.addAll(0, databaseBackend.getMessages(conversation, Config.PAGE_SIZE));
				this.databaseBackend.updateConversation(conversation);
			} else {
				String conversationName;
				Contact contact = account.getRoster().getContact(jid);
				if (contact != null) {
					conversationName = contact.getDisplayName();
				} else {
					conversationName = jid.getLocalpart();
				}
				if (muc) {
					conversation = new Conversation(conversationName, account, jid,
							Conversation.MODE_MULTI);
				} else {
					conversation = new Conversation(conversationName, account, jid.toBareJid(),
							Conversation.MODE_SINGLE);
				}
				this.databaseBackend.createConversation(conversation);
			}
			if (account.getXmppConnection() != null
					&& account.getXmppConnection().getFeatures().mam()
					&& !muc) {
				if (query == null) {
					this.mMessageArchiveService.query(conversation);
				} else {
					if (query.getConversation() == null) {
						this.mMessageArchiveService.query(conversation, query.getStart());
					}
				}
			}
			checkDeletedFiles(conversation);
			this.conversations.add(conversation);
			updateConversationUi();
			return conversation;
		}
	}

	public void archiveConversation(Conversation conversation) {
		getNotificationService().clear(conversation);
		conversation.setStatus(Conversation.STATUS_ARCHIVED);
		conversation.setNextEncryption(-1);
		synchronized (this.conversations) {
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				if (conversation.getAccount().getStatus() == Account.State.ONLINE) {
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
	}

	public void createAccount(final Account account) {
		account.initAccountServices(this);
		databaseBackend.createAccount(account);
		this.accounts.add(account);
		this.reconnectAccountInBackground(account);
		updateAccountUi();
	}

	public void updateAccount(final Account account) {
		this.statusListener.onStatusChanged(account);
		databaseBackend.updateAccount(account);
		reconnectAccount(account, false);
		updateAccountUi();
		getNotificationService().updateErrorNotification();
	}

	public void updateAccountPasswordOnServer(final Account account, final String newPassword, final OnAccountPasswordChanged callback) {
		final IqPacket iq = getIqGenerator().generateSetPassword(account, newPassword);
		sendIqPacket(account, iq, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					account.setPassword(newPassword);
					databaseBackend.updateAccount(account);
					callback.onPasswordChangeSucceeded();
				} else {
					callback.onPasswordChangeFailed();
				}
			}
		});
	}

	public void deleteAccount(final Account account) {
		synchronized (this.conversations) {
			for (final Conversation conversation : conversations) {
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
			getNotificationService().updateErrorNotification();
		}
	}

	public void setOnConversationListChangedListener(OnConversationUpdate listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnConversationUpdate = listener;
			this.mNotificationService.setIsInForeground(true);
			if (this.convChangedListenerCount < 2) {
				this.convChangedListenerCount++;
			}
		}
	}

	public void removeOnConversationListChangedListener() {
		synchronized (this) {
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

	public void setOnShowErrorToastListener(OnShowErrorToast onShowErrorToast) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnShowErrorToast = onShowErrorToast;
			if (this.showErrorToastListenerCount < 2) {
				this.showErrorToastListenerCount++;
			}
		}
		this.mOnShowErrorToast = onShowErrorToast;
	}

	public void removeOnShowErrorToastListener() {
		synchronized (this) {
			this.showErrorToastListenerCount--;
			if (this.showErrorToastListenerCount <= 0) {
				this.showErrorToastListenerCount = 0;
				this.mOnShowErrorToast = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnAccountListChangedListener(OnAccountUpdate listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnAccountUpdate = listener;
			if (this.accountChangedListenerCount < 2) {
				this.accountChangedListenerCount++;
			}
		}
	}

	public void removeOnAccountListChangedListener() {
		synchronized (this) {
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

	public void setOnRosterUpdateListener(final OnRosterUpdate listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnRosterUpdate = listener;
			if (this.rosterChangedListenerCount < 2) {
				this.rosterChangedListenerCount++;
			}
		}
	}

	public void removeOnRosterUpdateListener() {
		synchronized (this) {
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

	public void setOnUpdateBlocklistListener(final OnUpdateBlocklist listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnUpdateBlocklist = listener;
			if (this.updateBlocklistListenerCount < 2) {
				this.updateBlocklistListenerCount++;
			}
		}
	}

	public void removeOnUpdateBlocklistListener() {
		synchronized (this) {
			this.updateBlocklistListenerCount--;
			if (this.updateBlocklistListenerCount <= 0) {
				this.updateBlocklistListenerCount = 0;
				this.mOnUpdateBlocklist = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnKeyStatusUpdatedListener(final OnKeyStatusUpdated listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnKeyStatusUpdated = listener;
			if (this.keyStatusUpdatedListenerCount < 2) {
				this.keyStatusUpdatedListenerCount++;
			}
		}
	}

	public void removeOnNewKeysAvailableListener() {
		synchronized (this) {
			this.keyStatusUpdatedListenerCount--;
			if (this.keyStatusUpdatedListenerCount <= 0) {
				this.keyStatusUpdatedListenerCount = 0;
				this.mOnKeyStatusUpdated = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	public void setOnMucRosterUpdateListener(OnMucRosterUpdate listener) {
		synchronized (this) {
			if (checkListeners()) {
				switchToForeground();
			}
			this.mOnMucRosterUpdate = listener;
			if (this.mucRosterChangedListenerCount < 2) {
				this.mucRosterChangedListenerCount++;
			}
		}
	}

	public void removeOnMucRosterUpdateListener() {
		synchronized (this) {
			this.mucRosterChangedListenerCount--;
			if (this.mucRosterChangedListenerCount <= 0) {
				this.mucRosterChangedListenerCount = 0;
				this.mOnMucRosterUpdate = null;
				if (checkListeners()) {
					switchToBackground();
				}
			}
		}
	}

	private boolean checkListeners() {
		return (this.mOnAccountUpdate == null
				&& this.mOnConversationUpdate == null
				&& this.mOnRosterUpdate == null
				&& this.mOnUpdateBlocklist == null
				&& this.mOnShowErrorToast == null
				&& this.mOnKeyStatusUpdated == null);
	}

	private void switchToForeground() {
		for (Account account : getAccounts()) {
			if (account.getStatus() == Account.State.ONLINE) {
				XmppConnection connection = account.getXmppConnection();
				if (connection != null && connection.getFeatures().csi()) {
					connection.sendActive();
				}
			}
		}
		Log.d(Config.LOGTAG, "app switched into foreground");
	}

	private void switchToBackground() {
		for (Account account : getAccounts()) {
			if (account.getStatus() == Account.State.ONLINE) {
				XmppConnection connection = account.getXmppConnection();
				if (connection != null && connection.getFeatures().csi()) {
					connection.sendInactive();
				}
			}
		}
		for(Conversation conversation : getConversations()) {
			conversation.setIncomingChatState(ChatState.ACTIVE);
		}
		this.mNotificationService.setIsInForeground(false);
		Log.d(Config.LOGTAG, "app switched into background");
	}

	private void connectMultiModeConversations(Account account) {
		List<Conversation> conversations = getConversations();
		for (Conversation conversation : conversations) {
			if ((conversation.getMode() == Conversation.MODE_MULTI)
					&& (conversation.getAccount() == account)) {
				conversation.resetMucOptions();
				joinMuc(conversation);
			}
		}
	}


	public void joinMuc(Conversation conversation) {
		Account account = conversation.getAccount();
		account.pendingConferenceJoins.remove(conversation);
		account.pendingConferenceLeaves.remove(conversation);
		if (account.getStatus() == Account.State.ONLINE) {
			final String nick = conversation.getMucOptions().getProposedNick();
			final Jid joinJid = conversation.getMucOptions().createJoinJid(nick);
			if (joinJid == null) {
				return; //safety net
			}
			Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": joining conversation " + joinJid.toString());
			PresencePacket packet = new PresencePacket();
			packet.setFrom(conversation.getAccount().getJid());
			packet.setTo(joinJid);
			Element x = packet.addChild("x", "http://jabber.org/protocol/muc");
			if (conversation.getMucOptions().getPassword() != null) {
				x.addChild("password").setContent(conversation.getMucOptions().getPassword());
			}
			x.addChild("history").setAttribute("since", PresenceGenerator.getTimestamp(conversation.getLastMessageTransmitted()));
			String sig = account.getPgpSignature();
			if (sig != null) {
				packet.addChild("status").setContent("online");
				packet.addChild("x", "jabber:x:signed").setContent(sig);
			}
			sendPresencePacket(account, packet);
			fetchConferenceConfiguration(conversation);
			if (!joinJid.equals(conversation.getJid())) {
				conversation.setContactJid(joinJid);
				databaseBackend.updateConversation(conversation);
			}
			conversation.setHasMessagesLeftOnServer(false);
		} else {
			account.pendingConferenceJoins.add(conversation);
		}
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

	public void renameInMuc(final Conversation conversation, final String nick, final UiCallback<Conversation> callback) {
		final MucOptions options = conversation.getMucOptions();
		final Jid joinJid = options.createJoinJid(nick);
		if (options.online()) {
			Account account = conversation.getAccount();
			options.setOnRenameListener(new OnRenameListener() {

				@Override
				public void onSuccess() {
					conversation.setContactJid(joinJid);
					databaseBackend.updateConversation(conversation);
					Bookmark bookmark = conversation.getBookmark();
					if (bookmark != null) {
						bookmark.setNick(nick);
						pushBookmarks(bookmark.getAccount());
					}
					callback.success(conversation);
				}

				@Override
				public void onFailure() {
					callback.error(R.string.nick_in_use, conversation);
				}
			});

			PresencePacket packet = new PresencePacket();
			packet.setTo(joinJid);
			packet.setFrom(conversation.getAccount().getJid());

			String sig = account.getPgpSignature();
			if (sig != null) {
				packet.addChild("status").setContent("online");
				packet.addChild("x", "jabber:x:signed").setContent(sig);
			}
			sendPresencePacket(account, packet);
		} else {
			conversation.setContactJid(joinJid);
			databaseBackend.updateConversation(conversation);
			if (conversation.getAccount().getStatus() == Account.State.ONLINE) {
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
		if (account.getStatus() == Account.State.ONLINE) {
			PresencePacket packet = new PresencePacket();
			packet.setTo(conversation.getJid());
			packet.setFrom(conversation.getAccount().getJid());
			packet.setAttribute("type", "unavailable");
			sendPresencePacket(conversation.getAccount(), packet);
			conversation.getMucOptions().setOffline();
			conversation.deregisterWithBookmark();
			Log.d(Config.LOGTAG, conversation.getAccount().getJid().toBareJid()
					+ ": leaving muc " + conversation.getJid());
		} else {
			account.pendingConferenceLeaves.add(conversation);
		}
	}

	private String findConferenceServer(final Account account) {
		String server;
		if (account.getXmppConnection() != null) {
			server = account.getXmppConnection().getMucServer();
			if (server != null) {
				return server;
			}
		}
		for (Account other : getAccounts()) {
			if (other != account && other.getXmppConnection() != null) {
				server = other.getXmppConnection().getMucServer();
				if (server != null) {
					return server;
				}
			}
		}
		return null;
	}

	public void createAdhocConference(final Account account, final Iterable<Jid> jids, final UiCallback<Conversation> callback) {
		Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": creating adhoc conference with " + jids.toString());
		if (account.getStatus() == Account.State.ONLINE) {
			try {
				String server = findConferenceServer(account);
				if (server == null) {
					if (callback != null) {
						callback.error(R.string.no_conference_server_found, null);
					}
					return;
				}
				String name = new BigInteger(75, getRNG()).toString(32);
				Jid jid = Jid.fromParts(name, server, null);
				final Conversation conversation = findOrCreateConversation(account, jid, true);
				joinMuc(conversation);
				Bundle options = new Bundle();
				options.putString("muc#roomconfig_persistentroom", "1");
				options.putString("muc#roomconfig_membersonly", "1");
				options.putString("muc#roomconfig_publicroom", "0");
				options.putString("muc#roomconfig_whois", "anyone");
				pushConferenceConfiguration(conversation, options, new OnConferenceOptionsPushed() {
					@Override
					public void onPushSucceeded() {
						for (Jid invite : jids) {
							invite(conversation, invite);
						}
						if (account.countPresences() > 1) {
							directInvite(conversation, account.getJid().toBareJid());
						}
						if (callback != null) {
							callback.success(conversation);
						}
					}

					@Override
					public void onPushFailed() {
						if (callback != null) {
							callback.error(R.string.conference_creation_failed, conversation);
						}
					}
				});

			} catch (InvalidJidException e) {
				if (callback != null) {
					callback.error(R.string.conference_creation_failed, null);
				}
			}
		} else {
			if (callback != null) {
				callback.error(R.string.not_connected_try_again, null);
			}
		}
	}

	public void fetchConferenceConfiguration(final Conversation conversation) {
		IqPacket request = new IqPacket(IqPacket.TYPE.GET);
		request.setTo(conversation.getJid().toBareJid());
		request.query("http://jabber.org/protocol/disco#info");
		sendIqPacket(conversation.getAccount(), request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					ArrayList<String> features = new ArrayList<>();
					for (Element child : packet.query().getChildren()) {
						if (child != null && child.getName().equals("feature")) {
							String var = child.getAttribute("var");
							if (var != null) {
								features.add(var);
							}
						}
					}
					conversation.getMucOptions().updateFeatures(features);
					updateConversationUi();
				}
			}
		});
	}

	public void pushConferenceConfiguration(final Conversation conversation, final Bundle options, final OnConferenceOptionsPushed callback) {
		IqPacket request = new IqPacket(IqPacket.TYPE.GET);
		request.setTo(conversation.getJid().toBareJid());
		request.query("http://jabber.org/protocol/muc#owner");
		sendIqPacket(conversation.getAccount(), request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Data data = Data.parse(packet.query().findChild("x", "jabber:x:data"));
					for (Field field : data.getFields()) {
						if (options.containsKey(field.getName())) {
							field.setValue(options.getString(field.getName()));
						}
					}
					data.submit();
					IqPacket set = new IqPacket(IqPacket.TYPE.SET);
					set.setTo(conversation.getJid().toBareJid());
					set.query("http://jabber.org/protocol/muc#owner").addChild(data);
					sendIqPacket(account, set, new OnIqPacketReceived() {
						@Override
						public void onIqPacketReceived(Account account, IqPacket packet) {
							if (callback != null) {
								if (packet.getType() == IqPacket.TYPE.RESULT) {
									callback.onPushSucceeded();
								} else {
									callback.onPushFailed();
								}
							}
						}
					});
				} else {
					if (callback != null) {
						callback.onPushFailed();
					}
				}
			}
		});
	}

	public void pushSubjectToConference(final Conversation conference, final String subject) {
		MessagePacket packet = this.getMessageGenerator().conferenceSubject(conference, subject);
		this.sendMessagePacket(conference.getAccount(), packet);
		final MucOptions mucOptions = conference.getMucOptions();
		final MucOptions.User self = mucOptions.getSelf();
		if (!mucOptions.persistent() && self.getAffiliation().ranks(MucOptions.Affiliation.OWNER)) {
			Bundle options = new Bundle();
			options.putString("muc#roomconfig_persistentroom", "1");
			this.pushConferenceConfiguration(conference, options, null);
		}
	}

	public void changeAffiliationInConference(final Conversation conference, Jid user, MucOptions.Affiliation affiliation, final OnAffiliationChanged callback) {
		final Jid jid = user.toBareJid();
		IqPacket request = this.mIqGenerator.changeAffiliation(conference, jid, affiliation.toString());
		sendIqPacket(conference.getAccount(), request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					callback.onAffiliationChangedSuccessful(jid);
				} else {
					callback.onAffiliationChangeFailed(jid, R.string.could_not_change_affiliation);
				}
			}
		});
	}

	public void changeAffiliationsInConference(final Conversation conference, MucOptions.Affiliation before, MucOptions.Affiliation after) {
		List<Jid> jids = new ArrayList<>();
		for (MucOptions.User user : conference.getMucOptions().getUsers()) {
			if (user.getAffiliation() == before && user.getJid() != null) {
				jids.add(user.getJid());
			}
		}
		IqPacket request = this.mIqGenerator.changeAffiliation(conference, jids, after.toString());
		sendIqPacket(conference.getAccount(), request, mDefaultIqHandler);
	}

	public void changeRoleInConference(final Conversation conference, final String nick, MucOptions.Role role, final OnRoleChanged callback) {
		IqPacket request = this.mIqGenerator.changeRole(conference, nick, role.toString());
		Log.d(Config.LOGTAG, request.toString());
		sendIqPacket(conference.getAccount(), request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				Log.d(Config.LOGTAG, packet.toString());
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					callback.onRoleChangedSuccessful(nick);
				} else {
					callback.onRoleChangeFailed(nick, R.string.could_not_change_role);
				}
			}
		});
	}

	public void disconnect(Account account, boolean force) {
		if ((account.getStatus() == Account.State.ONLINE)
				|| (account.getStatus() == Account.State.DISABLED)) {
			if (!force) {
				List<Conversation> conversations = getConversations();
				for (Conversation conversation : conversations) {
					if (conversation.getAccount() == account) {
						if (conversation.getMode() == Conversation.MODE_MULTI) {
							leaveMuc(conversation);
						} else {
							if (conversation.endOtrIfNeeded()) {
								Log.d(Config.LOGTAG, account.getJid().toBareJid()
										+ ": ended otr session with "
										+ conversation.getJid());
							}
						}
					}
				}
				sendOfflinePresence(account);
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
		final Account account = conversation.getAccount();
		final Session otrSession = conversation.getOtrSession();
		Log.d(Config.LOGTAG,
				account.getJid().toBareJid() + " otr session established with "
						+ conversation.getJid() + "/"
						+ otrSession.getSessionID().getUserID());
		conversation.findUnsentMessagesWithEncryption(Message.ENCRYPTION_OTR, new Conversation.OnMessageFound() {

			@Override
			public void onMessageFound(Message message) {
				SessionID id = otrSession.getSessionID();
				try {
					message.setCounterpart(Jid.fromString(id.getAccountID() + "/" + id.getUserID()));
				} catch (InvalidJidException e) {
					return;
				}
				if (message.needsUploading()) {
					mJingleConnectionManager.createNewConnection(message);
				} else {
					MessagePacket outPacket = mMessageGenerator.generateOtrChat(message);
					if (outPacket != null) {
						mMessageGenerator.addDelay(outPacket, message.getTimeSent());
						message.setStatus(Message.STATUS_SEND);
						databaseBackend.updateMessage(message);
						sendMessagePacket(account, outPacket);
					}
				}
				updateConversationUi();
			}
		});
	}

	public boolean renewSymmetricKey(Conversation conversation) {
		Account account = conversation.getAccount();
		byte[] symmetricKey = new byte[32];
		this.mRandom.nextBytes(symmetricKey);
		Session otrSession = conversation.getOtrSession();
		if (otrSession != null) {
			MessagePacket packet = new MessagePacket();
			packet.setType(MessagePacket.TYPE_CHAT);
			packet.setFrom(account.getJid());
			MessageGenerator.addMessageHints(packet);
			packet.setAttribute("to", otrSession.getSessionID().getAccountID() + "/"
					+ otrSession.getSessionID().getUserID());
			try {
				packet.setBody(otrSession
						.transformSending(CryptoHelper.FILETRANSFER
								+ CryptoHelper.bytesToHex(symmetricKey))[0]);
				sendMessagePacket(account, packet);
				conversation.setSymmetricKey(symmetricKey);
				return true;
			} catch (OtrException e) {
				return false;
			}
		}
		return false;
	}

	public void pushContactToServer(final Contact contact) {
		contact.resetOption(Contact.Options.DIRTY_DELETE);
		contact.setOption(Contact.Options.DIRTY_PUSH);
		final Account account = contact.getAccount();
		if (account.getStatus() == Account.State.ONLINE) {
			final boolean ask = contact.getOption(Contact.Options.ASKING);
			final boolean sendUpdates = contact
					.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)
					&& contact.getOption(Contact.Options.PREEMPTIVE_GRANT);
			final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
			iq.query(Xmlns.ROSTER).addChild(contact.asElement());
			account.getXmppConnection().sendIqPacket(iq, mDefaultIqHandler);
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

	public void publishAvatar(final Account account,
							  final Uri image,
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
			final IqPacket packet = this.mIqGenerator.publishAvatar(avatar);
			this.sendIqPacket(account, packet, new OnIqPacketReceived() {

				@Override
				public void onIqPacketReceived(Account account, IqPacket result) {
					if (result.getType() == IqPacket.TYPE.RESULT) {
						final IqPacket packet = XmppConnectionService.this.mIqGenerator
								.publishAvatarMetadata(avatar);
						sendIqPacket(account, packet, new OnIqPacketReceived() {
							@Override
							public void onIqPacketReceived(Account account, IqPacket result) {
								if (result.getType() == IqPacket.TYPE.RESULT) {
									if (account.setAvatar(avatar.getFilename())) {
										getAvatarService().clear(account);
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

	private static String generateFetchKey(Account account, final Avatar avatar) {
		return account.getJid().toBareJid()+"_"+avatar.owner+"_"+avatar.sha1sum;
	}

	public void fetchAvatar(Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
		final String KEY = generateFetchKey(account, avatar);
		synchronized(this.mInProgressAvatarFetches) {
			if (this.mInProgressAvatarFetches.contains(KEY)) {
				return;
			} else {
				switch (avatar.origin) {
					case PEP:
						this.mInProgressAvatarFetches.add(KEY);
						fetchAvatarPep(account, avatar, callback);
						break;
					case VCARD:
						this.mInProgressAvatarFetches.add(KEY);
						fetchAvatarVcard(account, avatar, callback);
						break;
				}
			}
		}
	}

	private void fetchAvatarPep(Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.retrievePepAvatar(avatar);
		sendIqPacket(account, packet, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket result) {
				synchronized (mInProgressAvatarFetches) {
					mInProgressAvatarFetches.remove(generateFetchKey(account, avatar));
				}
				final String ERROR = account.getJid().toBareJid()
						+ ": fetching avatar for " + avatar.owner + " failed ";
				if (result.getType() == IqPacket.TYPE.RESULT) {
					avatar.image = mIqParser.avatarData(result);
					if (avatar.image != null) {
						if (getFileBackend().save(avatar)) {
							if (account.getJid().toBareJid().equals(avatar.owner)) {
								if (account.setAvatar(avatar.getFilename())) {
									databaseBackend.updateAccount(account);
								}
								getAvatarService().clear(account);
								updateConversationUi();
								updateAccountUi();
							} else {
								Contact contact = account.getRoster()
										.getContact(avatar.owner);
								contact.setAvatar(avatar);
								getAvatarService().clear(contact);
								updateConversationUi();
								updateRosterUi();
							}
							if (callback != null) {
								callback.success(avatar);
							}
							Log.d(Config.LOGTAG, account.getJid().toBareJid()
									+ ": succesfuly fetched pep avatar for " + avatar.owner);
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

	private void fetchAvatarVcard(final Account account, final Avatar avatar, final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.retrieveVcardAvatar(avatar);
		this.sendIqPacket(account, packet, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				synchronized (mInProgressAvatarFetches) {
					mInProgressAvatarFetches.remove(generateFetchKey(account, avatar));
				}
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Element vCard = packet.findChild("vCard", "vcard-temp");
					Element photo = vCard != null ? vCard.findChild("PHOTO") : null;
					String image = photo != null ? photo.findChildContent("BINVAL") : null;
					if (image != null) {
						avatar.image = image;
						if (getFileBackend().save(avatar)) {
							Log.d(Config.LOGTAG, account.getJid().toBareJid()
									+ ": successfully fetched vCard avatar for " + avatar.owner);
							Contact contact = account.getRoster()
									.getContact(avatar.owner);
							contact.setAvatar(avatar);
							getAvatarService().clear(contact);
							updateConversationUi();
							updateRosterUi();
						}
					}
				}
			}
		});
	}

	public void checkForAvatar(Account account, final UiCallback<Avatar> callback) {
		IqPacket packet = this.mIqGenerator.retrieveAvatarMetaData(null);
		this.sendIqPacket(account, packet, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Element pubsub = packet.findChild("pubsub",
							"http://jabber.org/protocol/pubsub");
					if (pubsub != null) {
						Element items = pubsub.findChild("items");
						if (items != null) {
							Avatar avatar = Avatar.parseMetadata(items);
							if (avatar != null) {
								avatar.owner = account.getJid().toBareJid();
								if (fileBackend.isAvatarCached(avatar)) {
									if (account.setAvatar(avatar.getFilename())) {
										databaseBackend.updateAccount(account);
									}
									getAvatarService().clear(account);
									callback.success(avatar);
								} else {
									fetchAvatarPep(account, avatar, callback);
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
		if (account.getStatus() == Account.State.ONLINE) {
			IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
			Element item = iq.query(Xmlns.ROSTER).addChild("item");
			item.setAttribute("jid", contact.getJid().toString());
			item.setAttribute("subscription", "remove");
			account.getXmppConnection().sendIqPacket(iq, mDefaultIqHandler);
		}
	}

	public void updateConversation(Conversation conversation) {
		this.databaseBackend.updateConversation(conversation);
	}

	public void reconnectAccount(final Account account, final boolean force) {
		synchronized (account) {
			if (account.getXmppConnection() != null) {
				disconnect(account, force);
			}
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {

				synchronized (this.mInProgressAvatarFetches) {
					for(Iterator<String> iterator = this.mInProgressAvatarFetches.iterator(); iterator.hasNext();) {
						final String KEY = iterator.next();
						if (KEY.startsWith(account.getJid().toBareJid()+"_")) {
							iterator.remove();
						}
					}
				}

				if (account.getXmppConnection() == null) {
					account.setXmppConnection(createConnection(account));
				}
				Thread thread = new Thread(account.getXmppConnection());
				thread.start();
				scheduleWakeUpCall(Config.CONNECT_TIMEOUT, account.getUuid().hashCode());
			} else {
				account.getRoster().clearPresences();
				account.setXmppConnection(null);
			}
		}
	}

	public void reconnectAccountInBackground(final Account account) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				reconnectAccount(account,false);
			}
		}).start();
	}

	public void invite(Conversation conversation, Jid contact) {
		Log.d(Config.LOGTAG,conversation.getAccount().getJid().toBareJid()+": inviting "+contact+" to "+conversation.getJid().toBareJid());
		MessagePacket packet = mMessageGenerator.invite(conversation, contact);
		sendMessagePacket(conversation.getAccount(), packet);
	}

	public void directInvite(Conversation conversation, Jid jid) {
		MessagePacket packet = mMessageGenerator.directInvite(conversation, jid);
		sendMessagePacket(conversation.getAccount(),packet);
	}

	public void resetSendingToWaiting(Account account) {
		for (Conversation conversation : getConversations()) {
			if (conversation.getAccount() == account) {
				conversation.findUnsentTextMessages(new Conversation.OnMessageFound() {

					@Override
					public void onMessageFound(Message message) {
						markMessage(message, Message.STATUS_WAITING);
					}
				});
			}
		}
	}

	public Message markMessage(final Account account, final Jid recipient, final String uuid, final int status) {
		if (uuid == null) {
			return null;
		}
		for (Conversation conversation : getConversations()) {
			if (conversation.getJid().toBareJid().equals(recipient) && conversation.getAccount() == account) {
				final Message message = conversation.findSentMessageWithUuidOrRemoteId(uuid);
				if (message != null) {
					markMessage(message, status);
				}
				return message;
			}
		}
		return null;
	}

	public boolean markMessage(Conversation conversation, String uuid, int status) {
		if (uuid == null) {
			return false;
		} else {
			Message message = conversation.findSentMessageWithUuid(uuid);
			if (message != null) {
				markMessage(message, status);
				return true;
			} else {
				return false;
			}
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

	public boolean sendChatStates() {
		return getPreferences().getBoolean("chat_states", false);
	}

	public boolean saveEncryptedMessages() {
		return !getPreferences().getBoolean("dont_save_encrypted", false);
	}

	public boolean indicateReceived() {
		return getPreferences().getBoolean("indicate_received", false);
	}

	public int unreadCount() {
		int count = 0;
		for(Conversation conversation : getConversations()) {
			count += conversation.unreadCount();
		}
		return count;
	}


	public void showErrorToastInUi(int resId) {
		if (mOnShowErrorToast != null) {
			mOnShowErrorToast.onShowErrorToast(resId);
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

	public void updateBlocklistUi(final OnUpdateBlocklist.Status status) {
		if (mOnUpdateBlocklist != null) {
			mOnUpdateBlocklist.OnUpdateBlocklist(status);
		}
	}

	public void updateMucRosterUi() {
		if (mOnMucRosterUpdate != null) {
			mOnMucRosterUpdate.onMucRosterUpdate();
		}
	}

	public void keyStatusUpdated() {
		if(mOnKeyStatusUpdated != null) {
			mOnKeyStatusUpdated.onKeyStatusUpdated();
		}
	}

	public Account findAccountByJid(final Jid accountJid) {
		for (Account account : this.accounts) {
			if (account.getJid().toBareJid().equals(accountJid.toBareJid())) {
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

	public void markRead(final Conversation conversation) {
		mNotificationService.clear(conversation);
		conversation.markRead();
		updateUnreadCountBadge();
	}

	public synchronized void updateUnreadCountBadge() {
		int count = unreadCount();
		if (unreadCount != count) {
			Log.d(Config.LOGTAG, "update unread count to " + count);
			if (count > 0) {
				ShortcutBadger.with(getApplicationContext()).count(count);
			} else {
				ShortcutBadger.with(getApplicationContext()).remove();
			}
			unreadCount = count;
		}
	}

	public void sendReadMarker(final Conversation conversation) {
		final Message markable = conversation.getLatestMarkableMessage();
		this.markRead(conversation);
		if (confirmMessages() && markable != null && markable.getRemoteMsgId() != null) {
			Log.d(Config.LOGTAG, conversation.getAccount().getJid().toBareJid() + ": sending read marker to " + markable.getCounterpart().toString());
			Account account = conversation.getAccount();
			final Jid to = markable.getCounterpart();
			MessagePacket packet = mMessageGenerator.confirm(account, to, markable.getRemoteMsgId());
			this.sendMessagePacket(conversation.getAccount(), packet);
		}
		updateConversationUi();
	}

	public SecureRandom getRNG() {
		return this.mRandom;
	}

	public MemorizingTrustManager getMemorizingTrustManager() {
		return this.mMemorizingTrustManager;
	}

	public void setMemorizingTrustManager(MemorizingTrustManager trustManager) {
		this.mMemorizingTrustManager = trustManager;
	}

	public void updateMemorizingTrustmanager() {
		final MemorizingTrustManager tm;
		final boolean dontTrustSystemCAs = getPreferences().getBoolean("dont_trust_system_cas", false);
		if (dontTrustSystemCAs) {
			 tm = new MemorizingTrustManager(getApplicationContext(), null);
		} else {
			tm = new MemorizingTrustManager(getApplicationContext());
		}
		setMemorizingTrustManager(tm);
	}

	public PowerManager getPowerManager() {
		return this.pm;
	}

	public LruCache<String, Bitmap> getBitmapCache() {
		return this.mBitmapCache;
	}

	public void syncRosterToDisk(final Account account) {
		Runnable runnable = new Runnable() {

			@Override
			public void run() {
				databaseBackend.writeRoster(account.getRoster());
			}
		};
		mDatabaseExecutor.execute(runnable);

	}

	public List<String> getKnownHosts() {
		final List<String> hosts = new ArrayList<>();
		for (final Account account : getAccounts()) {
			if (!hosts.contains(account.getServer().toString())) {
				hosts.add(account.getServer().toString());
			}
			for (final Contact contact : account.getRoster().getContacts()) {
				if (contact.showInRoster()) {
					final String server = contact.getServer().toString();
					if (server != null && !hosts.contains(server)) {
						hosts.add(server);
					}
				}
			}
		}
		return hosts;
	}

	public List<String> getKnownConferenceHosts() {
		final ArrayList<String> mucServers = new ArrayList<>();
		for (final Account account : accounts) {
			if (account.getXmppConnection() != null) {
				final String server = account.getXmppConnection().getMucServer();
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

	public void sendIqPacket(final Account account, final IqPacket packet, final OnIqPacketReceived callback) {
		final XmppConnection connection = account.getXmppConnection();
		if (connection != null) {
			connection.sendIqPacket(packet, callback);
		}
	}

	public void sendPresence(final Account account) {
		sendPresencePacket(account, mPresenceGenerator.sendPresence(account));
	}

	public void sendOfflinePresence(final Account account) {
		sendPresencePacket(account, mPresenceGenerator.sendOfflinePresence(account));
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

	public IqParser getIqParser() {
		return this.mIqParser;
	}

	public JingleConnectionManager getJingleConnectionManager() {
		return this.mJingleConnectionManager;
	}

	public MessageArchiveService getMessageArchiveService() {
		return this.mMessageArchiveService;
	}

	public List<Contact> findContacts(Jid jid) {
		ArrayList<Contact> contacts = new ArrayList<>();
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

	public void resendFailedMessages(final Message message) {
		final Collection<Message> messages = new ArrayList<>();
		Message current = message;
		while (current.getStatus() == Message.STATUS_SEND_FAILED) {
			messages.add(current);
			if (current.mergeable(current.next())) {
				current = current.next();
			} else {
				break;
			}
		}
		for (final Message msg : messages) {
			msg.setTime(System.currentTimeMillis());
			markMessage(msg, Message.STATUS_WAITING);
			this.resendMessage(msg,false);
		}
	}

	public void clearConversationHistory(final Conversation conversation) {
		conversation.clearMessages();
		conversation.setHasMessagesLeftOnServer(false); //avoid messages getting loaded through mam
		conversation.resetLastMessageTransmitted();
		new Thread(new Runnable() {
			@Override
			public void run() {
				databaseBackend.deleteMessagesInConversation(conversation);
			}
		}).start();
	}

	public void sendBlockRequest(final Blockable blockable) {
		if (blockable != null && blockable.getBlockedJid() != null) {
			final Jid jid = blockable.getBlockedJid();
			this.sendIqPacket(blockable.getAccount(), getIqGenerator().generateSetBlockRequest(jid), new OnIqPacketReceived() {

				@Override
				public void onIqPacketReceived(final Account account, final IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE.RESULT) {
						account.getBlocklist().add(jid);
						updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
					}
				}
			});
		}
	}

	public void sendUnblockRequest(final Blockable blockable) {
		if (blockable != null && blockable.getJid() != null) {
			final Jid jid = blockable.getBlockedJid();
			this.sendIqPacket(blockable.getAccount(), getIqGenerator().generateSetUnblockRequest(jid), new OnIqPacketReceived() {
				@Override
				public void onIqPacketReceived(final Account account, final IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE.RESULT) {
						account.getBlocklist().remove(jid);
						updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
					}
				}
			});
		}
	}

	public interface OnMoreMessagesLoaded {
		public void onMoreMessagesLoaded(int count, Conversation conversation);

		public void informUser(int r);
	}

	public interface OnAccountPasswordChanged {
		public void onPasswordChangeSucceeded();

		public void onPasswordChangeFailed();
	}

	public interface OnAffiliationChanged {
		public void onAffiliationChangedSuccessful(Jid jid);

		public void onAffiliationChangeFailed(Jid jid, int resId);
	}

	public interface OnRoleChanged {
		public void onRoleChangedSuccessful(String nick);

		public void onRoleChangeFailed(String nick, int resid);
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

	public interface OnMucRosterUpdate {
		public void onMucRosterUpdate();
	}

	public interface OnConferenceOptionsPushed {
		public void onPushSucceeded();

		public void onPushFailed();
	}

	public interface OnShowErrorToast {
		void onShowErrorToast(int resId);
	}

	public class XmppConnectionBinder extends Binder {
		public XmppConnectionService getService() {
			return XmppConnectionService.this;
		}
	}
}
