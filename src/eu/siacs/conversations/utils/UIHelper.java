package eu.siacs.conversations.utils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.Html;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class UIHelper {
	private static final int BG_COLOR = 0xFF181818;
	private static final int FG_COLOR = 0xFFFAFAFA;
	private static final int TRANSPARENT = 0x00000000;
	private static final int DATE_NO_YEAR_FLAGS = DateUtils.FORMAT_SHOW_DATE
			| DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;

	public static String readableTimeDifference(Context context, long time) {
		if (time == 0) {
			return context.getString(R.string.just_now);
		}
		Date date = new Date(time);
		long difference = (System.currentTimeMillis() - time) / 1000;
		if (difference < 60) {
			return context.getString(R.string.just_now);
		} else if (difference < 60 * 2) {
			return context.getString(R.string.minute_ago);
		} else if (difference < 60 * 15) {
			return context.getString(R.string.minutes_ago,
					Math.round(difference / 60.0));
		} else if (today(date) || difference < 6 * 60 * 60) {
			java.text.DateFormat df = DateFormat.getTimeFormat(context);
			return df.format(date);
		} else {
			return DateUtils.formatDateTime(context, date.getTime(),
					DATE_NO_YEAR_FLAGS);
		}
	}

	private static boolean today(Date date) {
		Calendar cal1 = Calendar.getInstance();
		Calendar cal2 = Calendar.getInstance();
		cal1.setTime(date);
		cal2.setTimeInMillis(System.currentTimeMillis());
		return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
				&& cal1.get(Calendar.DAY_OF_YEAR) == cal2
						.get(Calendar.DAY_OF_YEAR);
	}

	public static String lastseen(Context context, long time) {
		if (time == 0) {
			return context.getString(R.string.never_seen);
		}
		long difference = (System.currentTimeMillis() - time) / 1000;
		if (difference < 60) {
			return context.getString(R.string.last_seen_now);
		} else if (difference < 60 * 2) {
			return context.getString(R.string.last_seen_min);
		} else if (difference < 60 * 60) {
			return context.getString(R.string.last_seen_mins,
					Math.round(difference / 60.0));
		} else if (difference < 60 * 60 * 2) {
			return context.getString(R.string.last_seen_hour);
		} else if (difference < 60 * 60 * 24) {
			return context.getString(R.string.last_seen_hours,
					Math.round(difference / (60.0 * 60.0)));
		} else if (difference < 60 * 60 * 48) {
			return context.getString(R.string.last_seen_day);
		} else {
			return context.getString(R.string.last_seen_days,
					Math.round(difference / (60.0 * 60.0 * 24.0)));
		}
	}

	public static int getRealPx(int dp, Context context) {
		final DisplayMetrics metrics = context.getResources()
				.getDisplayMetrics();
		return ((int) (dp * metrics.density));
	}

	private static int getNameColor(String name) {
		/*
		 * int holoColors[] = { 0xFF1da9da, 0xFFb368d9, 0xFF83b600, 0xFFffa713,
		 * 0xFFe92727 };
		 */
		int holoColors[] = { 0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
				0xFF5677fc, 0xFF03a9f4, 0xFF00bcd4, 0xFF009688, 0xFFff5722,
				0xFF795548, 0xFF607d8b };
		return holoColors[(int) ((name.hashCode() & 0xffffffffl) % holoColors.length)];
	}

	private static void drawTile(Canvas canvas, String letter, int tileColor,
			int textColor, int left, int top, int right, int bottom) {
		Paint tilePaint = new Paint(), textPaint = new Paint();
		tilePaint.setColor(tileColor);
		textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
		textPaint.setColor(textColor);
		textPaint.setTypeface(Typeface.create("sans-serif-light",
				Typeface.NORMAL));
		textPaint.setTextSize((float) ((right - left) * 0.8));
		Rect rect = new Rect();

		canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
		textPaint.getTextBounds(letter, 0, 1, rect);
		float width = textPaint.measureText(letter);
		canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
				/ 2 + rect.height() / 2, textPaint);
	}

	private static Bitmap getUnknownContactPicture(String[] names, int size,
			int bgColor, int fgColor) {
		int tiles = (names.length > 4) ? 4 : (names.length < 1) ? 1
				: names.length;
		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		String[] letters = new String[tiles];
		int[] colors = new int[tiles];
		if (names.length < 1) {
			letters[0] = "?";
			colors[0] = 0xFFe92727;
		} else {
			for (int i = 0; i < tiles; ++i) {
				letters[i] = (names[i].length() > 0) ? names[i].substring(0, 1)
						.toUpperCase(Locale.US) : " ";
				colors[i] = getNameColor(names[i]);
			}

			if (names.length > 4) {
				letters[3] = "\u2026"; // Unicode ellipsis
				colors[3] = 0xFF202020;
			}
		}

		bitmap.eraseColor(bgColor);

		switch (tiles) {
		case 1:
			drawTile(canvas, letters[0], colors[0], fgColor, 0, 0, size, size);
			break;

		case 2:
			drawTile(canvas, letters[0], colors[0], fgColor, 0, 0,
					size / 2 - 1, size);
			drawTile(canvas, letters[1], colors[1], fgColor, size / 2 + 1, 0,
					size, size);
			break;

		case 3:
			drawTile(canvas, letters[0], colors[0], fgColor, 0, 0,
					size / 2 - 1, size);
			drawTile(canvas, letters[1], colors[1], fgColor, size / 2 + 1, 0,
					size, size / 2 - 1);
			drawTile(canvas, letters[2], colors[2], fgColor, size / 2 + 1,
					size / 2 + 1, size, size);
			break;

		case 4:
			drawTile(canvas, letters[0], colors[0], fgColor, 0, 0,
					size / 2 - 1, size / 2 - 1);
			drawTile(canvas, letters[1], colors[1], fgColor, 0, size / 2 + 1,
					size / 2 - 1, size);
			drawTile(canvas, letters[2], colors[2], fgColor, size / 2 + 1, 0,
					size, size / 2 - 1);
			drawTile(canvas, letters[3], colors[3], fgColor, size / 2 + 1,
					size / 2 + 1, size, size);
			break;
		}

		return bitmap;
	}

	private static Bitmap getMucContactPicture(Conversation conversation,
			int size, int bgColor, int fgColor) {
		List<User> members = conversation.getMucOptions().getUsers();
		if (members.size() == 0) {
			return getUnknownContactPicture(
					new String[] { conversation.getName(false) }, size,
					bgColor, fgColor);
		}
		ArrayList<String> names = new ArrayList<String>();
		names.add(conversation.getMucOptions().getActualNick());
		for (User user : members) {
			names.add(user.getName());
			if (names.size() > 4) {
				break;
			}
		}
		String[] mArrayNames = new String[names.size()];
		names.toArray(mArrayNames);
		return getUnknownContactPicture(mArrayNames, size, bgColor, fgColor);
	}

	public static Bitmap getContactPicture(Conversation conversation,
			int dpSize, Context context, boolean notification) {
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			return getContactPicture(conversation.getContact(), dpSize,
					context, notification);
		} else {
			int fgColor = UIHelper.FG_COLOR, bgColor = (notification) ? UIHelper.BG_COLOR
					: UIHelper.TRANSPARENT;

			return getMucContactPicture(conversation,
					getRealPx(dpSize, context), bgColor, fgColor);
		}
	}

	public static Bitmap getContactPicture(Contact contact, int dpSize,
			Context context, boolean notification) {
		String uri = contact.getProfilePhoto();
		if (uri == null) {
			return getContactPicture(contact.getDisplayName(), dpSize, context,
					notification);
		}
		try {
			Bitmap bm = BitmapFactory.decodeStream(context.getContentResolver()
					.openInputStream(Uri.parse(uri)));
			return Bitmap.createScaledBitmap(bm, getRealPx(dpSize, context),
					getRealPx(dpSize, context), false);
		} catch (FileNotFoundException e) {
			return getContactPicture(contact.getDisplayName(), dpSize, context,
					notification);
		}
	}

	public static Bitmap getContactPicture(String name, int dpSize,
			Context context, boolean notification) {
		int fgColor = UIHelper.FG_COLOR, bgColor = (notification) ? UIHelper.BG_COLOR
				: UIHelper.TRANSPARENT;

		return getUnknownContactPicture(new String[] { name },
				getRealPx(dpSize, context), bgColor, fgColor);
	}

	public static void showErrorNotification(Context context,
			List<Account> accounts) {
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		List<Account> accountsWproblems = new ArrayList<Account>();
		for (Account account : accounts) {
			if (account.hasErrorStatus()) {
				accountsWproblems.add(account);
			}
		}
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				context);
		if (accountsWproblems.size() == 0) {
			mNotificationManager.cancel(1111);
			return;
		} else if (accountsWproblems.size() == 1) {
			mBuilder.setContentTitle(context
					.getString(R.string.problem_connecting_to_account));
			mBuilder.setContentText(accountsWproblems.get(0).getJid());
		} else {
			mBuilder.setContentTitle(context
					.getString(R.string.problem_connecting_to_accounts));
			mBuilder.setContentText(context.getString(R.string.touch_to_fix));
		}
		mBuilder.setOngoing(true);
		mBuilder.setLights(0xffffffff, 2000, 4000);
		mBuilder.setSmallIcon(R.drawable.ic_notification);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(ConversationActivity.class);

		Intent manageAccountsIntent = new Intent(context,
				ManageAccountActivity.class);
		stackBuilder.addNextIntent(manageAccountsIntent);

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);

		mBuilder.setContentIntent(resultPendingIntent);
		Notification notification = mBuilder.build();
		mNotificationManager.notify(1111, notification);
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

	public static void updateNotification(Context context,
			List<Conversation> conversations, Conversation currentCon,
			boolean notify) {
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);

		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(context);
		boolean useSubject = preferences.getBoolean("use_subject_in_muc", true);
		boolean showNofifications = preferences.getBoolean("show_notification",
				true);
		boolean vibrate = preferences.getBoolean("vibrate_on_notification",
				true);
		boolean alwaysNotify = preferences.getBoolean(
				"notify_in_conversation_when_highlighted", false);

		if (!showNofifications) {
			mNotificationManager.cancel(2342);
			return;
		}

		String targetUuid = "";

		if ((currentCon != null)
				&& (currentCon.getMode() == Conversation.MODE_MULTI)
				&& (!alwaysNotify) && notify) {
			String nick = currentCon.getMucOptions().getActualNick();
			Pattern highlight = generateNickHighlightPattern(nick);
			Matcher m = highlight.matcher(currentCon.getLatestMessage()
					.getBody());
			notify = m.find();
		}

		List<Conversation> unread = new ArrayList<Conversation>();
		for (Conversation conversation : conversations) {
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				if ((!conversation.isRead())
						&& ((wasHighlighted(conversation) || (alwaysNotify)))) {
					unread.add(conversation);
				}
			} else {
				if (!conversation.isRead()) {
					unread.add(conversation);
				}
			}
		}
		String ringtone = preferences.getString("notification_ringtone", null);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				context);
		if (unread.size() == 0) {
			mNotificationManager.cancel(2342);
			return;
		} else if (unread.size() == 1) {
			Conversation conversation = unread.get(0);
			targetUuid = conversation.getUuid();
			mBuilder.setLargeIcon(conversation.getImage(context, 64));
			mBuilder.setContentTitle(conversation.getName(useSubject));
			if (notify) {
				mBuilder.setTicker(conversation.getLatestMessage()
						.getReadableBody(context));
			}
			StringBuilder bigText = new StringBuilder();
			List<Message> messages = conversation.getMessages();
			String firstLine = "";
			for (int i = messages.size() - 1; i >= 0; --i) {
				if (!messages.get(i).isRead()) {
					if (i == messages.size() - 1) {
						firstLine = messages.get(i).getReadableBody(context);
						bigText.append(firstLine);
					} else {
						firstLine = messages.get(i).getReadableBody(context);
						bigText.insert(0, firstLine + "\n");
					}
				} else {
					break;
				}
			}
			mBuilder.setContentText(firstLine);
			mBuilder.setStyle(new NotificationCompat.BigTextStyle()
					.bigText(bigText.toString()));
		} else {
			NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
			style.setBigContentTitle(unread.size() + " "
					+ context.getString(R.string.unread_conversations));
			StringBuilder names = new StringBuilder();
			for (int i = 0; i < unread.size(); ++i) {
				targetUuid = unread.get(i).getUuid();
				if (i < unread.size() - 1) {
					names.append(unread.get(i).getName(useSubject) + ", ");
				} else {
					names.append(unread.get(i).getName(useSubject));
				}
				style.addLine(Html.fromHtml("<b>"
						+ unread.get(i).getName(useSubject)
						+ "</b> "
						+ unread.get(i).getLatestMessage()
								.getReadableBody(context)));
			}
			mBuilder.setContentTitle(unread.size() + " "
					+ context.getString(R.string.unread_conversations));
			mBuilder.setContentText(names.toString());
			mBuilder.setStyle(style);
		}
		if ((currentCon != null) && (notify)) {
			targetUuid = currentCon.getUuid();
		}
		if (unread.size() != 0) {
			mBuilder.setSmallIcon(R.drawable.ic_notification);
			if (notify) {
				if (vibrate) {
					int dat = 70;
					long[] pattern = { 0, 3 * dat, dat, dat };
					mBuilder.setVibrate(pattern);
				}
				mBuilder.setLights(0xffffffff, 2000, 4000);
				if (ringtone != null) {
					mBuilder.setSound(Uri.parse(ringtone));
				}
			}

			TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
			stackBuilder.addParentStack(ConversationActivity.class);

			Intent viewConversationIntent = new Intent(context,
					ConversationActivity.class);
			viewConversationIntent.setAction(Intent.ACTION_VIEW);
			viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
					targetUuid);
			viewConversationIntent
					.setType(ConversationActivity.VIEW_CONVERSATION);

			stackBuilder.addNextIntent(viewConversationIntent);

			PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
					0, PendingIntent.FLAG_UPDATE_CURRENT);

			mBuilder.setContentIntent(resultPendingIntent);
			Notification notification = mBuilder.build();
			mNotificationManager.notify(2342, notification);
		}
	}

	private static boolean wasHighlighted(Conversation conversation) {
		List<Message> messages = conversation.getMessages();
		String nick = conversation.getMucOptions().getActualNick();
		Pattern highlight = generateNickHighlightPattern(nick);
		for (int i = messages.size() - 1; i >= 0; --i) {
			if (messages.get(i).isRead()) {
				break;
			} else {
				Matcher m = highlight.matcher(messages.get(i).getBody());
				if (m.find()) {
					return true;
				}
			}
		}
		return false;
	}

	public static void prepareContactBadge(final Activity activity,
			QuickContactBadge badge, final Contact contact, Context context) {
		if (contact.getSystemAccount() != null) {
			String[] systemAccount = contact.getSystemAccount().split("#");
			long id = Long.parseLong(systemAccount[0]);
			badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1]));
		}
		badge.setImageBitmap(contact.getImage(72, context));
	}

	public static AlertDialog getVerifyFingerprintDialog(
			final ConversationActivity activity,
			final Conversation conversation, final View msg) {
		final Contact contact = conversation.getContact();
		final Account account = conversation.getAccount();

		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Verify fingerprint");
		LayoutInflater inflater = activity.getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_verify_otr, null);
		TextView jid = (TextView) view.findViewById(R.id.verify_otr_jid);
		TextView fingerprint = (TextView) view
				.findViewById(R.id.verify_otr_fingerprint);
		TextView yourprint = (TextView) view
				.findViewById(R.id.verify_otr_yourprint);

		jid.setText(contact.getJid());
		fingerprint.setText(conversation.getOtrFingerprint());
		yourprint.setText(account.getOtrFingerprint());
		builder.setNegativeButton("Cancel", null);
		builder.setPositiveButton("Verify", new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				contact.addOtrFingerprint(conversation.getOtrFingerprint());
				msg.setVisibility(View.GONE);
				activity.xmppConnectionService.syncRosterToDisk(account);
			}
		});
		builder.setView(view);
		return builder.create();
	}

	public static Bitmap getSelfContactPicture(Account account, int size,
			boolean showPhoneSelfContactPicture, Context context) {
		if (showPhoneSelfContactPicture) {
			Uri selfiUri = PhoneHelper.getSefliUri(context);
			if (selfiUri != null) {
				try {
					return BitmapFactory.decodeStream(context
							.getContentResolver().openInputStream(selfiUri));
				} catch (FileNotFoundException e) {
					return getContactPicture(account.getJid(), size, context,
							false);
				}
			}
			return getContactPicture(account.getJid(), size, context, false);
		} else {
			return getContactPicture(account.getJid(), size, context, false);
		}
	}

	private static final Pattern armorRegex(String regex) {
		return Pattern.compile("(^|\\s+)" + regex + "(\\s+|$)"); }

	private static final String armorReplacement(String replacement) {
		return "$1" + replacement + "$2"; }

	private static final Object[][] patterns = new Object[][]{
		{armorRegex(":-?\\)"), armorReplacement("😃"), },
		{armorRegex(";-?\\)"), armorReplacement("😉"), },
		{armorRegex(":-?D"), armorReplacement("😀"), },
		{armorRegex(":-?[Ppb]"), armorReplacement("😋"), },
		{armorRegex("8-?\\)"), armorReplacement("😎"), },
		{armorRegex(":-?\\|"), armorReplacement("😐"), },
		{armorRegex(":-?[/\\\\]"), armorReplacement("😕"), },
		{armorRegex(":-?\\*"), armorReplacement("😗"), },
		{armorRegex(":-?[0Oo]"), armorReplacement("😮"), },
		{armorRegex(":-?\\("), armorReplacement("😞"), },
		{armorRegex("\\^\\^"), armorReplacement("😁"), },
	};

	public static String transformAsciiEmoticons(String body) {
		if (body != null) {
		// see https://developer.android.com/reference/java/util/regex/Pattern.html
		// see http://userguide.icu-project.org/strings/regexp
		// see https://de.wikipedia.org/wiki/Unicodeblock_Smileys
			for (Object[] r: patterns) {
				Pattern pattern = (Pattern)r[0];
				String replacement = (String)r[1];
				body = pattern.matcher(body).replaceAll(replacement);
			}
			body = body.trim();
		}
		return body;
	}
}
