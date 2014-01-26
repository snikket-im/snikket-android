package de.gultsch.chat.ui;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.utils.Beautifier;
import android.app.Fragment;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Profile;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class ConversationFragment extends Fragment {

	Conversation conversation;

	public void setConversation(Conversation conv) {
		this.conversation = conv;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		String[] mProjection = new String[]
			    {
			        Profile._ID,
			        Profile.PHOTO_THUMBNAIL_URI
			    };
			Cursor mProfileCursor = getActivity().getContentResolver().query(
			        Profile.CONTENT_URI,
			        mProjection ,
			        null,
			        null,
			        null);
			
		mProfileCursor.moveToFirst();
		final Uri profilePicture = Uri.parse(mProfileCursor.getString(1));
		
		Log.d("gultsch","found user profile pic "+profilePicture.toString());
		
		final View view = inflater.inflate(R.layout.fragment_conversation, container,
				false);
		((ImageButton) view.findViewById(R.id.textSendButton))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						EditText chatMsg = (EditText) view.findViewById(R.id.textinput);
						if (chatMsg.getText().length() < 1) return;
						Message message = new Message(conversation,chatMsg.getText().toString(),
								Message.ENCRYPTION_NONE);
						XmppActivity activity = (XmppActivity) getActivity();
						activity.xmppConnectionService.sendMessage(message);
						conversation.getMessages().add(message);
						chatMsg.setText("");
						
						ListView messagesView = (ListView) view.findViewById(R.id.messages_view);
						ArrayAdapter<Message> adapter = (ArrayAdapter<Message>) messagesView.getAdapter();
						adapter.notifyDataSetChanged();
						
						messagesView.setSelection(conversation.getMessages().size() -1);
					}
				});

		ListView messagesView = (ListView) view
				.findViewById(R.id.messages_view);
		messagesView.setAdapter(new ArrayAdapter<Message>(this.getActivity()
				.getApplicationContext(), R.layout.message_sent,
				this.conversation.getMessages()) {

			@Override
			public View getView(int position, View view, ViewGroup parent) {
				Message item = getItem(position);
				if ((item.getStatus() != Message.STATUS_RECIEVED)
						|| (item.getStatus() == Message.STATUS_SEND)) {
					view = (View) inflater.inflate(R.layout.message_sent, null);
					((ImageView) view.findViewById(R.id.message_photo)).setImageURI(profilePicture);
				}
				((TextView) view.findViewById(R.id.message_body)).setText(item.getBody());
				TextView time = (TextView) view.findViewById(R.id.message_time);
				if (item.getStatus() == Message.STATUS_UNSEND) {
					time.setTypeface(null, Typeface.ITALIC);
				} else {
					time.setText(Beautifier.readableTimeDifference(item.getTimeSent()));
				}
				return view;
			}
		});

		return view;
	}

	public Conversation getConversation() {
		return conversation;
	}
}
