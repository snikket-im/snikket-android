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
import eu.siacs.conversations.services.ImageProvider;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.XmppActivity.OnPresenceSelected;
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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
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
	
	protected int mPrimaryTextColor;
	protected int mSecondaryTextColor;

	protected String queuedPqpMessage = null;

	private EditText chatMsg;
	private String pastedText = null;
	private RelativeLayout snackbar;
	private TextView snackbarMessage;
	private TextView snackbarAction;

	protected Bitmap selfBitmap;

	private boolean useSubject = true;
	private boolean messagesLoaded = false;

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
			if (activity.hasPgp() && askForPassphraseIntent != null) {
				try {
					getActivity().startIntentSenderForResult(
							askForPassphraseIntent,
							ConversationActivity.REQUEST_DECRYPT_PGP, null, 0,
							0, 0);
				} catch (SendIntentException e) {
					//
				}
			}
		}
	};
	
	private OnClickListener clickToMuc = new OnClickListener() {

		@Override
		public void onClick(View v) {
			Intent intent = new Intent(getActivity(), ConferenceDetailsActivity.class);
			intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
			intent.putExtra("uuid", conversation.getUuid());
			startActivity(intent);
		}
	};
	
	private OnClickListener leaveMuc = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			activity.endConversation(conversation);
		}
	};

	private OnScrollListener mOnScrollListener = new OnScrollListener() {

		@Override
		public void onScrollStateChanged(AbsListView view, int scrollState) {
			// TODO Auto-generated method stub

		}

		@Override
		public void onScroll(AbsListView view, int firstVisibleItem,
				int visibleItemCount, int totalItemCount) {
			if (firstVisibleItem == 0 && messagesLoaded) {
				long timestamp = messageList.get(0).getTimeSent();
				messagesLoaded = false;
				List<Message> messages = activity.xmppConnectionService
						.getMoreMessages(conversation, timestamp);
				messageList.addAll(0, messages);
				messageListAdapter.notifyDataSetChanged();
				if (messages.size() != 0) {
					messagesLoaded = true;
				}
				messagesView.setSelectionFromTop(messages.size() + 1, 0);
			}
		}
	};

	private ConversationActivity activity;

	public void updateChatMsgHint() {
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

	@Override
	public View onCreateView(final LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {

		final DisplayMetrics metrics = getResources().getDisplayMetrics();

		this.inflater = inflater;

		mPrimaryTextColor = getResources().getColor(R.color.primarytext);
		mSecondaryTextColor = getResources().getColor(R.color.secondarytext);
		
		final View view = inflater.inflate(R.layout.fragment_conversation,
				container, false);
		chatMsg = (EditText) view.findViewById(R.id.textinput);
		chatMsg.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (activity.getSlidingPaneLayout().isSlideable()) {
					activity.getSlidingPaneLayout().closePane();
				}
			}
		});

		ImageButton sendButton = (ImageButton) view
				.findViewById(R.id.textSendButton);
		sendButton.setOnClickListener(this.sendMsgListener);

		snackbar = (RelativeLayout) view.findViewById(R.id.snackbar);
		snackbarMessage = (TextView) view.findViewById(R.id.snackbar_message);
		snackbarAction = (TextView) view.findViewById(R.id.snackbar_action);

		messagesView = (ListView) view.findViewById(R.id.messages_view);
		messagesView.setOnScrollListener(mOnScrollListener);
		messagesView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);

		messageListAdapter = new ArrayAdapter<Message>(this.getActivity()
				.getApplicationContext(), R.layout.message_sent,
				this.messageList) {

			private static final int SENT = 0;
			private static final int RECIEVED = 1;
			private static final int STATUS = 2;

			@Override
			public int getViewTypeCount() {
				return 3;
			}

			@Override
			public int getItemViewType(int position) {
				if (getItem(position).getType() == Message.TYPE_STATUS) {
					return STATUS;
				} else if (getItem(position).getStatus() <= Message.STATUS_RECIEVED) {
					return RECIEVED;
				} else {
					return SENT;
				}
			}

			private void displayStatus(ViewHolder viewHolder, Message message) {
				String filesize = null;
				String info = null;
				boolean error = false;
				boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
						&& message.getStatus() <= Message.STATUS_RECIEVED;
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
				case Message.STATUS_WAITING:
					info = getString(R.string.waiting);
					break;
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
				case Message.STATUS_RECEPTION_FAILED:
					info = getString(R.string.reception_failed);
					error = true;
				default:
					if (multiReceived) {
						info = message.getCounterpart();
					}
					break;
				}
				if (error) {
					viewHolder.time.setTextColor(0xFFe92727);
				} else {
					viewHolder.time.setTextColor(mSecondaryTextColor);
				}
				if (message.getEncryption() == Message.ENCRYPTION_NONE) {
					viewHolder.indicator.setVisibility(View.GONE);
				} else {
					viewHolder.indicator.setVisibility(View.VISIBLE);
				}

				String formatedTime = UIHelper.readableTimeDifference(
						getContext(), message.getTimeSent());
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
						if (error) {
							viewHolder.time.setText(info + " \u00B7 "
									+ formatedTime);
						} else {
							viewHolder.time.setText(info);
						}
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
				viewHolder.messageBody.setTextIsSelectable(false);
			}

			private void displayDecryptionFailed(ViewHolder viewHolder) {
				if (viewHolder.download_button != null) {
					viewHolder.download_button.setVisibility(View.GONE);
				}
				viewHolder.image.setVisibility(View.GONE);
				viewHolder.messageBody.setVisibility(View.VISIBLE);
				viewHolder.messageBody
						.setText(getString(R.string.decryption_failed));
				viewHolder.messageBody.setTextColor(0xFFe92727);
				viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
				viewHolder.messageBody.setTextIsSelectable(false);
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
				viewHolder.messageBody.setTextColor(mPrimaryTextColor);
				viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
				viewHolder.messageBody.setTextIsSelectable(true);
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
				viewHolder.image
						.setOnLongClickListener(new OnLongClickListener() {

							@Override
							public boolean onLongClick(View v) {
								Intent shareIntent = new Intent();
								shareIntent.setAction(Intent.ACTION_SEND);
								shareIntent.putExtra(Intent.EXTRA_STREAM,
										ImageProvider.getContentUri(message));
								shareIntent.setType("image/webp");
								startActivity(Intent.createChooser(shareIntent,
										getText(R.string.share_with)));
								return true;
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
						viewHolder.message_box = (LinearLayout) view
								.findViewById(R.id.message_box);
						viewHolder.contact_picture = (ImageView) view
								.findViewById(R.id.message_photo);
						viewHolder.contact_picture.setImageBitmap(selfBitmap);
						viewHolder.indicator = (ImageView) view
								.findViewById(R.id.security_indicator);
						viewHolder.image = (ImageView) view
								.findViewById(R.id.message_image);
						viewHolder.messageBody = (TextView) view
								.findViewById(R.id.message_body);
						viewHolder.time = (TextView) view
								.findViewById(R.id.message_time);
						view.setTag(viewHolder);
						break;
					case RECIEVED:
						view = (View) inflater.inflate(
								R.layout.message_recieved, null);
						viewHolder.message_box = (LinearLayout) view
								.findViewById(R.id.message_box);
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
						viewHolder.indicator = (ImageView) view
								.findViewById(R.id.security_indicator);
						viewHolder.image = (ImageView) view
								.findViewById(R.id.message_image);
						viewHolder.messageBody = (TextView) view
								.findViewById(R.id.message_body);
						viewHolder.time = (TextView) view
								.findViewById(R.id.message_time);
						view.setTag(viewHolder);
						break;
					case STATUS:
						view = (View) inflater.inflate(R.layout.message_status,
								null);
						viewHolder.contact_picture = (ImageView) view
								.findViewById(R.id.message_photo);
						if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

							viewHolder.contact_picture
									.setImageBitmap(mBitmapCache.get(
											item.getConversation().getName(
													useSubject), item
													.getConversation()
													.getContact(),
											getActivity()
													.getApplicationContext()));
							viewHolder.contact_picture.setAlpha(128);

						}
						break;
					default:
						viewHolder = null;
						break;
					}
				} else {
					viewHolder = (ViewHolder) view.getTag();
				}

				if (type == STATUS) {
					return view;
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
										highlightInConference(item
												.getCounterpart());
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
										}
									}
								});
					} else if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
							|| (item.getEncryption() == Message.ENCRYPTION_NONE)
							|| (item.getEncryption() == Message.ENCRYPTION_OTR)) {
						displayImageMessage(viewHolder, item);
					} else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
						displayInfoMessage(viewHolder,
								R.string.encrypted_message);
					} else {
						displayDecryptionFailed(viewHolder);
					}
				} else {
					if (item.getEncryption() == Message.ENCRYPTION_PGP) {
						if (activity.hasPgp()) {
							displayInfoMessage(viewHolder,
									R.string.encrypted_message);
						} else {
							displayInfoMessage(viewHolder,
									R.string.install_openkeychain);
							viewHolder.message_box
									.setOnClickListener(new OnClickListener() {

										@Override
										public void onClick(View v) {
											activity.showInstallPgpDialog();
										}
									});
						}
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
			chatMsg.setText(nick + ": ");
		} else {
			chatMsg.setText(oldString + " " + nick + " ");
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
				activity.getActionBar().setHomeButtonEnabled(true);
				activity.getActionBar().setTitle(
						conversation.getName(useSubject));
				activity.invalidateOptionsMenu();
			}
		}
	}

	private void decryptMessage(Message message) {
		PgpEngine engine = activity.xmppConnectionService.getPgpEngine();
		if (engine != null) {
			engine.decrypt(message, new UiCallback<Message>() {

				@Override
				public void userInputRequried(PendingIntent pi, Message message) {
					askForPassphraseIntent = pi.getIntentSender();
					showSnackbar(R.string.openpgp_messages_found,R.string.decrypt,clickToDecryptListener);
				}

				@Override
				public void success(Message message) {
					activity.xmppConnectionService.databaseBackend
							.updateMessage(message);
					updateMessages();
				}

				@Override
				public void error(int error, Message message) {
					message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
					// updateMessages();
				}
			});
		}
	}

	public void updateMessages() {
		if (getView() == null) {
			return;
		}
		hideSnackbar();
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
			if (this.conversation.getMessages().size() == 0) {
				this.messageList.clear();
				messagesLoaded = false;
			} else {
				for (Message message : this.conversation.getMessages()) {
					if (!this.messageList.contains(message)) {
						this.messageList.add(message);
					}
				}
				messagesLoaded = true;
				updateStatusMessages();
			}
			this.messageListAdapter.notifyDataSetChanged();
			if (conversation.getMode() == Conversation.MODE_SINGLE) {
				if (messageList.size() >= 1) {
					makeFingerprintWarning(conversation.getLatestEncryption());
				}
			} else {
				if (!conversation.getMucOptions().online()) {
					if (conversation.getMucOptions().getError() == MucOptions.ERROR_NICK_IN_USE) {
						showSnackbar(R.string.nick_in_use, R.string.edit,clickToMuc);
					} else if (conversation.getMucOptions().getError() == MucOptions.ERROR_ROOM_NOT_FOUND) {
						showSnackbar(R.string.conference_not_found,R.string.leave,leaveMuc);
					}
				}
			}
			getActivity().invalidateOptionsMenu();
			updateChatMsgHint();
			if (!activity.shouldPaneBeOpen()) {
				activity.xmppConnectionService.markRead(conversation);
				// TODO update notifications
				UIHelper.updateNotification(getActivity(),
						activity.getConversationList(), null, false);
				activity.updateConversationList();
			}
		}
	}

	private void messageSent() {
		int size = this.messageList.size();
		if (size >= 1) {
			messagesView.setSelection(size - 1);
		}
		chatMsg.setText("");
	}

	protected void updateStatusMessages() {
		boolean addedStatusMsg = false;
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			for (int i = this.messageList.size() - 1; i >= 0; --i) {
				if (addedStatusMsg) {
					if (this.messageList.get(i).getType() == Message.TYPE_STATUS) {
						this.messageList.remove(i);
						--i;
					}
				} else {
					if (this.messageList.get(i).getStatus() == Message.STATUS_RECIEVED) {
						addedStatusMsg = true;
					} else {
						if (this.messageList.get(i).getStatus() == Message.STATUS_SEND_DISPLAYED) {
							this.messageList.add(i + 1,
									Message.createStatusMessage(conversation));
							addedStatusMsg = true;
						}
					}
				}
			}
		}
	}

	protected void makeFingerprintWarning(int latestEncryption) {
		Set<String> knownFingerprints = conversation.getContact()
				.getOtrFingerprints();
		if ((latestEncryption == Message.ENCRYPTION_OTR)
				&& (conversation.hasValidOtrSession()
						&& (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) && (!knownFingerprints
							.contains(conversation.getOtrFingerprint())))) {
			showSnackbar(R.string.unknown_otr_fingerprint, R.string.verify, new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (conversation.getOtrFingerprint() != null) {
						AlertDialog dialog = UIHelper.getVerifyFingerprintDialog(
								(ConversationActivity) getActivity(), conversation,
								snackbar);
						dialog.show();
					}
				}
			});
		}
	}
	
	protected void showSnackbar(int message, int action, OnClickListener clickListener) {
		snackbar.setVisibility(View.VISIBLE);
		snackbarMessage.setText(message);
		snackbarAction.setText(action);
		snackbarAction.setOnClickListener(clickListener);
	}
	
	protected void hideSnackbar() {
		snackbar.setVisibility(View.GONE);
	}

	protected void sendPlainTextMessage(Message message) {
		ConversationActivity activity = (ConversationActivity) getActivity();
		activity.xmppConnectionService.sendMessage(message);
		messageSent();
	}

	protected void sendPgpMessage(final Message message) {
		final ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		final Contact contact = message.getConversation().getContact();
		if (activity.hasPgp()) {
			if (conversation.getMode() == Conversation.MODE_SINGLE) {
				if (contact.getPgpKeyId() != 0) {
					xmppService.getPgpEngine().hasKey(contact,
							new UiCallback<Contact>() {

								@Override
								public void userInputRequried(PendingIntent pi,
										Contact contact) {
									activity.runIntent(
											pi,
											ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
								}

								@Override
								public void success(Contact contact) {
									messageSent();
									activity.encryptTextMessage(message);
								}

								@Override
								public void error(int error, Contact contact) {

								}
							});

				} else {
					showNoPGPKeyDialog(false,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									conversation
											.setNextEncryption(Message.ENCRYPTION_NONE);
									message.setEncryption(Message.ENCRYPTION_NONE);
									xmppService.sendMessage(message);
									messageSent();
								}
							});
				}
			} else {
				if (conversation.getMucOptions().pgpKeysInUse()) {
					if (!conversation.getMucOptions().everybodyHasKeys()) {
						Toast warning = Toast
								.makeText(getActivity(),
										R.string.missing_public_keys,
										Toast.LENGTH_LONG);
						warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
						warning.show();
					}
					activity.encryptTextMessage(message);
					messageSent();
				} else {
					showNoPGPKeyDialog(true,
							new DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									conversation
											.setNextEncryption(Message.ENCRYPTION_NONE);
									message.setEncryption(Message.ENCRYPTION_NONE);
									xmppService.sendMessage(message);
									messageSent();
								}
							});
				}
			}
		} else {
			activity.showInstallPgpDialog();
		}
	}

	public void showNoPGPKeyDialog(boolean plural,
			DialogInterface.OnClickListener listener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		if (plural) {
			builder.setTitle(getString(R.string.no_pgp_keys));
			builder.setMessage(getText(R.string.contacts_have_no_pgp_keys));
		} else {
			builder.setTitle(getString(R.string.no_pgp_key));
			builder.setMessage(getText(R.string.contact_has_no_pgp_key));
		}
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.send_unencrypted),
				listener);
		builder.create().show();
	}

	protected void sendOtrMessage(final Message message) {
		final ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		if (conversation.hasValidOtrSession()) {
			activity.xmppConnectionService.sendMessage(message);
			messageSent();
		} else {
			activity.selectPresence(message.getConversation(),
					new OnPresenceSelected() {

						@Override
						public void onPresenceSelected() {
							message.setPresence(conversation.getNextPresence());
							xmppService.sendMessage(message);
							messageSent();
						}
					});
		}
	}

	private static class ViewHolder {

		protected LinearLayout message_box;
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
