package eu.siacs.conversations.services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.OnAccountListChangedListener;
import eu.siacs.conversations.ui.OnConversationListChangedListener;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.OnPhoneContactsLoadedListener;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnBindListener;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.OnStatusChanged;
import eu.siacs.conversations.xmpp.OnTLSExceptionReceived;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.OnJinglePacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
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
import android.os.Looper;
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
	private static final int PING_MIN_INTERVAL = 10;
	private static final int PING_TIMEOUT = 5;
	private static final int CONNECT_TIMEOUT = 60;
	private static final long CARBON_GRACE_PERIOD = 60000L;

	private static String ACTION_MERGE_PHONE_CONTACTS = "merge_phone_contacts";

	private MessageParser mMessageParser = new MessageParser(this);

	private List<Account> accounts;
	private List<Conversation> conversations = null;
	private JingleConnectionManager mJingleConnectionManager = new JingleConnectionManager(
			this);

	private OnConversationListChangedListener convChangedListener = null;
	private int convChangedListenerCount = 0;
	private OnAccountListChangedListener accountChangedListener = null;
	private OnTLSExceptionReceived tlsException = null;

	public void setOnTLSExceptionReceivedListener(
			OnTLSExceptionReceived listener) {
		tlsException = listener;
	}

	private Random mRandom = new Random(System.currentTimeMillis());

	private long lastCarbonMessageReceived = -CARBON_GRACE_PERIOD;

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
	private OnMessagePacketReceived messageListener = new OnMessagePacketReceived() {

		@Override
		public void onMessagePacketReceived(Account account,
				MessagePacket packet) {
			Message message = null;
			boolean notify = true;
			if (getPreferences().getBoolean(
					"notification_grace_period_after_carbon_received", true)) {
				notify = (SystemClock.elapsedRealtime() - lastCarbonMessageReceived) > CARBON_GRACE_PERIOD;
			}

			if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
				String pgpBody = mMessageParser.getPgpBody(packet);
				if (pgpBody != null) {
					message = mMessageParser.parsePgpChat(pgpBody, packet,
							account);
					message.markUnread();
				} else if ((packet.getBody() != null)
						&& (packet.getBody().startsWith("?OTR"))) {
					message = mMessageParser.parseOtrChat(packet, account);
					if (message != null) {
						message.markUnread();
					}
				} else if (packet.hasChild("body")) {
					message = mMessageParser
							.parsePlainTextChat(packet, account);
					message.markUnread();
				} else if (packet.hasChild("received")
						|| (packet.hasChild("sent"))) {
					message = mMessageParser
							.parseCarbonMessage(packet, account);
					if (message != null) {
						if (message.getStatus() == Message.STATUS_SEND) {
							lastCarbonMessageReceived = SystemClock
									.elapsedRealtime();
							notify = false;
							message.getConversation().markRead();
						} else {
							message.markUnread();
						}
					}
				}

			} else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
				message = mMessageParser.parseGroupchat(packet, account);
				if (message != null) {
					if (message.getStatus() == Message.STATUS_RECIEVED) {
						message.markUnread();
					} else {
						message.getConversation().markRead();
						notify = false;
					}
				}
			} else if (packet.getType() == MessagePacket.TYPE_ERROR) {
				mMessageParser.parseError(packet, account);
				return;
			} else if (packet.getType() == MessagePacket.TYPE_NORMAL) {
				if (packet.hasChild("x")) {
					Element x = packet.findChild("x");
					if (x.hasChild("invite")) {
						findOrCreateConversation(account, packet.getFrom(),
								true);
						if (convChangedListener != null) {
							convChangedListener.onConversationListChanged();
						}
						Log.d(LOGTAG,
								"invitation received to " + packet.getFrom());
					}

				} else {
					// Log.d(LOGTAG, "unparsed message " + packet.toString());
				}
			}
			if ((message == null) || (message.getBody() == null)) {
				return;
			}
			if (packet.hasChild("delay")) {
				try {
					String stamp = packet.findChild("delay").getAttribute(
							"stamp");
					stamp = stamp.replace("Z", "+0000");
					Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
							.parse(stamp);
					message.setTime(date.getTime());
				} catch (ParseException e) {
					Log.d(LOGTAG, "error trying to parse date" + e.getMessage());
				}
			}
			Conversation conversation = message.getConversation();
			conversation.getMessages().add(message);
			if (packet.getType() != MessagePacket.TYPE_ERROR) {
				databaseBackend.createMessage(message);
			}
			if (convChangedListener != null) {
				convChangedListener.onConversationListChanged();
			} else {
				UIHelper.updateNotification(getApplicationContext(),
						getConversations(), message.getConversation(), notify);
			}
		}
	};
	private OnStatusChanged statusListener = new OnStatusChanged() {

		@Override
		public void onStatusChanged(Account account) {
			if (accountChangedListener != null) {
				accountChangedListener.onAccountListChangedListener();
			}
			if (account.getStatus() == Account.STATUS_ONLINE) {
				List<Conversation> conversations = getConversations();
				for (int i = 0; i < conversations.size(); ++i) {
					if (conversations.get(i).getAccount() == account) {
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

	private OnPresencePacketReceived presenceListener = new OnPresencePacketReceived() {

		@Override
		public void onPresencePacketReceived(final Account account,
				PresencePacket packet) {
			if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
				Conversation muc = findMuc(
						packet.getAttribute("from").split("/")[0], account);
				if (muc != null) {
					muc.getMucOptions().processPacket(packet);
				} else {
					Log.d(LOGTAG, account.getJid()
							+ ": could not find muc for received muc package "
							+ packet.toString());
				}
			} else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
				Conversation muc = findMuc(
						packet.getAttribute("from").split("/")[0], account);
				if (muc != null) {
					Log.d(LOGTAG,
							account.getJid() + ": reading muc status packet "
									+ packet.toString());
					int error = muc.getMucOptions().getError();
					muc.getMucOptions().processPacket(packet);
					if ((muc.getMucOptions().getError() != error)
							&& (convChangedListener != null)) {
						Log.d(LOGTAG, "muc error status changed");
						convChangedListener.onConversationListChanged();
					}
				}
			} else {
				String[] fromParts = packet.getAttribute("from").split("/");
				String type = packet.getAttribute("type");
				if (fromParts[0].equals(account.getJid())) {
					if (fromParts.length == 2) {
						if (type == null) {
							account.updatePresence(fromParts[1], Presences
									.parseShow(packet.findChild("show")));
						} else if (type.equals("unavailable")) {
							account.removePresence(fromParts[1]);
						}
					}

				} else {
					Contact contact = account.getRoster().getContact(
							packet.getFrom());
					if (type == null) {
						if (fromParts.length == 2) {
							contact.updatePresence(fromParts[1], Presences
									.parseShow(packet.findChild("show")));
							PgpEngine pgp = getPgpEngine();
							if (pgp != null) {
								Element x = packet.findChild("x",
										"jabber:x:signed");
								if (x != null) {
									Element status = packet.findChild("status");
									String msg;
									if (status != null) {
										msg = status.getContent();
									} else {
										msg = "";
									}
									contact.setPgpKeyId(pgp.fetchKeyId(account,
											msg, x.getContent()));
								}
							}
						} else {
							// Log.d(LOGTAG,"presence without resource "+packet.toString());
						}
					} else if (type.equals("unavailable")) {
						if (fromParts.length != 2) {
							contact.clearPresences();
						} else {
							contact.removePresence(fromParts[1]);
						}
					} else if (type.equals("subscribe")) {
						Log.d(LOGTAG, "received subscribe packet from "
								+ packet.getFrom());
						if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
							Log.d(LOGTAG, "preemptive grant; granting");
							sendPresenceUpdatesTo(contact);
							contact.setOption(Contact.Options.FROM);
							contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
							if ((contact.getOption(Contact.Options.ASKING))
									&& (!contact.getOption(Contact.Options.TO))) {
								requestPresenceUpdatesFrom(contact);
							}
						} else {
							contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
						}
					} else {
						// Log.d(LOGTAG, packet.toString());
					}
				}
			}
		}
	};

	private OnIqPacketReceived unknownIqListener = new OnIqPacketReceived() {

		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.hasChild("query", "jabber:iq:roster")) {
				String from = packet.getFrom();
				if ((from == null) || (from.equals(account.getJid()))) {
					Element query = packet.findChild("query");
					processRosterItems(account, query);
				} else {
					Log.d(LOGTAG, "unauthorized roster push from: " + from);
				}
			} else if (packet
					.hasChild("open", "http://jabber.org/protocol/ibb")
					|| packet
							.hasChild("data", "http://jabber.org/protocol/ibb")) {
				XmppConnectionService.this.mJingleConnectionManager
						.deliverIbbPacket(account, packet);
			} else if (packet.hasChild("query",
					"http://jabber.org/protocol/disco#info")) {
				IqPacket iqResponse = packet
						.generateRespone(IqPacket.TYPE_RESULT);
				Element query = iqResponse.addChild("query",
						"http://jabber.org/protocol/disco#info");
				query.addChild("feature").setAttribute("var",
						"urn:xmpp:jingle:1");
				query.addChild("feature").setAttribute("var",
						"urn:xmpp:jingle:apps:file-transfer:3");
				query.addChild("feature").setAttribute("var",
						"urn:xmpp:jingle:transports:s5b:1");
				query.addChild("feature").setAttribute("var",
						"urn:xmpp:jingle:transports:ibb:1");
				account.getXmppConnection().sendIqPacket(iqResponse, null);
			} else {
				if ((packet.getType() == IqPacket.TYPE_GET)
						|| (packet.getType() == IqPacket.TYPE_SET)) {
					IqPacket response = packet
							.generateRespone(IqPacket.TYPE_ERROR);
					Element error = response.addChild("error");
					error.setAttribute("type", "cancel");
					error.addChild("feature-not-implemented",
							"urn:ietf:params:xml:ns:xmpp-stanzas");
					account.getXmppConnection().sendIqPacket(response, null);
				}
			}
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
			final Uri uri, final UiCallback callback) {
		final Message message;
		if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
			message = new Message(conversation, "",
					Message.ENCRYPTION_DECRYPTED);
		} else {
			message = new Message(conversation, "", Message.ENCRYPTION_NONE);
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
						callback.success();
					}
				} catch (FileBackend.ImageCopyException e) {
					callback.error(e.getResId());
				}
			}
		}).start();
		return message;
	}

	protected Conversation findMuc(String name, Account account) {
		for (Conversation conversation : this.conversations) {
			if (conversation.getContactJid().split("/")[0].equals(name)
					&& (conversation.getAccount() == account)) {
				return conversation;
			}
		}
		return null;
	}

	private void processRosterItems(Account account, Element elements) {
		String version = elements.getAttribute("ver");
		if (version != null) {
			account.getRoster().setVersion(version);
		}
		for (Element item : elements.getChildren()) {
			if (item.getName().equals("item")) {
				String jid = item.getAttribute("jid");
				String name = item.getAttribute("name");
				String subscription = item.getAttribute("subscription");
				Contact contact = account.getRoster().getContact(jid);
				if (!contact.getOption(Contact.Options.DIRTY_PUSH)) {
					contact.setServerName(name);
				}
				if (subscription.equals("remove")) {
					contact.resetOption(Contact.Options.IN_ROSTER);
					contact.resetOption(Contact.Options.DIRTY_DELETE);
				} else {
					contact.setOption(Contact.Options.IN_ROSTER);
					contact.parseSubscriptionFromElement(item);
				}
			}
		}
	}

	public class XmppConnectionBinder extends Binder {
		public XmppConnectionService getService() {
			return XmppConnectionService.this;
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		this.wakeLock.acquire();
		if ((intent != null)
				&& (ACTION_MERGE_PHONE_CONTACTS.equals(intent.getAction()))) {
			mergePhoneContactsWithRoster();
			return START_STICKY;
		} else if ((intent != null)
				&& (Intent.ACTION_SHUTDOWN.equals(intent.getAction()))) {
			logoutAndSave();
			return START_NOT_STICKY;
		}
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
				if (accountChangedListener != null) {
					accountChangedListener.onAccountListChangedListener();
				}
			}
		}
		if (wakeLock.isHeld()) {
			wakeLock.release();
		}
		return START_STICKY;
	}

	@Override
	public void onCreate() {
		ExceptionHelper.init(getApplicationContext());
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
		XmppConnection connection = new XmppConnection(account, this.pm);
		connection.setOnMessagePacketReceivedListener(this.messageListener);
		connection.setOnStatusChangedListener(this.statusListener);
		connection.setOnPresencePacketReceivedListener(this.presenceListener);
		connection
				.setOnUnregisteredIqPacketReceivedListener(this.unknownIqListener);
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
		connection.setOnBindListener(new OnBindListener() {

			@Override
			public void onBind(final Account account) {
				account.getRoster().clearPresences();
				account.clearPresences(); // self presences
				fetchRosterFromServer(account);
				sendPresence(account);
				connectMultiModeConversations(account);
				if (convChangedListener != null) {
					convChangedListener.onConversationListChanged();
				}
			}
		});
		return connection;
	}

	synchronized public void sendMessage(Message message, String presence) {
		Account account = message.getConversation().getAccount();
		Conversation conv = message.getConversation();
		MessagePacket packet = null;
		boolean saveInDb = false;
		boolean addToConversation = false;
		boolean send = false;
		if (account.getStatus() == Account.STATUS_ONLINE) {
			if (message.getType() == Message.TYPE_IMAGE) {
				mJingleConnectionManager.createNewConnection(message);
			} else {
				if (message.getEncryption() == Message.ENCRYPTION_OTR) {
					if (!conv.hasValidOtrSession()) {
						// starting otr session. messages will be send later
						conv.startOtrSession(getApplicationContext(), presence,
								true);
					} else if (conv.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) {
						// otr session aleary exists, creating message packet
						// accordingly
						packet = prepareMessagePacket(account, message,
								conv.getOtrSession());
						send = true;
						message.setStatus(Message.STATUS_SEND);
					}
					saveInDb = true;
					addToConversation = true;
				} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
					message.getConversation().endOtrIfNeeded();
					packet = prepareMessagePacket(account, message, null);
					packet.setBody("This is an XEP-0027 encryted message");
					packet.addChild("x", "jabber:x:encrypted").setContent(
							message.getEncryptedBody());
					message.setStatus(Message.STATUS_SEND);
					message.setEncryption(Message.ENCRYPTION_DECRYPTED);
					saveInDb = true;
					addToConversation = true;
					send = true;
				} else {
					message.getConversation().endOtrIfNeeded();
					// don't encrypt
					if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
						message.setStatus(Message.STATUS_SEND);
					}
					packet = prepareMessagePacket(account, message, null);
					send = true;
					saveInDb = true;
					addToConversation = true;
				}
			}
		} else {
			if (message.getEncryption() == Message.ENCRYPTION_PGP) {
				String pgpBody = message.getEncryptedBody();
				String decryptedBody = message.getBody();
				message.setBody(pgpBody);
				databaseBackend.createMessage(message);
				message.setEncryption(Message.ENCRYPTION_DECRYPTED);
				message.setBody(decryptedBody);
				addToConversation = true;
			} else {
				saveInDb = true;
				addToConversation = true;
			}

		}
		if (saveInDb) {
			databaseBackend.createMessage(message);
		}
		if (addToConversation) {
			conv.getMessages().add(message);
			if (convChangedListener != null) {
				convChangedListener.onConversationListChanged();
			}
		}
		if ((send) && (packet != null)) {
			account.getXmppConnection().sendMessagePacket(packet);
		}

	}

	private void sendUnsendMessages(Conversation conversation) {
		for (int i = 0; i < conversation.getMessages().size(); ++i) {
			if (conversation.getMessages().get(i).getStatus() == Message.STATUS_UNSEND) {
				resendMessage(conversation.getMessages().get(i));
			}
		}
	}

	private void resendMessage(Message message) {
		Account account = message.getConversation().getAccount();
		MessagePacket packet = null;
		if (message.getEncryption() == Message.ENCRYPTION_NONE) {
			packet = prepareMessagePacket(account, message, null);
		} else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
			packet = prepareMessagePacket(account, message, null);
			packet.setBody("This is an XEP-0027 encryted message");
			if (message.getEncryptedBody() == null) {
				markMessage(message, Message.STATUS_SEND_FAILED);
				return;
			}
			packet.addChild("x", "jabber:x:encrypted").setContent(
					message.getEncryptedBody());
		} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			packet = prepareMessagePacket(account, message, null);
			packet.setBody("This is an XEP-0027 encryted message");
			packet.addChild("x", "jabber:x:encrypted").setContent(
					message.getBody());
		}
		if (packet != null) {
			account.getXmppConnection().sendMessagePacket(packet);
			markMessage(message, Message.STATUS_SEND);
		}
	}

	public MessagePacket prepareMessagePacket(Account account, Message message,
			Session otrSession) {
		MessagePacket packet = new MessagePacket();
		if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
			packet.setType(MessagePacket.TYPE_CHAT);
			if (otrSession != null) {
				try {
					packet.setBody(otrSession.transformSending(message
							.getBody()));
				} catch (OtrException e) {
					Log.d(LOGTAG,
							account.getJid()
									+ ": could not encrypt message to "
									+ message.getCounterpart());
				}
				packet.addChild("private", "urn:xmpp:carbons:2");
				packet.addChild("no-copy", "urn:xmpp:hints");
				packet.setTo(otrSession.getSessionID().getAccountID() + "/"
						+ otrSession.getSessionID().getUserID());
				packet.setFrom(account.getFullJid());
			} else {
				packet.setBody(message.getBody());
				packet.setTo(message.getCounterpart());
				packet.setFrom(account.getJid());
			}
		} else if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
			packet.setType(MessagePacket.TYPE_GROUPCHAT);
			packet.setBody(message.getBody());
			packet.setTo(message.getCounterpart().split("/")[0]);
			packet.setFrom(account.getJid());
		}
		packet.setId(message.getUuid());
		return packet;
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
						Element roster = packet.findChild("query");
						if (roster != null) {
							account.getRoster().markAllAsNotInRoster();
							processRosterItems(account, roster);
						}
					}
				});
	}

	private void mergePhoneContactsWithRoster() {
		PhoneHelper.loadPhoneContacts(getApplicationContext(),
				new OnPhoneContactsLoadedListener() {
					@Override
					public void onPhoneContactsLoaded(List<Bundle> phoneContacts) {
						for(Account account : accounts) {
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
		Collections.sort(this.conversations, new Comparator<Conversation>() {
			@Override
			public int compare(Conversation lhs, Conversation rhs) {
				return (int) (rhs.getLatestMessage().getTimeSent() - lhs
						.getLatestMessage().getTimeSent());
			}
		});
		return this.conversations;
	}

	public List<Account> getAccounts() {
		return this.accounts;
	}

	public Conversation findOrCreateConversation(Account account, String jid,
			boolean muc) {
		for (Conversation conv : this.getConversations()) {
			if ((conv.getAccount().equals(account))
					&& (conv.getContactJid().split("/")[0].equals(jid))) {
				return conv;
			}
		}
		Conversation conversation = databaseBackend.findConversation(account,
				jid);
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
		if ((account.getStatus() == Account.STATUS_ONLINE)
				&& (conversation.getMode() == Conversation.MODE_MULTI)) {
			joinMuc(conversation);
		}
		if (this.convChangedListener != null) {
			this.convChangedListener.onConversationListChanged();
		}
		return conversation;
	}

	public void archiveConversation(Conversation conversation) {
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			leaveMuc(conversation);
		} else {
			conversation.endOtrIfNeeded();
		}
		this.databaseBackend.updateConversation(conversation);
		this.conversations.remove(conversation);
		if (this.convChangedListener != null) {
			this.convChangedListener.onConversationListChanged();
		}
	}

	public void clearConversationHistory(Conversation conversation) {
		this.databaseBackend.deleteMessagesInConversation(conversation);
		this.fileBackend.removeFiles(conversation);
		conversation.getMessages().clear();
		if (this.convChangedListener != null) {
			this.convChangedListener.onConversationListChanged();
		}
	}

	public int getConversationCount() {
		return this.databaseBackend.getConversationCount();
	}

	public void createAccount(Account account) {
		databaseBackend.createAccount(account);
		this.accounts.add(account);
		this.reconnectAccount(account, false);
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void updateAccount(Account account) {
		this.statusListener.onStatusChanged(account);
		databaseBackend.updateAccount(account);
		reconnectAccount(account, false);
		if (accountChangedListener != null) {
			accountChangedListener.onAccountListChangedListener();
		}
		UIHelper.showErrorNotification(getApplicationContext(), getAccounts());
	}

	public void deleteAccount(Account account) {
		if (account.getXmppConnection() != null) {
			this.disconnect(account, true);
		}
		databaseBackend.deleteAccount(account);
		this.accounts.remove(account);
		if (accountChangedListener != null) {
			accountChangedListener.onAccountListChangedListener();
		}
		UIHelper.showErrorNotification(getApplicationContext(), getAccounts());
	}

	public void setOnConversationListChangedListener(
			OnConversationListChangedListener listener) {
		this.convChangedListener = listener;
		this.convChangedListenerCount++;
	}

	public void removeOnConversationListChangedListener() {
		this.convChangedListenerCount--;
		if (this.convChangedListenerCount == 0) {
			this.convChangedListener = null;
		}
	}

	public void setOnAccountListChangedListener(
			OnAccountListChangedListener listener) {
		this.accountChangedListener = listener;
	}

	public void removeOnAccountListChangedListener() {
		this.accountChangedListener = null;
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
		String[] mucParts = conversation.getContactJid().split("/");
		String muc;
		String nick;
		if (mucParts.length == 2) {
			muc = mucParts[0];
			nick = mucParts[1];
		} else {
			muc = mucParts[0];
			nick = conversation.getAccount().getUsername();
		}
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("to", muc + "/" + nick);
		Element x = new Element("x");
		x.setAttribute("xmlns", "http://jabber.org/protocol/muc");
		if (conversation.getMessages().size() != 0) {
			long lastMsgTime = conversation.getLatestMessage().getTimeSent();
			long diff = (System.currentTimeMillis() - lastMsgTime) / 1000 - 1;
			x.addChild("history").setAttribute("seconds", diff + "");
		}
		packet.addChild(x);
		conversation.getAccount().getXmppConnection()
				.sendPresencePacket(packet);
	}

	private OnRenameListener renameListener = null;

	public void setOnRenameListener(OnRenameListener listener) {
		this.renameListener = listener;
	}

	public void renameInMuc(final Conversation conversation, final String nick) {
		final MucOptions options = conversation.getMucOptions();
		if (options.online()) {
			options.setOnRenameListener(new OnRenameListener() {

				@Override
				public void onRename(boolean success) {
					if (renameListener != null) {
						renameListener.onRename(success);
					}
					if (success) {
						String jid = conversation.getContactJid().split("/")[0]
								+ "/" + nick;
						conversation.setContactJid(jid);
						databaseBackend.updateConversation(conversation);
					}
				}
			});
			options.flagAboutToRename();
			PresencePacket packet = new PresencePacket();
			packet.setAttribute("to",
					conversation.getContactJid().split("/")[0] + "/" + nick);
			packet.setAttribute("from", conversation.getAccount().getFullJid());

			conversation.getAccount().getXmppConnection()
					.sendPresencePacket(packet, null);
		} else {
			String jid = conversation.getContactJid().split("/")[0] + "/"
					+ nick;
			conversation.setContactJid(jid);
			databaseBackend.updateConversation(conversation);
			if (conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
				joinMuc(conversation);
			}
		}
	}

	public void leaveMuc(Conversation conversation) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("to", conversation.getContactJid().split("/")[0]
				+ "/" + conversation.getMucOptions().getNick());
		packet.setAttribute("from", conversation.getAccount().getFullJid());
		packet.setAttribute("type", "unavailable");
		Log.d(LOGTAG, "send leaving muc " + packet);
		conversation.getAccount().getXmppConnection()
				.sendPresencePacket(packet);
		conversation.getMucOptions().setOffline();
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
		for(Contact contact : account.getRoster().getContacts()) {
			if (contact.getOption(Contact.Options.DIRTY_PUSH)) {
				pushContactToServer(contact);
			}
			if (contact.getOption(Contact.Options.DIRTY_DELETE)) {
				Log.d(LOGTAG,"dirty delete");
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

	public void pushContactToServer(Contact contact) {
		contact.resetOption(Contact.Options.DIRTY_DELETE);
		Account account = contact.getAccount();
		if (account.getStatus() == Account.STATUS_ONLINE) {
			IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
			iq.query("jabber:iq:roster").addChild(contact.asElement());
			account.getXmppConnection().sendIqPacket(iq, null);
			if (contact.getOption(Contact.Options.ASKING)) {
				requestPresenceUpdatesFrom(contact);
			}
			if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
				Log.d("xmppService", "contact had pending subscription");
				sendPresenceUpdatesTo(contact);
			}
			contact.resetOption(Contact.Options.DIRTY_PUSH);
		} else {
			contact.setOption(Contact.Options.DIRTY_PUSH);
		}
	}

	public void deleteContactOnServer(Contact contact) {
		contact.resetOption(Contact.Options.DIRTY_PUSH);
		Account account = contact.getAccount();
		if (account.getStatus() == Account.STATUS_ONLINE) {
			IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
			Element item = iq.query("jabber:iq:roster").addChild("item");
			item.setAttribute("jid", contact.getJid());
			item.setAttribute("subscription", "remove");
			account.getXmppConnection().sendIqPacket(iq, null);
			contact.resetOption(Contact.Options.DIRTY_DELETE);
		} else {
			contact.setOption(Contact.Options.DIRTY_DELETE);
		}
	}

	public void requestPresenceUpdatesFrom(Contact contact) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "subscribe");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void stopPresenceUpdatesFrom(Contact contact) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "unsubscribe");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void stopPresenceUpdatesTo(Contact contact) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "unsubscribed");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void sendPresenceUpdatesTo(Contact contact) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "subscribed");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void sendPresence(Account account) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("from", account.getFullJid());
		String sig = account.getPgpSignature();
		if (sig != null) {
			packet.addChild("status").setContent("online");
			packet.addChild("x", "jabber:x:signed").setContent(sig);
		}
		account.getXmppConnection().sendPresencePacket(packet);
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

	public void sendConversationSubject(Conversation conversation,
			String subject) {
		MessagePacket packet = new MessagePacket();
		packet.setType(MessagePacket.TYPE_GROUPCHAT);
		packet.setTo(conversation.getContactJid().split("/")[0]);
		Element subjectChild = new Element("subject");
		subjectChild.setContent(subject);
		packet.addChild(subjectChild);
		packet.setFrom(conversation.getAccount().getJid());
		Account account = conversation.getAccount();
		if (account.getStatus() == Account.STATUS_ONLINE) {
			account.getXmppConnection().sendMessagePacket(packet);
		}
	}

	public void inviteToConference(Conversation conversation,
			List<Contact> contacts) {
		for (Contact contact : contacts) {
			MessagePacket packet = new MessagePacket();
			packet.setTo(conversation.getContactJid().split("/")[0]);
			packet.setFrom(conversation.getAccount().getFullJid());
			Element x = new Element("x");
			x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
			Element invite = new Element("invite");
			invite.setAttribute("to", contact.getJid());
			x.addChild(invite);
			packet.addChild(x);
			Log.d(LOGTAG, packet.toString());
			conversation.getAccount().getXmppConnection()
					.sendMessagePacket(packet);
		}

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
		if (convChangedListener != null) {
			convChangedListener.onConversationListChanged();
		}
	}

	public SharedPreferences getPreferences() {
		return PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
	}

	public void updateUi(Conversation conversation, boolean notify) {
		if (convChangedListener != null) {
			convChangedListener.onConversationListChanged();
		} else {
			UIHelper.updateNotification(getApplicationContext(),
					getConversations(), conversation, notify);
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
}
