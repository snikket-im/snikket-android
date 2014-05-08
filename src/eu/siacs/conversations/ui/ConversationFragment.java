package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.openintents.openpgp.OpenPgpError;

import net.java.otr4j.session.SessionStatus;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OnPgpEngineResult;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
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
import android.net.Uri;
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
						if (item.getCounterpart() != null) {
							viewHolder.contact_picture
									.setImageBitmap(mBitmapCache.get(item
											.getCounterpart(), null,
											getActivity()
													.getApplicationContext()));
						} else {
							viewHolder.contact_picture
									.setImageBitmap(mBitmapCache.get(
											item.getConversation().getName(
													useSubject), null,
											getActivity()
													.getApplicationContext()));
						}
					}
				}

				if (item.getEncryption() == Message.ENCRYPTION_NONE) {
					viewHolder.indicator.setVisibility(View.GONE);
				} else {
					viewHolder.indicator.setVisibility(View.VISIBLE);
				}

				String filesize = "";

				if (item.getType() == Message.TYPE_IMAGE) {
					String[] fileParams = item.getBody().split(",");
					if ((fileParams.length >= 1)
							&& (item.getStatus() != Message.STATUS_PREPARING)) {
						long size = Long.parseLong(fileParams[0]);
						filesize = size / 1024 + " KB \u00B7 ";
					}
					if ((item.getStatus() == Message.STATUS_PREPARING)
							|| (item.getStatus() == Message.STATUS_RECIEVING)) {
						viewHolder.image.setVisibility(View.GONE);
						viewHolder.messageBody.setVisibility(View.VISIBLE);
						if (item.getStatus() == Message.STATUS_PREPARING) {
							viewHolder.messageBody
									.setText(getString(R.string.preparing_image));
						} else if (item.getStatus() == Message.STATUS_RECIEVING) {
							viewHolder.download_button.setVisibility(View.GONE);
							viewHolder.messageBody
									.setText(getString(R.string.receiving_image));
						}
						viewHolder.messageBody.setTextColor(0xff33B5E5);
						viewHolder.messageBody.setTypeface(null,
								Typeface.ITALIC);
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
						viewHolder.messageBody.setVisibility(View.GONE);
						viewHolder.image.setVisibility(View.VISIBLE);
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
						} else {
							Log.d("xmppService",
									"message body has less than 3 params");
						}
						activity.loadBitmap(item, viewHolder.image);
						viewHolder.image
								.setOnClickListener(new OnClickListener() {

									@Override
									public void onClick(View v) {
										Uri uri = Uri.parse("content://eu.siacs.conversations.images/"
												+ item.getConversationUuid()
												+ "/" + item.getUuid());
										Log.d("xmppService",
												"staring intent with uri:"
														+ uri.toString());
										Intent intent = new Intent(
												Intent.ACTION_VIEW);
										intent.setDataAndType(uri, "image/*");
										startActivity(intent);
									}
								});
					} else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
						viewHolder.image.setVisibility(View.GONE);
						viewHolder.messageBody.setVisibility(View.VISIBLE);
						viewHolder.messageBody
								.setText(getString(R.string.encrypted_message));
						viewHolder.messageBody.setTextColor(0xff33B5E5);
						viewHolder.messageBody.setTypeface(null,
								Typeface.ITALIC);
					} else {
						viewHolder.image.setVisibility(View.GONE);
						viewHolder.messageBody.setVisibility(View.VISIBLE);
						viewHolder.messageBody
								.setText(getString(R.string.decryption_failed));
						viewHolder.messageBody.setTextColor(0xFFe92727);
						viewHolder.messageBody.setTypeface(null,
								Typeface.NORMAL);
					}
				} else {
					viewHolder.image.setVisibility(View.GONE);
					viewHolder.messageBody.setVisibility(View.VISIBLE);
					String body = item.getBody();
					if (body != null) {
						if (item.getEncryption() == Message.ENCRYPTION_PGP) {
							viewHolder.messageBody
									.setText(getString(R.string.encrypted_message));
							viewHolder.messageBody.setTextColor(0xff33B5E5);
							viewHolder.messageBody.setTypeface(null,
									Typeface.ITALIC);
						} else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
							viewHolder.messageBody
									.setText(getString(R.string.decryption_failed));
							viewHolder.messageBody.setTextColor(0xFFe92727);
							viewHolder.messageBody.setTypeface(null,
									Typeface.NORMAL);
						} else {
							viewHolder.messageBody.setText(body.trim());
							viewHolder.messageBody.setTextColor(0xff333333);
							viewHolder.messageBody.setTypeface(null,
									Typeface.NORMAL);
						}
					}
				}
				switch (item.getStatus()) {
				case Message.STATUS_UNSEND:
					viewHolder.time.setTypeface(null, Typeface.ITALIC);
					viewHolder.time.setTextColor(0xFF8e8e8e);
					viewHolder.time.setText(filesize + "sending\u2026");
					break;
				case Message.STATUS_OFFERED:
					viewHolder.time.setTypeface(null, Typeface.ITALIC);
					viewHolder.time.setTextColor(0xFF8e8e8e);
					viewHolder.time.setText(filesize + "offering\u2026");
					break;
				case Message.STATUS_SEND_FAILED:
					viewHolder.time.setText(filesize
							+ getString(R.string.send_failed)
							+ " \u00B7 "
							+ UIHelper.readableTimeDifference(item
									.getTimeSent()));
					viewHolder.time.setTextColor(0xFFe92727);
					viewHolder.time.setTypeface(null, Typeface.NORMAL);
					break;
				case Message.STATUS_SEND_REJECTED:
					viewHolder.time.setText(filesize
							+ getString(R.string.send_rejected));
					viewHolder.time.setTextColor(0xFFe92727);
					viewHolder.time.setTypeface(null, Typeface.NORMAL);
					break;
				default:
					viewHolder.time.setTypeface(null, Typeface.NORMAL);
					viewHolder.time.setTextColor(0xFF8e8e8e);
					if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {
						viewHolder.time.setText(filesize
								+ UIHelper.readableTimeDifference(item
										.getTimeSent()));
					} else {
						viewHolder.time.setText(item.getCounterpart()
								+ " \u00B7 "
								+ UIHelper.readableTimeDifference(item
										.getTimeSent()));
					}
					break;
				}
				return view;
			}
		};
		messagesView.setAdapter(messageListAdapter);

		return view;
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
		this.conversation.setNextMessage(chatMsg.getText().toString());
	}

	public void onBackendConnected() {
		this.conversation = activity.getSelectedConversation();
		if (this.conversation == null) {
			return;
		}
		if (this.pastedText == null) {
			this.chatMsg.setText(conversation.getNextMessage());
		} else {
			chatMsg.setText(conversation.getNextMessage() + " " + pastedText);
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
		Log.d("xmppService", "called to decrypt");
		PgpEngine engine = activity.xmppConnectionService.getPgpEngine();
		if (engine != null) {
			engine.decrypt(message, new OnPgpEngineResult() {

				@Override
				public void userInputRequried(PendingIntent pi) {
					askForPassphraseIntent = pi.getIntentSender();
					pgpInfo.setVisibility(View.VISIBLE);
				}

				@Override
				public void success() {
					Log.d("xmppService", "successfully decrypted");
					activity.xmppConnectionService.databaseBackend
							.updateMessage(message);
					updateMessages();
				}

				@Override
				public void error(OpenPgpError openPgpError) {
					Log.d("xmppService",
							"decryption error" + openPgpError.getMessage());
					message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
					// updateMessages();
				}
			});
		} else {
			Log.d("xmppService", "engine was null");
		}
	}

	public void updateMessages() {
		ConversationActivity activity = (ConversationActivity) getActivity();
		if (this.conversation != null) {
			for (Message message : this.conversation.getMessages()) {
				if ((message.getEncryption() == Message.ENCRYPTION_PGP)
						&& (message.getStatus() == Message.STATUS_RECIEVED)) {
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
		activity.xmppConnectionService.sendMessage(message, null);
		chatMsg.setText("");
	}

	protected void sendPgpMessage(final Message message) {
		final ConversationActivity activity = (ConversationActivity) getActivity();
		final XmppConnectionService xmppService = activity.xmppConnectionService;
		final Contact contact = message.getConversation().getContact();
		final Account account = message.getConversation().getAccount();
		if (activity.hasPgp()) {
			if (contact.getPgpKeyId() != 0) {
				xmppService.getPgpEngine().hasKey(contact,
						new OnPgpEngineResult() {

							@Override
							public void userInputRequried(PendingIntent pi) {
								activity.runIntent(
										pi,
										ConversationActivity.REQUEST_SEND_MESSAGE);
							}

							@Override
							public void success() {
								xmppService.getPgpEngine().encrypt(account,
										message, new OnPgpEngineResult() {

											@Override
											public void userInputRequried(
													PendingIntent pi) {
												activity.runIntent(
														pi,
														ConversationActivity.REQUEST_SEND_MESSAGE);
											}

											@Override
											public void success() {
												xmppService.sendMessage(
														message, null);
												chatMsg.setText("");
											}

											@Override
											public void error(
													OpenPgpError openPgpError) {
												// TODO Auto-generated method
												// stub

											}
										});
							}

							@Override
							public void error(OpenPgpError openPgpError) {
								Log.d("xmppService", "openpgp error"
										+ openPgpError.getMessage());
							}
						});

			} else {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						getActivity());
				builder.setTitle("No openPGP key found");
				builder.setIconAttribute(android.R.attr.alertDialogIcon);
				builder.setMessage("There is no openPGP key associated with this contact");
				builder.setNegativeButton("Cancel", null);
				builder.setPositiveButton("Send plain text",
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								conversation
										.setNextEncryption(Message.ENCRYPTION_NONE);
								message.setEncryption(Message.ENCRYPTION_NONE);
								xmppService.sendMessage(message, null);
								chatMsg.setText("");
							}
						});
				builder.create().show();
			}
		}
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
		private Bitmap error = null;

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
}
