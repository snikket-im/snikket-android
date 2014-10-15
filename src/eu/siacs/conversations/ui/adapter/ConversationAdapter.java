package eu.siacs.conversations.ui.adapter;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
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
			view = (View) inflater.inflate(R.layout.conversation_list_row,
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
			convName.setText(conversation.getContactJid().split("/")[0]);
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

		if (message.getType() == Message.TYPE_IMAGE
				|| message.getDownloadable() != null) {
			Downloadable d = message.getDownloadable();
			if (d != null) {
				mLastMessage.setVisibility(View.VISIBLE);
				imagePreview.setVisibility(View.GONE);
				if (conversation.isRead()) {
					mLastMessage.setTypeface(null, Typeface.ITALIC);
				} else {
					mLastMessage.setTypeface(null, Typeface.BOLD_ITALIC);
				}
				if (d.getStatus() == Downloadable.STATUS_CHECKING) {
					mLastMessage.setText(R.string.checking_image);
				} else if (d.getStatus() == Downloadable.STATUS_DOWNLOADING) {
					mLastMessage.setText(R.string.receiving_image);
				} else if (d.getStatus() == Downloadable.STATUS_OFFER) {
					mLastMessage.setText(R.string.image_offered_for_download);
				} else if (d.getStatus() == Downloadable.STATUS_DELETED) {
					mLastMessage.setText(R.string.image_file_deleted);
				} else {
					mLastMessage.setText("");
				}
			} else {
				mLastMessage.setVisibility(View.GONE);
				imagePreview.setVisibility(View.VISIBLE);
				activity.loadBitmap(message, imagePreview);
			}
		} else {
			if ((message.getEncryption() != Message.ENCRYPTION_PGP)
					&& (message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED)) {
				String body = Config.PARSE_EMOTICONS ? UIHelper
						.transformAsciiEmoticons(message.getBody()) : message
						.getBody();
				mLastMessage.setText(body);
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
		profilePicture.setImageBitmap(conversation.getImage(activity, 56));

		return view;
	}
}
