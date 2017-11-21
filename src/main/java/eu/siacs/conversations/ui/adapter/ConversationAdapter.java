package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.widget.UnreadCountCustomView;
import eu.siacs.conversations.utils.UIHelper;

public class ConversationAdapter extends ArrayAdapter<Conversation> {

	private XmppActivity activity;

	public ConversationAdapter(XmppActivity activity, List<Conversation> conversations) {
		super(activity, 0, conversations);
		this.activity = activity;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		if (view == null) {
			LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.conversation_list_row,parent, false);
		}
		Conversation conversation = getItem(position);
		if (this.activity instanceof ConversationActivity) {
			View swipeableItem = view.findViewById(R.id.swipeable_item);
			ConversationActivity a = (ConversationActivity) this.activity;
			int c = a.highlightSelectedConversations() && conversation == a.getSelectedConversation() ? a.getSecondaryBackgroundColor() : a.getPrimaryBackgroundColor();
			swipeableItem.setBackgroundColor(c);
		}
		TextView convName = (TextView) view.findViewById(R.id.conversation_name);
		if (conversation.getMode() == Conversation.MODE_SINGLE || activity.useSubjectToIdentifyConference()) {
			convName.setText(conversation.getName());
		} else {
			convName.setText(conversation.getJid().toBareJid().toString());
		}
		TextView mLastMessage = (TextView) view.findViewById(R.id.conversation_lastmsg);
		ImageView mLastMessageImage = (ImageView) view.findViewById(R.id.conversation_lastmsg_img);
		TextView mTimestamp = (TextView) view.findViewById(R.id.conversation_lastupdate);
		TextView mSenderName = (TextView) view.findViewById(R.id.sender_name);
		ImageView imagePreview = (ImageView) view.findViewById(R.id.conversation_lastimage);
		ImageView notificationStatus = (ImageView) view.findViewById(R.id.notification_status);
		UnreadCountCustomView unreadCountCustomView = (UnreadCountCustomView) view.findViewById(R.id.unread_count);

		Message message = conversation.getLatestMessage();
		int unreadCount = conversation.unreadCount();
		if (unreadCount > 0) {
			unreadCountCustomView.setVisibility(View.VISIBLE);
			unreadCountCustomView.setUnreadCount(unreadCount);
		} else {
			unreadCountCustomView.setVisibility(View.GONE);
		}

		if (!conversation.isRead()) {
			convName.setTypeface(null, Typeface.BOLD);
		} else {
			convName.setTypeface(null, Typeface.NORMAL);
		}

		final boolean fileAvailable = message.getTransferable() == null || message.getTransferable().getStatus() != Transferable.STATUS_DELETED;
		if (message.getFileParams().width > 0 && fileAvailable) {
			mSenderName.setVisibility(View.GONE);
			mLastMessage.setVisibility(View.GONE);
			mLastMessageImage.setVisibility(View.GONE);
			imagePreview.setVisibility(View.VISIBLE);
			activity.loadBitmap(message, imagePreview);
		} else {
			final boolean showPreviewText;
			if (message.getType() == Message.TYPE_FILE && fileAvailable) {
				if (message.getFileParams().runtime > 0) {
					showPreviewText = false;
					mLastMessageImage.setImageResource(activity.getThemeResource(R.attr.ic_attach_record, R.drawable.ic_attach_record));
				} else {
					showPreviewText = true;
					mLastMessageImage.setImageResource(activity.getThemeResource(R.attr.ic_attach_document, R.drawable.ic_attach_document));
				}
				mLastMessageImage.setVisibility(View.VISIBLE);
			} else if (message.isGeoUri()) {
				showPreviewText = false;
				mLastMessageImage.setImageResource(activity.getThemeResource(R.attr.ic_attach_location, R.drawable.ic_attach_location));
				mLastMessageImage.setVisibility(View.VISIBLE);
			} else {
				showPreviewText = true;
				mLastMessageImage.setVisibility(View.GONE);
			}

			final Pair<String,Boolean> preview = UIHelper.getMessagePreview(activity,message);
			if (showPreviewText) {
				mLastMessage.setText(preview.first);
			} else {
				mLastMessageImage.setContentDescription(preview.first);
			}
			mLastMessage.setVisibility(showPreviewText ? View.VISIBLE : View.GONE);
			imagePreview.setVisibility(View.GONE);
			if (preview.second) {
				if (conversation.isRead()) {
					mLastMessage.setTypeface(null, Typeface.ITALIC);
					mSenderName.setTypeface(null, Typeface.NORMAL);
				} else {
					mLastMessage.setTypeface(null,Typeface.BOLD_ITALIC);
					mSenderName.setTypeface(null,Typeface.BOLD);
				}
			} else {
				if (conversation.isRead()) {
					mLastMessage.setTypeface(null,Typeface.NORMAL);
					mSenderName.setTypeface(null,Typeface.NORMAL);
				} else {
					mLastMessage.setTypeface(null,Typeface.BOLD);
					mSenderName.setTypeface(null,Typeface.BOLD);
				}
			}
			if (message.getStatus() == Message.STATUS_RECEIVED) {
				if (conversation.getMode() == Conversation.MODE_MULTI) {
					mSenderName.setVisibility(View.VISIBLE);
					mSenderName.setText(UIHelper.getMessageDisplayName(message).split("\\s+")[0]+':');
				} else {
					mSenderName.setVisibility(View.GONE);
				}
			} else if (message.getType() != Message.TYPE_STATUS) {
				mSenderName.setVisibility(View.VISIBLE);
				mSenderName.setText(activity.getString(R.string.me)+':');
			} else {
				mSenderName.setVisibility(View.GONE);
			}
		}

