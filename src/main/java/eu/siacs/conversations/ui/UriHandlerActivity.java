package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.utils.zxing.IntentIntegrator;
import eu.siacs.conversations.utils.zxing.IntentResult;
import eu.siacs.conversations.xmpp.jid.Jid;

public class UriHandlerActivity extends Activity {
    public static final String ACTION_SCAN_QR_CODE = "scan_qr_code";

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
        final List<Jid> accounts = DatabaseBackend.getInstance(this).getAccountJids();

        if (accounts.size() == 0) {
            intent = new Intent(getApplicationContext(), WelcomeActivity.class);
            WelcomeActivity.addInviteUri(intent, xmppUri);
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
        } else if (accounts.contains(xmppUri.getJid())) {
            intent = new Intent(getApplicationContext(), EditAccountActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra("jid", xmppUri.getJid().toBareJid().toString());
            intent.setData(uri);
        } else {
            intent = new Intent(getApplicationContext(), StartConversationActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.setData(uri);
        }

        startActivity(intent);
    }

    private void handleIntent(Intent data) {
        if (data == null || data.getAction() == null) {
            finish();
            return;
        }

        switch (data.getAction()) {
            case Intent.ACTION_VIEW:
            case Intent.ACTION_SENDTO:
                handleUri(data.getData());
                break;
            case ACTION_SCAN_QR_CODE:
                new IntentIntegrator(this).initiateScan(Arrays.asList("AZTEC", "QR_CODE"));
                return;
        }

        finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if ((requestCode & 0xFFFF) == IntentIntegrator.REQUEST_CODE) {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

            if (scanResult != null && scanResult.getFormatName() != null) {
                String data = scanResult.getContents();
                handleUri(Uri.parse(data));
            }
        }

        finish();
        super.onActivityResult(requestCode, requestCode, intent);
    }
}
