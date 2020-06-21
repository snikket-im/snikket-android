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
import android.widget.Toast;

import com.google.common.base.Strings;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.utils.ProvisioningUtils;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;

public class UriHandlerActivity extends AppCompatActivity {

    public static final String ACTION_SCAN_QR_CODE = "scan_qr_code";
    private static final String EXTRA_ALLOW_PROVISIONING = "extra_allow_provisioning";
    private static final int REQUEST_SCAN_QR_CODE = 0x1234;
    private static final int REQUEST_CAMERA_PERMISSIONS_TO_SCAN = 0x6789;
    private static final int REQUEST_CAMERA_PERMISSIONS_TO_SCAN_AND_PROVISION = 0x6790;
    private static final Pattern V_CARD_XMPP_PATTERN = Pattern.compile("\nIMPP([^:]*):(xmpp:.+)\n");
    private boolean handled = false;

    public static void scan(final Activity activity) {
        scan(activity, false);
    }

    public static void scan(final Activity activity, final boolean provisioning) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            final Intent intent = new Intent(activity, UriHandlerActivity.class);
            intent.setAction(UriHandlerActivity.ACTION_SCAN_QR_CODE);
            if (provisioning) {
                intent.putExtra(EXTRA_ALLOW_PROVISIONING, true);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            activity.startActivity(intent);
        } else {
            activity.requestPermissions(
                    new String[]{Manifest.permission.CAMERA},
                    provisioning ? REQUEST_CAMERA_PERMISSIONS_TO_SCAN_AND_PROVISION : REQUEST_CAMERA_PERMISSIONS_TO_SCAN
            );
        }
    }

    public static void onRequestPermissionResult(Activity activity, int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_CAMERA_PERMISSIONS_TO_SCAN && requestCode != REQUEST_CAMERA_PERMISSIONS_TO_SCAN_AND_PROVISION) {
            return;
        }
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_CAMERA_PERMISSIONS_TO_SCAN_AND_PROVISION) {
                    scan(activity, true);
                } else {
                    scan(activity);
                }
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

        if (SignupUtils.isSupportTokenRegistry() && xmppUri.isValidJid()) {
            final String preAuth = xmppUri.getParameter(XmppUri.PARAMETER_PRE_AUTH);
            final Jid jid = xmppUri.getJid();
            if (xmppUri.isAction(XmppUri.ACTION_REGISTER)) {
                if (jid.getEscapedLocal() != null && accounts.contains(jid.asBareJid())) {
                    Toast.makeText(this, R.string.account_already_exists, Toast.LENGTH_LONG).show();
                    return;
                }
                intent = SignupUtils.getTokenRegistrationIntent(this, jid, preAuth);
                startActivity(intent);
                return;
            }
            if (xmppUri.isAction(XmppUri.ACTION_ROSTER) && "y".equals(xmppUri.getParameter(XmppUri.PARAMETER_IBR))) {
                intent = SignupUtils.getTokenRegistrationIntent(this, jid.getDomain(), preAuth);
                intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, xmppUri.toString());
                startActivity(intent);
                return;
            }
        }

        if (accounts.size() == 0) {
            if (xmppUri.isValidJid()) {
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
        } else if (xmppUri.isValidJid()) {
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

    private boolean allowProvisioning() {
        final Intent launchIntent = getIntent();
        return launchIntent != null && launchIntent.getBooleanExtra(EXTRA_ALLOW_PROVISIONING, false);
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        super.onActivityResult(requestCode, requestCode, intent);
        if (requestCode == REQUEST_SCAN_QR_CODE && resultCode == RESULT_OK) {
            final String result = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
            if (Strings.isNullOrEmpty(result)) {
                finish();
                return;
            }
            if (result.startsWith("BEGIN:VCARD\n")) {
                final Matcher matcher = V_CARD_XMPP_PATTERN.matcher(result);
                if (matcher.find()) {
                    handleUri(Uri.parse(matcher.group(2)), true);
                }
                finish();
                return;
            } else if (QuickConversationsService.isConversations() && looksLikeJsonObject(result) && allowProvisioning()) {
                ProvisioningUtils.provision(this, result);
                finish();
                return;
            }
            handleUri(Uri.parse(result), true);
        }
        finish();
    }

    private static boolean looksLikeJsonObject(final String input) {
        return input.charAt(0) == '{' && input.charAt(input.length() - 1) == '}';
    }
}