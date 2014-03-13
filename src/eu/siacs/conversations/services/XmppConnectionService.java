package eu.siacs.conversations.services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.crypto.PgpEngine.OpenPgpException;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.persistance.OnPhoneContactsMerged;
import eu.siacs.conversations.ui.OnAccountListChangedListener;
import eu.siacs.conversations.ui.OnConversationListChangedListener;
import eu.siacs.conversations.ui.OnRosterFetchedListener;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.MessageParser;
import eu.siacs.conversations.utils.OnPhoneContactsLoadedListener;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.OnStatusChanged;
import eu.siacs.conversations.xmpp.OnTLSExceptionReceived;
import eu.siacs.conversations.xmpp.XmppConnection;
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
import android.database.DatabaseUtils;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;

public class XmppConnectionService extends Service {

	protected static final String LOGTAG = "xmppService";
	public DatabaseBackend databaseBackend;

	public long startDate;

	private static final int PING_MAX_INTERVAL = 300;
	private static final int PING_MIN_INTERVAL = 10;
	private static final int PING_TIMEOUT = 2;
	private static final int CONNECT_TIMEOUT = 60;

	private List<Account> accounts;
	private List<Conversation> conversations = null;

	public OnConversationListChangedListener convChangedListener = null;
	private OnAccountListChangedListener accountChangedListener = null;
	private OnTLSExceptionReceived tlsException = null;
	
	public void setOnTLSExceptionReceivedListener(
			OnTLSExceptionReceived listener) {
		tlsException = listener;
	}

	private Random mRandom = new Random(System.currentTimeMillis());

