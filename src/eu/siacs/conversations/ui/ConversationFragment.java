package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.services.ImageProvider;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jingle.JingleConnection;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Selection;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class ConversationFragment extends Fragment {

	protected Conversation conversation;
	protected ListView messagesView;
	protected LayoutInflater inflater;
	protected List<Message> messageList = new ArrayList<Message>();
	protected ArrayAdapter<Message> messageListAdapter;
	protected Contact contact;
	protected BitmapCache mBitmapCache = new BitmapCache();

	protected String queuedPqpMessage = null;

	private EditText chatMsg;
	private String pastedText = null;

	protected Bitmap selfBitmap;

	private boolean useSubject = true;

	private IntentSender askForPassphraseIntent = null;

	private OnClickListener sendMsgListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (chatMsg.getText().length() < 1)
				return;
			Message message = new Message(conversation, chatMsg.getText()
					.toString(), conversation.getNextEncryption());
			if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
				sendOtrMessage(message);
			} else if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP) {
				sendPgpMessage(message);
			} else {
				sendPlainTextMessage(message);
			}
		}
	};
	protected OnClickListener clickToDecryptListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (askForPassphraseIntent != null) {
				try {
					getActivity().startIntentSenderForResult(
							askForPassphraseIntent,
							ConversationActivity.REQUEST_DECRYPT_PGP, null, 0,
							0, 0);
				} catch (SendIntentException e) {
					Log.d("xmppService", "couldnt fire intent");
				}
			}
		}
	};

	private LinearLayout pgpInfo;
	private LinearLayout mucError;
	private TextView mucErrorText;
	private OnClickListener clickToMuc = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Intent intent = new Intent(getActivity(), MucDetailsActivity.class);
			intent.setAction(MucDetailsActivity.ACTION_VIEW_MUC);
			intent.putExtra("uuid", conversation.getUuid());
			startActivity(intent);
		}
	};
	private ConversationActivity activity;

	public void hidePgpPassphraseBox() {
		pgpInfo.setVisibility(View.GONE);
	}

	public void updateChatMsgHint() {
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			chatMsg.setHint(getString(R.string.send_message_to_conference));
		} else {
			switch (conversation.getNextEncryption()) {
			case Message.ENCRYPTION_NONE:
				chatMsg.setHint(getString(R.string.send_plain_text_message));
				break;
			case Message.ENCRYPTION_OTR:
				chatMsg.setHint(getString(R.string.send_otr_message));
				break;
			case Message.ENCRYPTION_PGP:
				chatMsg.setHint(getString(R.string.send_pgp_message));
				break;
			default:
				break;
			}
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {

		final DisplayMetrics metrics = getResources().getDisplayMetrics();

		this.inflater = inflater;

		final View view = inflater.inflate(R.layout.fragment_conversation,
				container, false);
		chatMsg = (EditText) view.findViewById(R.id.textinput);

		ImageButton sendButton = (ImageButton) view
				.findViewById(R.id.textSendButton);
		sendButton.setOnClickListener(this.sendMsgListener);

		pgpInfo = (LinearLayout) view.findViewById(R.id.pgp_keyentry);
		pgpInfo.setOnClickListener(clickToDecryptListener);
		mucError = (LinearLayout) view.findViewById(R.id.muc_error);
		mucError.setOnClickListener(clickToMuc);
		mucErrorText = (TextView) view.findViewById(R.id.muc_error_msg);

		messagesView = (ListView) view.findViewById(R.id.messages_view);

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
				if (getItem(position).getStatus() <= Message.STATUS_RECIEVED) {
					return RECIEVED;
				} else {
					return SENT;
				}
			}

			private void displayStatus(ViewHolder viewHolder, Message message) {
				String filesize = null;
				String info = null;
				boolean error = false;
				if (message.getType() == Message.TYPE_IMAGE) {
					String[] fileParams = message.getBody().split(",");
					try {
						long size = Long.parseLong(fileParams[0]);
						filesize = size / 1024 + " KB";
					} catch (NumberFormatException e) {
						filesize = "0 KB";
					}
				}
				switch (message.getStatus()) {
				case Message.STATUS_UNSEND:
					info = getString(R.string.sending);
					break;
				case Message.STATUS_OFFERED:
					info = getString(R.string.offering);
					break;
				case Message.STATUS_SEND_FAILED:
					info = getString(R.string.send_failed);
					error = true;
					break;
				case Message.STATUS_SEND_REJECTED:
					info = getString(R.string.send_rejected);
					error = true;
					break;
				default:
					if ((message.getConversation().getMode() == Conversation.MODE_MULTI)
							&& (message.getStatus() <= Message.STATUS_RECIEVED)) {
						info = message.getCounterpart();
					}
					break;
				}
				if (error) {
					viewHolder.time.setTextColor(0xFFe92727);
				} else {
					viewHolder.time.setTextColor(0xFF8e8e8e);
				}
				if (message.getEncryption() == Message.ENCRYPTION_NONE) {
					viewHolder.indicator.setVisibility(View.GONE);
				} else {
					viewHolder.indicator.setVisibility(View.VISIBLE);
				}

				String formatedTime = UIHelper.readableTimeDifference(getContext(), message
						.getTimeSent());
				if (message.getStatus() <= Message.STATUS_RECIEVED) {
					if ((filesize != null) && (info != null)) {
						viewHolder.time.setText(filesize + " \u00B7 " + info);
					} else if ((filesize == null) && (info != null)) {
						viewHolder.time.setText(formatedTime + " \u00B7 "
								+ info);
					} else if ((filesize != null) && (info == null)) {
						viewHolder.time.setText(formatedTime + " \u00B7 "
								+ filesize);
					} else {
						viewHolder.time.setText(formatedTime);
					}
				} else {
					if ((filesize != null) && (info != null)) {
						viewHolder.time.setText(filesize + " \u00B7 " + info);
					} else if ((filesize == null) && (info != null)) {
						viewHolder.time.setText(info + " \u00B7 "
								+ formatedTime);
					} else if ((filesize != null) && (info == null)) {
						viewHolder.time.setText(filesize + " \u00B7 "
								+ formatedTime);
					} else {
						viewHolder.time.setText(formatedTime);
					}
				}
			}

			private void displayInfoMessage(ViewHolder viewHolder, int r) {
				if (viewHolder.download_button != null) {
					viewHolder.download_button.setVisibility(View.GONE);
				}
				viewHolder.image.setVisibility(View.GONE);
				viewHolder.messageBody.setVisibility(View.VISIBLE);
				viewHolder.messageBody.setText(getString(r));
				viewHolder.messageBody.setTextColor(0xff33B5E5);
				viewHolder.messageBody.setTypeface(null, Typeface.ITALIC);
			}

			private void displayDecryptionFailed(ViewHolder viewHolder) {
				viewHolder.download_button.setVisibility(View.GONE);
				viewHolder.image.setVisibility(View.GONE);
				viewHolder.messageBody.setVisibility(View.VISIBLE);
				viewHolder.messageBody
						.setText(getString(R.string.decryption_failed));
				viewHolder.messageBody.setTextColor(0xFFe92727);
				viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
			}

			private void displayTextMessage(ViewHolder viewHolder, String text) {
				if (viewHolder.download_button != null) {
					viewHolder.download_button.setVisibility(View.GONE);
				}
				viewHolder.image.setVisibility(View.GONE);
				viewHolder.messageBody.setVisibility(View.VISIBLE);
				if (text != null) {
					viewHolder.messageBody.setText(text.trim());
				} else {
					viewHolder.messageBody.setText("");
				}
				viewHolder.messageBody.setTextColor(0xff333333);
				viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
			}

			private void displayImageMessage(ViewHolder viewHolder,
					final Message message) {
				if (viewHolder.download_button != null) {
					viewHolder.download_button.setVisibility(View.GONE);
				}
				viewHolder.messageBody.setVisibility(View.GONE);
				viewHolder.image.setVisibility(View.VISIBLE);
				String[] fileParams = message.getBody().split(",");
				if (fileParams.length == 3) {
					double target = metrics.density * 288;
					int w = Integer.parseInt(fileParams[1]);
					int h = Integer.parseInt(fileParams[2]);
					int scalledW;
					int scalledH;
					if (w <= h) {
						scalledW = (int) (w / ((double) h / target));
						scalledH = (int) target;
					} else {
						scalledW = (int) target;
						scalledH = (int) (h / ((double) w / target));
					}
					viewHolder.image
							.setLayoutParams(new LinearLayout.LayoutParams(
									scalledW, scalledH));
				}
				activity.loadBitmap(message, viewHolder.image);
				viewHolder.image.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setDataAndType(
								ImageProvider.getContentUri(message), "image/*");
						startActivity(intent);
					}
				});
			}

			@Override
			public View getView(int position, View view, ViewGroup parent) {
				final Message item = getItem(position);
				int type = getItemViewType(position);
				ViewHolder viewHolder;
				if (view == null) {
					viewHolder = new ViewHolder();
					switch (type) {
					case SENT:
						view = (View) inflater.inflate(R.layout.message_sent,
								null);
						viewHolder.contact_picture = (ImageView) view
								.findViewById(R.id.message_photo);
						viewHolder.contact_picture.setImageBitmap(selfBitmap);
						break;
					case RECIEVED:
						view = (View) inflater.inflate(
								R.layout.message_recieved, null);
						viewHolder.contact_picture = (ImageView) view
								.findViewById(R.id.message_photo);

						viewHolder.download_button = (Button) view
								.findViewById(R.id.download_button);

						if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

							viewHolder.contact_picture
									.setImageBitmap(mBitmapCache.get(
											item.getConversation().getName(
													useSubject), item
													.getConversation()
													.getContact(),
											getActivity()
													.getApplicationContext()));

						}
						break;
					default:
						viewHolder = null;
						break;
					}
					viewHolder.indicator = (ImageView) view
							.findViewById(R.id.security_indicator);
					viewHolder.image = (ImageView) view
							.findViewById(R.id.message_image);
					viewHolder.messageBody = (TextView) view
							.findViewById(R.id.message_body);
					viewHolder.time = (TextView) view
							.findViewById(R.id.message_time);
					view.setTag(viewHolder);
				} else {
					viewHolder = (ViewHolder) view.getTag();
				}

				if (type == RECIEVED) {
					if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
						viewHolder.contact_picture.setImageBitmap(mBitmapCache
								.get(item.getCounterpart(), null, getActivity()
										.getApplicationContext()));
						viewHolder.contact_picture
								.setOnClickListener(new OnClickListener() {

									@Override
									public void onClick(View v) {
										highlightInConference(item.getCounterpart());
									}
								});
					}
				}

				if (item.getType() == Message.TYPE_IMAGE) {
					if (item.getStatus() == Message.STATUS_RECIEVING) {
						displayInfoMessage(viewHolder, R.string.receiving_image);
					} else if (item.getStatus() == Message.STATUS_RECEIVED_OFFER) {
						viewHolder.image.setVisibility(View.GONE);
						viewHolder.messageBody.setVisibility(View.GONE);
						viewHolder.download_button.setVisibility(View.VISIBLE);
						viewHolder.download_button
								.setOnClickListener(new OnClickListener() {

									@Override
									public void onClick(View v) {
										JingleConnection connection = item
												.getJingleConnection();
										if (connection != null) {
											connection.accept();
										} else {
											Log.d("xmppService",
													"attached jingle connection was null");
										}
									}
								});
					} else if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
							|| (item.getEncryption() == Message.ENCRYPTION_NONE)) {
						displayImageMessage(viewHolder, item);
					} else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
						displayInfoMessage(viewHolder,
								R.string.encrypted_message);
					} else {
						displayDecryptionFailed(viewHolder);
					}
				} else {
					if (item.getEncryption() == Message.ENCRYPTION_PGP) {
						displayInfoMessage(viewHolder,
								R.string.encrypted_message);
					} else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
						displayDecryptionFailed(viewHolder);
					} else {
						displayTextMessage(viewHolder, item.getBody());
					}
				}

				displayStatus(viewHolder, item);

				return view;
			}
		};
		messagesView.setAdapter(messageListAdapter);

		return view;
	}

	protected void highlightInConference(String nick) {
		String oldString = chatMsg.getText().toString().trim();
		if (oldString.isEmpty()) {
			chatMsg.setText(nick+": ");
		} else {
			chatMsg.setText(oldString+" "+nick+" ");
		}
		int position = chatMsg.length();
		Editable etext = chatMsg.getText();
		Selection.setSelection(etext, position);
	}
	
	protected Bitmap findSelfPicture() {
		SharedPreferences sharedPref = PreferenceManager
				.getDefaultSharedPreferences(getActivity()
						.getApplicationContext());
		boolean showPhoneSelfContactPicture = sharedPref.getBoolean(
				"show_phone_selfcontact_picture", true);

		return UIHelper.getSelfContactPicture(conversation.getAccount(), 48,
				showPhoneSelfContactPicture, getActivity());
	}

	@Override
	public void onStart() {
		super.onStart();
		this.activity = (ConversationActivity) getActivity();
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(activity);
		this.useSubject = preferences.getBoolean("use_subject_in_muc", true);
		if (activity.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		if (this.conversation != null) {
			this.conversation.setNextMessage(chatMsg.getText().toString());
		}
	}

	public void onBackendConnected() {
		this.conversation = activity.getSelectedConversation();
		if (this.conversation == null) {
			return;
		}
		String oldString = conversation.getNextMessage().trim();
		if (this.pastedText == null) {
			this.chatMsg.setText(oldString);
		} else {
			
			if (oldString.isEmpty()) {
				chatMsg.setText(pastedText);
			} else {
				chatMsg.setText(oldString + " " + pastedText);
			}
			pastedText = null;
		}
		int position = chatMsg.length();
		Editable etext = chatMsg.getText();
		Selection.setSelection(etext, position);
		this.selfBitmap = findSelfPicture();
		updateMessages();
		if (activity.getSlidingPaneLayout().isSlideable()) {
			if (!activity.shouldPaneBeOpen()) {
				activity.getSlidingPaneLayout().closePane();
				activity.getActionBar().setDisplayHomeAsUpEnabled(true);
				activity.getActionBar().setTitle(
						conversation.getName(useSubject));
				activity.invalidateOptionsMenu();

			}
		}
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			activity.xmppConnectionService
					.setOnRenameListener(new OnRenameListener() {

						@Override
						public void onRename(final boolean success) {
							activity.xmppConnectionService
									.updateConversation(conversation);
							getActivity().runOnUiThread(new Runnable() {

								@Override
								public void run() {
									if (success) {
										Toast.makeText(
												getActivity(),
												getString(R.string.your_nick_has_been_changed),
												Toast.LENGTH_SHORT).show();
									} else {
										Toast.makeText(
												getActivity(),
												getString(R.string.nick_in_use),
												Toast.LENGTH_SHORT).show();
									}
								}
							});
						}
					});
		}
	}

	private void decryptMessage(final Message message) {
		PgpEngine engine = activity.xmppConnectionService.getPgpEngine();
		if (engine != null) {
			engine.decrypt(message, new UiCallback() {

				@Override
				public void userInputRequried(PendingIntent pi) {
					askForPassphraseIntent = pi.getIntentSender();
					pgpInfo.setVisibility(View.VISIBLE);
				}

				@Override
				public void success() {
					activity.xmppConnectionService.databaseBackend
							.updateMessage(message);
					updateMessages();
				}

				@Override
				public void error(int error) {
					message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
					// updateMessages();
				}
			});
		} else {
			pgpInfo.setVisibility(View.VISIBLE);
		}
	}

	public void updateMessages() {
		if (getView() == null) {
			return;
		}
		ConversationActivity activity = (ConversationActivity) getActivity();
		if (this.conversation != null) {
			for (Message message : this.conversation.getMessages()) {
				if ((message.getEncryption() == Message.ENCRYPTION_PGP)
						&& ((message.getStatus() == Message.STATUS_RECIEVED) || (message
								.getStatus() == Message.STATUS_SEND))) {
					decryptMessage(message);
					break;
				}
			}
			this.messageList.clear();
			this.messageList.addAll(this.conversation.getMessages());
			this.messageListAdapter.notifyDataSetChanged();
			if (conversation.getMode() == Conversation.MODE_SINGLE) {
				if (messageList.size() >= 1) {
					makeFingerprintWarning(conversation.getLatestEncryption());
				}
			} else {
				if (conversation.getMucOptions().getError() != 0) {
					mucError.setVisibility(View.VISIBLE);
					if (conversation.getMucOptions().getError() == MucOptions.ERROR_NICK_IN_USE) {
						mucErrorText.setText(getString(R.string.nick_in_use));
					}
				} else {
					mucError.setVisibility(View.GONE);
				}
			}
			getActivity().invalidateOptionsMenu();
			updateChatMsgHint();
			int size = this.messageList.size();
			if (size >= 1)
				messagesView.setSelection(size - 1);
			if (!activity.shouldPaneBeOpen()) {
				conversation.markRead();
				// TODO update notifications
				UIHelper.updateNotification(getActivity(),
						activity.getConversationList(), null, false);
				activity.updateConversationList();
			}
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
					AlertDialog dialog = UIHelper.getVerifyFingerprintDialog(
							(ConversationActivity) getActivity(), conversation,
							fingerprintWarning);
					dialog.show();
				}
			});
		} else {
			fingerprintWarning.setVisibility(View.GONE);
		}
	}

	protected void sendPlainTextMessage(Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		activity.xmppConnectionService.sendMessage(message, null);
		chatMsg.setText("");
	}

	protected void sendPgpMessage(final Message message) {
		activity.pendingMessage = message;
		final ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		final Contact contact = message.getConversation().getContact();
		if (activity.hasPgp()) {
			if (contact.getPgpKeyId() != 0) {
				xmppService.getPgpEngine().hasKey(contact, new UiCallback() {

					@Override
					public void userInputRequried(PendingIntent pi) {
						activity.runIntent(pi,
								ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
					}

					@Override
					public void success() {
						activity.encryptTextMessage();
					}

					@Override
					public void error(int error) {

					}
				});

			} else {
				showNoPGPKeyDialog(new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						conversation.setNextEncryption(Message.ENCRYPTION_NONE);
						message.setEncryption(Message.ENCRYPTION_NONE);
						xmppService.sendMessage(message, null);
						chatMsg.setText("");
					}
				});
			}
		}
	}

	public void showNoPGPKeyDialog(DialogInterface.OnClickListener listener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.no_pgp_key));
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage(getText(R.string.contact_has_no_pgp_key));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.send_unencrypted),
				listener);
		builder.create().show();
	}

	protected void sendOtrMessage(final Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		if (conversation.hasValidOtrSession()) {
			activity.xmppConnectionService.sendMessage(message, null);
			chatMsg.setText("");
		} else {
			activity.selectPresence(message.getConversation(),
					new OnPresenceSelected() {

						@Override
						public void onPresenceSelected(boolean success,
								String presence) {
							if (success) {
								xmppService.sendMessage(message, presence);
								chatMsg.setText("");
							}
						}

						@Override
						public void onSendPlainTextInstead() {
							message.setEncryption(Message.ENCRYPTION_NONE);
							xmppService.sendMessage(message, null);
							chatMsg.setText("");
						}
					}, "otr");
		}
	}

	private static class ViewHolder {

		protected Button download_button;
		protected ImageView image;
		protected ImageView indicator;
		protected TextView time;
		protected TextView messageBody;
		protected ImageView contact_picture;

	}

	private class BitmapCache {
		private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();

		public Bitmap get(String name, Contact contact, Context context) {
			if (bitmaps.containsKey(name)) {
				return bitmaps.get(name);
			} else {
				Bitmap bm;
				if (contact != null) {
					bm = UIHelper
							.getContactPicture(contact, 48, context, false);
				} else {
					bm = UIHelper.getContactPicture(name, 48, context, false);
				}
				bitmaps.put(name, bm);
				return bm;
			}
		}
	}

	public void setText(String text) {
		this.pastedText = text;
	}

	public void clearInputField() {
		this.chatMsg.setText("");
	}
}