		long muted_till = conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL,0);
		if (muted_till == Long.MAX_VALUE) {
			notificationStatus.setVisibility(View.VISIBLE);
			int ic_notifications_off = 	  activity.getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
			notificationStatus.setImageResource(ic_notifications_off);
		} else if (muted_till >= System.currentTimeMillis()) {
			notificationStatus.setVisibility(View.VISIBLE);
			int ic_notifications_paused = activity.getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
			notificationStatus.setImageResource(ic_notifications_paused);
		} else if (conversation.alwaysNotify()) {
			notificationStatus.setVisibility(View.GONE);
		} else {
			notificationStatus.setVisibility(View.VISIBLE);
			int ic_notifications_none =	  activity.getThemeResource(R.attr.icon_notifications_none, R.drawable.ic_notifications_none_black_24dp);
			notificationStatus.setImageResource(ic_notifications_none);
		}

		mTimestamp.setText(UIHelper.readableTimeDifference(activity,conversation.getLatestMessage().getTimeSent()));
		ImageView profilePicture = (ImageView) view.findViewById(R.id.conversation_image);
		loadAvatar(conversation,profilePicture);

		return view;
	}

	class BitmapWorkerTask extends AsyncTask<Conversation, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private Conversation conversation = null;

		public BitmapWorkerTask(ImageView imageView) {
			imageViewReference = new WeakReference<>(imageView);
		}

		@Override
		protected Bitmap doInBackground(Conversation... params) {
			return activity.avatarService().get(params[0], activity.getPixel(56), isCancelled());
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (bitmap != null && !isCancelled()) {
				final ImageView imageView = imageViewReference.get();
				if (imageView != null) {
					imageView.setImageBitmap(bitmap);
					imageView.setBackgroundColor(0x00000000);
				}
			}
		}
	}

	public void loadAvatar(Conversation conversation, ImageView imageView) {
		if (cancelPotentialWork(conversation, imageView)) {
			final Bitmap bm = activity.avatarService().get(conversation, activity.getPixel(56), true);
			if (bm != null) {
				cancelPotentialWork(conversation, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(0x00000000);
			} else {
				imageView.setBackgroundColor(UIHelper.getColorForName(conversation.getName()));
				imageView.setImageDrawable(null);
				final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
				final AsyncDrawable asyncDrawable = new AsyncDrawable(activity.getResources(), null, task);
				imageView.setImageDrawable(asyncDrawable);
				try {
					task.execute(conversation);
				} catch (final RejectedExecutionException ignored) {
				}
			}
		}
	}

	public static boolean cancelPotentialWork(Conversation conversation, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Conversation oldConversation = bitmapWorkerTask.conversation;
			if (oldConversation == null || conversation != oldConversation) {
				bitmapWorkerTask.cancel(true);
			} else {
				return false;
			}
		}
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(res, bitmap);
			bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}
}