	private ContentObserver contactObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			Log.d(LOGTAG, "contact list has changed");
			mergePhoneContactsWithRoster(null);
		}
	};

	private XmppConnectionService service = this;

	private final IBinder mBinder = new XmppConnectionBinder();
	private OnMessagePacketReceived messageListener = new OnMessagePacketReceived() {

		@Override
		public void onMessagePacketReceived(Account account,
				MessagePacket packet) {
			Message message = null;
			boolean notify = true;
			if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
				String pgpBody = MessageParser.getPgpBody(packet);
				if (pgpBody != null) {
					message = MessageParser.parsePgpChat(pgpBody, packet,
							account, service);
					message.markUnread();
				} else if (packet.hasChild("body")
						&& (packet.getBody().startsWith("?OTR"))) {
					message = MessageParser.parseOtrChat(packet, account,
							service);
					if (message != null) {
						message.markUnread();
					}
				} else if (packet.hasChild("body")) {
					message = MessageParser.parsePlainTextChat(packet, account,
							service);
					message.markUnread();
				} else if (packet.hasChild("received")
						|| (packet.hasChild("sent"))) {
					message = MessageParser.parseCarbonMessage(packet, account,
							service);
					if (message != null) {
						message.getConversation().markRead();
					}
					notify = false;
				}

			} else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
				message = MessageParser
						.parseGroupchat(packet, account, service);
				if (message != null) {
					if (message.getStatus() == Message.STATUS_RECIEVED) {
						message.markUnread();
					} else {
						message.getConversation().markRead();
						notify = false;
					}
				}
			} else if (packet.getType() == MessagePacket.TYPE_ERROR) {
				message = MessageParser.parseError(packet, account, service);
			} else {
				// Log.d(LOGTAG, "unparsed message " + packet.toString());
			}
			if (message == null) {
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
				databaseBackend.clearPresences(account);
				if (account.getXmppConnection().hasFeatureRosterManagment()) {
					updateRoster(account, null);
				}
				connectMultiModeConversations(account);
				List<Conversation> conversations = getConversations();
				for (int i = 0; i < conversations.size(); ++i) {
					if (conversations.get(i).getAccount() == account) {
						sendUnsendMessages(conversations.get(i));
					}
				}
				if (convChangedListener != null) {
					convChangedListener.onConversationListChanged();
				}
				scheduleWakeupCall(PING_MAX_INTERVAL, true);
			} else if (account.getStatus() == Account.STATUS_OFFLINE) {
				databaseBackend.clearPresences(account);
				if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					int timeToReconnect = mRandom.nextInt(50) + 10;
					scheduleWakeupCall(timeToReconnect, false);
				}

			} else if (account.getStatus() == Account.STATUS_REGISTRATION_SUCCESSFULL) {
				databaseBackend.updateAccount(account);
				reconnectAccount(account, true);
			}
		}
	};

	private OnPresencePacketReceived presenceListener = new OnPresencePacketReceived() {

		@Override
		public void onPresencePacketReceived(Account account,
				PresencePacket packet) {
			if (packet.hasChild("x")
					&& (packet.findChild("x").getAttribute("xmlns")
							.startsWith("http://jabber.org/protocol/muc"))) {
				Conversation muc = findMuc(packet.getAttribute("from").split(
						"/")[0]);
				if (muc != null) {
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
				Contact contact = findContact(account, fromParts[0]);
				if (contact == null) {
					// most likely self or roster not synced
					return;
				}
				String type = packet.getAttribute("type");
				if (type == null) {
					Element show = packet.findChild("show");
					if (show == null) {
						contact.updatePresence(fromParts[1], Presences.ONLINE);
					} else if (show.getContent().equals("away")) {
						contact.updatePresence(fromParts[1], Presences.AWAY);
					} else if (show.getContent().equals("xa")) {
						contact.updatePresence(fromParts[1], Presences.XA);
					} else if (show.getContent().equals("chat")) {
						contact.updatePresence(fromParts[1], Presences.CHAT);
					} else if (show.getContent().equals("dnd")) {
						contact.updatePresence(fromParts[1], Presences.DND);
					}
					PgpEngine pgp = getPgpEngine();
					if (pgp != null) {
						Element x = packet.findChild("x");
						if ((x != null)
								&& (x.getAttribute("xmlns")
										.equals("jabber:x:signed"))) {
							try {
								contact.setPgpKeyId(pgp.fetchKeyId(packet
										.findChild("status").getContent(), x
										.getContent()));
							} catch (OpenPgpException e) {
								Log.d(LOGTAG, "faulty pgp. just ignore");
							}
						}
					}
					databaseBackend.updateContact(contact);
				} else if (type.equals("unavailable")) {
					if (fromParts.length != 2) {
						// Log.d(LOGTAG,"received presence with no resource "+packet.toString());
					} else {
						contact.removePresence(fromParts[1]);
						databaseBackend.updateContact(contact);
					}
				} else if (type.equals("subscribe")) {
					if (contact
							.getSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT)) {
						sendPresenceUpdatesTo(contact);
						contact.setSubscriptionOption(Contact.Subscription.FROM);
						contact.resetSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
						replaceContactInConversation(contact.getJid(), contact);
						databaseBackend.updateContact(contact);
						if ((contact
								.getSubscriptionOption(Contact.Subscription.ASKING))
								&& (!contact
										.getSubscriptionOption(Contact.Subscription.TO))) {
							requestPresenceUpdatesFrom(contact);
						}
					} else {
						// TODO: ask user to handle it maybe
					}
				} else {
					//Log.d(LOGTAG, packet.toString());
				}
				replaceContactInConversation(contact.getJid(), contact);
			}
		}
	};

	private OnIqPacketReceived unknownIqListener = new OnIqPacketReceived() {

		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.hasChild("query")) {
				Element query = packet.findChild("query");
				String xmlns = query.getAttribute("xmlns");
				if ((xmlns != null) && (xmlns.equals("jabber:iq:roster"))) {
					processRosterItems(account, query);
					mergePhoneContactsWithRoster(null);
				}
			}
		}
	};

	private OpenPgpServiceConnection pgpServiceConnection;
	private PgpEngine mPgpEngine = null;
	private Intent pingIntent;
	private PendingIntent pendingPingIntent = null;

	public PgpEngine getPgpEngine() {
		if (pgpServiceConnection.isBound()) {
			if (this.mPgpEngine == null) {
				this.mPgpEngine = new PgpEngine(new OpenPgpApi(
						getApplicationContext(),
						pgpServiceConnection.getService()));
			}
			return mPgpEngine;
		} else {
			return null;
		}

	}

	protected Conversation findMuc(String name) {
		for (Conversation conversation : this.conversations) {
			if (conversation.getContactJid().split("/")[0].equals(name)) {
				return conversation;
			}
		}
		return null;
	}

	private void processRosterItems(Account account, Element elements) {
		String version = elements.getAttribute("ver");
		if (version != null) {
			account.setRosterVersion(version);
			databaseBackend.updateAccount(account);
		}
		for (Element item : elements.getChildren()) {
			if (item.getName().equals("item")) {
				String jid = item.getAttribute("jid");
				String subscription = item.getAttribute("subscription");
				Contact contact = databaseBackend.findContact(account, jid);
				if (contact == null) {
					if (!subscription.equals("remove")) {
						String name = item.getAttribute("name");
						if (name == null) {
							name = jid.split("@")[0];
						}
						contact = new Contact(account, name, jid, null);
						contact.parseSubscriptionFromElement(item);
						databaseBackend.createContact(contact);
					}
				} else {
					if (subscription.equals("remove")) {
						databaseBackend.deleteContact(contact);
						replaceContactInConversation(contact.getJid(), null);
					} else {
						contact.parseSubscriptionFromElement(item);
						databaseBackend.updateContact(contact);
						replaceContactInConversation(contact.getJid(), contact);
					}
				}
			}
		}
	}

	private void replaceContactInConversation(String jid, Contact contact) {
		List<Conversation> conversations = getConversations();
		for (int i = 0; i < conversations.size(); ++i) {
			if ((conversations.get(i).getContactJid().equals(jid))) {
				conversations.get(i).setContact(contact);
				break;
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
		Log.d(LOGTAG,"calling start service. caller was:"+intent.getAction());
		ConnectivityManager cm = (ConnectivityManager) getApplicationContext()
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null
				&& activeNetwork.isConnected();

		for (Account account : accounts) {
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {
				if (!isConnected) {
					account.setStatus(Account.STATUS_NO_INTERNET);
					if (statusListener!=null) {
						statusListener.onStatusChanged(account);
					}
				} else {
					if (account.getStatus() == Account.STATUS_NO_INTERNET) {
						account.setStatus(Account.STATUS_OFFLINE);
						if (statusListener!=null) {
							statusListener.onStatusChanged(account);
						}
					}

					// TODO 3 remaining cases
					if (account.getStatus() == Account.STATUS_ONLINE) {
						long lastReceived = account.getXmppConnection().lastPaketReceived;
						long lastSent = account.getXmppConnection().lastPingSent;
						if (lastSent - lastReceived >= PING_TIMEOUT * 1000) {
							Log.d(LOGTAG, account.getJid() + ": ping timeout");
							this.reconnectAccount(account,true);
						} else if (SystemClock.elapsedRealtime() - lastReceived >= PING_MIN_INTERVAL * 1000) {
							account.getXmppConnection().sendPing();
							account.getXmppConnection().lastPingSent = SystemClock.elapsedRealtime();
							this.scheduleWakeupCall(2, false);
						}
					} else if (account.getStatus() == Account.STATUS_OFFLINE) {
						if (account.getXmppConnection() == null) {
							account.setXmppConnection(this
									.createConnection(account));
						}
						account.getXmppConnection().lastPingSent = SystemClock.elapsedRealtime();
						new Thread(account.getXmppConnection()).start();
					} else if ((account.getStatus() == Account.STATUS_CONNECTING)&&((SystemClock.elapsedRealtime() - account.getXmppConnection().lastConnect) / 1000 >= CONNECT_TIMEOUT)) {
						Log.d(LOGTAG,account.getJid()+": time out during connect reconnecting");
						reconnectAccount(account,true);
					} else {
						Log.d(LOGTAG,"seconds since last connect:"+((SystemClock.elapsedRealtime() - account.getXmppConnection().lastConnect) / 1000));
						Log.d(LOGTAG,account.getJid()+": status="+account.getStatus());
						// TODO notify user of ssl cert problem or auth problem or what ever
					}
					//in any case. reschedule wakup call
					this.scheduleWakeupCall(PING_MAX_INTERVAL, true);
				}
				if (accountChangedListener != null) {
					accountChangedListener.onAccountListChangedListener();
				}
			}
		}
		return START_STICKY;
	}

	@Override
	public void onCreate() {
		ExceptionHelper.init(getApplicationContext());
		databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
		this.accounts = databaseBackend.getAccounts();

		getContentResolver().registerContentObserver(
				ContactsContract.Contacts.CONTENT_URI, true, contactObserver);
		this.pgpServiceConnection = new OpenPgpServiceConnection(
				getApplicationContext(), "org.sufficientlysecure.keychain");
		this.pgpServiceConnection.bindToService();

	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		for (Account account : accounts) {
			if (account.getXmppConnection() != null) {
				disconnect(account, true);
			}
		}
	}

	protected void scheduleWakeupCall(int seconds, boolean ping) {
		long timeToWake = SystemClock.elapsedRealtime() + seconds * 1000;
		Context context = getApplicationContext();
		AlarmManager alarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		
		
		
		if (ping) {
			if (this.pingIntent==null) {
				this.pingIntent = new Intent(context, EventReceiver.class);
				this.pingIntent.setAction("ping");
				this.pingIntent.putExtra("time", timeToWake);
				this.pendingPingIntent = PendingIntent.getBroadcast(context, 0,
						this.pingIntent, 0);
				alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,timeToWake, pendingPingIntent);
				//Log.d(LOGTAG,"schedule ping in "+seconds+" seconds");
			} else {
				long scheduledTime = this.pingIntent.getLongExtra("time", 0);
				if (scheduledTime<SystemClock.elapsedRealtime() || (scheduledTime > timeToWake)) {
					this.pingIntent.putExtra("time", timeToWake);
					alarmManager.cancel(this.pendingPingIntent);
					this.pendingPingIntent = PendingIntent.getBroadcast(context, 0,
							this.pingIntent, 0);
					alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,timeToWake, pendingPingIntent);
					//Log.d(LOGTAG,"reschedule old ping to ping in "+seconds+" seconds");
				}
			}
		} else {
			Intent intent = new Intent(context, EventReceiver.class);
			intent.setAction("ping_check");
			PendingIntent alarmIntent = PendingIntent.getBroadcast(context, 0,
					intent, 0);
			alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,timeToWake, alarmIntent);
		}

	}

	public XmppConnection createConnection(Account account) {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		XmppConnection connection = new XmppConnection(account, pm);
		connection.setOnMessagePacketReceivedListener(this.messageListener);
		connection.setOnStatusChangedListener(this.statusListener);
		connection.setOnPresencePacketReceivedListener(this.presenceListener);
		connection
				.setOnUnregisteredIqPacketReceivedListener(this.unknownIqListener);
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
		return connection;
	}

	public void sendMessage(Message message, String presence) {
		Account account = message.getConversation().getAccount();
		Conversation conv = message.getConversation();
		boolean saveInDb = false;
		boolean addToConversation = false;
		if (account.getStatus() == Account.STATUS_ONLINE) {
			MessagePacket packet;
			if (message.getEncryption() == Message.ENCRYPTION_OTR) {
				if (!conv.hasValidOtrSession()) {
					// starting otr session. messages will be send later
					conv.startOtrSession(getApplicationContext(), presence);
				} else if (conv.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) {
					// otr session aleary exists, creating message packet
					// accordingly
					packet = prepareMessagePacket(account, message,
							conv.getOtrSession());
					account.getXmppConnection().sendMessagePacket(packet);
					message.setStatus(Message.STATUS_SEND);
				}
				saveInDb = true;
				addToConversation = true;
			} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
				message.getConversation().endOtrIfNeeded();
				long keyId = message.getConversation().getContact()
						.getPgpKeyId();
				packet = new MessagePacket();
				packet.setType(MessagePacket.TYPE_CHAT);
				packet.setFrom(message.getConversation().getAccount()
						.getFullJid());
				packet.setTo(message.getCounterpart());
				packet.setBody("This is an XEP-0027 encryted message");
				Element x = new Element("x");
				x.setAttribute("xmlns", "jabber:x:encrypted");
				x.setContent(this.getPgpEngine().encrypt(keyId,
						message.getBody()));
				packet.addChild(x);
				account.getXmppConnection().sendMessagePacket(packet);
				message.setStatus(Message.STATUS_SEND);
				message.setEncryption(Message.ENCRYPTION_DECRYPTED);
				saveInDb = true;
				addToConversation = true;
			} else {
				message.getConversation().endOtrIfNeeded();
				// don't encrypt
				if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
					message.setStatus(Message.STATUS_SEND);
					saveInDb = true;
					addToConversation = true;
				}

				packet = prepareMessagePacket(account, message, null);
				account.getXmppConnection().sendMessagePacket(packet);
			}
		} else {
			// account is offline
			saveInDb = true;
			addToConversation = true;

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

	}

	private void sendUnsendMessages(Conversation conversation) {
		for (int i = 0; i < conversation.getMessages().size(); ++i) {
			if (conversation.getMessages().get(i).getStatus() == Message.STATUS_UNSEND) {
				Message message = conversation.getMessages().get(i);
				MessagePacket packet = prepareMessagePacket(
						conversation.getAccount(), message, null);
				conversation.getAccount().getXmppConnection()
						.sendMessagePacket(packet);
				message.setStatus(Message.STATUS_SEND);
				if (conversation.getMode() == Conversation.MODE_SINGLE) {
					databaseBackend.updateMessage(message);
				} else {
					databaseBackend.deleteMessage(message);
					conversation.getMessages().remove(i);
					i--;
				}
			}
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
				Element privateMarker = new Element("private");
				privateMarker.setAttribute("xmlns", "urn:xmpp:carbons:2");
				packet.addChild(privateMarker);
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
		return packet;
	}

	public void getRoster(Account account,
			final OnRosterFetchedListener listener) {
		List<Contact> contacts = databaseBackend.getContactsByAccount(account);
		for (int i = 0; i < contacts.size(); ++i) {
			contacts.get(i).setAccount(account);
		}
		if (listener != null) {
			listener.onRosterFetched(contacts);
		}
	}

	public void updateRoster(final Account account,
			final OnRosterFetchedListener listener) {
		IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
		Element query = new Element("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		if (!"".equals(account.getRosterVersion())) {
			Log.d(LOGTAG, account.getJid() + ": fetching roster version "
					+ account.getRosterVersion());
		} else {
			Log.d(LOGTAG, account.getJid() + ": fetching roster");
		}
		query.setAttribute("ver", account.getRosterVersion());
		iqPacket.addChild(query);
		account.getXmppConnection().sendIqPacket(iqPacket,
				new OnIqPacketReceived() {

					@Override
					public void onIqPacketReceived(final Account account,
							IqPacket packet) {
						Element roster = packet.findChild("query");
						if (roster != null) {
							Log.d(LOGTAG, account.getJid()
									+ ": processing roster");
							processRosterItems(account, roster);
							StringBuilder mWhere = new StringBuilder();
							mWhere.append("jid NOT IN(");
							List<Element> items = roster.getChildren();
							for (int i = 0; i < items.size(); ++i) {
								mWhere.append(DatabaseUtils
										.sqlEscapeString(items.get(i)
												.getAttribute("jid")));
								if (i != items.size() - 1) {
									mWhere.append(",");
								}
							}
							mWhere.append(") and accountUuid = \"");
							mWhere.append(account.getUuid());
							mWhere.append("\"");
							List<Contact> contactsToDelete = databaseBackend
									.getContacts(mWhere.toString());
							for (Contact contact : contactsToDelete) {
								databaseBackend.deleteContact(contact);
								replaceContactInConversation(contact.getJid(),
										null);
							}

						} else {
							Log.d(LOGTAG, account.getJid()
									+ ": empty roster returend");
						}
						mergePhoneContactsWithRoster(new OnPhoneContactsMerged() {

							@Override
							public void phoneContactsMerged() {
								if (listener != null) {
									getRoster(account, listener);
								}
							}
						});
					}
				});
	}

	public void mergePhoneContactsWithRoster(
			final OnPhoneContactsMerged listener) {
		PhoneHelper.loadPhoneContacts(getApplicationContext(),
				new OnPhoneContactsLoadedListener() {
					@Override
					public void onPhoneContactsLoaded(
							Hashtable<String, Bundle> phoneContacts) {
						List<Contact> contacts = databaseBackend
								.getContactsByAccount(null);
						for (int i = 0; i < contacts.size(); ++i) {
							Contact contact = contacts.get(i);
							if (phoneContacts.containsKey(contact.getJid())) {
								Bundle phoneContact = phoneContacts.get(contact
										.getJid());
								String systemAccount = phoneContact
										.getInt("phoneid")
										+ "#"
										+ phoneContact.getString("lookup");
								contact.setSystemAccount(systemAccount);
								contact.setPhotoUri(phoneContact
										.getString("photouri"));
								contact.setDisplayName(phoneContact
										.getString("displayname"));
								databaseBackend.updateContact(contact);
								replaceContactInConversation(contact.getJid(),
										contact);
							} else {
								if ((contact.getSystemAccount() != null)
										|| (contact.getProfilePhoto() != null)) {
									contact.setSystemAccount(null);
									contact.setPhotoUri(null);
									databaseBackend.updateContact(contact);
									replaceContactInConversation(
											contact.getJid(), contact);
								}
							}
						}
						if (listener != null) {
							listener.phoneContactsMerged();
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
				conv.setContact(findContact(account, conv.getContactJid()));
				conv.setMessages(databaseBackend.getMessages(conv, 50));
			}
		}
		return this.conversations;
	}

	public List<Account> getAccounts() {
		return this.accounts;
	}

	public Contact findContact(Account account, String jid) {
		Contact contact = databaseBackend.findContact(account, jid);
		if (contact != null) {
			contact.setAccount(account);
		}
		return contact;
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
				if (account.getStatus() == Account.STATUS_ONLINE) {
					joinMuc(conversation);
				}
			} else {
				conversation.setMode(Conversation.MODE_SINGLE);
			}
			conversation.setMessages(databaseBackend.getMessages(conversation, 50));
			this.databaseBackend.updateConversation(conversation);
			conversation.setContact(findContact(account,
					conversation.getContactJid()));
		} else {
			String conversationName;
			Contact contact = findContact(account, jid);
			if (contact != null) {
				conversationName = contact.getDisplayName();
			} else {
				conversationName = jid.split("@")[0];
			}
			if (muc) {
				conversation = new Conversation(conversationName, account, jid,
						Conversation.MODE_MULTI);
				if (account.getStatus() == Account.STATUS_ONLINE) {
					joinMuc(conversation);
				}
			} else {
				conversation = new Conversation(conversationName, account, jid,
						Conversation.MODE_SINGLE);
			}
			conversation.setContact(contact);
			this.databaseBackend.createConversation(conversation);
		}
		this.conversations.add(conversation);
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

	public void deleteContact(Contact contact) {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		Element query = new Element("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		Element item = new Element("item");
		item.setAttribute("jid", contact.getJid());
		item.setAttribute("subscription", "remove");
		query.addChild(item);
		iq.addChild(query);
		contact.getAccount().getXmppConnection().sendIqPacket(iq, null);
		replaceContactInConversation(contact.getJid(), null);
		databaseBackend.deleteContact(contact);
	}

	public void updateAccount(Account account) {
		databaseBackend.updateAccount(account);
		reconnectAccount(account,false);
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void deleteAccount(Account account) {
		Log.d(LOGTAG, "called delete account");
		if (account.getXmppConnection() != null) {
			this.disconnect(account, false);
		}
		databaseBackend.deleteAccount(account);
		this.accounts.remove(account);
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void setOnConversationListChangedListener(
			OnConversationListChangedListener listener) {
		this.convChangedListener = listener;
	}

	public void removeOnConversationListChangedListener() {
		this.convChangedListener = null;
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
			Element history = new Element("history");
			long lastMsgTime = conversation.getLatestMessage().getTimeSent();
			long diff = (System.currentTimeMillis() - lastMsgTime) / 1000 - 1;
			history.setAttribute("seconds", diff + "");
			x.addChild(history);
		}
		packet.addChild(x);
		conversation.getAccount().getXmppConnection()
				.sendPresencePacket(packet);
	}

	private OnRenameListener renameListener = null;
	private boolean pongReceived;

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
						databaseBackend.updateConversation(conversation);
					}
				}
			});
			PresencePacket packet = new PresencePacket();
			packet.setAttribute("to",
					conversation.getContactJid().split("/")[0] + "/" + nick);
			packet.setAttribute("from", conversation.getAccount().getFullJid());

			conversation.getAccount().getXmppConnection()
					.sendPresencePacket(packet, new OnPresencePacketReceived() {

						@Override
						public void onPresencePacketReceived(Account account,
								PresencePacket packet) {
							final boolean changed;
							String type = packet.getAttribute("type");
							changed = (!"error".equals(type));
							if (!changed) {
								options.getOnRenameListener().onRename(false);
							} else {
								if (type == null) {
									options.getOnRenameListener()
											.onRename(true);
									options.setNick(packet.getAttribute("from")
											.split("/")[1]);
								} else {
									options.processPacket(packet);
								}
							}
						}
					});
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
		packet.setAttribute("to", conversation.getContactJid());
		packet.setAttribute("from", conversation.getAccount().getFullJid());
		packet.setAttribute("type", "unavailable");
		conversation.getAccount().getXmppConnection()
				.sendPresencePacket(packet);
		conversation.getMucOptions().setOffline();
	}

	public void disconnect(Account account, boolean force) {
		if ((account.getStatus() == Account.STATUS_ONLINE)||(account.getStatus() == Account.STATUS_DISABLED)) {
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
			account.getXmppConnection().disconnect(force);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public void updateContact(Contact contact) {
		databaseBackend.updateContact(contact);
		replaceContactInConversation(contact.getJid(), contact);
	}

	public void updateMessage(Message message) {
		databaseBackend.updateMessage(message);
	}

	public void createContact(Contact contact) {
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
		if (autoGrant) {
			contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
			contact.setSubscriptionOption(Contact.Subscription.ASKING);
		}
		databaseBackend.createContact(contact);
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		Element query = new Element("query");
		query.setAttribute("xmlns", "jabber:iq:roster");
		Element item = new Element("item");
		item.setAttribute("jid", contact.getJid());
		item.setAttribute("name", contact.getJid());
		query.addChild(item);
		iq.addChild(query);
		Account account = contact.getAccount();
		account.getXmppConnection().sendIqPacket(iq, null);
		if (autoGrant) {
			requestPresenceUpdatesFrom(contact);
		}
		replaceContactInConversation(contact.getJid(), contact);
	}

	public void requestPresenceUpdatesFrom(Contact contact) {
		// Requesting a Subscription type=subscribe
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "subscribe");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		Log.d(LOGTAG, packet.toString());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void stopPresenceUpdatesFrom(Contact contact) {
		// Unsubscribing type='unsubscribe'
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "unsubscribe");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		Log.d(LOGTAG, packet.toString());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void stopPresenceUpdatesTo(Contact contact) {
		// Canceling a Subscription type=unsubscribed
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "unsubscribed");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		Log.d(LOGTAG, packet.toString());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void sendPresenceUpdatesTo(Contact contact) {
		// type='subscribed'
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("type", "subscribed");
		packet.setAttribute("to", contact.getJid());
		packet.setAttribute("from", contact.getAccount().getJid());
		Log.d(LOGTAG, packet.toString());
		contact.getAccount().getXmppConnection().sendPresencePacket(packet);
	}

	public void sendPgpPresence(Account account, String signature) {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("from", account.getFullJid());
		Element status = new Element("status");
		status.setContent("online");
		packet.addChild(status);
		Element x = new Element("x");
		x.setAttribute("xmlns", "jabber:x:signed");
		x.setContent(signature);
		packet.addChild(x);
		account.getXmppConnection().sendPresencePacket(packet);
	}

	public void generatePgpAnnouncement(Account account)
			throws PgpEngine.UserInputRequiredException {
		if (account.getStatus() == Account.STATUS_ONLINE) {
			String signature = getPgpEngine().generateSignature("online");
			account.setKey("pgp_signature", signature);
			databaseBackend.updateAccount(account);
			sendPgpPresence(account, signature);
		}
	}

	public void updateConversation(Conversation conversation) {
		this.databaseBackend.updateConversation(conversation);
	}

	public Contact findContact(String uuid) {
		Contact contact = this.databaseBackend.getContact(uuid);
		for (Account account : getAccounts()) {
			if (contact.getAccountUuid().equals(account.getUuid())) {
				contact.setAccount(account);
			}
		}
		return contact;
	}

	public void removeOnTLSExceptionReceivedListener() {
		this.tlsException = null;
	}

	//TODO dont let thread sleep but schedule wake up
	public void reconnectAccount(final Account account,final boolean force) {
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
					scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2),false);
				}
			}
		}).start();
	}
}