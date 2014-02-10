package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.utils.PhoneHelper;
import de.gultsch.chat.utils.UIHelper;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

public class ConversationFragment extends Fragment {
	
	protected Conversation conversation;
	protected ListView messagesView;
	protected LayoutInflater inflater;
	protected List<Message> messageList = new ArrayList<Message>();
	protected ArrayAdapter<Message> messageListAdapter;
	protected Contact contact;
	
	@Override
	public View onCreateView(final LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {

		this.inflater = inflater;


		final View view = inflater.inflate(R.layout.fragment_conversation,
				container, false);
		((ImageButton) view.findViewById(R.id.textSendButton))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						ConversationActivity activity = (ConversationActivity) getActivity();
						EditText chatMsg = (EditText) view
								.findViewById(R.id.textinput);
						if (chatMsg.getText().length() < 1)
							return;
						Message message = new Message(conversation, chatMsg
								.getText().toString(), Message.ENCRYPTION_NONE);
						activity.xmppConnectionService.sendMessage(conversation.getAccount(),message);
						chatMsg.setText("");
						
						if (conversation.getMode()==Conversation.MODE_SINGLE) {
							conversation.getMessages().add(message);
							messageList.add(message);
						}
						
						activity.updateConversationList();
						
						messagesView.setSelection(messageList.size() - 1);
					}
				});

		messagesView = (ListView) view.findViewById(R.id.messages_view);
		
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(getActivity().getApplicationContext());
		boolean showPhoneSelfContactPicture = sharedPref.getBoolean("show_phone_selfcontact_picture",true);
		
		final Uri selfiUri;
		if (showPhoneSelfContactPicture) {
			selfiUri =  PhoneHelper.getSefliUri(getActivity());
		} else {
			selfiUri = null;
		}
		
		messageListAdapter = new ArrayAdapter<Message>(this.getActivity()
				.getApplicationContext(), R.layout.message_sent, this.messageList) {

			private static final int SENT = 0;
			private static final int RECIEVED = 1;
			
			@Override
			public int getViewTypeCount() {
				return 2;
			}

			@Override
			public int getItemViewType(int position) {
				if (getItem(position).getStatus() == Message.STATUS_RECIEVED) {
					return RECIEVED;
				} else {
					return SENT;
				}
			}

			@Override
			public View getView(int position, View view, ViewGroup parent) {
				Message item = getItem(position);
				int type = getItemViewType(position);
				if (view == null) {
					switch (type) {
					case SENT:
						view = (View) inflater.inflate(R.layout.message_sent,
								null);
						break;
					case RECIEVED:
						view = (View) inflater.inflate(
								R.layout.message_recieved, null);
						break;
					}
				}
				ImageView imageView = (ImageView) view.findViewById(R.id.message_photo);
				if (type == RECIEVED) {
					if(item.getConversation().getMode()==Conversation.MODE_SINGLE) {
						Uri uri = item.getConversation().getProfilePhotoUri();
						if (uri!=null) {
							imageView.setImageURI(uri);
						} else {
							imageView.setImageBitmap(UIHelper.getUnknownContactPicture(item.getConversation().getName(), 200));
						}
					} else if (item.getConversation().getMode()==Conversation.MODE_MULTI) {
						if (item.getCounterpart()!=null) {
							imageView.setImageBitmap(UIHelper.getUnknownContactPicture(item.getCounterpart(), 200));
						} else {
							imageView.setImageBitmap(UIHelper.getUnknownContactPicture(item.getConversation().getName(), 200));
						}
					}
				} else {
					if (selfiUri!=null) {
						imageView.setImageURI(selfiUri);
					} else {
						imageView.setImageBitmap(UIHelper.getUnknownContactPicture(conversation.getAccount().getJid(),200));
					}
				}
				TextView messageBody = (TextView) view.findViewById(R.id.message_body);
				String body = item.getBody();
				if (body!=null) {
					messageBody.setText(body.trim());
				}
				TextView time = (TextView) view.findViewById(R.id.message_time);
				if (item.getStatus() == Message.STATUS_UNSEND) {
					time.setTypeface(null, Typeface.ITALIC);
				} else {
					time.setText(UIHelper.readableTimeDifference(item
							.getTimeSent()));
				}
				return view;
			}
		};
		messagesView.setAdapter(messageListAdapter);

		return view;
	}

	@Override
	public void onStart() {
		super.onStart();
		final ConversationActivity activity = (ConversationActivity) getActivity();
		
		if (activity.xmppConnectionServiceBound) {
			this.conversation = activity.getSelectedConversation();
			updateMessages();
			// rendering complete. now go tell activity to close pane
			if (!activity.shouldPaneBeOpen()) {
				activity.getSlidingPaneLayout().closePane();
				activity.getActionBar().setDisplayHomeAsUpEnabled(true);
				activity.getActionBar().setTitle(conversation.getName());
				activity.invalidateOptionsMenu();
				if (!conversation.isRead()) {
					conversation.markRead();
					activity.updateConversationList();
				}
			}
		}
	}
	
	public void onBackendConnected() {
		final ConversationActivity activity = (ConversationActivity) getActivity();
		this.conversation = activity.getSelectedConversation();
		updateMessages();
		// rendering complete. now go tell activity to close pane
		if (!activity.shouldPaneBeOpen()) {
			activity.getSlidingPaneLayout().closePane();
			activity.getActionBar().setDisplayHomeAsUpEnabled(true);
			activity.getActionBar().setTitle(conversation.getName());
			activity.invalidateOptionsMenu();
			if (!conversation.isRead()) {
				conversation.markRead();
				activity.updateConversationList();
			}
		}
	}

	public void updateMessages() {
		this.messageList.clear();
		this.messageList.addAll(this.conversation.getMessages());
		this.messageListAdapter.notifyDataSetChanged();
		int size = this.messageList.size();
		if (size >= 1)
			messagesView.setSelection(size - 1);
		ConversationActivity activity = (ConversationActivity) getActivity();
		if (!activity.shouldPaneBeOpen()) {
			conversation.markRead();
			activity.updateConversationList();
		}
	}
}
