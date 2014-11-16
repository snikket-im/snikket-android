package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class UIHelper {
	private static final int SHORT_DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE
			| DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;
	private static final int FULL_DATE_FLAGS = DateUtils.FORMAT_SHOW_TIME
			| DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE;

	public static String readableTimeDifference(Context context, long time) {
		return readableTimeDifference(context, time, false);
	}

	public static String readableTimeDifferenceFull(Context context, long time) {
		return readableTimeDifference(context, time, true);
	}

	private static String readableTimeDifference(Context context, long time,
			boolean fullDate) {
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
		} else if (today(date)) {
			java.text.DateFormat df = DateFormat.getTimeFormat(context);
			return df.format(date);
		} else {
			if (fullDate) {
				return DateUtils.formatDateTime(context, date.getTime(),
						FULL_DATE_FLAGS);
			} else {
				return DateUtils.formatDateTime(context, date.getTime(),
						SHORT_DATE_FLAGS);
			}
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

	public static void showErrorNotification(Context context,
			List<Account> accounts) {
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		List<Account> accountsWproblems = new ArrayList<>();
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
			mBuilder.setContentText(accountsWproblems.get(0).getJid().toBareJid().toString());
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

	private final static class EmoticonPattern {
		Pattern pattern;
		String replacement;

		EmoticonPattern(String ascii, int unicode) {
			this.pattern = Pattern.compile("(?<=(^|\\s))" + ascii
					+ "(?=(\\s|$))");
			this.replacement = new String(new int[] { unicode, }, 0, 1);
		}

		String replaceAll(String body) {
			return pattern.matcher(body).replaceAll(replacement);
		}
	}

	private static final EmoticonPattern[] patterns = new EmoticonPattern[] {
			new EmoticonPattern(":-?D", 0x1f600),
			new EmoticonPattern("\\^\\^", 0x1f601),
			new EmoticonPattern(":'D", 0x1f602),
			new EmoticonPattern("\\]-?D", 0x1f608),
			new EmoticonPattern(";-?\\)", 0x1f609),
			new EmoticonPattern(":-?\\)", 0x1f60a),
			new EmoticonPattern("[B8]-?\\)", 0x1f60e),
			new EmoticonPattern(":-?\\|", 0x1f610),
			new EmoticonPattern(":-?[/\\\\]", 0x1f615),
			new EmoticonPattern(":-?\\*", 0x1f617),
			new EmoticonPattern(":-?[Ppb]", 0x1f61b),
			new EmoticonPattern(":-?\\(", 0x1f61e),
			new EmoticonPattern(":-?[0Oo]", 0x1f62e),
			new EmoticonPattern("\\\\o/", 0x1F631), };

	public static String transformAsciiEmoticons(String body) {
		if (body != null) {
			for (EmoticonPattern p : patterns) {
				body = p.replaceAll(body);
			}
			body = body.trim();
		}
		return body;
	}

	public static int getColorForName(String name) {
		int colors[] = {0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
				0xFF5677fc, 0xFF03a9f4, 0xFF00bcd4, 0xFF009688, 0xFFff5722,
				0xFF795548, 0xFF607d8b};
		return colors[(int) ((name.hashCode() & 0xffffffffl) % colors.length)];
	}
}
