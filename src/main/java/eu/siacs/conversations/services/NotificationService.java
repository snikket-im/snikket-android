package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.BigPictureStyle;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.ui.TimePreference;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.UIHelper;

public class NotificationService {

	private final XmppConnectionService mXmppConnectionService;

	private final LinkedHashMap<String, ArrayList<Message>> notifications = new LinkedHashMap<>();

	public static final int NOTIFICATION_ID = 0x2342;
	public static final int FOREGROUND_NOTIFICATION_ID = 0x8899;
	public static final int ERROR_NOTIFICATION_ID = 0x5678;

	private Conversation mOpenConversation;
	private boolean mIsInForeground;
	private long mLastNotification;

	public NotificationService(final XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public boolean notify(final Message message) {
		return (message.getStatus() == Message.STATUS_RECEIVED)
				&& notificationsEnabled()
				&& !message.getConversation().isMuted()
				&& (message.getConversation().isPnNA()
				|| conferenceNotificationsEnabled()
				|| wasHighlightedOrPrivate(message)
		);
	}

	public void notifyPebble(final Message message) {
		final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

		final Conversation conversation = message.getConversation();
		final JSONObject jsonData = new JSONObject(new HashMap<String, String>(2) {{
			put("title", conversation.getName());
			put("body", message.getBody());
		}});
		final String notificationData = new JSONArray().put(jsonData).toString();

		i.putExtra("messageType", "PEBBLE_ALERT");
		i.putExtra("sender", "Conversations"); /* XXX: Shouldn't be hardcoded, e.g., AbstractGenerator.APP_NAME); */
		i.putExtra("notificationData", notificationData);
		// notify Pebble App
		i.setPackage("com.getpebble.android");
		mXmppConnectionService.sendBroadcast(i);
		// notify Gadgetbridge
		i.setPackage("nodomain.freeyourgadget.gadgetbridge");
		mXmppConnectionService.sendBroadcast(i);
	}


	public boolean notificationsEnabled() {
		return mXmppConnectionService.getPreferences().getBoolean("show_notification", true);
	}

	public boolean isQuietHours() {
		if (!mXmppConnectionService.getPreferences().getBoolean("enable_quiet_hours", false)) {
			return false;
		}
		final long startTime = mXmppConnectionService.getPreferences().getLong("quiet_hours_start", TimePreference.DEFAULT_VALUE) % Config.MILLISECONDS_IN_DAY;
		final long endTime = mXmppConnectionService.getPreferences().getLong("quiet_hours_end", TimePreference.DEFAULT_VALUE) % Config.MILLISECONDS_IN_DAY;
		final long nowTime = Calendar.getInstance().getTimeInMillis() % Config.MILLISECONDS_IN_DAY;

		if (endTime < startTime) {
			return nowTime > startTime || nowTime < endTime;
		} else {
			return nowTime > startTime && nowTime < endTime;
		}
	}

	public boolean conferenceNotificationsEnabled() {
		return mXmppConnectionService.getPreferences().getBoolean("always_notify_in_conference", false);
	}

	public void pushFromBacklog(final Message message) {
		if (notify(message)) {
			pushToStack(message);
		}
	}

	public void finishBacklog() {
		synchronized (notifications) {
			mXmppConnectionService.updateUnreadCountBadge();
			updateNotification(false);
		}
	}

	private void pushToStack(final Message message) {
		final String conversationUuid = message.getConversationUuid();
		if (notifications.containsKey(conversationUuid)) {
			notifications.get(conversationUuid).add(message);
		} else {
			final ArrayList<Message> mList = new ArrayList<>();
			mList.add(message);
			notifications.put(conversationUuid, mList);
		}
	}

	public void push(final Message message) {
		mXmppConnectionService.updateUnreadCountBadge();
		if (!notify(message)) {
			return;
		}
		final boolean isScreenOn = mXmppConnectionService.isInteractive();
		if (this.mIsInForeground && isScreenOn && this.mOpenConversation == message.getConversation()) {
			return;
		}
		synchronized (notifications) {
			pushToStack(message);
			final Account account = message.getConversation().getAccount();
			final boolean doNotify = (!(this.mIsInForeground && this.mOpenConversation == null) || !isScreenOn)
					&& !account.inGracePeriod()
					&& !this.inMiniGracePeriod(account);
			updateNotification(doNotify);
			if (doNotify) {
				notifyPebble(message);
			}
		}
	}

	public void clear() {
		synchronized (notifications) {
			notifications.clear();
			updateNotification(false);
		}
	}

	public void clear(final Conversation conversation) {
		synchronized (notifications) {
			notifications.remove(conversation.getUuid());
			updateNotification(false);
		}
	}

	private void setNotificationColor(final Builder mBuilder) {
		mBuilder.setColor(mXmppConnectionService.getResources().getColor(R.color.primary));
	}

	public void updateNotification(final boolean notify) {
		final NotificationManager notificationManager = (NotificationManager) mXmppConnectionService
				.getSystemService(Context.NOTIFICATION_SERVICE);
		final SharedPreferences preferences = mXmppConnectionService.getPreferences();

		final String ringtone = preferences.getString("notification_ringtone", null);
		final boolean vibrate = preferences.getBoolean("vibrate_on_notification", true);

		if (notifications.size() == 0) {
			notificationManager.cancel(NOTIFICATION_ID);
		} else {
			if (notify) {
				this.markLastNotification();
			}
			final Builder mBuilder;
			if (notifications.size() == 1) {
				mBuilder = buildSingleConversations(notify);
			} else {
				mBuilder = buildMultipleConversation();
			}
			if (notify && !isQuietHours()) {
				if (vibrate) {
					final int dat = 70;
					final long[] pattern = {0, 3 * dat, dat, dat};
					mBuilder.setVibrate(pattern);
				}
				if (ringtone != null) {
					mBuilder.setSound(Uri.parse(ringtone));
				}
			}
			if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				mBuilder.setCategory(Notification.CATEGORY_MESSAGE);
			}
			setNotificationColor(mBuilder);
			mBuilder.setDefaults(0);
			mBuilder.setSmallIcon(R.drawable.ic_notification);
			mBuilder.setDeleteIntent(createDeleteIntent());
			mBuilder.setLights(0xff00FF00, 2000, 3000);
			final Notification notification = mBuilder.build();
			notificationManager.notify(NOTIFICATION_ID, notification);
		}
	}

