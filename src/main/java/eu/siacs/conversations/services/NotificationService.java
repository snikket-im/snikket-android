package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.BigPictureStyle;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.DisplayMetrics;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;

public class NotificationService {

	private XmppConnectionService mXmppConnectionService;

	private LinkedHashMap<String, ArrayList<Message>> notifications = new LinkedHashMap<String, ArrayList<Message>>();

	public static int NOTIFICATION_ID = 0x2342;
	private Conversation mOpenConversation;
	private boolean mIsInForeground;
	private long mLastNotification;

	public NotificationService(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public boolean notify(Message message) {
		return (message.getStatus() == Message.STATUS_RECEIVED)
				&& notificationsEnabled()
				&& !message.getConversation().isMuted()
				&& (message.getConversation().getMode() == Conversation.MODE_SINGLE
					|| conferenceNotificationsEnabled()
					|| wasHighlightedOrPrivate(message)
					);
	}

	public boolean notificationsEnabled() {
		return mXmppConnectionService.getPreferences().getBoolean("show_notification", true);
	}

	public boolean conferenceNotificationsEnabled() {
		return mXmppConnectionService.getPreferences().getBoolean("always_notify_in_conference", false);
	}

	public void push(Message message) {
		if (!notify(message)) {
			return;
		}
		PowerManager pm = (PowerManager) mXmppConnectionService
				.getSystemService(Context.POWER_SERVICE);
		boolean isScreenOn = pm.isScreenOn();

		if (this.mIsInForeground && isScreenOn
				&& this.mOpenConversation == message.getConversation()) {
			return;
		}
		synchronized (notifications) {
			String conversationUuid = message.getConversationUuid();
			if (notifications.containsKey(conversationUuid)) {
				notifications.get(conversationUuid).add(message);
			} else {
				ArrayList<Message> mList = new ArrayList<Message>();
				mList.add(message);
				notifications.put(conversationUuid, mList);
			}
			Account account = message.getConversation().getAccount();
			updateNotification((!(this.mIsInForeground && this.mOpenConversation == null) || !isScreenOn)
					&& !account.inGracePeriod()
					&& !this.inMiniGracePeriod(account));
		}

	}

	public void clear() {
		synchronized (notifications) {
			notifications.clear();
			updateNotification(false);
		}
	}

	public void clear(Conversation conversation) {
		synchronized (notifications) {
			notifications.remove(conversation.getUuid());
			updateNotification(false);
		}
	}

	private void updateNotification(boolean notify) {
		NotificationManager notificationManager = (NotificationManager) mXmppConnectionService
				.getSystemService(Context.NOTIFICATION_SERVICE);
		SharedPreferences preferences = mXmppConnectionService.getPreferences();

		String ringtone = preferences.getString("notification_ringtone", null);
		boolean vibrate = preferences.getBoolean("vibrate_on_notification",
				true);

		if (notifications.size() == 0) {
			notificationManager.cancel(NOTIFICATION_ID);
		} else {
			if (notify) {
				this.markLastNotification();
			}
			Builder mBuilder;
			if (notifications.size() == 1) {
				mBuilder = buildSingleConversations(notify);
			} else {
				mBuilder = buildMultipleConversation();
			}
			if (notify) {
				if (vibrate) {
					int dat = 70;
					long[] pattern = {0, 3 * dat, dat, dat};
					mBuilder.setVibrate(pattern);
				}
				if (ringtone != null) {
					mBuilder.setSound(Uri.parse(ringtone));
				}
			}
			mBuilder.setSmallIcon(R.drawable.ic_notification);
			mBuilder.setDeleteIntent(createDeleteIntent());
			mBuilder.setLights(0xffffffff, 2000, 4000);
			Notification notification = mBuilder.build();
			notificationManager.notify(NOTIFICATION_ID, notification);
		}
	}

	private Builder buildMultipleConversation() {
		Builder mBuilder = new NotificationCompat.Builder(
				mXmppConnectionService);
		NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
		style.setBigContentTitle(notifications.size()
				+ " "
				+ mXmppConnectionService
				.getString(R.string.unread_conversations));
		StringBuilder names = new StringBuilder();
		Conversation conversation = null;
		for (ArrayList<Message> messages : notifications.values()) {
			if (messages.size() > 0) {
				conversation = messages.get(0).getConversation();
				String name = conversation.getName();
				style.addLine(Html.fromHtml("<b>" + name + "</b> "
						+ getReadableBody(messages.get(0))));
				names.append(name);
				names.append(", ");
			}
		}
		if (names.length() >= 2) {
			names.delete(names.length() - 2, names.length());
		}
		mBuilder.setContentTitle(notifications.size()
				+ " "
				+ mXmppConnectionService
				.getString(R.string.unread_conversations));
		mBuilder.setContentText(names.toString());
		mBuilder.setStyle(style);
		if (conversation != null) {
			mBuilder.setContentIntent(createContentIntent(conversation
					.getUuid()));
		}
		return mBuilder;
	}

	private Builder buildSingleConversations(boolean notify) {
		Builder mBuilder = new NotificationCompat.Builder(
				mXmppConnectionService);
		ArrayList<Message> messages = notifications.values().iterator().next();
		if (messages.size() >= 1) {
			Conversation conversation = messages.get(0).getConversation();
			mBuilder.setLargeIcon(mXmppConnectionService.getAvatarService()
					.get(conversation, getPixel(64)));
			mBuilder.setContentTitle(conversation.getName());
			Message message;
			if ((message = getImage(messages)) != null) {
				modifyForImage(mBuilder, message, messages, notify);
			} else {
				modifyForTextOnly(mBuilder, messages, notify);
			}
			mBuilder.setContentIntent(createContentIntent(conversation
					.getUuid()));
		}
		return mBuilder;

	}

	private void modifyForImage(Builder builder, Message message,
								ArrayList<Message> messages, boolean notify) {
		try {
			Bitmap bitmap = mXmppConnectionService.getFileBackend()
					.getThumbnail(message, getPixel(288), false);
			ArrayList<Message> tmp = new ArrayList<Message>();
			for (Message msg : messages) {
				if (msg.getType() == Message.TYPE_TEXT
						&& msg.getDownloadable() == null) {
					tmp.add(msg);
				}
			}
			BigPictureStyle bigPictureStyle = new NotificationCompat.BigPictureStyle();
			bigPictureStyle.bigPicture(bitmap);
			if (tmp.size() > 0) {
				bigPictureStyle.setSummaryText(getMergedBodies(tmp));
				builder.setContentText(getReadableBody(tmp.get(0)));
			} else {
				builder.setContentText(mXmppConnectionService.getString(R.string.image_file));
			}
			builder.setStyle(bigPictureStyle);
		} catch (FileNotFoundException e) {
			modifyForTextOnly(builder, messages, notify);
		}
	}

	private void modifyForTextOnly(Builder builder,
								   ArrayList<Message> messages, boolean notify) {
		builder.setStyle(new NotificationCompat.BigTextStyle()
				.bigText(getMergedBodies(messages)));
		builder.setContentText(getReadableBody(messages.get(0)));
		if (notify) {
			builder.setTicker(getReadableBody(messages.get(messages.size() - 1)));
		}
	}

	private Message getImage(ArrayList<Message> messages) {
		for (Message message : messages) {
			if (message.getType() == Message.TYPE_IMAGE
					&& message.getDownloadable() == null
					&& message.getEncryption() != Message.ENCRYPTION_PGP) {
				return message;
			}
		}
		return null;
	}

	private String getMergedBodies(ArrayList<Message> messages) {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < messages.size(); ++i) {
			text.append(getReadableBody(messages.get(i)));
			if (i != messages.size() - 1) {
				text.append("\n");
			}
		}
		return text.toString();
	}

