package eu.siacs.conversations.ui.adapter;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
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
		Conversation conv = getItem(position);
		if (this.activity instanceof ConversationActivity) {
			ConversationActivity activity = (ConversationActivity) this.activity;
			if (!activity.getSlidingPaneLayout().isSlideable()) {
				if (conv == activity.getSelectedConversation()) {
					view.setBackgroundColor(0xffdddddd);
				} else {
					view.setBackgroundColor(Color.TRANSPARENT);
				}
			} else {
				view.setBackgroundColor(Color.TRANSPARENT);
			}
		}
		TextView convName = (TextView) view
				.findViewById(R.id.conversation_name);
		convName.setText(conv.getName());
		TextView convLastMsg = (TextView) view
				.findViewById(R.id.conversation_lastmsg);
		ImageView imagePreview = (ImageView) view
				.findViewById(R.id.conversation_lastimage);

		Message latestMessage = conv.getLatestMessage();

		if (latestMessage.getType() == Message.TYPE_TEXT
				|| latestMessage.getType() == Message.TYPE_PRIVATE) {
			if (latestMessage.getEncryption() == Message.ENCRYPTION_OTR
					&& latestMessage.getStatus() == Message.STATUS_RECEPTION_FAILED) {
				convLastMsg.setText(R.string.unable_to_decrypt_otr_message);
			} else if ((latestMessage.getEncryption() != Message.ENCRYPTION_PGP)
					&& (latestMessage.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED)) {
				String body = Config.PARSE_EMOTICONS ? UIHelper
						.transformAsciiEmoticons(latestMessage.getBody())
						: latestMessage.getBody();
				convLastMsg.setText(body);
			} else {
				convLastMsg.setText(R.string.encrypted_message_received);
			}
			convLastMsg.setVisibility(View.VISIBLE);
			imagePreview.setVisibility(View.GONE);
		} else if (latestMessage.getType() == Message.TYPE_IMAGE) {
			if (latestMessage.getStatus() >= Message.STATUS_RECEIVED) {
				convLastMsg.setVisibility(View.GONE);
				imagePreview.setVisibility(View.VISIBLE);
				activity.loadBitmap(latestMessage, imagePreview);
			} else {
				convLastMsg.setVisibility(View.VISIBLE);
				imagePreview.setVisibility(View.GONE);
				if (latestMessage.getStatus() == Message.STATUS_RECEIVED_OFFER) {
					convLastMsg.setText(R.string.image_offered_for_download);
				} else if (latestMessage.getStatus() == Message.STATUS_RECEIVING) {
					convLastMsg.setText(R.string.receiving_image);
				} else {
					convLastMsg.setText("");
				}
			}
		}

		if (!conv.isRead()) {
			convName.setTypeface(null, Typeface.BOLD);
			convLastMsg.setTypeface(null, Typeface.BOLD);
		} else {
			convName.setTypeface(null, Typeface.NORMAL);
			convLastMsg.setTypeface(null, Typeface.NORMAL);
		}

		((TextView) view.findViewById(R.id.conversation_lastupdate))
				.setText(UIHelper.readableTimeDifference(getContext(), conv
						.getLatestMessage().getTimeSent()));

		ImageView profilePicture = (ImageView) view
				.findViewById(R.id.conversation_image);
		profilePicture.setImageBitmap(conv.getImage(activity, 56));

		return view;
	}
}
