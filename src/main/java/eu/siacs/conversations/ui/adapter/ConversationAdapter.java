package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsOverviewFragment;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.Color;
import eu.siacs.conversations.ui.widget.UnreadCountCustomView;
import eu.siacs.conversations.utils.EmojiWrapper;
import eu.siacs.conversations.utils.IrregularUnicodeDetector;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

	private XmppActivity activity;
	private List<Conversation> conversations;
	private OnConversationClickListener listener;

	public ConversationAdapter(XmppActivity activity, List<Conversation> conversations) {
		this.activity = activity;
		this.conversations = conversations;
	}

	private static boolean cancelPotentialWork(Conversation conversation, ImageView imageView) {
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

	@NonNull
	@Override
	public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.conversation_list_row, parent, false);
		return ConversationViewHolder.get(view);
	}

	@Override
	public void onBindViewHolder(@NonNull ConversationViewHolder viewHolder, int position) {
		Conversation conversation = conversations.get(position);
		if (conversation == null) {
			return;
		}
		CharSequence name = conversation.getName();
		if (name instanceof Jid) {
			viewHolder.name.setText(IrregularUnicodeDetector.style(activity, (Jid) name));
		} else {
			viewHolder.name.setText(EmojiWrapper.transform(name));
		}

		if (conversation == ConversationFragment.getConversation(activity)) {
			viewHolder.frame.setBackgroundColor(Color.get(activity,R.attr.color_background_tertiary));
		} else {
			viewHolder.frame.setBackgroundColor(Color.get(activity,R.attr.color_background_primary));
		}

		Message message = conversation.getLatestMessage();
		final int unreadCount = conversation.unreadCount();
		final boolean isRead = conversation.isRead();
		final Conversation.Draft draft = isRead ? conversation.getDraft() : null;
		if (unreadCount > 0) {
			viewHolder.unreadCount.setVisibility(View.VISIBLE);
			viewHolder.unreadCount.setUnreadCount(unreadCount);
		} else {
			viewHolder.unreadCount.setVisibility(View.GONE);
		}

		if (isRead) {
			viewHolder.name.setTypeface(null, Typeface.NORMAL);
		} else {
			viewHolder.name.setTypeface(null, Typeface.BOLD);
		}

		if (draft != null) {
			viewHolder.lastMessageIcon.setVisibility(View.GONE);
			viewHolder.lastMessage.setText(EmojiWrapper.transform(draft.getMessage()));
			viewHolder.sender.setText(R.string.draft);
			viewHolder.sender.setVisibility(View.VISIBLE);
			viewHolder.lastMessage.setTypeface(null, Typeface.NORMAL);
			viewHolder.sender.setTypeface(null, Typeface.ITALIC);
		} else {
			final boolean fileAvailable = message.getTransferable() == null || message.getTransferable().getStatus() != Transferable.STATUS_DELETED;
			final boolean showPreviewText;
			if (fileAvailable && (message.isFileOrImage() || message.treatAsDownloadable() || message.isGeoUri())) {
				final int imageResource;
				if (message.isGeoUri()) {
					imageResource = activity.getThemeResource(R.attr.ic_attach_location, R.drawable.ic_attach_location);
					showPreviewText = false;
				} else {
					final String mime = message.getMimeType();
					switch (mime == null ? "" : mime.split("/")[0]) {
						case "image":
							imageResource = activity.getThemeResource(R.attr.ic_attach_photo, R.drawable.ic_attach_photo);
							showPreviewText = false;
							break;
						case "video":
							imageResource = activity.getThemeResource(R.attr.ic_attach_videocam, R.drawable.ic_attach_videocam);
							showPreviewText = false;
							break;
						case "audio":
							imageResource = activity.getThemeResource(R.attr.ic_attach_record, R.drawable.ic_attach_record);
							showPreviewText = false;
							break;
						default:
							imageResource = activity.getThemeResource(R.attr.ic_attach_document, R.drawable.ic_attach_document);
							showPreviewText = true;
							break;
					}
				}
				viewHolder.lastMessageIcon.setImageResource(imageResource);
				viewHolder.lastMessageIcon.setVisibility(View.VISIBLE);
			} else {
				viewHolder.lastMessageIcon.setVisibility(View.GONE);
				showPreviewText = true;
			}
			final Pair<CharSequence, Boolean> preview = UIHelper.getMessagePreview(activity, message, viewHolder.lastMessage.getCurrentTextColor());
			if (showPreviewText) {
				viewHolder.lastMessage.setText(EmojiWrapper.transform(UIHelper.shorten(preview.first)));
			} else {
				viewHolder.lastMessageIcon.setContentDescription(preview.first);
			}
			viewHolder.lastMessage.setVisibility(showPreviewText ? View.VISIBLE : View.GONE);
			if (preview.second) {
				if (isRead) {
					viewHolder.lastMessage.setTypeface(null, Typeface.ITALIC);
					viewHolder.sender.setTypeface(null, Typeface.NORMAL);
				} else {
					viewHolder.lastMessage.setTypeface(null, Typeface.BOLD_ITALIC);
					viewHolder.sender.setTypeface(null, Typeface.BOLD);
				}
			} else {
				if (isRead) {
					viewHolder.lastMessage.setTypeface(null, Typeface.NORMAL);
					viewHolder.sender.setTypeface(null, Typeface.NORMAL);
				} else {
					viewHolder.lastMessage.setTypeface(null, Typeface.BOLD);
					viewHolder.sender.setTypeface(null, Typeface.BOLD);
				}
			}
			if (message.getStatus() == Message.STATUS_RECEIVED) {
				if (conversation.getMode() == Conversation.MODE_MULTI) {
					viewHolder.sender.setVisibility(View.VISIBLE);
					viewHolder.sender.setText(UIHelper.getMessageDisplayName(message).split("\\s+")[0] + ':');
				} else {
					viewHolder.sender.setVisibility(View.GONE);
				}
			} else if (message.getType() != Message.TYPE_STATUS) {
				viewHolder.sender.setVisibility(View.VISIBLE);
				viewHolder.sender.setText(activity.getString(R.string.me) + ':');
			} else {
				viewHolder.sender.setVisibility(View.GONE);
			}
		}

		long muted_till = conversation.getLongAttribute(Conversation.ATTRIBUTE_MUTED_TILL, 0);
		if (muted_till == Long.MAX_VALUE) {
			viewHolder.notificationIcon.setVisibility(View.VISIBLE);
			int ic_notifications_off = activity.getThemeResource(R.attr.icon_notifications_off, R.drawable.ic_notifications_off_black_24dp);
			viewHolder.notificationIcon.setImageResource(ic_notifications_off);
		} else if (muted_till >= System.currentTimeMillis()) {
			viewHolder.notificationIcon.setVisibility(View.VISIBLE);
			int ic_notifications_paused = activity.getThemeResource(R.attr.icon_notifications_paused, R.drawable.ic_notifications_paused_black_24dp);
			viewHolder.notificationIcon.setImageResource(ic_notifications_paused);
		} else if (conversation.alwaysNotify()) {
			viewHolder.notificationIcon.setVisibility(View.GONE);
		} else {
			viewHolder.notificationIcon.setVisibility(View.VISIBLE);
			int ic_notifications_none = activity.getThemeResource(R.attr.icon_notifications_none, R.drawable.ic_notifications_none_black_24dp);
			viewHolder.notificationIcon.setImageResource(ic_notifications_none);
		}

		long timestamp;
		if (draft != null) {
			timestamp = draft.getTimestamp();
		} else {
			timestamp = conversation.getLatestMessage().getTimeSent();
		}
		viewHolder.timestamp.setText(UIHelper.readableTimeDifference(activity, timestamp));
		loadAvatar(conversation, viewHolder.avatar);
		viewHolder.itemView.setOnClickListener(v -> listener.onConversationClick(v,conversation));
	}

	@Override
	public int getItemCount() {
		return conversations.size();
	}

	public void setConversationClickListener(OnConversationClickListener listener) {
		this.listener = listener;
	}

	private void loadAvatar(Conversation conversation, ImageView imageView) {
		if (cancelPotentialWork(conversation, imageView)) {
			final Bitmap bm = activity.avatarService().get(conversation, activity.getPixel(56), true);
			if (bm != null) {
				cancelPotentialWork(conversation, imageView);
				imageView.setImageBitmap(bm);
				imageView.setBackgroundColor(0x00000000);
			} else {
				imageView.setBackgroundColor(UIHelper.getColorForName(conversation.getName().toString()));
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

	public void insert(Conversation c, int position) {
		conversations.add(position,c);
		notifyDataSetChanged();
	}

	public void remove(Conversation conversation,int position) {
		conversations.remove(conversation);
		notifyItemRemoved(position);
	}

	public static class ConversationViewHolder extends RecyclerView.ViewHolder {
		private TextView name;
		private TextView lastMessage;
		private ImageView lastMessageIcon;
		private TextView sender;
		private TextView timestamp;
		private ImageView notificationIcon;
		private UnreadCountCustomView unreadCount;
		private ImageView avatar;
		private FrameLayout frame;

		private ConversationViewHolder(View view) {
			super(view);
		}

		public static ConversationViewHolder get(View layout) {
			ConversationViewHolder conversationViewHolder = (ConversationViewHolder) layout.getTag();
			if (conversationViewHolder == null) {
				conversationViewHolder = new ConversationViewHolder(layout);
				conversationViewHolder.frame = layout.findViewById(R.id.frame);
				conversationViewHolder.name = layout.findViewById(R.id.conversation_name);
				conversationViewHolder.lastMessage = layout.findViewById(R.id.conversation_lastmsg);
				conversationViewHolder.lastMessageIcon = layout.findViewById(R.id.conversation_lastmsg_img);
				conversationViewHolder.timestamp = layout.findViewById(R.id.conversation_lastupdate);
				conversationViewHolder.sender = layout.findViewById(R.id.sender_name);
				conversationViewHolder.notificationIcon = layout.findViewById(R.id.notification_status);
				conversationViewHolder.unreadCount = layout.findViewById(R.id.unread_count);
				conversationViewHolder.avatar = layout.findViewById(R.id.conversation_image);
				layout.setTag(conversationViewHolder);
			}
			return conversationViewHolder;
		}
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

	class BitmapWorkerTask extends AsyncTask<Conversation, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private Conversation conversation = null;

		public BitmapWorkerTask(ImageView imageView) {
			imageViewReference = new WeakReference<>(imageView);
		}

		@Override
		protected Bitmap doInBackground(Conversation... params) {
			this.conversation = params[0];
			return activity.avatarService().get(this.conversation, activity.getPixel(56), isCancelled());
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

	public interface OnConversationClickListener {
		void onConversationClick(View view, Conversation conversation);
	}
}
