package de.gultsch.chat.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.ui.ConversationActivity;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.DisplayMetrics;
import android.util.Log;

public class UIHelper {
	public static String readableTimeDifference(long time) {
		if (time == 0) {
			return "just now";
		}
		Date date = new Date(time);
		long difference = (System.currentTimeMillis() - time) / 1000;
		if (difference < 60) {
			return "just now";
		} else if (difference < 60 * 10) {
			return difference / 60 + " min ago";
		} else if (difference < 60 * 60 * 24) {
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
			return sdf.format(date);
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat("M/D");
			return sdf.format(date);
		}
	}

	public static Bitmap getUnknownContactPicture(String name, int size) {
		String firstLetter = name.substring(0, 1).toUpperCase();
		String centerLetter = name.substring(name.length() / 2,
				(name.length() / 2) + 1);

		int holoColors[] = { 0xFF1da9da, 0xFFb368d9, 0xFF83b600, 0xFFffa713,
				0xFFe92727 };

		int color = holoColors[centerLetter.charAt(0) % holoColors.length];

		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		bitmap.eraseColor(color);

		Paint paint = new Paint();
		paint.setColor(0xffe5e5e5);
		paint.setTextSize((float) (size * 0.9));
		paint.setAntiAlias(true);
		Rect rect = new Rect();
		paint.getTextBounds(firstLetter, 0, 1, rect);
		float width = paint.measureText(firstLetter);
		canvas.drawText(firstLetter, (size / 2) - (width / 2), (size / 2)
				+ (rect.height() / 2), paint);

		return bitmap;
	}
	
	public static Notification getUnreadMessageNotification(Context context, Conversation conversation) {
		
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
		String ringtone = sharedPref.getString("notification_ringtone",null);
		
		Resources res = context.getResources();
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
		mBuilder.setLargeIcon(UIHelper.getUnknownContactPicture(conversation.getName(),(int) res.getDimension(android.R.dimen.notification_large_icon_width)));
		mBuilder.setContentTitle(conversation.getName());
		mBuilder.setContentText(conversation.getLatestMessage());
		mBuilder.setSmallIcon(R.drawable.notification);
		mBuilder.setLights(0xffffffff, 2000, 4000);
		if (ringtone!=null) {
			mBuilder.setSound(Uri.parse(ringtone));
		}
		
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(ConversationActivity.class);
		
		Intent viewConversationIntent = new Intent(context,ConversationActivity.class);
		viewConversationIntent.setAction(Intent.ACTION_VIEW);
		viewConversationIntent.putExtra(
				ConversationActivity.CONVERSATION,
				conversation.getUuid());
		viewConversationIntent
				.setType(ConversationActivity.VIEW_CONVERSATION);
		
		stackBuilder.addNextIntent(viewConversationIntent);
		
		PendingIntent resultPendingIntent =
		        stackBuilder.getPendingIntent(
		            0,
		            PendingIntent.FLAG_UPDATE_CURRENT
		        );

		
		mBuilder.setContentIntent(resultPendingIntent);
		return mBuilder.build();
	}
}
