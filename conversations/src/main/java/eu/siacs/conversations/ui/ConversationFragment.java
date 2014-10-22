package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.EditMessage.OnEnterPressed;
import eu.siacs.conversations.ui.XmppActivity.OnPresenceSelected;
import eu.siacs.conversations.ui.XmppActivity.OnValueEdited;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.adapter.MessageAdapter.OnContactPictureClicked;
import eu.siacs.conversations.ui.adapter.MessageAdapter.OnContactPictureLongClicked;
import eu.siacs.conversations.utils.UIHelper;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.text.Editable;
import android.text.Selection;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.OnScrollListener;
import android.widget.TextView.OnEditorActionListener;
import android.widget.AbsListView;

import android.widget.ListView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ConversationFragment extends Fragment {

	protected Conversation conversation;
	protected ListView messagesView;
	protected LayoutInflater inflater;
	protected List<Message> messageList = new ArrayList<Message>();
	protected MessageAdapter messageListAdapter;
	protected Contact contact;

	protected String queuedPqpMessage = null;

	private EditMessage mEditMessage;
	private ImageButton mSendButton;
	private String pastedText = null;
	private RelativeLayout snackbar;
	private TextView snackbarMessage;
	private TextView snackbarAction;

	private boolean messagesLoaded = false;

	private IntentSender askForPassphraseIntent = null;

	private ConcurrentLinkedQueue<Message> mEncryptedMessages = new ConcurrentLinkedQueue<Message>();
	private boolean mDecryptJobRunning = false;

	private OnEditorActionListener mEditorActionListener = new OnEditorActionListener() {

		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			if (actionId == EditorInfo.IME_ACTION_SEND) {
				InputMethodManager imm = (InputMethodManager) v.getContext()
						.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
				sendMessage();
				return true;
			} else {
				return false;
			}
		}
	};

	private OnClickListener mSendButtonListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			sendMessage();
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
			Intent intent = new Intent(getActivity(),
					ConferenceDetailsActivity.class);
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

	private OnClickListener joinMuc = new OnClickListener() {

		@Override
		public void onClick(View v) {
			activity.xmppConnectionService.joinMuc(conversation);
		}
	};

	private OnClickListener enterPassword = new OnClickListener() {

		@Override
		public void onClick(View v) {
			MucOptions muc = conversation.getMucOptions();
			String password = muc.getPassword();
			if (password == null) {
				password = "";
			}
			activity.quickPasswordEdit(password, new OnValueEdited() {

				@Override
				public void onValueEdited(String value) {
					activity.xmppConnectionService.providePasswordForMuc(
							conversation, value);
				}
			});
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
				int size = activity.xmppConnectionService.loadMoreMessages(
						conversation, timestamp);
				messageList.clear();
				messageList.addAll(conversation.getMessages());
				updateStatusMessages();
				messageListAdapter.notifyDataSetChanged();
				if (size != 0) {
					messagesLoaded = true;
				}
				messagesView.setSelectionFromTop(size + 1, 0);
			}
		}
	};

	private ConversationActivity activity;

	private void sendMessage() {
		if (this.conversation == null) {
			return;
		}
		if (mEditMessage.getText().length() < 1) {
			if (this.conversation.getMode() == Conversation.MODE_MULTI) {
				conversation.setNextPresence(null);
				updateChatMsgHint();
			}
			return;
		}
		Message message = new Message(conversation, mEditMessage.getText()
				.toString(), conversation.getNextEncryption(activity
				.forceEncryption()));
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			if (conversation.getNextPresence() != null) {
				message.setPresence(conversation.getNextPresence());
				message.setType(Message.TYPE_PRIVATE);
				conversation.setNextPresence(null);
			}
		}
		if (conversation.getNextEncryption(activity.forceEncryption()) == Message.ENCRYPTION_OTR) {
			sendOtrMessage(message);
		} else if (conversation.getNextEncryption(activity.forceEncryption()) == Message.ENCRYPTION_PGP) {
			sendPgpMessage(message);
		} else {
			sendPlainTextMessage(message);
		}
	}

	public void updateChatMsgHint() {
		if (conversation.getMode() == Conversation.MODE_MULTI
				&& conversation.getNextPresence() != null) {
			this.mEditMessage.setHint(getString(
					R.string.send_private_message_to,
					conversation.getNextPresence()));
		} else {
			switch (conversation.getNextEncryption(activity.forceEncryption())) {
			case Message.ENCRYPTION_NONE:
				mEditMessage
						.setHint(getString(R.string.send_plain_text_message));
				break;
			case Message.ENCRYPTION_OTR:
				mEditMessage.setHint(getString(R.string.send_otr_message));
				break;
			case Message.ENCRYPTION_PGP:
				mEditMessage.setHint(getString(R.string.send_pgp_message));
				break;
			default:
				break;
			}
		}
	}

	@Override
	public View onCreateView(final LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.fragment_conversation,
				container, false);
		mEditMessage = (EditMessage) view.findViewById(R.id.textinput);
		mEditMessage.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				activity.hideConversationsOverview();
			}
		});
		mEditMessage.setOnEditorActionListener(mEditorActionListener);
		mEditMessage.setOnEnterPressedListener(new OnEnterPressed() {

			@Override
			public void onEnterPressed() {
				sendMessage();
			}
		});

		mSendButton = (ImageButton) view.findViewById(R.id.textSendButton);
		mSendButton.setOnClickListener(this.mSendButtonListener);

		snackbar = (RelativeLayout) view.findViewById(R.id.snackbar);
		snackbarMessage = (TextView) view.findViewById(R.id.snackbar_message);
		snackbarAction = (TextView) view.findViewById(R.id.snackbar_action);

		messagesView = (ListView) view.findViewById(R.id.messages_view);
		messagesView.setOnScrollListener(mOnScrollListener);
		messagesView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
		messageListAdapter = new MessageAdapter(
				(ConversationActivity) getActivity(), this.messageList);
		messageListAdapter
				.setOnContactPictureClicked(new OnContactPictureClicked() {

					@Override
					public void onContactPictureClicked(Message message) {
						if (message.getStatus() <= Message.STATUS_RECEIVED) {
							if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
								if (message.getPresence() != null) {
									highlightInConference(message.getPresence());
								} else {
									highlightInConference(message
											.getCounterpart());
								}
							} else {
								Contact contact = message.getConversation()
										.getContact();
								if (contact.showInRoster()) {
									activity.switchToContactDetails(contact);
								} else {
									activity.showAddToRosterDialog(message
											.getConversation());
								}
							}
						}
					}
				});
		messageListAdapter
				.setOnContactPictureLongClicked(new OnContactPictureLongClicked() {

					@Override
					public void onContactPictureLongClicked(Message message) {
						if (message.getStatus() <= Message.STATUS_RECEIVED) {
							if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
								if (message.getPresence() != null) {
									privateMessageWith(message.getPresence());
								} else {
									privateMessageWith(message.getCounterpart());
								}
							}
						}
					}
				});
		messagesView.setAdapter(messageListAdapter);

		return view;
	}

	protected void privateMessageWith(String counterpart) {
		this.mEditMessage.setText("");
		this.conversation.setNextPresence(counterpart);
		updateChatMsgHint();
	}

	protected void highlightInConference(String nick) {
		String oldString = mEditMessage.getText().toString().trim();
		if (oldString.isEmpty() || mEditMessage.getSelectionStart() == 0) {
			mEditMessage.getText().insert(0, nick + ": ");
		} else {
			if (mEditMessage.getText().charAt(
					mEditMessage.getSelectionStart() - 1) != ' ') {
				nick = " " + nick;
			}
			mEditMessage.getText().insert(mEditMessage.getSelectionStart(),
					nick + " ");
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		this.activity = (ConversationActivity) getActivity();
		if (activity.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
	}

	@Override
	public void onStop() {
		mDecryptJobRunning = false;
		super.onStop();
		if (this.conversation != null) {
			this.conversation.setNextMessage(mEditMessage.getText().toString());
		}
	}

	public void onBackendConnected() {
		this.activity = (ConversationActivity) getActivity();
		this.conversation = activity.getSelectedConversation();
		if (this.conversation == null) {
			return;
		}
		String oldString = conversation.getNextMessage().trim();
		if (this.pastedText == null) {
			this.mEditMessage.setText(oldString);
		} else {

			if (oldString.isEmpty()) {
				mEditMessage.setText(pastedText);
			} else {
				mEditMessage.setText(oldString + " " + pastedText);
			}
			pastedText = null;
		}
		int position = mEditMessage.length();
		Editable etext = mEditMessage.getText();
		Selection.setSelection(etext, position);
		if (activity.isConversationsOverviewHideable()) {
			if (!activity.shouldPaneBeOpen()) {
				activity.hideConversationsOverview();
				activity.openConversation(conversation);
			}
		}
		if (this.conversation.getMode() == Conversation.MODE_MULTI) {
			conversation.setNextPresence(null);
		}
		updateMessages();
	}

	public void updateMessages() {
		if (getView() == null) {
			return;
		}
		hideSnackbar();
		final ConversationActivity activity = (ConversationActivity) getActivity();
		if (this.conversation != null) {
			final Contact contact = this.conversation.getContact();
			if (this.conversation.isMuted()) {
				showSnackbar(R.string.notifications_disabled, R.string.enable,
						new OnClickListener() {

							@Override
							public void onClick(View v) {
								conversation.setMutedTill(0);
								activity.xmppConnectionService.databaseBackend
										.updateConversation(conversation);
								updateMessages();
							}
						});
			} else if (!contact.showInRoster()
					&& contact
							.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
				showSnackbar(R.string.contact_added_you, R.string.add_back,
						new OnClickListener() {

							@Override
							public void onClick(View v) {
								activity.xmppConnectionService
										.createContact(contact);
								activity.switchToContactDetails(contact);
							}
						});
			}
			for (Message message : this.conversation.getMessages()) {
				if ((message.getEncryption() == Message.ENCRYPTION_PGP)
						&& ((message.getStatus() == Message.STATUS_RECEIVED) || (message
								.getStatus() == Message.STATUS_SEND))) {
					if (!mEncryptedMessages.contains(message)) {
						mEncryptedMessages.add(message);
					}
				}
			}
			decryptNext();
			this.messageList.clear();
			if (this.conversation.getMessages().size() == 0) {
				messagesLoaded = false;
			} else {
				this.messageList.addAll(this.conversation.getMessages());
				messagesLoaded = true;
				updateStatusMessages();
			}
			this.messageListAdapter.notifyDataSetChanged();
			if (conversation.getMode() == Conversation.MODE_SINGLE) {
				if (messageList.size() >= 1) {
					makeFingerprintWarning(conversation.getLatestEncryption());
				}
			} else {
				if (!conversation.getMucOptions().online()
						&& conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
					int error = conversation.getMucOptions().getError();
					switch (error) {
					case MucOptions.ERROR_NICK_IN_USE:
						showSnackbar(R.string.nick_in_use, R.string.edit,
								clickToMuc);
						break;
					case MucOptions.ERROR_ROOM_NOT_FOUND:
						showSnackbar(R.string.conference_not_found,
								R.string.leave, leaveMuc);
						break;
					case MucOptions.ERROR_PASSWORD_REQUIRED:
						showSnackbar(R.string.conference_requires_password,
								R.string.enter_password, enterPassword);
						break;
					case MucOptions.ERROR_BANNED:
						showSnackbar(R.string.conference_banned,
								R.string.leave, leaveMuc);
						break;
					case MucOptions.ERROR_MEMBERS_ONLY:
						showSnackbar(R.string.conference_members_only,
								R.string.leave, leaveMuc);
						break;
					case MucOptions.KICKED_FROM_ROOM:
						showSnackbar(R.string.conference_kicked, R.string.join,
								joinMuc);
						break;
					default:
						break;
					}
				}
			}
			getActivity().invalidateOptionsMenu();
			updateChatMsgHint();
			if (!activity.shouldPaneBeOpen()) {
				activity.xmppConnectionService.markRead(conversation, true);
				activity.updateConversationList();
			}
			this.updateSendButton();
		}
	}

	private void decryptNext() {
		Message next = this.mEncryptedMessages.peek();
		PgpEngine engine = activity.xmppConnectionService.getPgpEngine();

		if (next != null && engine != null && !mDecryptJobRunning) {
			mDecryptJobRunning = true;
			engine.decrypt(next, new UiCallback<Message>() {

				@Override
				public void userInputRequried(PendingIntent pi, Message message) {
					mDecryptJobRunning = false;
					askForPassphraseIntent = pi.getIntentSender();
					showSnackbar(R.string.openpgp_messages_found,
							R.string.decrypt, clickToDecryptListener);
				}

				@Override
				public void success(Message message) {
					mDecryptJobRunning = false;
					mEncryptedMessages.remove();
					activity.xmppConnectionService.updateMessage(message);
				}

				@Override
				public void error(int error, Message message) {
					message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
					mDecryptJobRunning = false;
					mEncryptedMessages.remove();
					activity.xmppConnectionService.updateConversationUi();
				}
			});
		}
	}

	private void messageSent() {
		int size = this.messageList.size();
		messagesView.setSelection(size - 1);
		mEditMessage.setText("");
		updateChatMsgHint();
	}

	public void updateSendButton() {
		Conversation c = this.conversation;
		if (activity.useSendButtonToIndicateStatus() && c != null
				&& c.getAccount().getStatus() == Account.STATUS_ONLINE) {
			if (c.getMode() == Conversation.MODE_SINGLE) {
				switch (c.getContact().getMostAvailableStatus()) {
				case Presences.CHAT:
					this.mSendButton
							.setImageResource(R.drawable.ic_action_send_now_online);
					break;
				case Presences.ONLINE:
					this.mSendButton
							.setImageResource(R.drawable.ic_action_send_now_online);
					break;
				case Presences.AWAY:
					this.mSendButton
							.setImageResource(R.drawable.ic_action_send_now_away);
					break;
				case Presences.XA:
					this.mSendButton
							.setImageResource(R.drawable.ic_action_send_now_away);
					break;
				case Presences.DND:
					this.mSendButton
							.setImageResource(R.drawable.ic_action_send_now_dnd);
					break;
				default:
					this.mSendButton
							.setImageResource(R.drawable.ic_action_send_now_offline);
					break;
				}
			} else if (c.getMode() == Conversation.MODE_MULTI) {
				if (c.getMucOptions().online()) {
					this.mSendButton
							.setImageResource(R.drawable.ic_action_send_now_online);
				} else {
					this.mSendButton
							.setImageResource(R.drawable.ic_action_send_now_offline);
				}
			} else {
				this.mSendButton
						.setImageResource(R.drawable.ic_action_send_now_offline);
			}
		} else {
			this.mSendButton
					.setImageResource(R.drawable.ic_action_send_now_offline);
		}
	}

	protected void updateStatusMessages() {
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			for (int i = this.messageList.size() - 1; i >= 0; --i) {
				if (this.messageList.get(i).getStatus() == Message.STATUS_RECEIVED) {
					return;
				} else {
					if (this.messageList.get(i).getStatus() == Message.STATUS_SEND_DISPLAYED) {
						this.messageList.add(i + 1,
								Message.createStatusMessage(conversation));
						return;
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
						&& (!conversation.isMuted())
						&& (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) && (!knownFingerprints
							.contains(conversation.getOtrFingerprint())))) {
			showSnackbar(R.string.unknown_otr_fingerprint, R.string.verify,
					new OnClickListener() {

						@Override
						public void onClick(View v) {
							if (conversation.getOtrFingerprint() != null) {
								AlertDialog dialog = UIHelper
										.getVerifyFingerprintDialog(
												(ConversationActivity) getActivity(),
												conversation, snackbar);
								dialog.show();
							}
						}
					});
		}
	}

	protected void showSnackbar(int message, int action,
			OnClickListener clickListener) {
		snackbar.setVisibility(View.VISIBLE);
		snackbar.setOnClickListener(null);
		snackbarMessage.setText(message);
		snackbarMessage.setOnClickListener(null);
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
									xmppService.databaseBackend
											.updateConversation(conversation);
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
									xmppService.databaseBackend
											.updateConversation(conversation);
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

	public void setText(String text) {
		this.pastedText = text;
	}

	public void clearInputField() {
		this.mEditMessage.setText("");
	}
}
