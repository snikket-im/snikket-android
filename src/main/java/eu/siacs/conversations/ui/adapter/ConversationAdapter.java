package eu.siacs.conversations.ui.adapter;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.UIHelper;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ConversationAdapter extends ArrayAdapter<Conversation> {

	private XmppActivity activity;

	public ConversationAdapter(XmppActivity activity,
			List<Conversation> conversations) {
		super(activity, 0, conversations);
		this.activity = activity;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		if (view == null) {
			LayoutInflater inflater = (LayoutInflater) activity
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.conversation_list_row,
					parent, false);
		}
		Conversation conversation = getItem(position);
		if (this.activity instanceof ConversationActivity) {
			ConversationActivity activity = (ConversationActivity) this.activity;
			if (!activity.isConversationsOverviewHideable()) {
				if (conversation == activity.getSelectedConversation()) {
					view.setBackgroundColor(activity
							.getSecondaryBackgroundColor());
				} else {
					view.setBackgroundColor(Color.TRANSPARENT);
				}
			} else {
				view.setBackgroundColor(Color.TRANSPARENT);
			}
		}
		TextView convName = (TextView) view
			.findViewById(R.id.conversation_name);
		if (conversation.getMode() == Conversation.MODE_SINGLE
				|| activity.useSubjectToIdentifyConference()) {
			convName.setText(conversation.getName());
		} else {
			convName.setText(conversation.getJid().toBareJid().toString());
		}
		TextView mLastMessage = (TextView) view
			.findViewById(R.id.conversation_lastmsg);
		TextView mTimestamp = (TextView) view
			.findViewById(R.id.conversation_lastupdate);
		ImageView imagePreview = (ImageView) view
			.findViewById(R.id.conversation_lastimage);

		Message message = conversation.getLatestMessage();

		if (!conversation.isRead()) {
			convName.setTypeface(null, Typeface.BOLD);
		} else {
			convName.setTypeface(null, Typeface.NORMAL);
		}

		if (message.getType() == Message.TYPE_IMAGE || message.getType() == Message.TYPE_FILE
				|| message.getDownloadable() != null) {
			Downloadable d = message.getDownloadable();
			if (conversation.isRead()) {
				mLastMessage.setTypeface(null, Typeface.ITALIC);
			} else {
				mLastMessage.setTypeface(null, Typeface.BOLD_ITALIC);
			}
			if (d != null) {
				mLastMessage.setVisibility(View.VISIBLE);
				imagePreview.setVisibility(View.GONE);
				if (d.getStatus() == Downloadable.STATUS_CHECKING) {
					mLastMessage.setText(R.string.checking_image);
				} else if (d.getStatus() == Downloadable.STATUS_DOWNLOADING) {
					if (message.getType() == Message.TYPE_FILE) {
						mLastMessage.setText(getContext().getString(R.string.receiving_file,d.getMimeType(), d.getProgress()));
					} else {
						mLastMessage.setText(getContext().getString(R.string.receiving_image, d.getProgress()));
					}
				} else if (d.getStatus() == Downloadable.STATUS_OFFER) {
					if (message.getType() == Message.TYPE_FILE) {
						mLastMessage.setText(R.string.file_offered_for_download);
					} else {
						mLastMessage.setText(R.string.image_offered_for_download);
					}
				} else if (d.getStatus() == Downloadable.STATUS_OFFER_CHECK_FILESIZE) {
					mLastMessage.setText(R.string.image_offered_for_download);
				} else if (d.getStatus() == Downloadable.STATUS_DELETED) {
					if (message.getType() == Message.TYPE_FILE) {
						mLastMessage.setText(R.string.file_deleted);
					} else {
						mLastMessage.setText(R.string.image_file_deleted);
					}
				} else if (d.getStatus() == Downloadable.STATUS_FAILED) {
					if (message.getType() == Message.TYPE_FILE) {
						mLastMessage.setText(R.string.file_transmission_failed);
					} else {
						mLastMessage.setText(R.string.image_transmission_failed);
					}
				} else if (message.getImageParams().width > 0) {
					mLastMessage.setVisibility(View.GONE);
					imagePreview.setVisibility(View.VISIBLE);
					activity.loadBitmap(message, imagePreview);
				} else {
					mLastMessage.setText("");
				}
			} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
				imagePreview.setVisibility(View.GONE);
				mLastMessage.setVisibility(View.VISIBLE);
				mLastMessage.setText(R.string.encrypted_message_received);
			} else if (message.getType() == Message.TYPE_FILE && message.getImageParams().width <= 0) {
				DownloadableFile file = activity.xmppConnectionService.getFileBackend().getFile(message);
				mLastMessage.setVisibility(View.VISIBLE);
				imagePreview.setVisibility(View.GONE);
				mLastMessage.setText(getContext().getString(R.string.file,file.getMimeType()));
			} else {
				mLastMessage.setVisibility(View.GONE);
				imagePreview.setVisibility(View.VISIBLE);
				activity.loadBitmap(message, imagePreview);
			}
		} else {
			if ((message.getEncryption() != Message.ENCRYPTION_PGP)
					&& (message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED)) {
				mLastMessage.setText(message.getBody());
			} else {
				mLastMessage.setText(R.string.encrypted_message_received);
			}
			if (!conversation.isRead()) {
				mLastMessage.setTypeface(null, Typeface.BOLD);
			} else {
				mLastMessage.setTypeface(null, Typeface.NORMAL);
			}
			mLastMessage.setVisibility(View.VISIBLE);
			imagePreview.setVisibility(View.GONE);
		}
		mTimestamp.setText(UIHelper.readableTimeDifference(getContext(),
					conversation.getLatestMessage().getTimeSent()));

		ImageView profilePicture = (ImageView) view
			.findViewById(R.id.conversation_image);
		profilePicture.setImageBitmap(activity.avatarService().get(
					conversation, activity.getPixel(56)));

		return view;
	}
}
