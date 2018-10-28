package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.XmppUri;

public class WelcomeActivity extends XmppActivity {

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
		setContentView(R.layout.welcome);
		setSupportActionBar(findViewById(R.id.toolbar));
		final ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayShowHomeEnabled(false);
			ab.setDisplayHomeAsUpEnabled(false);
		}
		final Button createAccount = findViewById(R.id.create_account);
		createAccount.setOnClickListener(v -> {
			final Intent intent = new Intent(WelcomeActivity.this, MagicCreateActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			addInviteUri(intent);
			startActivity(intent);
		});
		final Button useOwnProvider = findViewById(R.id.use_own_provider);
		useOwnProvider.setOnClickListener(v -> {
			List<Account> accounts = xmppConnectionService.getAccounts();
			Intent intent = new Intent(WelcomeActivity.this, EditAccountActivity.class);
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

	public void addInviteUri(Intent intent) {
		StartConversationActivity.addInviteUri(intent, getIntent());
	}

	public static void launch(AppCompatActivity activity) {
		Intent intent = new Intent(activity, WelcomeActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		activity.startActivity(intent);
		activity.overridePendingTransition(0,0);
	}

}
