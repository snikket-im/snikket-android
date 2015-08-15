package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ShareWithActivity extends XmppActivity {

	private class Share {
		public List<Uri> uris = new ArrayList<>();
		public boolean image;
		public String account;
		public String contact;
		public String text;
	}

	private Share share;

	private static final int REQUEST_START_NEW_CONVERSATION = 0x0501;
	private ListView mListView;
	private List<Conversation> mConversations = new ArrayList<>();

	private UiCallback<Message> attachFileCallback = new UiCallback<Message>() {

		@Override
		public void userInputRequried(PendingIntent pi, Message object) {
			// TODO Auto-generated method stub

		}

		@Override
		public void success(Message message) {
			xmppConnectionService.sendMessage(message);
		}

		@Override
		public void error(int errorCode, Message object) {
			// TODO Auto-generated method stub

		}
	};

	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_START_NEW_CONVERSATION
				&& resultCode == RESULT_OK) {
			share.contact = data.getStringExtra("contact");
			share.account = data.getStringExtra("account");
		}
		if (xmppConnectionServiceBound
				&& share != null
				&& share.contact != null
				&& share.account != null) {
			share();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getActionBar() != null) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setHomeButtonEnabled(false);
		}

		setContentView(R.layout.share_with);
		setTitle(getString(R.string.title_activity_sharewith));

		mListView = (ListView) findViewById(R.id.choose_conversation_list);
		ConversationAdapter mAdapter = new ConversationAdapter(this,
				this.mConversations);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				share(mConversations.get(position));
			}
		});

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
		final String type = intent.getType();
		if (Intent.ACTION_SEND.equals(intent.getAction())) {
			final Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
			if (type != null && uri != null && !type.equalsIgnoreCase("text/plain")) {
				this.share.uris.add(uri);
				this.share.image = type.startsWith("image/") || isImage(uri);
			} else {
				this.share.text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
			}
		} else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
			this.share.image = type != null && type.startsWith("image/");
			if (!this.share.image) {
				return;
			}

			this.share.uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
		}
		if (xmppConnectionServiceBound) {
			xmppConnectionService.populateWithOrderedConversations(mConversations, this.share.uris.size() == 0);
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
				&& share.contact != null && share.account != null) {
			share();
			return;
		}
		xmppConnectionService.populateWithOrderedConversations(mConversations,
				this.share != null && this.share.uris.size() == 0);
	}

	private void share() {
		Account account;
		try {
			account = xmppConnectionService.findAccountByJid(Jid.fromString(share.account));
		} catch (final InvalidJidException e) {
			account = null;
		}
		if (account == null) {
			return;
		}
		final Conversation conversation;
		try {
			conversation = xmppConnectionService
					.findOrCreateConversation(account, Jid.fromString(share.contact), false);
		} catch (final InvalidJidException e) {
			return;
		}
		share(conversation);
	}

	private void share(final Conversation conversation) {
		if (share.uris.size() != 0) {
			OnPresenceSelected callback = new OnPresenceSelected() {
				@Override
				public void onPresenceSelected() {
					if (share.image) {
						Toast.makeText(getApplicationContext(),
								getText(R.string.preparing_image),
								Toast.LENGTH_LONG).show();
						for (Iterator<Uri> i = share.uris.iterator(); i.hasNext(); i.remove()) {
							ShareWithActivity.this.xmppConnectionService
									.attachImageToConversation(conversation, i.next(),
											attachFileCallback);
						}
					} else {
						Toast.makeText(getApplicationContext(),
								getText(R.string.preparing_file),
								Toast.LENGTH_LONG).show();
						ShareWithActivity.this.xmppConnectionService
								.attachFileToConversation(conversation, share.uris.get(0),
										attachFileCallback);
					}
					switchToConversation(conversation, null, true);
					finish();
				}
			};
			if (conversation.getAccount().httpUploadAvailable()) {
				callback.onPresenceSelected();
			} else {
				selectPresence(conversation, callback);
			}
		} else {
			switchToConversation(conversation, this.share.text, true);
			finish();
		}

	}

	public void refreshUiReal() {
		//nothing to do. This Activity doesn't implement any listeners
	}

}
