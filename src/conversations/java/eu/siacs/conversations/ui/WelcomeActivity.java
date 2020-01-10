package eu.siacs.conversations.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityWelcomeBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.InstallReferrerService;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

import static eu.siacs.conversations.utils.PermissionUtils.allGranted;
import static eu.siacs.conversations.utils.PermissionUtils.writeGranted;

public class WelcomeActivity extends XmppActivity {

    private static final int REQUEST_IMPORT_BACKUP = 0x63fb;

    private XmppUri inviteUri;

    private BroadcastReceiver installReferrerBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent data) {
            final String invite = data.getStringExtra(StartConversationActivity.EXTRA_INVITE_URI);
            if (invite == null) {
                return;
            }
            Log.d(Config.LOGTAG, "welcome activity received install referrer uri: " + invite);
            final XmppUri xmppUri = new XmppUri(invite);
            processXmppUri(xmppUri);
        }
    };

    private boolean processXmppUri(final XmppUri xmppUri) {
        if (xmppUri.isValidJid()) {
            final String preauth = xmppUri.getParamater("preauth");
            final Jid jid = xmppUri.getJid();
            final Intent intent;
            if (xmppUri.isAction(XmppUri.ACTION_REGISTER)) {
                intent = SignupUtils.getTokenRegistrationIntent(this, jid, preauth);
            } else if (xmppUri.isAction(XmppUri.ACTION_ROSTER) && "y".equals(xmppUri.getParamater("ibr"))) {
                intent = SignupUtils.getTokenRegistrationIntent(this, Jid.ofDomain(jid.getDomain()), preauth);
                intent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, xmppUri.toString());
            } else {
                intent = null;
            }
            if (intent != null) {
                startActivity(intent);
                finish();
                return true;
            }
            this.inviteUri = xmppUri;
        }
        return false;
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(InstallReferrerService.INSTALL_REFERRER_BROADCAST_ACTION);
        registerReceiver(installReferrerBroadcastReceiver, intentFilter);
    }

    @Override
    public void onStop() {
        unregisterReceiver(installReferrerBroadcastReceiver);
        super.onStop();
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null) {
            setIntent(intent);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final String referrer = preferences.getString(SignupUtils.INSTALL_REFERRER,null);
        final XmppUri referrerUri = referrer == null ? null : new XmppUri(referrer);
        if (referrerUri != null && processXmppUri(referrerUri)) {
            return;
        }
        ActivityWelcomeBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_welcome);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar(), false);
        binding.registerNewAccount.setOnClickListener(v -> {
            final Intent intent = new Intent(this, PickServerActivity.class);
            addInviteUri(intent);
            startActivity(intent);
        });
        binding.useExisting.setOnClickListener(v -> {
            List<Account> accounts = xmppConnectionService.getAccounts();
            Intent intent = new Intent(WelcomeActivity.this, EditAccountActivity.class);
            intent.putExtra(EditAccountActivity.EXTRA_FORCE_REGISTER, false);
            if (accounts.size() == 1) {
                intent.putExtra("jid", accounts.get(0).getJid().asBareJid().toString());
                intent.putExtra("init", true);
            } else if (accounts.size() >= 1) {
                intent = new Intent(WelcomeActivity.this, ManageAccountActivity.class);
            }
            addInviteUri(intent);
            startActivity(intent);
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.welcome_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_import_backup) {
            if (hasStoragePermission(REQUEST_IMPORT_BACKUP)) {
                startActivity(new Intent(this, ImportBackupActivity.class));
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (grantResults.length > 0) {
            if (allGranted(grantResults)) {
                switch (requestCode) {
                    case REQUEST_IMPORT_BACKUP:
                        startActivity(new Intent(this, ImportBackupActivity.class));
                        break;
                }
            } else {
                Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
            }
        }
        if (writeGranted(grantResults, permissions)) {
            if (xmppConnectionService != null) {
                xmppConnectionService.restartFileObserver();
            }
        }
    }

    public void addInviteUri(Intent to) {
        final Intent from = getIntent();
        if (from != null && from.hasExtra(StartConversationActivity.EXTRA_INVITE_URI)) {
            final String invite = from.getStringExtra(StartConversationActivity.EXTRA_INVITE_URI);
            to.putExtra(StartConversationActivity.EXTRA_INVITE_URI, invite);
        } else if (this.inviteUri != null) {
            Log.d(Config.LOGTAG,"injecting referrer uri into on-boarding flow");
            to.putExtra(StartConversationActivity.EXTRA_INVITE_URI, this.inviteUri.toString());
        }
    }

    public static void launch(AppCompatActivity activity) {
        Intent intent = new Intent(activity, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }

}