	private String getReadableBody(Message message) {
		if (message.getDownloadable() != null
				&& (message.getDownloadable().getStatus() == Downloadable.STATUS_OFFER || message
				.getDownloadable().getStatus() == Downloadable.STATUS_OFFER_CHECK_FILESIZE)) {
			return mXmppConnectionService.getText(
					R.string.image_offered_for_download).toString();
		} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			return mXmppConnectionService.getText(
					R.string.encrypted_message_received).toString();
		} else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
			return mXmppConnectionService.getText(R.string.decryption_failed)
					.toString();
		} else if (message.getType() == Message.TYPE_IMAGE) {
			return mXmppConnectionService.getText(R.string.image_file)
					.toString();
		} else {
			return message.getBody().trim();
		}
	}

	private PendingIntent createContentIntent(String conversationUuid) {
		TaskStackBuilder stackBuilder = TaskStackBuilder
				.create(mXmppConnectionService);
		stackBuilder.addParentStack(ConversationActivity.class);

		Intent viewConversationIntent = new Intent(mXmppConnectionService,
				ConversationActivity.class);
		viewConversationIntent.setAction(Intent.ACTION_VIEW);
		viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
				conversationUuid);
		viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);

		stackBuilder.addNextIntent(viewConversationIntent);

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);
		return resultPendingIntent;
	}

	private PendingIntent createDeleteIntent() {
		Intent intent = new Intent(mXmppConnectionService,
				XmppConnectionService.class);
		intent.setAction("clear_notification");
		return PendingIntent.getService(mXmppConnectionService, 0, intent, 0);
	}

	private boolean wasHighlightedOrPrivate(Message message) {
		String nick = message.getConversation().getMucOptions().getActualNick();
		Pattern highlight = generateNickHighlightPattern(nick);
		if (message.getBody() == null || nick == null) {
			return false;
		}
		Matcher m = highlight.matcher(message.getBody());
		return (m.find() || message.getType() == Message.TYPE_PRIVATE);
	}

	private static Pattern generateNickHighlightPattern(String nick) {
		// We expect a word boundary, i.e. space or start of string, followed by
		// the
		// nick (matched in case-insensitive manner), followed by optional
		// punctuation (for example "bob: i disagree" or "how are you alice?"),
		// followed by another word boundary.
		return Pattern.compile("\\b" + nick + "\\p{Punct}?\\b",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	public void setOpenConversation(Conversation conversation) {
		this.mOpenConversation = conversation;
	}

	public void setIsInForeground(boolean foreground) {
		this.mIsInForeground = foreground;
	}

	private int getPixel(int dp) {
		DisplayMetrics metrics = mXmppConnectionService.getResources()
				.getDisplayMetrics();
		return ((int) (dp * metrics.density));
	}

	private void markLastNotification() {
		this.mLastNotification = SystemClock.elapsedRealtime();
	}

	private boolean inMiniGracePeriod(Account account) {
		int miniGrace = account.getStatus() == Account.STATUS_ONLINE ? Config.MINI_GRACE_PERIOD
				: Config.MINI_GRACE_PERIOD * 2;
		return SystemClock.elapsedRealtime() < (this.mLastNotification + miniGrace);
	}
}
