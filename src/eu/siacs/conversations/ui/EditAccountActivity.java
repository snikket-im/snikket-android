package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.CompoundButton.OnCheckedChangeListener;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.utils.Validator;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class EditAccountActivity extends XmppActivity {

	private AutoCompleteTextView mAccountJid;
	private EditText mPassword;
	private EditText mPasswordConfirm;
	private CheckBox mRegisterNew;
	private Button mCancelButton;
	private Button mSaveButton;

	private String jidToEdit;
	private Account mAccount;
	private Avatar mAvatar = null;

	private boolean mUserInputIsValid = false;
	private boolean mFetchingAvatar = false;
	private boolean mFinishedInitialSetup = false;
	
	private OnClickListener mSaveButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			if (mAccount != null && mFinishedInitialSetup) {
				Intent intent;
				if (mAvatar!=null) {
					intent = new Intent(getApplicationContext(), StartConversationActivity.class);
				} else {
					intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
					intent.putExtra("account", mAccount.getJid());
				}
				startActivity(intent);
				finish();
				return;
			} else if (mAccount != null && mAccount.errorStatus()
					&& !mUserInputIsValid) {
				xmppConnectionService.reconnectAccount(mAccount, true);
				return;
			}
			boolean registerNewAccount = mRegisterNew.isChecked();
			String[] jidParts = mAccountJid.getText().toString().split("@");
			String username = jidParts[0];
			String server;
			if (jidParts.length >= 2) {
				server = jidParts[1];
			} else {
				server = "";
			}
			String password = mPassword.getText().toString();
			String passwordConfirm = mPasswordConfirm.getText().toString();
			if (registerNewAccount) {
				if (!password.equals(passwordConfirm)) {
					mPasswordConfirm
							.setError(getString(R.string.passwords_do_not_match));
					return;
				}
			}
			if (mAccount != null) {
				mAccount.setPassword(password);
				mAccount.setUsername(username);
				mAccount.setServer(server);
				mAccount.setOption(Account.OPTION_REGISTER, mRegisterNew.isChecked());
				xmppConnectionService.updateAccount(mAccount);
			} else {
				if (xmppConnectionService.findAccountByJid(mAccountJid.getText().toString())!=null) {
					mAccountJid.setError(getString(R.string.account_already_exists));
					return;
				}
				mAccount = new Account(username, server, password);
				mAccount.setOption(Account.OPTION_USETLS, true);
				mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
				if (registerNewAccount) {
					mAccount.setOption(Account.OPTION_REGISTER, true);
				}
				xmppConnectionService.createAccount(mAccount);
			}
			if (jidToEdit != null) {
				finish();
			} else {
				mUserInputIsValid = false;
				updateSaveButton();
				updateAccountInformation();
			}

		}
	};
	private OnClickListener mCancelButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			finish();
		}
	};
	private TextWatcher mTextWatcher = new TextWatcher() {

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			if (Validator.isValidJid(mAccountJid.getText().toString())) {
				mUserInputIsValid = inputDataDiffersFromAccount();
			} else {
				mUserInputIsValid = false;
			}
			updateSaveButton();
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		@Override
		public void afterTextChanged(Editable s) {
		}
	};
	private OnAccountUpdate mOnAccountUpdateListener = new OnAccountUpdate() {

		@Override
		public void onAccountUpdate() {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (jidToEdit==null && mAccount!=null && mAccount.getStatus() == Account.STATUS_ONLINE) {
						if (!mFetchingAvatar) {
							mFetchingAvatar = true;
							xmppConnectionService.checkForAvatar(mAccount, mAvatarFetchCallback);
						}
					} else {
						updateSaveButton();
					}
				}
			});
		}
	};
	private UiCallback<Avatar> mAvatarFetchCallback = new UiCallback<Avatar>() {
		
		@Override
		public void userInputRequried(PendingIntent pi, Avatar avatar) {
			finishInitialSetup(avatar);
		}
		
		@Override
		public void success(Avatar avatar) {
			finishInitialSetup(avatar);
		}
		
		@Override
		public void error(int errorCode, Avatar avatar) {
			finishInitialSetup(avatar);
		}
	};

	protected void finishInitialSetup(Avatar avatar) {
		this.mFinishedInitialSetup = true;
		this.mAvatar = avatar;
		runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				updateSaveButton();
			}
		});
	}
	
	protected boolean inputDataDiffersFromAccount() {
		if (mAccount == null) {
			return true;
		} else {
			return (!mAccount.getJid().equals(mAccountJid.getText().toString()))
					|| (!mAccount.getPassword().equals(
							mPassword.getText().toString()) || mAccount
							.isOptionSet(Account.OPTION_REGISTER) != mRegisterNew
							.isChecked());
		}
	}

	protected void updateSaveButton() {
		if (mAccount != null && mFinishedInitialSetup) {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
			this.mSaveButton.setText(R.string.next);
		} else if (mAccount != null
				&& mAccount.getStatus() == Account.STATUS_CONNECTING
				&& !mUserInputIsValid) {
			this.mSaveButton.setEnabled(false);
			this.mSaveButton.setTextColor(getSecondaryTextColor());
			this.mSaveButton.setText(R.string.account_status_connecting);
		} else if (mAccount != null && mAccount.errorStatus()
				&& !mUserInputIsValid) {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
			this.mSaveButton.setText(R.string.connect);
		} else if (mUserInputIsValid) {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
		} else {
			this.mSaveButton.setEnabled(false);
			this.mSaveButton.setTextColor(getSecondaryTextColor());
			this.mSaveButton.setText(R.string.save);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit_account);
		this.mAccountJid = (AutoCompleteTextView) findViewById(R.id.account_jid);
		this.mPassword = (EditText) findViewById(R.id.account_password);
		this.mPasswordConfirm = (EditText) findViewById(R.id.account_password_confirm);
		this.mRegisterNew = (CheckBox) findViewById(R.id.account_register_new);
		this.mSaveButton = (Button) findViewById(R.id.save_button);
		this.mCancelButton = (Button) findViewById(R.id.cancel_button);
		this.mSaveButton.setOnClickListener(this.mSaveButtonClickListener);
		this.mCancelButton.setOnClickListener(this.mCancelButtonClickListener);
		this.mRegisterNew
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if (isChecked) {
							mPasswordConfirm.setVisibility(View.VISIBLE);
						} else {
							mPasswordConfirm.setVisibility(View.GONE);
						}
						mUserInputIsValid = inputDataDiffersFromAccount();
						updateSaveButton();
					}
				});
		this.mAccountJid.addTextChangedListener(this.mTextWatcher);
		this.mPassword.addTextChangedListener(this.mTextWatcher);
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (getIntent() != null) {
			this.jidToEdit = getIntent().getStringExtra("jid");
			if (this.jidToEdit != null) {
				this.mRegisterNew.setVisibility(View.GONE);
				getActionBar().setTitle(R.string.mgmt_account_edit);
			} else {
				getActionBar().setTitle(R.string.action_add_account);
			}
		}
	}

	@Override
	protected void onBackendConnected() {
		this.xmppConnectionService
				.setOnAccountListChangedListener(this.mOnAccountUpdateListener);
		this.mAccountJid.setAdapter(null);
		if (this.jidToEdit != null) {
			this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
			updateAccountInformation();
		} else if (this.xmppConnectionService.getAccounts().size() == 0) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setDisplayShowHomeEnabled(false);
			this.mCancelButton.setEnabled(false);
		}
		this.mAccountJid.setAdapter(new KnownHostsAdapter(this,
				android.R.layout.simple_list_item_1, xmppConnectionService
						.getKnownHosts()));
		updateSaveButton();
	}

	private void updateAccountInformation() {
		this.mAccountJid.setText(this.mAccount.getJid());
		this.mPassword.setText(this.mAccount.getPassword());
		if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
			this.mRegisterNew.setVisibility(View.VISIBLE);
			this.mRegisterNew.setChecked(true);
			this.mPasswordConfirm.setText(this.mAccount.getPassword());
		} else {
			this.mRegisterNew.setVisibility(View.GONE);
			this.mRegisterNew.setChecked(false);
		}
	}
}
