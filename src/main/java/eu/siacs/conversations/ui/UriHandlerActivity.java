package eu.siacs.conversations.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

public class UriHandlerActivity extends AppCompatActivity {

    public static final String ACTION_SCAN_QR_CODE = "scan_qr_code";
    private static final int REQUEST_SCAN_QR_CODE = 0x1234;
    private static final int REQUEST_CAMERA_PERMISSIONS_TO_SCAN = 0x6789;

    private boolean handled = false;

    public static void scan(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(activity, UriHandlerActivity.class);
            intent.setAction(UriHandlerActivity.ACTION_SCAN_QR_CODE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
        } else {
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSIONS_TO_SCAN);
        }
    }

    public static void onRequestPermissionResult(Activity activity, int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_CAMERA_PERMISSIONS_TO_SCAN) {
            return;
        }
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scan(activity);
            } else {
                Toast.makeText(activity, R.string.qr_code_scanner_needs_access_to_camera, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.handled = savedInstanceState != null && savedInstanceState.getBoolean("handled", false);
        getLayoutInflater().inflate(R.layout.toolbar, findViewById(android.R.id.content));
        setSupportActionBar(findViewById(R.id.toolbar));
    }

    @Override
    public void onStart() {
        super.onStart();
        handleIntent(getIntent());
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("handled", this.handled);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleUri(Uri uri) {
        handleUri(uri, false);
    }

    private void handleUri(Uri uri, final boolean scanned) {
        final Intent intent;
        final XmppUri xmppUri = new XmppUri(uri);
        final List<Jid> accounts = DatabaseBackend.getInstance(this).getAccountJids(true);

        if (SignupUtils.isSupportTokenRegistry() && xmppUri.isJidValid()) {
            final String preauth = xmppUri.getParamater("preauth");
            final Jid jid = xmppUri.getJid();
            if (xmppUri.isAction(XmppUri.ACTION_REGISTER)) {
                if (jid.isDomainJid()) {
                    intent = SignupUtils.getTokenRegistrationIntent(this, jid.getDomain(), preauth);
                    startActivity(intent);
                    return;
                }
                return;
            }
            if (xmppUri.isAction(XmppUri.ACTION_ROSTER) && "y".equals(xmppUri.getParamater("ibr"))) {
                intent = SignupUtils.getTokenRegistrationIntent(this, jid.getDomain(), preauth);
                intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, xmppUri.toString());
                startActivity(intent);
                return;
            }
        }

        if (accounts.size() == 0) {
            if (xmppUri.isJidValid()) {
                intent = SignupUtils.getSignUpIntent(this);
                intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, xmppUri.toString());
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
            }

            return;
        }

        if (xmppUri.isAction(XmppUri.ACTION_MESSAGE)) {

            final Jid jid = xmppUri.getJid();
            final String body = xmppUri.getBody();

            if (jid != null) {
                Class clazz;
                try {
                    clazz = Class.forName("eu.siacs.conversations.ui.ShareViaAccountActivity");
                } catch (ClassNotFoundException e) {
                    clazz = null;

                }
                if (clazz != null) {
                    intent = new Intent(this, clazz);
                    intent.putExtra("contact", jid.toEscapedString());
                    intent.putExtra("body", body);
                } else {
                    intent = new Intent(this, StartConversationActivity.class);
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(uri);
                    intent.putExtra("account", accounts.get(0).toEscapedString());
                }

            } else {
                intent = new Intent(this, ShareWithActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, body);
            }
        } else if (accounts.contains(xmppUri.getJid())) {
            intent = new Intent(getApplicationContext(), EditAccountActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.putExtra("jid", xmppUri.getJid().asBareJid().toString());
            intent.setData(uri);
            intent.putExtra("scanned", scanned);
        } else if (xmppUri.isJidValid()) {
            intent = new Intent(getApplicationContext(), StartConversationActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra("scanned", scanned);
            intent.setData(uri);
        } else {
            Toast.makeText(this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(intent);
    }

    private void handleIntent(Intent data) {
        if (handled) {
            return;
        }
        if (data == null || data.getAction() == null) {
            finish();
            return;
        }

        handled = true;

        switch (data.getAction()) {
            case Intent.ACTION_VIEW:
            case Intent.ACTION_SENDTO:
                handleUri(data.getData());
                break;
            case ACTION_SCAN_QR_CODE:
                Intent intent = new Intent(this, ScanActivity.class);
                startActivityForResult(intent, REQUEST_SCAN_QR_CODE);
                return;
        }

        finish();
    }

    private static final Pattern VCARD_XMPP_PATTERN = Pattern.compile("\nIMPP([^:]*):(xmpp:.+)\n");

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, requestCode, intent);
        if (requestCode == REQUEST_SCAN_QR_CODE && resultCode == RESULT_OK) {
            String result = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            if (result != null) {
                if (result.startsWith("BEGIN:VCARD\n")) {
                    Matcher matcher = VCARD_XMPP_PATTERN.matcher(result);
                    if (matcher.find()) {
                        result = matcher.group(2);
                    }
                }
                Uri uri = Uri.parse(result);
                handleUri(uri, true);
            }
        }
        finish();
    }
}