	private Builder buildMultipleConversation() {
		final Builder mBuilder = new NotificationCompat.Builder(
				mXmppConnectionService);
		final NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
		style.setBigContentTitle(notifications.size()
				+ " "
				+ mXmppConnectionService
				.getString(R.string.unread_conversations));
		final StringBuilder names = new StringBuilder();
		Conversation conversation = null;
		for (final ArrayList<Message> messages : notifications.values()) {
			if (messages.size() > 0) {
				conversation = messages.get(0).getConversation();
				final String name = conversation.getName();
				if (Config.PARANOID_MODE) {
					int count = messages.size();
					style.addLine(Html.fromHtml("<b>"+name+"</b> "+mXmppConnectionService.getResources().getQuantityString(R.plurals.x_messages,count,count)));
				} else {
					style.addLine(Html.fromHtml("<b>" + name + "</b> "
							+ UIHelper.getMessagePreview(mXmppConnectionService, messages.get(0)).first));
				}
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
			mBuilder.setContentIntent(createContentIntent(conversation));
		}
		return mBuilder;
	}

	private Builder buildSingleConversations(final boolean notify) {
		final Builder mBuilder = new NotificationCompat.Builder(
				mXmppConnectionService);
		final ArrayList<Message> messages = notifications.values().iterator().next();
		if (messages.size() >= 1) {
			final Conversation conversation = messages.get(0).getConversation();
			mBuilder.setLargeIcon(mXmppConnectionService.getAvatarService()
					.get(conversation, getPixel(64)));
			mBuilder.setContentTitle(conversation.getName());
			if (Config.PARANOID_MODE) {
				int count = messages.size();
				mBuilder.setContentText(mXmppConnectionService.getResources().getQuantityString(R.plurals.x_messages,count,count));
			} else {
				Message message;
				if ((message = getImage(messages)) != null) {
					modifyForImage(mBuilder, message, messages, notify);
				} else if (conversation.getMode() == Conversation.MODE_MULTI) {
					modifyForConference(mBuilder, conversation, messages, notify);
				} else {
					modifyForTextOnly(mBuilder, messages, notify);
				}
				if ((message = getFirstDownloadableMessage(messages)) != null) {
					mBuilder.addAction(
							Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
									R.drawable.ic_file_download_white_24dp : R.drawable.ic_action_download,
							mXmppConnectionService.getResources().getString(R.string.download_x_file,
									UIHelper.getFileDescriptionString(mXmppConnectionService, message)),
							createDownloadIntent(message)
					);
				}
				if ((message = getFirstLocationMessage(messages)) != null) {
					mBuilder.addAction(R.drawable.ic_room_white_24dp,
							mXmppConnectionService.getString(R.string.show_location),
							createShowLocationIntent(message));
				}
			}
			mBuilder.setContentIntent(createContentIntent(conversation));
		}
		return mBuilder;
	}

	private void modifyForImage(final Builder builder, final Message message,
								final ArrayList<Message> messages, final boolean notify) {
		try {
			final Bitmap bitmap = mXmppConnectionService.getFileBackend()
					.getThumbnail(message, getPixel(288), false);
			final ArrayList<Message> tmp = new ArrayList<>();
			for (final Message msg : messages) {
				if (msg.getType() == Message.TYPE_TEXT
						&& msg.getTransferable() == null) {
					tmp.add(msg);
				}
			}
			final BigPictureStyle bigPictureStyle = new NotificationCompat.BigPictureStyle();
			bigPictureStyle.bigPicture(bitmap);
			if (tmp.size() > 0) {
				bigPictureStyle.setSummaryText(getMergedBodies(tmp));
				builder.setContentText(UIHelper.getMessagePreview(mXmppConnectionService, tmp.get(0)).first);
			} else {
				builder.setContentText(mXmppConnectionService.getString(
						R.string.received_x_file,
						UIHelper.getFileDescriptionString(mXmppConnectionService, message)));
			}
			builder.setStyle(bigPictureStyle);
		} catch (final FileNotFoundException e) {
			modifyForTextOnly(builder, messages, notify);
		}
	}

	private void modifyForTextOnly(final Builder builder,
								   final ArrayList<Message> messages, final boolean notify) {
		builder.setStyle(new NotificationCompat.BigTextStyle().bigText(getMergedBodies(messages)));
		builder.setContentText(UIHelper.getMessagePreview(mXmppConnectionService, messages.get(0)).first);
		if (notify) {
			builder.setTicker(UIHelper.getMessagePreview(mXmppConnectionService, messages.get(messages.size() - 1)).first);
		}
	}

	private void modifyForConference(Builder builder, Conversation conversation, List<Message> messages, boolean notify) {
		final Message first = messages.get(0);
		final Message last = messages.get(messages.size() - 1);
		final NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
		style.setBigContentTitle(conversation.getName());
		for(Message message : messages) {
			style.addLine(Html.fromHtml("<b>"+UIHelper.getMessageDisplayName(message)+"</b> "+UIHelper.getMessagePreview(mXmppConnectionService,message).first));
		}
		builder.setContentText(UIHelper.getMessageDisplayName(first)+ ": " +UIHelper.getMessagePreview(mXmppConnectionService, messages.get(0)).first);
		builder.setStyle(style);
		if (notify) {
			builder.setTicker(UIHelper.getMessageDisplayName(last) + ": " + UIHelper.getMessagePreview(mXmppConnectionService,last).first);
		}
	}

	private Message getImage(final Iterable<Message> messages) {
		for (final Message message : messages) {
			if (message.getType() != Message.TYPE_TEXT
					&& message.getTransferable() == null
					&& message.getEncryption() != Message.ENCRYPTION_PGP
					&& message.getFileParams().height > 0) {
				return message;
			}
		}
		return null;
	}

	private Message getFirstDownloadableMessage(final Iterable<Message> messages) {
		for (final Message message : messages) {
			if ((message.getType() == Message.TYPE_FILE || message.getType() == Message.TYPE_IMAGE) &&
					message.getTransferable() != null) {
				return message;
			}
		}
		return null;
	}

	private Message getFirstLocationMessage(final Iterable<Message> messages) {
		for (final Message message : messages) {
			if (GeoHelper.isGeoUri(message.getBody())) {
				return message;
			}
		}
		return null;
	}

	private CharSequence getMergedBodies(final ArrayList<Message> messages) {
		final StringBuilder text = new StringBuilder();
		for (int i = 0; i < messages.size(); ++i) {
			text.append(UIHelper.getMessagePreview(mXmppConnectionService, messages.get(i)).first);
			if (i != messages.size() - 1) {
				text.append("\n");
			}
		}
		return text.toString();
	}

	private PendingIntent createShowLocationIntent(final Message message) {
		Iterable<Intent> intents = GeoHelper.createGeoIntentsFromMessage(message);
		for (Intent intent : intents) {
			if (intent.resolveActivity(mXmppConnectionService.getPackageManager()) != null) {
				return PendingIntent.getActivity(mXmppConnectionService, 18, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			}
		}
		return createOpenConversationsIntent();
	}

	private PendingIntent createContentIntent(final String conversationUuid, final String downloadMessageUuid) {
		final TaskStackBuilder stackBuilder = TaskStackBuilder
				.create(mXmppConnectionService);
		stackBuilder.addParentStack(ConversationActivity.class);

		final Intent viewConversationIntent = new Intent(mXmppConnectionService,
				ConversationActivity.class);
		if (downloadMessageUuid != null) {
			viewConversationIntent.setAction(ConversationActivity.ACTION_DOWNLOAD);
		} else {
			viewConversationIntent.setAction(Intent.ACTION_VIEW);
		}
		if (conversationUuid != null) {
			viewConversationIntent.putExtra(ConversationActivity.CONVERSATION, conversationUuid);
			viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);
		}
		if (downloadMessageUuid != null) {
			viewConversationIntent.putExtra(ConversationActivity.MESSAGE, downloadMessageUuid);
		}

		stackBuilder.addNextIntent(viewConversationIntent);

		return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	private PendingIntent createDownloadIntent(final Message message) {
		return createContentIntent(message.getConversationUuid(), message.getUuid());
	}

	private PendingIntent createContentIntent(final Conversation conversation) {
		return createContentIntent(conversation.getUuid(), null);
	}

	private PendingIntent createDeleteIntent() {
		final Intent intent = new Intent(mXmppConnectionService,
				XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_CLEAR_NOTIFICATION);
		return PendingIntent.getService(mXmppConnectionService, 0, intent, 0);
	}

	private PendingIntent createDisableForeground() {
		final Intent intent = new Intent(mXmppConnectionService,
				XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_DISABLE_FOREGROUND);
		return PendingIntent.getService(mXmppConnectionService, 34, intent, 0);
	}

	private PendingIntent createTryAgainIntent() {
		final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_TRY_AGAIN);
		return PendingIntent.getService(mXmppConnectionService, 45, intent, 0);
	}

	private PendingIntent createDisableAccountIntent(final Account account) {
		final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_DISABLE_ACCOUNT);
		intent.putExtra("account", account.getJid().toBareJid().toString());
		return PendingIntent.getService(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
	}

	private boolean wasHighlightedOrPrivate(final Message message) {
		final String nick = message.getConversation().getMucOptions().getActualNick();
		final Pattern highlight = generateNickHighlightPattern(nick);
		if (message.getBody() == null || nick == null) {
			return false;
		}
		final Matcher m = highlight.matcher(message.getBody());
		return (m.find() || message.getType() == Message.TYPE_PRIVATE);
	}

	private static Pattern generateNickHighlightPattern(final String nick) {
		// We expect a word boundary, i.e. space or start of string, followed by
		// the
		// nick (matched in case-insensitive manner), followed by optional
		// punctuation (for example "bob: i disagree" or "how are you alice?"),
		// followed by another word boundary.
		return Pattern.compile("\\b" + Pattern.quote(nick) + "\\p{Punct}?\\b",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	public void setOpenConversation(final Conversation conversation) {
		this.mOpenConversation = conversation;
	}

	public void setIsInForeground(final boolean foreground) {
		this.mIsInForeground = foreground;
	}

	private int getPixel(final int dp) {
		final DisplayMetrics metrics = mXmppConnectionService.getResources()
				.getDisplayMetrics();
		return ((int) (dp * metrics.density));
	}

	private void markLastNotification() {
		this.mLastNotification = SystemClock.elapsedRealtime();
	}

	private boolean inMiniGracePeriod(final Account account) {
		final int miniGrace = account.getStatus() == Account.State.ONLINE ? Config.MINI_GRACE_PERIOD
				: Config.MINI_GRACE_PERIOD * 2;
		return SystemClock.elapsedRealtime() < (this.mLastNotification + miniGrace);
	}

	public Notification createForegroundNotification() {
		final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);

		mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.conversations_foreground_service));
		if (Config.SHOW_CONNECTED_ACCOUNTS) {
			List<Account> accounts = mXmppConnectionService.getAccounts();
			int enabled = 0;
			int connected = 0;
			for (Account account : accounts) {
				if (account.isOnlineAndConnected()) {
					connected++;
					enabled++;
				} else if (!account.isOptionSet(Account.OPTION_DISABLED)) {
					enabled++;
				}
			}
			mBuilder.setContentText(mXmppConnectionService.getString(R.string.connected_accounts, connected, enabled));
		} else {
			mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_open_conversations));
		}
		mBuilder.setContentIntent(createOpenConversationsIntent());
		mBuilder.setWhen(0);
		mBuilder.setPriority(Config.SHOW_CONNECTED_ACCOUNTS ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_MIN);
		final int cancelIcon;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mBuilder.setCategory(Notification.CATEGORY_SERVICE);
			cancelIcon = R.drawable.ic_cancel_white_24dp;
		} else {
			cancelIcon = R.drawable.ic_action_cancel;
		}
		mBuilder.setSmallIcon(R.drawable.ic_link_white_24dp);
		mBuilder.addAction(cancelIcon,
				mXmppConnectionService.getString(R.string.disable_foreground_service),
				createDisableForeground());
		return mBuilder.build();
	}

	private PendingIntent createOpenConversationsIntent() {
		return PendingIntent.getActivity(mXmppConnectionService, 0, new Intent(mXmppConnectionService, ConversationActivity.class), 0);
	}

	public void updateErrorNotification() {
		final NotificationManager notificationManager = (NotificationManager) mXmppConnectionService.getSystemService(Context.NOTIFICATION_SERVICE);
		final List<Account> errors = new ArrayList<>();
		for (final Account account : mXmppConnectionService.getAccounts()) {
			if (account.hasErrorStatus()) {
				errors.add(account);
			}
		}
		if (mXmppConnectionService.getPreferences().getBoolean("keep_foreground_service", false)) {
			notificationManager.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
		}
		final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
		if (errors.size() == 0) {
			notificationManager.cancel(ERROR_NOTIFICATION_ID);
			return;
		} else if (errors.size() == 1) {
			mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_account));
			mBuilder.setContentText(errors.get(0).getJid().toBareJid().toString());
		} else {
			mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_accounts));
			mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_fix));
		}
		mBuilder.addAction(R.drawable.ic_autorenew_white_24dp,
				mXmppConnectionService.getString(R.string.try_again),
				createTryAgainIntent());
		if (errors.size() == 1) {
			mBuilder.addAction(R.drawable.ic_block_white_24dp,
					mXmppConnectionService.getString(R.string.disable_account),
					createDisableAccountIntent(errors.get(0)));
		}
		mBuilder.setOngoing(true);
		//mBuilder.setLights(0xffffffff, 2000, 4000);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			mBuilder.setSmallIcon(R.drawable.ic_warning_white_24dp);
		} else {
			mBuilder.setSmallIcon(R.drawable.ic_stat_alert_warning);
		}
		final TaskStackBuilder stackBuilder = TaskStackBuilder.create(mXmppConnectionService);
		stackBuilder.addParentStack(ConversationActivity.class);

		final Intent manageAccountsIntent = new Intent(mXmppConnectionService, ManageAccountActivity.class);
		stackBuilder.addNextIntent(manageAccountsIntent);

		final PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

		mBuilder.setContentIntent(resultPendingIntent);
		notificationManager.notify(ERROR_NOTIFICATION_ID, mBuilder.build());
	}
}
