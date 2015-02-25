package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.XmppConnection.Features;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class EditAccountActivity extends XmppActivity implements OnAccountUpdate{

	private AutoCompleteTextView mAccountJid;
	private EditText mPassword;
	private EditText mPasswordConfirm;
	private CheckBox mRegisterNew;
	private Button mCancelButton;
	private Button mSaveButton;
	private TableLayout mMoreTable;

	private LinearLayout mStats;
	private TextView mServerInfoSm;
	private TextView mServerInfoRosterVersion;
	private TextView mServerInfoCarbons;
	private TextView mServerInfoMam;
	private TextView mServerInfoCSI;
	private TextView mServerInfoBlocking;
	private TextView mServerInfoPep;
	private TextView mSessionEst;
	private TextView mOtrFingerprint;
	private ImageView mAvatar;
	private RelativeLayout mOtrFingerprintBox;
	private ImageButton mOtrFingerprintToClipboardButton;

	private Jid jidToEdit;
	private Account mAccount;

	private boolean mFetchingAvatar = false;

	private final OnClickListener mSaveButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(final View v) {
			if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED) {
				mAccount.setOption(Account.OPTION_DISABLED, false);
				xmppConnectionService.updateAccount(mAccount);
				return;
			}
			final boolean registerNewAccount = mRegisterNew.isChecked();
			final Jid jid;
			try {
				jid = Jid.fromString(mAccountJid.getText().toString());
			} catch (final InvalidJidException e) {
				mAccountJid.setError(getString(R.string.invalid_jid));
				mAccountJid.requestFocus();
				return;
			}
			if (jid.isDomainJid()) {
				mAccountJid.setError(getString(R.string.invalid_jid));
				mAccountJid.requestFocus();
				return;
			}
			final String password = mPassword.getText().toString();
			final String passwordConfirm = mPasswordConfirm.getText().toString();
			if (registerNewAccount) {
				if (!password.equals(passwordConfirm)) {
					mPasswordConfirm.setError(getString(R.string.passwords_do_not_match));
					mPasswordConfirm.requestFocus();
					return;
				}
			}
			if (mAccount != null) {
				try {
					mAccount.setUsername(jid.hasLocalpart() ? jid.getLocalpart() : "");
					mAccount.setServer(jid.getDomainpart());
				} catch (final InvalidJidException ignored) {
					return;
				}
				mAccount.setPassword(password);
				mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
				xmppConnectionService.updateAccount(mAccount);
			} else {
				try {
					if (xmppConnectionService.findAccountByJid(Jid.fromString(mAccountJid.getText().toString())) != null) {
						mAccountJid.setError(getString(R.string.account_already_exists));
						mAccountJid.requestFocus();
						return;
					}
				} catch (final InvalidJidException e) {
					return;
				}
				mAccount = new Account(jid.toBareJid(), password);
				mAccount.setOption(Account.OPTION_USETLS, true);
				mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
				mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
				xmppConnectionService.createAccount(mAccount);
			}
			if (jidToEdit != null) {
				finish();
			} else {
				updateSaveButton();
				updateAccountInformation();
			}

		}
	};
	private final OnClickListener mCancelButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(final View v) {
			finish();
		}
	};
	@Override
	public void onAccountUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				invalidateOptionsMenu();
				if (mAccount != null
						&& mAccount.getStatus() != Account.State.ONLINE
						&& mFetchingAvatar) {
					startActivity(new Intent(getApplicationContext(),
								ManageAccountActivity.class));
					finish();
				} else if (jidToEdit == null && mAccount != null
						&& mAccount.getStatus() == Account.State.ONLINE) {
					if (!mFetchingAvatar) {
						mFetchingAvatar = true;
						xmppConnectionService.checkForAvatar(mAccount,
								mAvatarFetchCallback);
					}
				} else {
					updateSaveButton();
				}
				if (mAccount != null) {
					updateAccountInformation();
				}
			}
		});
	}
	private final UiCallback<Avatar> mAvatarFetchCallback = new UiCallback<Avatar>() {

		@Override
		public void userInputRequried(final PendingIntent pi, final Avatar avatar) {
			finishInitialSetup(avatar);
		}

		@Override
		public void success(final Avatar avatar) {
			finishInitialSetup(avatar);
		}

		@Override
		public void error(final int errorCode, final Avatar avatar) {
			finishInitialSetup(avatar);
		}
	};
	private final TextWatcher mTextWatcher = new TextWatcher() {

		@Override
		public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
			updateSaveButton();
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
		}

		@Override
		public void afterTextChanged(final Editable s) {

		}
	};

	private final OnClickListener mAvatarClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			if (mAccount != null) {
				final Intent intent = new Intent(getApplicationContext(),
						PublishProfilePictureActivity.class);
				intent.putExtra("account", mAccount.getJid().toBareJid().toString());
				startActivity(intent);
			}
		}
	};

	protected void finishInitialSetup(final Avatar avatar) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				final Intent intent;
				if (avatar != null) {
					intent = new Intent(getApplicationContext(),
							StartConversationActivity.class);
					intent.putExtra("init",true);
				} else {
					intent = new Intent(getApplicationContext(),
							PublishProfilePictureActivity.class);
					intent.putExtra("account", mAccount.getJid().toBareJid().toString());
					intent.putExtra("setup", true);
				}
				startActivity(intent);
				finish();
			}
		});
	}

	protected void updateSaveButton() {
		if (mAccount != null && (mAccount.getStatus() == Account.State.CONNECTING || mFetchingAvatar)) {
			this.mSaveButton.setEnabled(false);
			this.mSaveButton.setTextColor(getSecondaryTextColor());
			this.mSaveButton.setText(R.string.account_status_connecting);
		} else if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED) {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
			this.mSaveButton.setText(R.string.enable);
		} else {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setTextColor(getPrimaryTextColor());
			if (jidToEdit != null) {
				if (mAccount != null && mAccount.isOnlineAndConnected()) {
					this.mSaveButton.setText(R.string.save);
					if (!accountInfoEdited()) {
						this.mSaveButton.setEnabled(false);
						this.mSaveButton.setTextColor(getSecondaryTextColor());
					}
				} else {
					this.mSaveButton.setText(R.string.connect);
				}
			} else {
				this.mSaveButton.setText(R.string.next);
			}
		}
	}

	protected boolean accountInfoEdited() {
		return (!this.mAccount.getJid().toBareJid().toString().equals(
					this.mAccountJid.getText().toString()))
			|| (!this.mAccount.getPassword().equals(
						this.mPassword.getText().toString()));
	}

	@Override
	protected String getShareableUri() {
		if (mAccount!=null) {
			return mAccount.getShareableUri();
		} else {
			return "";
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit_account);
		this.mAccountJid = (AutoCompleteTextView) findViewById(R.id.account_jid);
		this.mAccountJid.addTextChangedListener(this.mTextWatcher);
		this.mPassword = (EditText) findViewById(R.id.account_password);
		this.mPassword.addTextChangedListener(this.mTextWatcher);
		this.mPasswordConfirm = (EditText) findViewById(R.id.account_password_confirm);
		this.mAvatar = (ImageView) findViewById(R.id.avater);
		this.mAvatar.setOnClickListener(this.mAvatarClickListener);
		this.mRegisterNew = (CheckBox) findViewById(R.id.account_register_new);
		this.mStats = (LinearLayout) findViewById(R.id.stats);
		this.mSessionEst = (TextView) findViewById(R.id.session_est);
		this.mServerInfoRosterVersion = (TextView) findViewById(R.id.server_info_roster_version);
		this.mServerInfoCarbons = (TextView) findViewById(R.id.server_info_carbons);
		this.mServerInfoMam = (TextView) findViewById(R.id.server_info_mam);
		this.mServerInfoCSI = (TextView) findViewById(R.id.server_info_csi);
		this.mServerInfoBlocking = (TextView) findViewById(R.id.server_info_blocking);
		this.mServerInfoSm = (TextView) findViewById(R.id.server_info_sm);
		this.mServerInfoPep = (TextView) findViewById(R.id.server_info_pep);
		this.mOtrFingerprint = (TextView) findViewById(R.id.otr_fingerprint);
		this.mOtrFingerprintBox = (RelativeLayout) findViewById(R.id.otr_fingerprint_box);
		this.mOtrFingerprintToClipboardButton = (ImageButton) findViewById(R.id.action_copy_to_clipboard);
		this.mSaveButton = (Button) findViewById(R.id.save_button);
		this.mCancelButton = (Button) findViewById(R.id.cancel_button);
		this.mSaveButton.setOnClickListener(this.mSaveButtonClickListener);
		this.mCancelButton.setOnClickListener(this.mCancelButtonClickListener);
		this.mMoreTable = (TableLayout) findViewById(R.id.server_info_more);
		final OnCheckedChangeListener OnCheckedShowConfirmPassword = new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView,
					final boolean isChecked) {
				if (isChecked) {
					mPasswordConfirm.setVisibility(View.VISIBLE);
				} else {
					mPasswordConfirm.setVisibility(View.GONE);
				}
				updateSaveButton();
			}
		};
		this.mRegisterNew.setOnCheckedChangeListener(OnCheckedShowConfirmPassword);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.editaccount, menu);
		final MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);
		final MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
		final MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
		final MenuItem changePassword = menu.findItem(R.id.action_change_password_on_server);
		if (mAccount == null) {
			showQrCode.setVisible(false);
			showBlocklist.setVisible(false);
			showMoreInfo.setVisible(false);
			changePassword.setVisible(false);
		} else if (mAccount.getStatus() != Account.State.ONLINE) {
			showBlocklist.setVisible(false);
			showMoreInfo.setVisible(false);
			changePassword.setVisible(false);
		} else if (!mAccount.getXmppConnection().getFeatures().blocking()) {
			showBlocklist.setVisible(false);
		}
		return true;
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (getIntent() != null) {
			try {
				this.jidToEdit = Jid.fromString(getIntent().getStringExtra("jid"));
			} catch (final InvalidJidException | NullPointerException ignored) {
				this.jidToEdit = null;
			}
			if (this.jidToEdit != null) {
				this.mRegisterNew.setVisibility(View.GONE);
				if (getActionBar() != null) {
					getActionBar().setTitle(getString(R.string.account_details));
				}
			} else {
				this.mAvatar.setVisibility(View.GONE);
				if (getActionBar() != null) {
					getActionBar().setTitle(R.string.action_add_account);
				}
			}
		}
	}

	@Override
	protected void onBackendConnected() {
		final KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
				android.R.layout.simple_list_item_1,
				xmppConnectionService.getKnownHosts());
		if (this.jidToEdit != null) {
			this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
			updateAccountInformation();
		} else if (this.xmppConnectionService.getAccounts().size() == 0) {
			if (getActionBar() != null) {
				getActionBar().setDisplayHomeAsUpEnabled(false);
				getActionBar().setDisplayShowHomeEnabled(false);
				getActionBar().setHomeButtonEnabled(false);
			}
			this.mCancelButton.setEnabled(false);
			this.mCancelButton.setTextColor(getSecondaryTextColor());
		}
		this.mAccountJid.setAdapter(mKnownHostsAdapter);
		updateSaveButton();
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_show_block_list:
				final Intent showBlocklistIntent = new Intent(this, BlocklistActivity.class);
				showBlocklistIntent.putExtra("account", mAccount.getJid().toString());
				startActivity(showBlocklistIntent);
				break;
			case R.id.action_server_info_show_more:
				mMoreTable.setVisibility(item.isChecked() ? View.GONE : View.VISIBLE);
				item.setChecked(!item.isChecked());
				break;
			case R.id.action_change_password_on_server:
				final Intent changePasswordIntent = new Intent(this, ChangePasswordActivity.class);
				changePasswordIntent.putExtra("account", mAccount.getJid().toString());
				startActivity(changePasswordIntent);
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void updateAccountInformation() {
		this.mAccountJid.setText(this.mAccount.getJid().toBareJid().toString());
		this.mPassword.setText(this.mAccount.getPassword());
		if (this.jidToEdit != null) {
			this.mAvatar.setVisibility(View.VISIBLE);
			this.mAvatar.setImageBitmap(avatarService().get(this.mAccount, getPixel(72)));
		}
		if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
			this.mRegisterNew.setVisibility(View.VISIBLE);
			this.mRegisterNew.setChecked(true);
			this.mPasswordConfirm.setText(this.mAccount.getPassword());
		} else {
			this.mRegisterNew.setVisibility(View.GONE);
			this.mRegisterNew.setChecked(false);
		}
		if (this.mAccount.isOnlineAndConnected() && !this.mFetchingAvatar) {
			this.mStats.setVisibility(View.VISIBLE);
			this.mSessionEst.setText(UIHelper.readableTimeDifferenceFull(this, this.mAccount.getXmppConnection()
						.getLastSessionEstablished()));
			Features features = this.mAccount.getXmppConnection().getFeatures();
			if (features.rosterVersioning()) {
				this.mServerInfoRosterVersion.setText(R.string.server_info_available);
			} else {
				this.mServerInfoRosterVersion.setText(R.string.server_info_unavailable);
			}
			if (features.carbons()) {
				this.mServerInfoCarbons.setText(R.string.server_info_available);
			} else {
				this.mServerInfoCarbons
					.setText(R.string.server_info_unavailable);
			}
			if (features.mam()) {
				this.mServerInfoMam.setText(R.string.server_info_available);
			} else {
				this.mServerInfoMam.setText(R.string.server_info_unavailable);
			}
			if (features.csi()) {
				this.mServerInfoCSI.setText(R.string.server_info_available);
			} else {
				this.mServerInfoCSI.setText(R.string.server_info_unavailable);
			}
			if (features.blocking()) {
				this.mServerInfoBlocking.setText(R.string.server_info_available);
			} else {
				this.mServerInfoBlocking.setText(R.string.server_info_unavailable);
			}
			if (features.sm()) {
				this.mServerInfoSm.setText(R.string.server_info_available);
			} else {
				this.mServerInfoSm.setText(R.string.server_info_unavailable);
			}
			if (features.pubsub()) {
				this.mServerInfoPep.setText(R.string.server_info_available);
			} else {
				this.mServerInfoPep.setText(R.string.server_info_unavailable);
			}
			final String fingerprint = this.mAccount.getOtrFingerprint();
			if (fingerprint != null) {
				this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
				this.mOtrFingerprint.setText(CryptoHelper.prettifyFingerprint(fingerprint));
				this.mOtrFingerprintToClipboardButton
					.setVisibility(View.VISIBLE);
				this.mOtrFingerprintToClipboardButton
					.setOnClickListener(new View.OnClickListener() {

						@Override
						public void onClick(final View v) {

							if (copyTextToClipboard(fingerprint, R.string.otr_fingerprint)) {
								Toast.makeText(
										EditAccountActivity.this,
										R.string.toast_message_otr_fingerprint,
										Toast.LENGTH_SHORT).show();
							}
						}
					});
			} else {
				this.mOtrFingerprintBox.setVisibility(View.GONE);
			}
		} else {
			if (this.mAccount.errorStatus()) {
				this.mAccountJid.setError(getString(this.mAccount.getStatus().getReadableId()));
				this.mAccountJid.requestFocus();
			}
			this.mStats.setVisibility(View.GONE);
		}
	}
}
