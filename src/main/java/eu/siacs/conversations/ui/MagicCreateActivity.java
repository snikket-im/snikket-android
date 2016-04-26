package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class MagicCreateActivity extends XmppActivity implements TextWatcher {

	private TextView mFullJidDisplay;
	private EditText mUsername;
	private SecureRandom mRandom;

	private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456780+-/#$!?";
	private static final int PW_LENGTH = 10;

	@Override
	protected void refreshUiReal() {

	}

	@Override
	void onBackendConnected() {

	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.magic_create);
		mFullJidDisplay = (TextView) findViewById(R.id.full_jid);
		mUsername = (EditText) findViewById(R.id.username);
		mRandom = new SecureRandom();
		Button next = (Button) findViewById(R.id.create_account);
		next.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String username = mUsername.getText().toString();
				if (username.contains("@") || username.length() < 3) {
					mUsername.setError(getString(R.string.invalid_username));
					mUsername.requestFocus();
				} else {
					mUsername.setError(null);
					try {
						Jid jid = Jid.fromParts(username.toLowerCase(), Config.MAGIC_CREATE_DOMAIN, null);
						Account account = xmppConnectionService.findAccountByJid(jid);
						if (account == null) {
							account = new Account(jid, createPassword());
							account.setOption(Account.OPTION_REGISTER, true);
							account.setOption(Account.OPTION_DISABLED, true);
							account.setOption(Account.OPTION_MAGIC_CREATE, true);
							xmppConnectionService.createAccount(account);
						}
						Intent intent = new Intent(MagicCreateActivity.this, EditAccountActivity.class);
						intent.putExtra("jid", account.getJid().toBareJid().toString());
						intent.putExtra("init", true);
						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
						Toast.makeText(MagicCreateActivity.this, R.string.secure_password_generated, Toast.LENGTH_SHORT).show();
						startActivity(intent);
					} catch (InvalidJidException e) {
						mUsername.setError(getString(R.string.invalid_username));
						mUsername.requestFocus();
					}
				}
			}
		});
		mUsername.addTextChangedListener(this);
	}

	private String createPassword() {
		StringBuilder builder = new StringBuilder(PW_LENGTH);
		for(int i = 0; i < PW_LENGTH; ++i) {
			builder.append(CHARS.charAt(mRandom.nextInt(CHARS.length() - 1)));
		}
		return builder.toString();
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	@Override
	public void afterTextChanged(Editable s) {
		if (s.toString().trim().length() > 0) {
			try {
				mFullJidDisplay.setVisibility(View.VISIBLE);
				Jid jid = Jid.fromParts(s.toString().toLowerCase(), Config.MAGIC_CREATE_DOMAIN, null);
				mFullJidDisplay.setText(getString(R.string.your_full_jid_will_be, jid.toString()));
			} catch (InvalidJidException e) {
				mFullJidDisplay.setVisibility(View.INVISIBLE);
			}

		} else {
			mFullJidDisplay.setVisibility(View.INVISIBLE);
		}
	}
}
