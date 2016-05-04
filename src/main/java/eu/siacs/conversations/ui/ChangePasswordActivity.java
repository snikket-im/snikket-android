package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ChangePasswordActivity extends XmppActivity implements XmppConnectionService.OnAccountPasswordChanged {

	private Button mChangePasswordButton;
	private View.OnClickListener mOnChangePasswordButtonClicked = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			if (mAccount != null) {
				final String currentPassword = mCurrentPassword.getText().toString();
				final String newPassword = mNewPassword.getText().toString();
				final String newPasswordConfirm = mNewPasswordConfirm.getText().toString();
				if (!mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) && !currentPassword.equals(mAccount.getPassword())) {
					mCurrentPassword.requestFocus();
					mCurrentPassword.setError(getString(R.string.account_status_unauthorized));
				} else if (!newPassword.equals(newPasswordConfirm)) {
					mNewPasswordConfirm.requestFocus();
					mNewPasswordConfirm.setError(getString(R.string.passwords_do_not_match));
				} else if (newPassword.trim().isEmpty()) {
					mNewPassword.requestFocus();
					mNewPassword.setError(getString(R.string.password_should_not_be_empty));
				} else {
					mCurrentPassword.setError(null);
					mNewPassword.setError(null);
					mNewPasswordConfirm.setError(null);
					xmppConnectionService.updateAccountPasswordOnServer(mAccount, newPassword, ChangePasswordActivity.this);
					mChangePasswordButton.setEnabled(false);
					mChangePasswordButton.setTextColor(getSecondaryTextColor());
					mChangePasswordButton.setText(R.string.updating);
				}
			}
		}
	};
	private TextView mCurrentPasswordLabel;
	private EditText mCurrentPassword;
	private EditText mNewPassword;
	private EditText mNewPasswordConfirm;
	private Account mAccount;

	@Override
	void onBackendConnected() {
		this.mAccount = extractAccount(getIntent());
		if (this.mAccount != null && this.mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
			this.mCurrentPasswordLabel.setVisibility(View.GONE);
			this.mCurrentPassword.setVisibility(View.GONE);
		} else {
			this.mCurrentPasswordLabel.setVisibility(View.VISIBLE);
			this.mCurrentPassword.setVisibility(View.VISIBLE);
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_change_password);
		Button mCancelButton = (Button) findViewById(R.id.left_button);
		mCancelButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				finish();
			}
		});
		this.mChangePasswordButton = (Button) findViewById(R.id.right_button);
		this.mChangePasswordButton.setOnClickListener(this.mOnChangePasswordButtonClicked);
		this.mCurrentPasswordLabel = (TextView) findViewById(R.id.current_password_label);
		this.mCurrentPassword = (EditText) findViewById(R.id.current_password);
		this.mNewPassword = (EditText) findViewById(R.id.new_password);
		this.mNewPasswordConfirm = (EditText) findViewById(R.id.new_password_confirm);
	}

	@Override
	protected void onStart() {
		super.onStart();
		Intent intent = getIntent();
		String password = intent != null ? intent.getStringExtra("password") : null;
		if (password != null) {
			this.mNewPassword.getEditableText().clear();
			this.mNewPassword.getEditableText().append(password);
		}
	}

	@Override
	public void onPasswordChangeSucceeded() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(ChangePasswordActivity.this,R.string.password_changed,Toast.LENGTH_LONG).show();
				finish();
			}
		});
	}

	@Override
	public void onPasswordChangeFailed() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mNewPassword.setError(getString(R.string.could_not_change_password));
				mChangePasswordButton.setEnabled(true);
				mChangePasswordButton.setTextColor(getPrimaryTextColor());
				mChangePasswordButton.setText(R.string.change_password);
			}
		});

	}

	public void refreshUiReal() {

	}
}
