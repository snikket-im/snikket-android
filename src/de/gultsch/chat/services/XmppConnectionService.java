package de.gultsch.chat.services;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.entities.Presences;
import de.gultsch.chat.persistance.DatabaseBackend;
import de.gultsch.chat.ui.OnAccountListChangedListener;
import de.gultsch.chat.ui.OnConversationListChangedListener;
import de.gultsch.chat.ui.OnRosterFetchedListener;
import de.gultsch.chat.utils.OnPhoneContactsLoadedListener;
import de.gultsch.chat.utils.PhoneHelper;
import de.gultsch.chat.utils.UIHelper;
import de.gultsch.chat.xml.Element;
import de.gultsch.chat.xmpp.IqPacket;
import de.gultsch.chat.xmpp.MessagePacket;
import de.gultsch.chat.xmpp.OnIqPacketReceived;
import de.gultsch.chat.xmpp.OnMessagePacketReceived;
import de.gultsch.chat.xmpp.OnPresencePacketReceived;
import de.gultsch.chat.xmpp.OnStatusChanged;
import de.gultsch.chat.xmpp.PresencePacket;
import de.gultsch.chat.xmpp.XmppConnection;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.ContactsContract;
import android.util.Log;

public class XmppConnectionService extends Service {

	protected static final String LOGTAG = "xmppService";
	protected DatabaseBackend databaseBackend;

	public long startDate;

	private List<Account> accounts;
	private List<Conversation> conversations = null;

	private OnConversationListChangedListener convChangedListener = null;
	private OnAccountListChangedListener accountChangedListener = null;

