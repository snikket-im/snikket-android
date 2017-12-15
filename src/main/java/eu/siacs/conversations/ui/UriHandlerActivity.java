package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Parcelable;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Arrays;

import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.utils.XmppUri;
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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    Uri getInviteJellyBean(NdefRecord record) {
        return record.toUri();
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

    private void handleNfcIntent(Intent data) {
        for (Parcelable message : data.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)) {
            if (message instanceof NdefMessage) {
                for (NdefRecord record : ((NdefMessage) message).getRecords()) {
                    switch (record.getTnf()) {
                        case NdefRecord.TNF_WELL_KNOWN:
                            if (Arrays.equals(record.getType(), NdefRecord.RTD_URI)) {
                                if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                    handleUri(getInviteJellyBean(record));
                                } else {
                                    byte[] payload = record.getPayload();
                                    if (payload[0] == 0) {
                                        Uri uri = Uri.parse(new String(Arrays.copyOfRange(
                                                payload, 1, payload.length)));
                                        handleUri(uri);
                                    }
                                }
                            }
                    }
                }
            }
        }
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
                break;
            case ACTION_SCAN_QR_CODE:
                new IntentIntegrator(this).initiateScan(Arrays.asList("AZTEC", "QR_CODE"));
                return;
            case NfcAdapter.ACTION_NDEF_DISCOVERED:
                handleNfcIntent(data);
        }

        finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if ((requestCode & 0xFFFF) == IntentIntegrator.REQUEST_CODE) {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode,
                    intent);

            if (scanResult != null && scanResult.getFormatName() != null) {
                String data = scanResult.getContents();
                handleUri(Uri.parse(data));
            }
        }

        finish();
        super.onActivityResult(requestCode, requestCode, intent);
    }
}
