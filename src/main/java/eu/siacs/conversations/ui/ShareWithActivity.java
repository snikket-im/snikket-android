package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ShareWithActivity extends XmppActivity {

	private class Share {
		public Uri uri;
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

	protected void onActivityResult(int requestCode, int resultCode,
			final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_START_NEW_CONVERSATION
				&& resultCode == RESULT_OK) {
			share.contact = data.getStringExtra("contact");
			share.account = data.getStringExtra("account");
			Log.d(Config.LOGTAG, "contact: " + share.contact + " account:"
					+ share.account);
		}
		if (xmppConnectionServiceBound && share != null
				&& share.contact != null && share.account != null) {
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
			public void onItemClick(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				Conversation conversation = mConversations.get(position);
				if (conversation.getMode() == Conversation.MODE_SINGLE
						|| share.uri == null) {
					share(mConversations.get(position));
				}
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
			final Intent intent = new Intent(getApplicationContext(),
					ChooseContactActivity.class);
			startActivityForResult(intent, REQUEST_START_NEW_CONVERSATION);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onStart() {
		final String type = getIntent().getType();
		if (type != null && !type.startsWith("text/")) {
			this.share.uri = (Uri) getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
			this.share.image = type.startsWith("image/") || URLConnection.guessContentTypeFromName(share.uri.getPath()).startsWith("image/");
		} else {
			this.share.text = getIntent().getStringExtra(Intent.EXTRA_TEXT);
		}
		if (xmppConnectionServiceBound) {
			xmppConnectionService.populateWithOrderedConversations(
					mConversations, this.share.uri == null);
		}
		super.onStart();
	}

	@Override
	void onBackendConnected() {
		if (xmppConnectionServiceBound && share != null
				&& share.contact != null && share.account != null) {
			share();
			return;
		}
		xmppConnectionService.populateWithOrderedConversations(mConversations,
				this.share != null && this.share.uri == null);
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
		if (share.uri != null) {
			selectPresence(conversation, new OnPresenceSelected() {
				@Override
				public void onPresenceSelected() {
					if (share.image) {
						Toast.makeText(getApplicationContext(),
								getText(R.string.preparing_image),
								Toast.LENGTH_LONG).show();
						ShareWithActivity.this.xmppConnectionService
							.attachImageToConversation(conversation, share.uri,
									attachFileCallback);
					} else {
						Toast.makeText(getApplicationContext(),
								getText(R.string.preparing_file),
								Toast.LENGTH_LONG).show();
						ShareWithActivity.this.xmppConnectionService
							.attachFileToConversation(conversation, share.uri,
									attachFileCallback);
					}
					switchToConversation(conversation, null, true);
					finish();
				}
			});

		} else {
			switchToConversation(conversation, this.share.text, true);
			finish();
		}

	}

}