	private ContentObserver contactObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			Log.d(LOGTAG, "contact list has changed");
			mergePhoneContactsWithRoster();
		}
	};

	private final IBinder mBinder = new XmppConnectionBinder();
	private OnMessagePacketReceived messageListener = new OnMessagePacketReceived() {

		@Override
		public void onMessagePacketReceived(Account account,
				MessagePacket packet) {
			if ((packet.getType() == MessagePacket.TYPE_CHAT)
					|| (packet.getType() == MessagePacket.TYPE_GROUPCHAT)) {
				boolean notify = true;
				boolean runOtrCheck = false;
				int status = Message.STATUS_RECIEVED;
				int encryption = Message.ENCRYPTION_NONE;
				String body;
				String fullJid;
				if (!packet.hasChild("body")) {
					Element forwarded;
					if (packet.hasChild("received")) {
						forwarded = packet.findChild("received").findChild(
								"forwarded");
					} else if (packet.hasChild("sent")) {
						forwarded = packet.findChild("sent").findChild(
								"forwarded");
						status = Message.STATUS_SEND;
						notify = false;
					} else {
						return; // massage has no body and is not carbon. just
						// skip
					}
					if (forwarded != null) {
						Element message = forwarded.findChild("message");
						if ((message == null) || (!message.hasChild("body")))
							return; // either malformed or boring
						if (status == Message.STATUS_RECIEVED) {
							fullJid = message.getAttribute("from");
						} else {
							fullJid = message.getAttribute("to");
						}
						body = message.findChild("body").getContent();
					} else {
						return; // packet malformed. has no forwarded element
					}
				} else {
					fullJid = packet.getFrom();
					body = packet.getBody();
					runOtrCheck = true;
				}
				Conversation conversation = null;
				String[] fromParts = fullJid.split("/");
				String jid = fromParts[0];
				boolean muc = (packet.getType() == MessagePacket.TYPE_GROUPCHAT);
				String counterPart = null;
				conversation = findOrCreateConversation(account, jid, muc);
				if (muc) {
					if ((fromParts.length == 1) || (packet.hasChild("subject"))) {
						return;
					}
					counterPart = fromParts[1];
					if (counterPart.equals(account.getUsername())) {
						status = Message.STATUS_SEND;
						notify = false;
					}
				} else {
					counterPart = fullJid;
					if ((runOtrCheck) && body.startsWith("?OTR")) {
						if (!conversation.hasValidOtrSession()) {
							conversation.startOtrSession(
									getApplicationContext(), fromParts[1]);
						}
						try {
							Session otrSession = conversation.getOtrSession();
							SessionStatus before = otrSession
									.getSessionStatus();
							body = otrSession.transformReceiving(body);
							SessionStatus after = otrSession.getSessionStatus();
							if ((before != after)
									&& (after == SessionStatus.ENCRYPTED)) {
								Log.d(LOGTAG, "otr session etablished");
								List<Message> messages = conversation
										.getMessages();
								for (int i = 0; i < messages.size(); ++i) {
									Message msg = messages.get(i);
									if ((msg.getStatus() == Message.STATUS_UNSEND)
											&& (msg.getEncryption() == Message.ENCRYPTION_OTR)) {
										MessagePacket outPacket = prepareMessagePacket(
												account, msg, otrSession);
										msg.setStatus(Message.STATUS_SEND);
										databaseBackend.updateMessage(msg);
										account.getXmppConnection()
												.sendMessagePacket(outPacket);
									}
								}
								if (convChangedListener!=null) {
									convChangedListener.onConversationListChanged();
								}
							} else if ((before != after) && (after == SessionStatus.FINISHED)) {
								conversation.resetOtrSession();
								Log.d(LOGTAG,"otr session stoped");
							}
						} catch (Exception e) {
							Log.d(LOGTAG, "error receiving otr. resetting");
							conversation.resetOtrSession();
							return;
						}
						if (body == null) {
							return;
						}
						encryption = Message.ENCRYPTION_OTR;
					}
				}
				Message message = new Message(conversation, counterPart, body,
						encryption, status);
				if (packet.hasChild("delay")) {
					try {
						String stamp = packet.findChild("delay").getAttribute(
								"stamp");
						stamp = stamp.replace("Z", "+0000");
						Date date = new SimpleDateFormat(
								"yyyy-MM-dd'T'HH:mm:ssZ").parse(stamp);
						message.setTime(date.getTime());
					} catch (ParseException e) {
						Log.d(LOGTAG,
								"error trying to parse date" + e.getMessage());
					}
				}
				if (notify) {
					message.markUnread();
				}
				conversation.getMessages().add(message);
				databaseBackend.createMessage(message);
				if (convChangedListener != null) {
					convChangedListener.onConversationListChanged();
				} else {
					if (notify) {
						NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
						mNotificationManager.notify(2342, UIHelper
								.getUnreadMessageNotification(
										getApplicationContext(), conversation));
					}
				}
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
			}
		}
	};

	private OnPresencePacketReceived presenceListener = new OnPresencePacketReceived() {

		@Override
		public void onPresencePacketReceived(Account account,
				PresencePacket packet) {
			String[] fromParts = packet.getAttribute("from").split("/");
			Contact contact = findContact(account, fromParts[0]);
			if (contact == null) {
				// most likely muc, self or roster not synced
				// Log.d(LOGTAG,"got presence for non contact "+packet.toString());
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
				databaseBackend.updateContact(contact);
			} else if (type.equals("unavailable")) {
				if (fromParts.length != 2) {
					// Log.d(LOGTAG,"received presence with no resource "+packet.toString());
				} else {
					contact.removePresence(fromParts[1]);
					databaseBackend.updateContact(contact);
				}
			}
			replaceContactInConversation(contact);
		}
	};
	
	private void replaceContactInConversation(Contact contact) {
		List<Conversation> conversations = getConversations();
		for(int i = 0; i < conversations.size(); ++i) {
			if (conversations.get(i).getContact().equals(contact)) {
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
		for (Account account : accounts) {
			if (account.getXmppConnection() == null) {
				if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					account.setXmppConnection(this.createConnection(account));
				}
			}
		}
		return START_STICKY;
	}

	@Override
	public void onCreate() {
		databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
		this.accounts = databaseBackend.getAccounts();

		getContentResolver().registerContentObserver(
				ContactsContract.Contacts.CONTENT_URI, true, contactObserver);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		for (Account account : accounts) {
			if (account.getXmppConnection() != null) {
				disconnect(account);
			}
		}
	}

	public XmppConnection createConnection(Account account) {
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		XmppConnection connection = new XmppConnection(account, pm);
		connection.setOnMessagePacketReceivedListener(this.messageListener);
		connection.setOnStatusChangedListener(this.statusListener);
		connection.setOnPresencePacketReceivedListener(this.presenceListener);
		Thread thread = new Thread(connection);
		thread.start();
		return connection;
	}
	
	public void sendMessage(Account account, Message message, String presence) {
		Conversation conv = message.getConversation();
		boolean saveInDb = false;
		boolean addToConversation = false;
		if (account.getStatus() == Account.STATUS_ONLINE) {
			MessagePacket packet;
			if (message.getEncryption() == Message.ENCRYPTION_OTR) {
				if (!conv.hasValidOtrSession()) {
					//starting otr session. messages will be send later
					conv.startOtrSession(getApplicationContext(), presence);
				} else if (conv.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED){
					//otr session aleary exists, creating message packet accordingly
					packet = prepareMessagePacket(account, message,
							conv.getOtrSession());
					account.getXmppConnection().sendMessagePacket(packet);
					message.setStatus(Message.STATUS_SEND);
				}
				saveInDb = true;
				addToConversation = true;
			} else {
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

	private MessagePacket prepareMessagePacket(Account account,
			Message message, Session otrSession) {
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
				packet.setTo(otrSession.getSessionID().getAccountID()+"/"+otrSession.getSessionID().getUserID());
				packet.setFrom(account.getFullJid());
			} else {
				packet.setBody(message.getBody());
				packet.setTo(message.getCounterpart());
				packet.setFrom(account.getJid());
			}
		} else if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
			packet.setType(MessagePacket.TYPE_GROUPCHAT);
			packet.setBody(message.getBody());
			packet.setTo(message.getCounterpart());
			packet.setFrom(account.getJid());
		}
		return packet;
	}

	public void getRoster(Account account,
			final OnRosterFetchedListener listener) {
		List<Contact> contacts = databaseBackend.getContacts(account);
		for (int i = 0; i < contacts.size(); ++i) {
			contacts.get(i).setAccount(account);
		}
		if (listener != null) {
			listener.onRosterFetched(contacts);
		}
	}

	public void updateRoster(final Account account,
			final OnRosterFetchedListener listener) {

		PhoneHelper.loadPhoneContacts(this,
				new OnPhoneContactsLoadedListener() {

					@Override
					public void onPhoneContactsLoaded(
							final Hashtable<String, Bundle> phoneContacts) {
						IqPacket iqPacket = new IqPacket(IqPacket.TYPE_GET);
						Element query = new Element("query");
						query.setAttribute("xmlns", "jabber:iq:roster");
						query.setAttribute("ver", "");
						iqPacket.addChild(query);
						account.getXmppConnection().sendIqPacket(iqPacket,
								new OnIqPacketReceived() {

									@Override
									public void onIqPacketReceived(
											Account account, IqPacket packet) {
										List<Contact> contacts = new ArrayList<Contact>();
										Element roster = packet
												.findChild("query");
										if (roster != null) {
											for (Element item : roster
													.getChildren()) {
												Contact contact;
												String name = item
														.getAttribute("name");
												String jid = item
														.getAttribute("jid");
												if (phoneContacts
														.containsKey(jid)) {
													Bundle phoneContact = phoneContacts
															.get(jid);
													String systemAccount = phoneContact
															.getInt("phoneid")
															+ "#"
															+ phoneContact
																	.getString("lookup");
													contact = new Contact(
															account,
															phoneContact
																	.getString("displayname"),
															jid,
															phoneContact
																	.getString("photouri"));
													contact.setSystemAccount(systemAccount);
												} else {
													if (name == null) {
														name = jid.split("@")[0];
													}
													contact = new Contact(
															account, name, jid,
															null);

												}
												contact.setAccount(account);
												contact.setSubscription(item
														.getAttribute("subscription"));
												contacts.add(contact);
											}
											databaseBackend
													.mergeContacts(contacts);
											if (listener != null) {
												listener.onRosterFetched(contacts);
											}
										}
									}
								});

					}
				});
	}

	public void mergePhoneContactsWithRoster() {
		PhoneHelper.loadPhoneContacts(this,
				new OnPhoneContactsLoadedListener() {
					@Override
					public void onPhoneContactsLoaded(
							Hashtable<String, Bundle> phoneContacts) {
						List<Contact> contacts = databaseBackend
								.getContacts(null);
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
							} else {
								if ((contact.getSystemAccount() != null)
										|| (contact.getProfilePhoto() != null)) {
									contact.setSystemAccount(null);
									contact.setPhotoUri(null);
									databaseBackend.updateContact(contact);
								}
							}
						}
					}
				});
	}

	public void addConversation(Conversation conversation) {
		databaseBackend.createConversation(conversation);
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
		return databaseBackend.findContact(account, jid);
	}

	public Conversation findOrCreateConversation(Account account, String jid,
			boolean muc) {
		for (Conversation conv : this.getConversations()) {
			if ((conv.getAccount().equals(account))
					&& (conv.getContactJid().equals(jid))) {
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
			this.databaseBackend.updateConversation(conversation);
			conversation.setContact(findContact(account, conversation.getContactJid()));
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
			try {
				conversation.endOtrIfNeeded();
			} catch (OtrException e) {
				Log.d(LOGTAG,
						"error ending otr session for "
								+ conversation.getName());
			}
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
		account.setXmppConnection(this.createConnection(account));
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void updateAccount(Account account) {
		databaseBackend.updateAccount(account);
		if (account.getXmppConnection() != null) {
			disconnect(account);
		}
		if (!account.isOptionSet(Account.OPTION_DISABLED)) {
			account.setXmppConnection(this.createConnection(account));
		}
		if (accountChangedListener != null)
			accountChangedListener.onAccountListChangedListener();
	}

	public void deleteAccount(Account account) {
		Log.d(LOGTAG, "called delete account");
		if (account.getXmppConnection() != null) {
			this.disconnect(account);
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
		String muc = conversation.getContactJid();
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("to", muc + "/"
				+ conversation.getAccount().getUsername());
		Element x = new Element("x");
		x.setAttribute("xmlns", "http://jabber.org/protocol/muc");
		if (conversation.getMessages().size() != 0) {
			Element history = new Element("history");
			long lastMsgTime = conversation.getLatestMessage().getTimeSent();
			long diff = (System.currentTimeMillis() - lastMsgTime) / 1000;
			history.setAttribute("seconds",diff+"");
			x.addChild(history);
		}
		packet.addChild(x);
		conversation.getAccount().getXmppConnection()
				.sendPresencePacket(packet);
	}

	public void leaveMuc(Conversation conversation) {

	}

	public void disconnect(Account account) {
		List<Conversation> conversations = getConversations();
		for (int i = 0; i < conversations.size(); i++) {
			Conversation conversation = conversations.get(i);
			if (conversation.getAccount() == account) {
				if (conversation.getMode() == Conversation.MODE_MULTI) {
					leaveMuc(conversation);
				} else {
					try {
						conversation.endOtrIfNeeded();
					} catch (OtrException e) {
						Log.d(LOGTAG, "error ending otr session for "
								+ conversation.getName());
					}
				}
			}
		}
		account.getXmppConnection().disconnect();
		Log.d(LOGTAG, "disconnected account: " + account.getJid());
		account.setXmppConnection(null);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	public void updateContact(Contact contact) {
		databaseBackend.updateContact(contact);
	}
}