package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import eu.siacs.conversations.utils.ExceptionHelper;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.IntentSender.SendIntentException;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public abstract class XmppActivity extends Activity {

	public static final int REQUEST_ANNOUNCE_PGP = 0x73731;

	protected final static String LOGTAG = "xmppService";

	public XmppConnectionService xmppConnectionService;
	public boolean xmppConnectionServiceBound = false;
	protected boolean handledViewIntent = false;

	protected ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			XmppConnectionBinder binder = (XmppConnectionBinder) service;
			xmppConnectionService = binder.getService();
			xmppConnectionServiceBound = true;
			onBackendConnected();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			xmppConnectionServiceBound = false;
		}
	};

	@Override
	protected void onStart() {
		super.onStart();
		if (!xmppConnectionServiceBound) {
			connectToBackend();
		}
	}

	public void connectToBackend() {
		Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction("ui");
		startService(intent);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (xmppConnectionServiceBound) {
			unbindService(mConnection);
			xmppConnectionServiceBound = false;
		}
	}

	protected void hideKeyboard() {
		InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		View focus = getCurrentFocus();

		if (focus != null) {

			inputManager.hideSoftInputFromWindow(focus.getWindowToken(),
					InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}

	public boolean hasPgp() {
		return xmppConnectionService.getPgpEngine() != null;
	}

	public void showInstallPgpDialog() {
		Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.openkeychain_required));
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage(getText(R.string.openkeychain_required_long));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setNeutralButton(getString(R.string.restart),
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (xmppConnectionServiceBound) {
							unbindService(mConnection);
							xmppConnectionServiceBound = false;
						}
						stopService(new Intent(XmppActivity.this,
								XmppConnectionService.class));
						finish();
					}
				});
		builder.setPositiveButton(getString(R.string.install),
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						Uri uri = Uri
								.parse("market://details?id=org.sufficientlysecure.keychain");
						Intent intent = new Intent(Intent.ACTION_VIEW, uri);
						startActivity(intent);
						finish();
					}
				});
		builder.create().show();
	}

	abstract void onBackendConnected();

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		case R.id.action_accounts:
			startActivity(new Intent(this, ManageAccountActivity.class));
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ExceptionHelper.init(getApplicationContext());
	}

	public void switchToConversation(Conversation conversation) {
		switchToConversation(conversation, null, false);
	}
	
	public void switchToConversation(Conversation conversation, String text,
			boolean newTask) {
		Intent viewConversationIntent = new Intent(this,
				ConversationActivity.class);
		viewConversationIntent.setAction(Intent.ACTION_VIEW);
		viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
				conversation.getUuid());
		if (text != null) {
			viewConversationIntent.putExtra(ConversationActivity.TEXT, text);
		}
		viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);
		if (newTask) {
			viewConversationIntent.setFlags(viewConversationIntent.getFlags()
					| Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_SINGLE_TOP);
		} else {
			viewConversationIntent.setFlags(viewConversationIntent.getFlags()
					| Intent.FLAG_ACTIVITY_CLEAR_TOP);
		}
		startActivity(viewConversationIntent);
	}
	
	public void switchToContactDetails(Contact contact) {
		Intent intent = new Intent(this, ContactDetailsActivity.class);
		intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
		intent.putExtra("account", contact.getAccount().getJid());
		intent.putExtra("contact", contact.getJid());
		startActivity(intent);
	}

	protected void announcePgp(Account account, final Conversation conversation) {
		xmppConnectionService.getPgpEngine().generateSignature(account,
				"online", new UiCallback<Account>() {

					@Override
					public void userInputRequried(PendingIntent pi,
							Account account) {
						try {
							startIntentSenderForResult(pi.getIntentSender(),
									REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
						} catch (SendIntentException e) {
							Log.d("xmppService",
									"coulnd start intent for pgp anncouncment");
						}
					}

					@Override
					public void success(Account account) {
						xmppConnectionService.databaseBackend
								.updateAccount(account);
						xmppConnectionService.sendPresence(account);
						if (conversation != null) {
							conversation
									.setNextEncryption(Message.ENCRYPTION_PGP);
						}
					}

					@Override
					public void error(int error, Account account) {
						displayErrorDialog(error);
					}
				});
	}

	protected void displayErrorDialog(final int errorCode) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						XmppActivity.this);
				builder.setIconAttribute(android.R.attr.alertDialogIcon);
				builder.setTitle(getString(R.string.error));
				builder.setMessage(errorCode);
				builder.setNeutralButton(R.string.accept, null);
				builder.create().show();
			}
		});

	}
	
	protected void showAddToRosterDialog(final Conversation conversation) {
		String jid = conversation.getContactJid();
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(jid);
		builder.setMessage(getString(R.string.not_in_roster));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.add_contact),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						String jid = conversation.getContactJid();
						Account account = conversation.getAccount();
						Contact contact = account.getRoster().getContact(jid);
						xmppConnectionService.createContact(contact);
					}
				});
		builder.create().show();
	}
	
	public void selectPresence(final Conversation conversation,
			final OnPresenceSelected listener) {
		Contact contact = conversation.getContact();
		if (contact == null) {
			showAddToRosterDialog(conversation);
		} else {
			Presences presences = contact.getPresences();
			if (presences.size() == 0) {
				conversation.setNextPresence(null);
				listener.onPresenceSelected();
			} else if (presences.size() == 1) {
				String presence = (String) presences.asStringArray()[0];
				conversation.setNextPresence(presence);
				listener.onPresenceSelected();
			} else {
				final StringBuilder presence = new StringBuilder();
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(getString(R.string.choose_presence));
				final String[] presencesArray = presences.asStringArray();
				int preselectedPresence = 0;
				for (int i = 0; i < presencesArray.length; ++i) {
					if (presencesArray[i].equals(contact.lastseen.presence)) {
						preselectedPresence = i;
						break;
					}
				}
				presence.append(presencesArray[preselectedPresence]);
				builder.setSingleChoiceItems(presencesArray,
						preselectedPresence,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								presence.delete(0, presence.length());
								presence.append(presencesArray[which]);
							}
						});
				builder.setNegativeButton(R.string.cancel, null);
				builder.setPositiveButton(R.string.ok, new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						conversation.setNextPresence(presence.toString());
						listener.onPresenceSelected();
					}
				});
				builder.create().show();
			}
		}
	}
}
