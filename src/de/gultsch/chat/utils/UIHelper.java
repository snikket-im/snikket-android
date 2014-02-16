package de.gultsch.chat.utils;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.ui.ConversationActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

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

		int holoColors[] = { 0xFF1da9da, 0xFFb368d9, 0xFF83b600, 0xFFffa713,
				0xFFe92727 };

		int color = holoColors[Math.abs(name.hashCode()) % holoColors.length];

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

	public static Notification getUnreadMessageNotification(Context context,
			Conversation conversation) {

		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(context);
		String ringtone = sharedPref.getString("notification_ringtone", null);

		Resources res = context.getResources();
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				context);
		mBuilder.setLargeIcon(UIHelper.getUnknownContactPicture(conversation
				.getName(), (int) res
				.getDimension(android.R.dimen.notification_large_icon_width)));
		mBuilder.setContentTitle(conversation.getName());
		mBuilder.setTicker(conversation.getLatestMessage().getBody().trim());
		StringBuilder bigText = new StringBuilder();
		List<Message> messages = conversation.getMessages();
		String firstLine = "";
		for(int i = messages.size() -1; i >= 0; --i) {
			if (!messages.get(i).isRead()) {
				if (i == messages.size() -1 ) {
					firstLine = messages.get(i).getBody().trim();
					bigText.append(firstLine);
				} else {
					firstLine = messages.get(i).getBody().trim();
					bigText.insert(0, firstLine+"\n");
				}
			} else {
				break;
			}
		}
		mBuilder.setContentText(firstLine);
		mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(bigText.toString()));
		mBuilder.setSmallIcon(R.drawable.notification);
		mBuilder.setLights(0xffffffff, 2000, 4000);
		if (ringtone != null) {
			mBuilder.setSound(Uri.parse(ringtone));
		}

		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(ConversationActivity.class);

		Intent viewConversationIntent = new Intent(context,
				ConversationActivity.class);
		viewConversationIntent.setAction(Intent.ACTION_VIEW);
		viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
				conversation.getUuid());
		viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);

		stackBuilder.addNextIntent(viewConversationIntent);

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);

		mBuilder.setContentIntent(resultPendingIntent);
		return mBuilder.build();
	}

	public static void prepareContactBadge(final Activity activity,
			QuickContactBadge badge, final Contact contact) {
		if (contact.getSystemAccount()!=null) {
			String[] systemAccount = contact.getSystemAccount().split("#");
			long id = Long.parseLong(systemAccount[0]);
			badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1]));
	
			if (contact.getProfilePhoto() != null) {
				badge.setImageURI(Uri.parse(contact.getProfilePhoto()));
			} else {
				badge.setImageBitmap(UIHelper.getUnknownContactPicture(contact.getDisplayName(), 400));
			}
		} else {
			badge.setImageBitmap(UIHelper.getUnknownContactPicture(contact.getDisplayName(), 400));
		}

	}
	
	public static AlertDialog getVerifyFingerprintDialog(final ConversationActivity activity,final Conversation conversation, final LinearLayout msg) {
		final Contact contact = conversation.getContact();
		final Account account = conversation.getAccount();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle("Verify fingerprint");
		LayoutInflater inflater = activity.getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_verify_otr, null);
		TextView jid = (TextView) view.findViewById(R.id.verify_otr_jid);
		TextView fingerprint = (TextView) view.findViewById(R.id.verify_otr_fingerprint);
		TextView yourprint = (TextView) view.findViewById(R.id.verify_otr_yourprint);
		
		jid.setText(contact.getJid());
		fingerprint.setText(conversation.getOtrFingerprint());
		yourprint.setText(account.getOtrFingerprint());
		builder.setNegativeButton("Cancel", null);
		builder.setPositiveButton("Verify", new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				contact.addOtrFingerprint(conversation.getOtrFingerprint());
				msg.setVisibility(View.GONE);
				activity.xmppConnectionService.updateContact(contact);
			}
		});
		builder.setView(view);
		return builder.create();
	}
}
