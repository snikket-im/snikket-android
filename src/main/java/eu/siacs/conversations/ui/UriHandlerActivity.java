package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.jid.Jid;

public class UriHandlerActivity extends Activity {

    @Override
    public void onStart() {
        super.onStart();
        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleUri(Uri uri) {
        final Intent intent;
        final XmppUri xmppUri = new XmppUri(uri);
        final int numAccounts = DatabaseBackend.getInstance(this).getAccountJids().size();

        if (numAccounts == 0) {
            intent = new Intent(getApplicationContext(), WelcomeActivity.class);
            startActivity(intent);
            return;
        }

        if (xmppUri.isAction(XmppUri.ACTION_MESSAGE)) {
            final Jid jid = xmppUri.getJid();
            final String body = xmppUri.getBody();

            if (jid != null) {
                intent = new Intent(getApplicationContext(), ShareViaAccountActivity.class);
                intent.putExtra(ShareViaAccountActivity.EXTRA_CONTACT, jid.toString());
                intent.putExtra(ShareViaAccountActivity.EXTRA_BODY, body);
            } else {
                intent = new Intent(getApplicationContext(), ShareWithActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, body);
            }
        } else {
            intent = new Intent(getApplicationContext(), StartConversationActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.setData(uri);
        }

        startActivity(intent);
    }

    private void handleIntent(Intent data) {
        if (data == null) {
            finish();
            return;
        }

        switch (data.getAction()) {
            case Intent.ACTION_VIEW:
            case Intent.ACTION_SENDTO:
                handleUri(data.getData());
        }

        finish();
    }
}
