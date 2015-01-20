package eu.siacs.conversations.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.UIHelper;

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
			LayoutInflater inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(R.layout.conversation_list_row,parent, false);
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
		TextView convName = (TextView) view.findViewById(R.id.conversation_name);
		if (conversation.getMode() == Conversation.MODE_SINGLE || activity.useSubjectToIdentifyConference()) {
			convName.setText(conversation.getName());
		} else {
			convName.setText(conversation.getJid().toBareJid().toString());
		}
		TextView mLastMessage = (TextView) view.findViewById(R.id.conversation_lastmsg);
		TextView mTimestamp = (TextView) view.findViewById(R.id.conversation_lastupdate);
		ImageView imagePreview = (ImageView) view.findViewById(R.id.conversation_lastimage);

		Message message = conversation.getLatestMessage();

		if (!conversation.isRead()) {
			convName.setTypeface(null, Typeface.BOLD);
		} else {
			convName.setTypeface(null, Typeface.NORMAL);
		}

		if (message.getImageParams().width > 0
				&& (message.getDownloadable() == null
				|| message.getDownloadable().getStatus() != Downloadable.STATUS_DELETED)) {
			mLastMessage.setVisibility(View.GONE);
			imagePreview.setVisibility(View.VISIBLE);
			activity.loadBitmap(message, imagePreview);
		} else {
			Pair<String,Boolean> preview = UIHelper.getMessagePreview(activity,message);
			mLastMessage.setVisibility(View.VISIBLE);
			imagePreview.setVisibility(View.GONE);
			mLastMessage.setText(preview.first);
			if (preview.second) {
				if (conversation.isRead()) {
					mLastMessage.setTypeface(null, Typeface.ITALIC);
				} else {
					mLastMessage.setTypeface(null,Typeface.BOLD_ITALIC);
				}
			} else {
				if (conversation.isRead()) {
					mLastMessage.setTypeface(null,Typeface.NORMAL);
				} else {
					mLastMessage.setTypeface(null,Typeface.BOLD);
				}
			}
		}

		mTimestamp.setText(UIHelper.readableTimeDifference(activity,conversation.getLatestMessage().getTimeSent()));
		ImageView profilePicture = (ImageView) view.findViewById(R.id.conversation_image);
		profilePicture.setImageBitmap(activity.avatarService().get(conversation, activity.getPixel(56)));

		return view;
	}
}