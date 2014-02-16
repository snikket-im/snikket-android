package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.SessionStatus;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.services.XmppConnectionService;
import de.gultsch.chat.utils.PhoneHelper;
import de.gultsch.chat.utils.UIHelper;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
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

	private EditText chatMsg;

	private OnClickListener sendMsgListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			ConversationActivity activity = (ConversationActivity) getActivity();
			final XmppConnectionService xmppService = activity.xmppConnectionService;
			if (chatMsg.getText().length() < 1)
				return;
			final Message message = new Message(conversation, chatMsg.getText()
					.toString(), conversation.nextMessageEncryption);
			if (conversation.nextMessageEncryption == Message.ENCRYPTION_OTR) {
				if (conversation.hasValidOtrSession()) {
					activity.xmppConnectionService.sendMessage(
							conversation.getAccount(), message, null);
					chatMsg.setText("");
				} else {
					Hashtable<String, Integer> presences = conversation
							.getContact().getPresences();
					if (presences.size() == 0) {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								getActivity());
						builder.setTitle("Contact is offline");
						builder.setIconAttribute(android.R.attr.alertDialogIcon);
						builder.setMessage("Sending OTR encrypted messages to an offline contact is impossible.");
						builder.setPositiveButton("Send plain text",
								new DialogInterface.OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
										message.setEncryption(Message.ENCRYPTION_NONE);
										xmppService.sendMessage(
												conversation.getAccount(),
												message, null);
										chatMsg.setText("");
									}
								});
						builder.setNegativeButton("Cancel", null);
						builder.create().show();
					} else if (presences.size() == 1) {
						xmppService.sendMessage(conversation.getAccount(),
								message,
								(String) presences.keySet().toArray()[0]);
						chatMsg.setText("");
					}
				}
			} else {
				xmppService.sendMessage(conversation.getAccount(), message,
						null);
				chatMsg.setText("");
			}
		}
	};

	public void updateChatMsgHint() {
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			chatMsg.setHint("Send message to conference");
		} else {
			switch (conversation.nextMessageEncryption) {
			case Message.ENCRYPTION_NONE:
				chatMsg.setHint("Send plain text message");
				break;
			case Message.ENCRYPTION_OTR:
				chatMsg.setHint("Send OTR encrypted message");
				break;
			case Message.ENCRYPTION_PGP:
				chatMsg.setHint("Send openPGP encryted messeage");
			default:
				break;
			}
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {

		this.inflater = inflater;

		final View view = inflater.inflate(R.layout.fragment_conversation,
				container, false);
		chatMsg = (EditText) view.findViewById(R.id.textinput);
		ImageButton sendButton = (ImageButton) view
				.findViewById(R.id.textSendButton);
		sendButton.setOnClickListener(this.sendMsgListener);

		messagesView = (ListView) view.findViewById(R.id.messages_view);

		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(getActivity()
						.getApplicationContext());
		boolean showPhoneSelfContactPicture = sharedPref.getBoolean(
				"show_phone_selfcontact_picture", true);

		final Uri selfiUri;
		if (showPhoneSelfContactPicture) {
			selfiUri = PhoneHelper.getSefliUri(getActivity());
		} else {
			selfiUri = null;
		}

		messageListAdapter = new ArrayAdapter<Message>(this.getActivity()
				.getApplicationContext(), R.layout.message_sent,
				this.messageList) {

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
				ImageView imageView = (ImageView) view
						.findViewById(R.id.message_photo);
				if (type == RECIEVED) {
					if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
						Uri uri = item.getConversation().getProfilePhotoUri();
						if (uri != null) {
							imageView.setImageURI(uri);
						} else {
							imageView.setImageBitmap(UIHelper
									.getUnknownContactPicture(item
											.getConversation().getName(), 200));
						}
					} else if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
						if (item.getCounterpart() != null) {
							imageView.setImageBitmap(UIHelper
									.getUnknownContactPicture(
											item.getCounterpart(), 200));
						} else {
							imageView.setImageBitmap(UIHelper
									.getUnknownContactPicture(item
											.getConversation().getName(), 200));
						}
					}
				} else {
					if (selfiUri != null) {
						imageView.setImageURI(selfiUri);
					} else {
						imageView.setImageBitmap(UIHelper
								.getUnknownContactPicture(conversation
										.getAccount().getJid(), 200));
					}
				}
				TextView messageBody = (TextView) view
						.findViewById(R.id.message_body);
				String body = item.getBody();
				if (body != null) {
					messageBody.setText(body.trim());
				}
				TextView time = (TextView) view.findViewById(R.id.message_time);
				if (item.getStatus() == Message.STATUS_UNSEND) {
					time.setTypeface(null, Typeface.ITALIC);
					time.setText("sending\u2026");
				} else {
					time.setTypeface(null, Typeface.NORMAL);
					if ((item.getConversation().getMode() == Conversation.MODE_SINGLE)
							|| (type != RECIEVED)) {
						time.setText(UIHelper.readableTimeDifference(item
								.getTimeSent()));
					} else {
						time.setText(item.getCounterpart()
								+ " \u00B7 "
								+ UIHelper.readableTimeDifference(item
										.getTimeSent()));
					}
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
		ConversationActivity activity = (ConversationActivity) getActivity();
		this.messageList.clear();
		this.messageList.addAll(this.conversation.getMessages());
		this.messageListAdapter.notifyDataSetChanged();
		if (messageList.size() >= 1) {
			int latestEncryption = this.conversation.getLatestMessage()
					.getEncryption();
			conversation.nextMessageEncryption = latestEncryption;
			makeFingerprintWarning(latestEncryption);
		}
		getActivity().invalidateOptionsMenu();
		updateChatMsgHint();
		int size = this.messageList.size();
		if (size >= 1)
			messagesView.setSelection(size - 1);
		if (!activity.shouldPaneBeOpen()) {
			conversation.markRead();
			activity.updateConversationList();
		}
	}

	protected void makeFingerprintWarning(int latestEncryption) {
		final LinearLayout fingerprintWarning = (LinearLayout) getView()
				.findViewById(R.id.new_fingerprint);
		Set<String> knownFingerprints = conversation.getContact()
				.getOtrFingerprints();
		if ((latestEncryption == Message.ENCRYPTION_OTR)
				&& (conversation.hasValidOtrSession()
						&& (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) && (!knownFingerprints
							.contains(conversation.getOtrFingerprint())))) {
			fingerprintWarning.setVisibility(View.VISIBLE);
			TextView fingerprint = (TextView) getView().findViewById(
					R.id.otr_fingerprint);
			fingerprint.setText(conversation.getOtrFingerprint());
			fingerprintWarning.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					AlertDialog dialog = UIHelper.getVerifyFingerprintDialog((ConversationActivity) getActivity(),conversation,fingerprintWarning);
					dialog.show();
				}
			});
		} else {
			fingerprintWarning.setVisibility(View.GONE);
		}
	}
}
