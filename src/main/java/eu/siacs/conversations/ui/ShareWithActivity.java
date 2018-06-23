package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.service.EmojiService;
import eu.siacs.conversations.ui.util.PresenceSelector;
import eu.siacs.conversations.xmpp.XmppConnection;
import rocks.xmpp.addr.Jid;

public class ShareWithActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

	private static final int REQUEST_STORAGE_PERMISSION = 0x733f32;
	private boolean mReturnToPrevious = false;
	private Conversation mPendingConversation = null;

	@Override
	public void onConversationUpdate() {
		refreshUi();
	}

	private class Share {
		public List<Uri> uris = new ArrayList<>();
		public boolean image;
		public String account;
		public String contact;
		public String text;
		public String uuid;
		public boolean multiple = false;
		public String type;
	}

	private Share share;

	private static final int REQUEST_START_NEW_CONVERSATION = 0x0501;
	private RecyclerView mListView;
	private ConversationAdapter mAdapter;
	private List<Conversation> mConversations = new ArrayList<>();
	private Toast mToast;
	private AtomicInteger attachmentCounter = new AtomicInteger(0);

	private UiInformableCallback<Message> attachFileCallback = new UiInformableCallback<Message>() {

		@Override
		public void inform(final String text) {
			runOnUiThread(() -> replaceToast(text));
		}

		@Override
		public void userInputRequried(PendingIntent pi, Message object) {
			// TODO Auto-generated method stub

		}

		@Override
		public void success(final Message message) {
			runOnUiThread(() -> {
				if (attachmentCounter.decrementAndGet() <=0 ) {
					int resId;
					if (share.image && share.multiple) {
						resId = R.string.shared_images_with_x;
					} else if (share.image) {
						resId = R.string.shared_image_with_x;
					} else {
						resId = R.string.shared_file_with_x;
					}
					Conversation conversation = (Conversation) message.getConversation();
					replaceToast(getString(resId, conversation.getName()));
					if (mReturnToPrevious) {
						finish();
					} else {
						switchToConversation(conversation);
					}
				}
			});
		}

		@Override
		public void error(final int errorCode, Message object) {
			runOnUiThread(() -> {
				replaceToast(getString(errorCode));
				if (attachmentCounter.decrementAndGet() <=0 ) {
					finish();
				}
			});
		}
	};

	protected void hideToast() {
		if (mToast != null) {
			mToast.cancel();
		}
	}

	protected void replaceToast(String msg) {
		hideToast();
		mToast = Toast.makeText(this, msg ,Toast.LENGTH_LONG);
		mToast.show();
	}

	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_START_NEW_CONVERSATION
				&& resultCode == RESULT_OK) {
			share.contact = data.getStringExtra("contact");
			share.account = data.getStringExtra(EXTRA_ACCOUNT);
		}
		if (xmppConnectionServiceBound
				&& share != null
				&& share.contact != null
				&& share.account != null) {
			share();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		if (grantResults.length > 0)
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (requestCode == REQUEST_STORAGE_PERMISSION) {
					if (this.mPendingConversation != null) {
						share(this.mPendingConversation);
					} else {
						Log.d(Config.LOGTAG,"unable to find stored conversation");
					}
				}
			} else {
				Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
			}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		new EmojiService(this).init();

		setContentView(R.layout.activity_share_with);

		setSupportActionBar(findViewById(R.id.toolbar));
		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(false);
			getSupportActionBar().setHomeButtonEnabled(false);
		}

		setTitle(getString(R.string.title_activity_sharewith));

		mListView = findViewById(R.id.choose_conversation_list);
		mAdapter = new ConversationAdapter(this, this.mConversations);
		mListView.setLayoutManager(new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false));
		mListView.setAdapter(mAdapter);
		mAdapter.setConversationClickListener((view, conversation) -> share(conversation));
		this.share = new Share();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.share_with, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_add:
				final Intent intent = new Intent(getApplicationContext(), ChooseContactActivity.class);
				startActivityForResult(intent, REQUEST_START_NEW_CONVERSATION);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onStart() {
		super.onStart();
		Intent intent = getIntent();
		if (intent == null) {
			return;
		}
		this.mReturnToPrevious = getBooleanPreference("return_to_previous", R.bool.return_to_previous);
		final String type = intent.getType();
		final String action = intent.getAction();
		Log.d(Config.LOGTAG, "action: "+action+ ", type:"+type);
		share.uuid = intent.getStringExtra("uuid");
		if (Intent.ACTION_SEND.equals(action)) {
			final String text = intent.getStringExtra(Intent.EXTRA_TEXT);
			final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
			if (type != null && uri != null && (text == null || !type.equals("text/plain"))) {
				this.share.uris.clear();
				this.share.uris.add(uri);
				this.share.image = type.startsWith("image/") || isImage(uri);
				this.share.type = type;
			} else {
				this.share.text = text;
			}
		} else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
			this.share.image = type != null && type.startsWith("image/");
			if (!this.share.image) {
				return;
			}
			this.share.uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
		}
		if (xmppConnectionServiceBound) {
			if (share.uuid != null) {
				share();
			} else {
				xmppConnectionService.populateWithOrderedConversations(mConversations, this.share.uris.size() == 0);
			}
		}

	}

	protected boolean isImage(Uri uri) {
		try {
			String guess = URLConnection.guessContentTypeFromName(uri.toString());
			return (guess != null && guess.startsWith("image/"));
		} catch (final StringIndexOutOfBoundsException ignored) {
			return false;
		}
	}

	@Override
	void onBackendConnected() {
		if (xmppConnectionServiceBound && share != null
				&& ((share.contact != null && share.account != null) || share.uuid != null)) {
			share();
			return;
		}
		refreshUiReal();
	}

	private void share() {
		final Conversation conversation;
		if (share.uuid != null) {
			conversation = xmppConnectionService.findConversationByUuid(share.uuid);
			if (conversation == null) {
				return;
			}
		}else{
			Account account;
			try {
				account = xmppConnectionService.findAccountByJid(Jid.of(share.account));
			} catch (final IllegalArgumentException e) {
				account = null;
			}
			if (account == null) {
				return;
			}

			try {
				conversation = xmppConnectionService
						.findOrCreateConversation(account, Jid.of(share.contact), false,true);
			} catch (final IllegalArgumentException e) {
				return;
			}
		}
		share(conversation);
	}

	private void share(final Conversation conversation) {
		if (share.uris.size() != 0 && !hasStoragePermission(REQUEST_STORAGE_PERMISSION)) {
			mPendingConversation = conversation;
			return;
		}
		final Account account = conversation.getAccount();
		final XmppConnection connection = account.getXmppConnection();
		final long max = connection == null ? -1 : connection.getFeatures().getMaxHttpUploadSize();
		mListView.setEnabled(false);
		if (conversation.getNextEncryption() == Message.ENCRYPTION_PGP && !hasPgp()) {
			if (share.uuid == null) {
				showInstallPgpDialog();
			} else {
				Toast.makeText(this,R.string.openkeychain_not_installed,Toast.LENGTH_SHORT).show();
				finish();
			}
			return;
		}
		if (share.uris.size() != 0) {
			PresenceSelector.OnPresenceSelected callback = () -> {
				attachmentCounter.set(share.uris.size());
				if (share.image) {
					share.multiple = share.uris.size() > 1;
					replaceToast(getString(share.multiple ? R.string.preparing_images : R.string.preparing_image));
					for (Iterator<Uri> i = share.uris.iterator(); i.hasNext(); i.remove()) {
						final Uri uri = i.next();
						delegateUriPermissionsToService(uri);
						xmppConnectionService.attachImageToConversation(conversation, uri, attachFileCallback);
					}
				} else {
					replaceToast(getString(R.string.preparing_file));
					final Uri uri = share.uris.get(0);
					delegateUriPermissionsToService(uri);
					xmppConnectionService.attachFileToConversation(conversation, uri, share.type,  attachFileCallback);
				}
			};
			if (account.httpUploadAvailable()
					&& ((share.image && !neverCompressPictures())
					|| conversation.getMode() == Conversation.MODE_MULTI
					|| FileBackend.allFilesUnderSize(this, share.uris, max))) {
				callback.onPresenceSelected();
			} else {
				selectPresence(conversation, callback);
			}
		} else {
			if (mReturnToPrevious && this.share.text != null && !this.share.text.isEmpty() ) {
				final PresenceSelector.OnPresenceSelected callback = new PresenceSelector.OnPresenceSelected() {

					private void finishAndSend(Message message) {
						replaceToast(getString(R.string.shared_text_with_x, conversation.getName()));
						finish();
					}

					private UiCallback<Message> messageEncryptionCallback = new UiCallback<Message>() {
						@Override
						public void success(final Message message) {
							runOnUiThread(() -> finishAndSend(message));
						}

						@Override
						public void error(final int errorCode, Message object) {
							runOnUiThread(() -> {
								replaceToast(getString(errorCode));
								finish();
							});
						}

						@Override
						public void userInputRequried(PendingIntent pi, Message object) {
							finish();
						}
					};

					@Override
					public void onPresenceSelected() {

						final int encryption = conversation.getNextEncryption();

						Message message = new Message(conversation,share.text, encryption);

						Log.d(Config.LOGTAG,"on presence selected encrpytion="+encryption);

						if (encryption == Message.ENCRYPTION_PGP) {
							replaceToast(getString(R.string.encrypting_message));
							xmppConnectionService.getPgpEngine().encrypt(message,messageEncryptionCallback);
							return;
						}
						xmppConnectionService.sendMessage(message);
						finishAndSend(message);
					}
				};
				if (conversation.getNextEncryption() == Message.ENCRYPTION_OTR) {
					selectPresence(conversation, callback);
				} else {
					callback.onPresenceSelected();
				}
			} else {
				switchToConversation(conversation, this.share.text, true);
			}
		}

	}

	public void refreshUiReal() {
		xmppConnectionService.populateWithOrderedConversations(mConversations, this.share != null && this.share.uris.size() == 0);
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onBackPressed() {
		if (attachmentCounter.get() >= 1) {
			replaceToast(getString(R.string.sharing_files_please_wait));
		} else {
			super.onBackPressed();
		}
	}
}
