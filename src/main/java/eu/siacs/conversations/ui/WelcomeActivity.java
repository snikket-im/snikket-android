package eu.siacs.conversations.ui;

import android.app.ActionBar;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;

public class WelcomeActivity extends XmppActivity {

	@Override
	protected void refreshUiReal() {

	}

	@Override
	void onBackendConnected() {

	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		if (getResources().getBoolean(R.bool.portrait_only)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
		super.onCreate(savedInstanceState);
		setContentView(R.layout.welcome);
		final ActionBar ab = getActionBar();
		if (ab != null) {
			ab.setDisplayShowHomeEnabled(false);
			ab.setDisplayHomeAsUpEnabled(false);
		}
		final Button createAccount = (Button) findViewById(R.id.create_account);
		createAccount.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(WelcomeActivity.this, MagicCreateActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
				startActivity(intent);
			}
		});
		final Button useOwnProvider = (Button) findViewById(R.id.use_own_provider);
		useOwnProvider.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				List<Account> accounts = xmppConnectionService.getAccounts();
				Intent intent = new Intent(WelcomeActivity.this, EditAccountActivity.class);
				if (accounts.size() == 1) {
					intent.putExtra("jid",accounts.get(0).getJid().toBareJid().toString());
					intent.putExtra("init",true);
				} else if (accounts.size() >= 1) {
					intent = new Intent(WelcomeActivity.this, ManageAccountActivity.class);
				}
				startActivity(intent);
			}
		});

	}

}
