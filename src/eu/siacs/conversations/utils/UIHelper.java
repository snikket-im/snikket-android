package eu.siacs.conversations.utils;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
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
import android.content.res.Resources;
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
import android.support.v4.app.NotificationCompat.InboxStyle;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class UIHelper {
	private static final int BG_COLOR = 0xFF181818;
	private static final int FG_COLOR = 0xFFE5E5E5;
	private static final int TRANSPARENT = 0x00000000;

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
			SimpleDateFormat sdf = new SimpleDateFormat("HH:mm",Locale.US);
			return sdf.format(date);
		} else {
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd",Locale.US);
			return sdf.format(date);
		}
	}

	public static int getRealPx(int dp, Context context) {
		final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		return ((int) (dp * metrics.density));
	}

	private static int getNameColor(String name) {
		int holoColors[] = { 0xFF1da9da, 0xFFb368d9, 0xFF83b600, 0xFFffa713,
				0xFFe92727 };
		int color = holoColors[Math.abs(name.toLowerCase(Locale.getDefault()).hashCode()) % holoColors.length];
		return color;
	}

	private static Bitmap getUnknownContactPicture(String[] names, int size, int bgColor, int fgColor) {
		int tiles = (names.length > 4)? 4 :
					(names.length < 1)? 1 :
						names.length;
		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		String[] letters = new String[tiles];
		int[] colors = new int[tiles];
		if (names.length < 1) {
			letters[0] = "?";
			colors[0] = 0xFFe92727;
		} else {
			for(int i = 0; i < tiles; ++i) {
				letters[i] = (names[i].length() > 0) ?
						names[i].substring(0, 1).toUpperCase(Locale.US) : " ";
				colors[i] = getNameColor(names[i]);
			}

			if (names.length > 4) {
				letters[3] = "\u2026"; // Unicode ellipsis
				colors[3] = 0xFF444444;
			}
		}
		Paint textPaint = new Paint(), tilePaint = new Paint();
		textPaint.setColor(fgColor);
		textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
		Rect rect, left, right, topLeft, bottomLeft, topRight, bottomRight;
		float width;

		switch(tiles) {
			case 1:
				bitmap.eraseColor(colors[0]);

				textPaint.setTextSize((float) (size * 0.8));
				textPaint.setAntiAlias(true);
				rect = new Rect();
				textPaint.getTextBounds(letters[0], 0, 1, rect);
				width = textPaint.measureText(letters[0]);
				canvas.drawText(letters[0], (size / 2) - (width / 2), (size / 2)
						+ (rect.height() / 2), textPaint);
				break;

			case 2:
				bitmap.eraseColor(bgColor);

				tilePaint.setColor(colors[0]);
				left = new Rect(0, 0, (size/2)-1, size);
				canvas.drawRect(left, tilePaint);

				tilePaint.setColor(colors[1]);
				right = new Rect((size/2)+1, 0, size, size);
				canvas.drawRect(right, tilePaint);

				textPaint.setTextSize((float) (size * 0.8*0.5));
				textPaint.setAntiAlias(true);
				rect = new Rect();
				textPaint.getTextBounds(letters[0], 0, 1, rect);
				width = textPaint.measureText(letters[0]);
				canvas.drawText(letters[0], (size / 4) - (width / 2), (size / 2)
						+ (rect.height() / 2), textPaint);
				textPaint.getTextBounds(letters[1], 0, 1, rect);
				width = textPaint.measureText(letters[1]);
				canvas.drawText(letters[1], (3 * size / 4) - (width / 2), (size / 2)
						+ (rect.height() / 2), textPaint);
				break;

			case 3:
				bitmap.eraseColor(bgColor);

				tilePaint.setColor(colors[0]);
				left = new Rect(0, 0, (size/2)-1, size);
				canvas.drawRect(left, tilePaint);

				tilePaint.setColor(colors[1]);
				topRight = new Rect((size/2)+1, 0, size, (size/2 - 1));
				canvas.drawRect(topRight, tilePaint);

				tilePaint.setColor(colors[2]);
				bottomRight = new Rect((size/2)+1, (size/2 + 1), size, size);
				canvas.drawRect(bottomRight, tilePaint);

				textPaint.setTextSize((float) (size * 0.8*0.5));
				textPaint.setAntiAlias(true);
				rect = new Rect();

				textPaint.getTextBounds(letters[0], 0, 1, rect);
				width = textPaint.measureText(letters[0]);
				canvas.drawText(letters[0], (size / 4) - (width / 2), (size / 2)
						+ (rect.height() / 2), textPaint);

				textPaint.getTextBounds(letters[1], 0, 1, rect);
				width = textPaint.measureText(letters[1]);
				canvas.drawText(letters[1], (3 * size / 4) - (width / 2), (size / 4)
						+ (rect.height() / 2), textPaint);

				textPaint.getTextBounds(letters[2], 0, 1, rect);
				width = textPaint.measureText(letters[2]);
				canvas.drawText(letters[2], (3 * size / 4) - (width / 2), (3* size / 4)
						+ (rect.height() / 2), textPaint);
				break;

			case 4:
				bitmap.eraseColor(bgColor);

				tilePaint.setColor(colors[0]);
				topLeft = new Rect(0, 0, (size/2)-1, (size/2)-1);
				canvas.drawRect(topLeft, tilePaint);

				tilePaint.setColor(colors[1]);
				bottomLeft = new Rect(0, (size/2)+1, (size/2)-1, size);
				canvas.drawRect(bottomLeft, tilePaint);

				tilePaint.setColor(colors[2]);
				topRight = new Rect((size/2)+1, 0, size, (size/2 - 1));
				canvas.drawRect(topRight, tilePaint);

				tilePaint.setColor(colors[3]);
				bottomRight = new Rect((size/2)+1, (size/2 + 1), size, size);
				canvas.drawRect(bottomRight, tilePaint);

				textPaint.setTextSize((float) (size * 0.8*0.5));
				textPaint.setAntiAlias(true);
				rect = new Rect();

				textPaint.getTextBounds(letters[0], 0, 1, rect);
				width = textPaint.measureText(letters[0]);
				canvas.drawText(letters[0], (size / 4) - (width / 2), (size / 4)
						+ (rect.height() / 2), textPaint);

				textPaint.getTextBounds(letters[1], 0, 1, rect);
				width = textPaint.measureText(letters[1]);
				canvas.drawText(letters[1], (size / 4) - (width / 2), (3* size / 4)
						+ (rect.height() / 2), textPaint);

				textPaint.getTextBounds(letters[2], 0, 1, rect);
				width = textPaint.measureText(letters[2]);
				canvas.drawText(letters[2], (3 * size / 4) - (width / 2), (size / 4)
						+ (rect.height() / 2), textPaint);

				textPaint.getTextBounds(letters[3], 0, 1, rect);
				width = textPaint.measureText(letters[3]);
				canvas.drawText(letters[3], (3 * size / 4) - (width / 2), (3* size / 4)
						+ (rect.height() / 2), textPaint);
				break;
		}
		return bitmap;
	}

	private static Bitmap getUnknownContactPicture(String[] names, int size) {
		return getUnknownContactPicture(names, size, UIHelper.BG_COLOR, UIHelper.FG_COLOR);
	}

	private static Bitmap getMucContactPicture(Conversation conversation, int size, int bgColor, int fgColor) {
		List<User> members = conversation.getMucOptions().getUsers();
		if (members.size() == 0) {
			return getUnknownContactPicture(new String[]{conversation.getName(false)}, size, bgColor, fgColor);
		}
		String[] names = new String[members.size()+1];
		names[0] = conversation.getMucOptions().getNick();
		for(int i = 0; i < members.size(); ++i) {
			names[i+1] = members.get(i).getName();
		}

		return getUnknownContactPicture(names, size, bgColor, fgColor);
	}

	public static Bitmap getContactPicture(Conversation conversation, int dpSize, Context context, boolean notification) {
		if(conversation.getMode() == Conversation.MODE_SINGLE) {
			if (conversation.getContact() != null){
				return getContactPicture(conversation.getContact(), dpSize,
						context, notification);
			} else {
				return getContactPicture(conversation.getName(false), dpSize,
						context, notification);
			}
		} else{
			int fgColor = UIHelper.FG_COLOR,
				bgColor = (notification) ?
					UIHelper.BG_COLOR : UIHelper.TRANSPARENT;

			return getMucContactPicture(conversation, getRealPx(dpSize, context),
					bgColor, fgColor);
		}
	}

	public static Bitmap getContactPicture(Contact contact, int dpSize, Context context, boolean notification) {
		int fgColor = UIHelper.FG_COLOR,
			bgColor = (notification) ?
				UIHelper.BG_COLOR : UIHelper.TRANSPARENT;

		String uri = contact.getProfilePhoto();
		if (uri==null) {
			return getContactPicture(contact.getDisplayName(), dpSize,
					context, notification);
		}
		try {
			Bitmap bm = BitmapFactory.decodeStream(context.getContentResolver()
					.openInputStream(Uri.parse(uri)));
			return Bitmap.createScaledBitmap(bm, getRealPx(dpSize, context),
					getRealPx(dpSize, context), false);
		} catch (FileNotFoundException e) {
			return getContactPicture(contact.getDisplayName(), dpSize,
					context, notification);
		}
	}

	public static Bitmap getContactPicture(String name, int dpSize, Context context, boolean notification) {
		int fgColor = UIHelper.FG_COLOR,
			bgColor = (notification) ?
				UIHelper.BG_COLOR : UIHelper.TRANSPARENT;

		return getUnknownContactPicture(new String[]{name}, getRealPx(dpSize, context),
				bgColor, fgColor);
	}

	public static Bitmap getErrorPicture(int size) {
		Bitmap bitmap = Bitmap
				.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		bitmap.eraseColor(0xFFe92727);

		Paint paint = new Paint();
		paint.setColor(0xffe5e5e5);
		paint.setTextSize((float) (size * 0.9));
		paint.setAntiAlias(true);
		Rect rect = new Rect();
		paint.getTextBounds("!", 0, 1, rect);
		float width = paint.measureText("!");
		canvas.drawText("!", (size / 2) - (width / 2),
				(size / 2) + (rect.height() / 2), paint);

		return bitmap;
	}
	
	public static void showErrorNotification(Context context, List<Account> accounts) {
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		List<Account> accountsWproblems = new ArrayList<Account>();
		for(Account account : accounts) {
			if (account.hasErrorStatus()) {
				accountsWproblems.add(account);
			}
		}
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
		if (accountsWproblems.size() == 0) {
			mNotificationManager.cancel(1111);
			return;
		} else if (accountsWproblems.size() == 1) {
			mBuilder.setContentTitle(context.getString(R.string.problem_connecting_to_account));
			mBuilder.setContentText(accountsWproblems.get(0).getJid());
		} else {
			mBuilder.setContentTitle(context.getString(R.string.problem_connecting_to_accounts));
			mBuilder.setContentText(context.getString(R.string.touch_to_fix));
		}
		mBuilder.setOngoing(true);
		mBuilder.setLights(0xffffffff, 2000, 4000);
		mBuilder.setSmallIcon(R.drawable.notification);
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
		stackBuilder.addParentStack(ConversationActivity.class);

		Intent manageAccountsIntent = new Intent(context,
				ManageAccountActivity.class);
		stackBuilder.addNextIntent(manageAccountsIntent);

		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
				0, PendingIntent.FLAG_UPDATE_CURRENT);
		
		mBuilder.setContentIntent(resultPendingIntent);
		Notification notification = mBuilder.build();
		mNotificationManager.notify(1111, notification);
	}

	public static void updateNotification(Context context,
			List<Conversation> conversations, Conversation currentCon, boolean notify) {
		NotificationManager mNotificationManager = (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		boolean useSubject = preferences.getBoolean("use_subject_in_muc", true);
		boolean showNofifications = preferences.getBoolean("show_notification",true);
		boolean vibrate = preferences.getBoolean("vibrate_on_notification", true);
		boolean alwaysNotify = preferences.getBoolean("notify_in_conversation_when_highlighted", false);

		if (!showNofifications) {
			mNotificationManager.cancel(2342);
			return;
		}
		
		String targetUuid = "";
		
		if ((currentCon != null) &&(currentCon.getMode() == Conversation.MODE_MULTI)&&(!alwaysNotify)) {
			String nick = currentCon.getMucOptions().getNick();
			notify = currentCon.getLatestMessage().getBody().contains(nick);
		}
		
		List<Conversation> unread = new ArrayList<Conversation>();
		for (Conversation conversation : conversations) {
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				if ((!conversation.isRead())&&((wasHighlighted(conversation)||(alwaysNotify)))) {
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
			mBuilder.setLargeIcon(UIHelper.getContactPicture(conversation, 64,
							context, true));
			mBuilder.setContentTitle(conversation.getName(useSubject));
			if (notify) {
				mBuilder.setTicker(conversation.getLatestMessage().getBody().trim());
			}
			StringBuilder bigText = new StringBuilder();
			List<Message> messages = conversation.getMessages();
			String firstLine = "";
			for (int i = messages.size() - 1; i >= 0; --i) {
				if (!messages.get(i).isRead()) {
					if (i == messages.size() - 1) {
						firstLine = messages.get(i).getBody().trim();
						bigText.append(firstLine);
					} else {
						firstLine = messages.get(i).getBody().trim();
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
			style.setBigContentTitle(unread.size() + " unread Conversations");
			StringBuilder names = new StringBuilder();
			for (int i = 0; i < unread.size(); ++i) {
				targetUuid = unread.get(i).getUuid();
				if (i < unread.size() - 1) {
					names.append(unread.get(i).getName(useSubject) + ", ");
				} else {
					names.append(unread.get(i).getName(useSubject));
				}
				style.addLine(Html.fromHtml("<b>" + unread.get(i).getName(useSubject)
						+ "</b> " + unread.get(i).getLatestMessage().getBody().trim()));
			}
			mBuilder.setContentTitle(unread.size() + " unread Conversations");
			mBuilder.setContentText(names.toString());
			mBuilder.setStyle(style);
		}
		if ((currentCon!=null)&&(notify)) {
			targetUuid=currentCon.getUuid();
		}
		if (unread.size() != 0) {
			mBuilder.setSmallIcon(R.drawable.notification);
			if (notify) {
				if (vibrate) {
					int dat = 70;
					long[] pattern = {0,3*dat,dat,dat};
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
		String nick = conversation.getMucOptions().getNick();
		for(int i = messages.size() - 1; i >= 0; --i) {
			if (messages.get(i).isRead()) {
				break;
			} else {
				if (messages.get(i).getBody().contains(nick)) {
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
		badge.setImageBitmap(UIHelper.getContactPicture(contact, 72, context, false));
	}

	public static AlertDialog getVerifyFingerprintDialog(
			final ConversationActivity activity,
			final Conversation conversation, final LinearLayout msg) {
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
				activity.xmppConnectionService.updateContact(contact);
			}
		});
		builder.setView(view);
		return builder.create();
	}

	public static Bitmap getSelfContactPicture(Account account, int size, boolean showPhoneSelfContactPicture, Context context) {
		if (showPhoneSelfContactPicture) {
			Uri selfiUri = PhoneHelper.getSefliUri(context);
			if (selfiUri != null) {
				try {
					return BitmapFactory.decodeStream(context
							.getContentResolver().openInputStream(selfiUri));
				} catch (FileNotFoundException e) {
					return getContactPicture(account.getJid(), size, context, false);
				}
			}
			return getContactPicture(account.getJid(), size, context, false);
		} else {
			return getContactPicture(account.getJid(), size, context, false);
		}
	}
}
