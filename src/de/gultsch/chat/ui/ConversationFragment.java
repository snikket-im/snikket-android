package de.gultsch.chat.ui;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
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
	protected BitmapCache mBitmapCache = new BitmapCache();

	private EditText chatMsg;

	private OnClickListener sendMsgListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			ConversationActivity activity = (ConversationActivity) getActivity();
			final XmppConnectionService xmppService = activity.xmppConnectionService;
			if (chatMsg.getText().length() < 1)
				return;
			Message message = new Message(conversation, chatMsg.getText()
					.toString(), conversation.nextMessageEncryption);
			if (conversation.nextMessageEncryption == Message.ENCRYPTION_OTR) {
				sendOtrMessage(message);
			} else {
				sendPlainTextMessage(message);
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

		Bitmap self;

		if (showPhoneSelfContactPicture) {
			Uri selfiUri = PhoneHelper.getSefliUri(getActivity());
			try {
				self = BitmapFactory.decodeStream(getActivity()
						.getContentResolver().openInputStream(selfiUri));
			} catch (FileNotFoundException e) {
				self = UIHelper.getUnknownContactPicture(conversation
						.getAccount().getJid(), 200);
			}
		} else {
			self = UIHelper.getUnknownContactPicture(conversation.getAccount()
					.getJid(), 200);
		}

		final Bitmap selfBitmap = self;

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
				ViewHolder viewHolder;
				if (view == null) {
					switch (type) {
					case SENT:
						viewHolder = new ViewHolder();
						view = (View) inflater.inflate(R.layout.message_sent,
								null);
						viewHolder.imageView = (ImageView) view
								.findViewById(R.id.message_photo);
						viewHolder.messageBody = (TextView) view
								.findViewById(R.id.message_body);
						viewHolder.time = (TextView) view
								.findViewById(R.id.message_time);
						view.setTag(viewHolder);
						break;
					case RECIEVED:
						viewHolder = new ViewHolder();
						view = (View) inflater.inflate(
								R.layout.message_recieved, null);
						viewHolder.imageView = (ImageView) view
								.findViewById(R.id.message_photo);
						viewHolder.messageBody = (TextView) view
								.findViewById(R.id.message_body);
						viewHolder.time = (TextView) view
								.findViewById(R.id.message_time);
						view.setTag(viewHolder);
						break;
					default:
						viewHolder = null;
						break;
					}
				} else {
					viewHolder = (ViewHolder) view.getTag();
				}
				if (type == RECIEVED) {
					if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
						Uri uri = item.getConversation().getProfilePhotoUri();
						if (uri != null) {
							viewHolder.imageView.setImageBitmap(mBitmapCache.get(item.getConversation().getName(), uri));
						} else {
							viewHolder.imageView.setImageBitmap(mBitmapCache.get(item.getConversation().getName(),null));
						}
					} else if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
						if (item.getCounterpart() != null) {
							viewHolder.imageView.setImageBitmap(mBitmapCache.get(item.getCounterpart(),null));
						} else {
							viewHolder.imageView.setImageBitmap(mBitmapCache.get(item.getConversation().getName(),null));
						}
					}
				} else {
					viewHolder.imageView.setImageBitmap(selfBitmap);
				}
				String body = item.getBody();
				if (body != null) {
					viewHolder.messageBody.setText(body.trim());
				}
				if (item.getStatus() == Message.STATUS_UNSEND) {
					viewHolder.time.setTypeface(null, Typeface.ITALIC);
					viewHolder.time.setText("sending\u2026");
				} else {
					viewHolder.time.setTypeface(null, Typeface.NORMAL);
					if ((item.getConversation().getMode() == Conversation.MODE_SINGLE)
							|| (type != RECIEVED)) {
						viewHolder.time.setText(UIHelper
								.readableTimeDifference(item.getTimeSent()));
					} else {
						viewHolder.time.setText(item.getCounterpart()
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
		if (conversation.getContact() != null) {
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
						AlertDialog dialog = UIHelper
								.getVerifyFingerprintDialog(
										(ConversationActivity) getActivity(),
										conversation, fingerprintWarning);
						dialog.show();
					}
				});
			} else {
				fingerprintWarning.setVisibility(View.GONE);
			}
		} else {
			fingerprintWarning.setVisibility(View.GONE);
		}
	}

	protected void sendPlainTextMessage(Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		activity.xmppConnectionService.sendMessage(conversation.getAccount(),
				message, null);
		chatMsg.setText("");
	}

	protected void sendOtrMessage(final Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		if (conversation.hasValidOtrSession()) {
			activity.xmppConnectionService.sendMessage(
					conversation.getAccount(), message, null);
			chatMsg.setText("");
		} else {
			Hashtable<String, Integer> presences;
			if (conversation.getContact() != null) {
				presences = conversation.getContact().getPresences();
			} else {
				presences = null;
			}
			if ((presences != null) && (presences.size() == 0)) {
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
										conversation.getAccount(), message,
										null);
								chatMsg.setText("");
							}
						});
				builder.setNegativeButton("Cancel", null);
				builder.create().show();
			} else if (presences.size() == 1) {
				xmppService.sendMessage(conversation.getAccount(), message,
						(String) presences.keySet().toArray()[0]);
				chatMsg.setText("");
			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						getActivity());
				builder.setTitle("Choose Presence");
				final String[] presencesArray = new String[presences.size()];
				presences.keySet().toArray(presencesArray);
				builder.setItems(presencesArray,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								xmppService.sendMessage(
										conversation.getAccount(), message,
										presencesArray[which]);
								chatMsg.setText("");
							}
						});
				builder.create().show();
			}
		}
	}

	private static class ViewHolder {

		protected TextView time;
		protected TextView messageBody;
		protected ImageView imageView;

	}
	
	private class BitmapCache {
		private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();
		public Bitmap get(String name, Uri uri) {
			if (bitmaps.containsKey(name)) {
				return bitmaps.get(name);
			} else {
				Bitmap bm;
				if (uri!=null) {
					try {
						bm = BitmapFactory.decodeStream(getActivity()
								.getContentResolver().openInputStream(uri));
					} catch (FileNotFoundException e) {
						bm = UIHelper.getUnknownContactPicture(name, 200);
					}
				} else {
					bm = UIHelper.getUnknownContactPicture(name, 200);
				}
				bitmaps.put(name, bm);
				return bm;
			}
		}
	}
}
