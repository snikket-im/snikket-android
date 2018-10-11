package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
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

import org.openintents.openpgp.util.OpenPgpUtils;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.databinding.ActivityEditAccountBinding;
import eu.siacs.conversations.databinding.DialogPresenceBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.services.BarcodeProvider;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnCaptchaRequested;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.ui.adapter.PresenceTemplateAdapter;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.XmppConnection.Features;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.pep.Avatar;
import rocks.xmpp.addr.Jid;

public class EditAccountActivity extends OmemoActivity implements OnAccountUpdate, OnUpdateBlocklist,
		OnKeyStatusUpdated, OnCaptchaRequested, KeyChainAliasCallback, XmppConnectionService.OnShowErrorToast, XmppConnectionService.OnMamPreferencesFetched {

	private static final int REQUEST_DATA_SAVER = 0xf244;
	private static final int REQUEST_CHANGE_STATUS = 0xee11;
	private TextInputLayout mAccountJidLayout;
	private EditText mPassword;
	private TextInputLayout mPasswordLayout;
	private Button mCancelButton;
	private Button mSaveButton;
	private Button mDisableOsOptimizationsButton;
	private TextView getmDisableOsOptimizationsBody;
	private TableLayout mMoreTable;

	private TextView mAxolotlFingerprint;
	private TextView mPgpFingerprint;
	private TextView mOwnFingerprintDesc;
	private TextView getmPgpFingerprintDesc;
	private ImageView mAvatar;
	private RelativeLayout mAxolotlFingerprintBox;
	private RelativeLayout mPgpFingerprintBox;
	private ImageButton mAxolotlFingerprintToClipboardButton;
	private ImageButton mPgpDeleteFingerprintButton;
	private LinearLayout keys;
	private EditText mPort;
	private AlertDialog mCaptchaDialog = null;

	private Jid jidToEdit;
	private boolean mInitMode = false;
	private boolean mUsernameMode = Config.DOMAIN_LOCK != null;
	private boolean mShowOptions = false;
	private Account mAccount;
	private String messageFingerprint;

	private final PendingItem<PresenceTemplate> mPendingPresenceTemplate = new PendingItem<>();

	private boolean mFetchingAvatar = false;

	private final OnClickListener mSaveButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(final View v) {
			final String password = mPassword.getText().toString();
			final boolean wasDisabled = mAccount != null && mAccount.getStatus() == Account.State.DISABLED;
			final boolean accountInfoEdited = accountInfoEdited();

			if (!mInitMode && passwordChangedInMagicCreateMode()) {
				gotoChangePassword(password);
				return;
			}
			if (mInitMode && mAccount != null) {
				mAccount.setOption(Account.OPTION_DISABLED, false);
			}
			if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !accountInfoEdited) {
				mAccount.setOption(Account.OPTION_DISABLED, false);
				if (!xmppConnectionService.updateAccount(mAccount)) {
					Toast.makeText(EditAccountActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
				}
				return;
			}
			final boolean registerNewAccount = binding.accountRegisterNew.isChecked() && !Config.DISALLOW_REGISTRATION_IN_UI;
			if (mUsernameMode && binding.accountJid.getText().toString().contains("@")) {
				mAccountJidLayout.setError(getString(R.string.invalid_username));
				removeErrorsOnAllBut(mAccountJidLayout);
				binding.accountJid.requestFocus();
				return;
			}

			XmppConnection connection = mAccount == null ? null : mAccount.getXmppConnection();
			boolean openRegistrationUrl = registerNewAccount && !accountInfoEdited && mAccount != null && mAccount.getStatus() == Account.State.REGISTRATION_WEB;
			boolean openPaymentUrl = mAccount != null && mAccount.getStatus() == Account.State.PAYMENT_REQUIRED;
			final boolean redirectionWorthyStatus = openPaymentUrl || openRegistrationUrl;
			URL url = connection != null && redirectionWorthyStatus ? connection.getRedirectionUrl() : null;
			if (url != null && !wasDisabled) {
				try {
					startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())));
					return;
				} catch (ActivityNotFoundException e) {
					Toast.makeText(EditAccountActivity.this, R.string.application_found_to_open_website, Toast.LENGTH_SHORT).show();
					return;
				}
			}

			final Jid jid;
			try {
				if (mUsernameMode) {
					jid = Jid.of(binding.accountJid.getText().toString(), getUserModeDomain(), null);
				} else {
					jid = Jid.of(binding.accountJid.getText().toString());
				}
			} catch (final NullPointerException | IllegalArgumentException e) {
				if (mUsernameMode) {
					mAccountJidLayout.setError(getString(R.string.invalid_username));
				} else {
					mAccountJidLayout.setError(getString(R.string.invalid_jid));
				}
				binding.accountJid.requestFocus();
				removeErrorsOnAllBut(mAccountJidLayout);
				return;
			}
			String hostname = null;
			int numericPort = 5222;
			if (mShowOptions) {
				hostname = binding.hostname.getText().toString().replaceAll("\\s", "");
				final String port = mPort.getText().toString().replaceAll("\\s", "");
				if (hostname.contains(" ")) {
					binding.hostnameLayout.setError(getString(R.string.not_valid_hostname));
					binding.hostname.requestFocus();
					removeErrorsOnAllBut(binding.hostnameLayout);
					return;
				}
				try {
					numericPort = Integer.parseInt(port);
					if (numericPort < 0 || numericPort > 65535) {
						binding.portLayout.setError(getString(R.string.not_a_valid_port));
						removeErrorsOnAllBut(binding.portLayout);
						mPort.requestFocus();
						return;
					}

				} catch (NumberFormatException e) {
					binding.portLayout.setError(getString(R.string.not_a_valid_port));
					removeErrorsOnAllBut(binding.portLayout);
					mPort.requestFocus();
					return;
				}
			}

			if (jid.getLocal() == null) {
				if (mUsernameMode) {
					mAccountJidLayout.setError(getString(R.string.invalid_username));
				} else {
					mAccountJidLayout.setError(getString(R.string.invalid_jid));
				}
				removeErrorsOnAllBut(mAccountJidLayout);
				binding.accountJid.requestFocus();
				return;
			}
			if (mAccount != null) {
				if (mInitMode && mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
					mAccount.setOption(Account.OPTION_MAGIC_CREATE, mAccount.getPassword().contains(password));
				}
				mAccount.setJid(jid);
				mAccount.setPort(numericPort);
				mAccount.setHostname(hostname);
				mAccountJidLayout.setError(null);
				mAccount.setPassword(password);
				mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
				if (!xmppConnectionService.updateAccount(mAccount)) {
					Toast.makeText(EditAccountActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
					return;
				}
			} else {
				if (xmppConnectionService.findAccountByJid(jid) != null) {
					mAccountJidLayout.setError(getString(R.string.account_already_exists));
					removeErrorsOnAllBut(mAccountJidLayout);
					binding.accountJid.requestFocus();
					return;
				}
				mAccount = new Account(jid.asBareJid(), password);
				mAccount.setPort(numericPort);
				mAccount.setHostname(hostname);
				mAccount.setOption(Account.OPTION_USETLS, true);
				mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
				mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
				xmppConnectionService.createAccount(mAccount);
			}
			binding.hostnameLayout.setError(null);
			binding.portLayout.setError(null);
			if (mAccount.isEnabled()
					&& !registerNewAccount
					&& !mInitMode) {
				finish();
			} else {
				updateSaveButton();
				updateAccountInformation(true);
			}

		}
	};
	private final OnClickListener mCancelButtonClickListener = new OnClickListener() {

		@Override
		public void onClick(final View v) {
			deleteAccountAndReturnIfNecessary();
			finish();
		}
	};
	private Toast mFetchingMamPrefsToast;
	private String mSavedInstanceAccount;
	private boolean mSavedInstanceInit = false;
	private Button mClearDevicesButton;
	private XmppUri pendingUri = null;
	private boolean mUseTor;
	private ActivityEditAccountBinding binding;

	public void refreshUiReal() {
		invalidateOptionsMenu();
		if (mAccount != null
				&& mAccount.getStatus() != Account.State.ONLINE
				&& mFetchingAvatar) {
			//TODO: maybe better redirect to StartConversationActivity
			startActivity(new Intent(this, ManageAccountActivity.class));
			finish();
		} else if (mInitMode && mAccount != null && mAccount.getStatus() == Account.State.ONLINE) {
			if (!mFetchingAvatar) {
				mFetchingAvatar = true;
				xmppConnectionService.checkForAvatar(mAccount, mAvatarFetchCallback);
			}
		}
		if (mAccount != null) {
			updateAccountInformation(false);
		}
		updateSaveButton();
	}

	@Override
	public boolean onNavigateUp() {
		deleteAccountAndReturnIfNecessary();
		return super.onNavigateUp();
	}

	@Override
	public void onBackPressed() {
		deleteAccountAndReturnIfNecessary();
		super.onBackPressed();
	}

	private void deleteAccountAndReturnIfNecessary() {
		if (mInitMode && mAccount != null && !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
			xmppConnectionService.deleteAccount(mAccount);
		}

		if (xmppConnectionService.getAccounts().size() == 0 && Config.MAGIC_CREATE_DOMAIN != null) {
			Intent intent = new Intent(EditAccountActivity.this, WelcomeActivity.class);
			WelcomeActivity.addInviteUri(intent, getIntent());
			startActivity(intent);
		}
	}

	@Override
	public void onAccountUpdate() {
		refreshUi();
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
			updatePortLayout();
			updateSaveButton();
		}

		@Override
		public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
		}

		@Override
		public void afterTextChanged(final Editable s) {

		}
	};

	private View.OnFocusChangeListener mEditTextFocusListener = new View.OnFocusChangeListener() {
		@Override
		public void onFocusChange(View view, boolean b) {
			EditText et = (EditText) view;
			if (b) {
				int resId = mUsernameMode ? R.string.username : R.string.account_settings_example_jabber_id;
				if (view.getId() == R.id.hostname) {
					resId = mUseTor ? R.string.hostname_or_onion : R.string.hostname_example;
				}
				final int res = resId;
				new Handler().postDelayed(() -> et.setHint(res), 200);
			} else {
				et.setHint(null);
			}
		}
	};


	private final OnClickListener mAvatarClickListener = new OnClickListener() {
		@Override
		public void onClick(final View view) {
			if (mAccount != null) {
				final Intent intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
				intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toString());
				startActivity(intent);
			}
		}
	};

	protected void finishInitialSetup(final Avatar avatar) {
		runOnUiThread(() -> {
			SoftKeyboardUtils.hideSoftKeyboard(EditAccountActivity.this);
			final Intent intent;
			final XmppConnection connection = mAccount.getXmppConnection();
			final boolean wasFirstAccount = xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1;
			if (avatar != null || (connection != null && !connection.getFeatures().pep())) {
				intent = new Intent(getApplicationContext(), StartConversationActivity.class);
				if (wasFirstAccount) {
					intent.putExtra("init", true);
				}
			} else {
				intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
				intent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().asBareJid().toString());
				intent.putExtra("setup", true);
			}
			if (wasFirstAccount) {
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			}
			WelcomeActivity.addInviteUri(intent, getIntent());
			startActivity(intent);
			finish();
		});
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_BATTERY_OP || requestCode == REQUEST_DATA_SAVER) {
			updateAccountInformation(mAccount == null);
		}
		if (requestCode == REQUEST_CHANGE_STATUS) {
			PresenceTemplate template = mPendingPresenceTemplate.pop();
			if (template != null && resultCode == Activity.RESULT_OK) {
				generateSignature(data, template);
			} else {
				Log.d(Config.LOGTAG, "pgp result not ok");
			}
		}
	}

	@Override
	protected void processFingerprintVerification(XmppUri uri) {
		processFingerprintVerification(uri, true);
	}


	protected void processFingerprintVerification(XmppUri uri, boolean showWarningToast) {
		if (mAccount != null && mAccount.getJid().asBareJid().equals(uri.getJid()) && uri.hasFingerprints()) {
			if (xmppConnectionService.verifyFingerprints(mAccount, uri.getFingerprints())) {
				Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
				updateAccountInformation(false);
			}
		} else if (showWarningToast) {
			Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
		}
	}

	private void updatePortLayout() {
		String hostname = this.binding.hostname.getText().toString();
		this.binding.portLayout.setEnabled(!TextUtils.isEmpty(hostname));
	}

	protected void updateSaveButton() {
		boolean accountInfoEdited = accountInfoEdited();

		if (!mInitMode && passwordChangedInMagicCreateMode()) {
			this.mSaveButton.setText(R.string.change_password);
			this.mSaveButton.setEnabled(true);
		} else if (accountInfoEdited && !mInitMode) {
			this.mSaveButton.setText(R.string.save);
			this.mSaveButton.setEnabled(true);
		} else if (mAccount != null
				&& (mAccount.getStatus() == Account.State.CONNECTING || mAccount.getStatus() == Account.State.REGISTRATION_SUCCESSFUL || mFetchingAvatar)) {
			this.mSaveButton.setEnabled(false);
			this.mSaveButton.setText(R.string.account_status_connecting);
		} else if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !mInitMode) {
			this.mSaveButton.setEnabled(true);
			this.mSaveButton.setText(R.string.enable);
		} else {
			this.mSaveButton.setEnabled(true);
			if (!mInitMode) {
				if (mAccount != null && mAccount.isOnlineAndConnected()) {
					this.mSaveButton.setText(R.string.save);
					if (!accountInfoEdited) {
						this.mSaveButton.setEnabled(false);
					}
				} else {
					XmppConnection connection = mAccount == null ? null : mAccount.getXmppConnection();
					URL url = connection != null && mAccount.getStatus() == Account.State.PAYMENT_REQUIRED ? connection.getRedirectionUrl() : null;
					if (url != null) {
						this.mSaveButton.setText(R.string.open_website);
					} else {
						this.mSaveButton.setText(R.string.connect);
					}
				}
			} else {
				XmppConnection connection = mAccount == null ? null : mAccount.getXmppConnection();
				URL url = connection != null && mAccount.getStatus() == Account.State.REGISTRATION_WEB ? connection.getRedirectionUrl() : null;
				if (url != null && this.binding.accountRegisterNew.isChecked() && !accountInfoEdited) {
					this.mSaveButton.setText(R.string.open_website);
				} else {
					this.mSaveButton.setText(R.string.next);
				}
			}
		}
	}

	protected boolean accountInfoEdited() {
		if (this.mAccount == null) {
			return false;
		}
		return jidEdited() ||
				!this.mAccount.getPassword().equals(this.mPassword.getText().toString()) ||
				!this.mAccount.getHostname().equals(this.binding.hostname.getText().toString()) ||
				!String.valueOf(this.mAccount.getPort()).equals(this.mPort.getText().toString());
	}

	protected boolean jidEdited() {
		final String unmodified;
		if (mUsernameMode) {
			unmodified = this.mAccount.getJid().getLocal();
		} else {
			unmodified = this.mAccount.getJid().asBareJid().toString();
		}
		return !unmodified.equals(this.binding.accountJid.getText().toString());
	}

	protected boolean passwordChangedInMagicCreateMode() {
		return mAccount != null
				&& mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)
				&& !this.mAccount.getPassword().equals(this.mPassword.getText().toString())
				&& !this.jidEdited()
				&& mAccount.isOnlineAndConnected();
	}

	@Override
	protected String getShareableUri(boolean http) {
		if (mAccount != null) {
			return http ? mAccount.getShareableLink() : mAccount.getShareableUri();
		} else {
			return null;
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {
			this.mSavedInstanceAccount = savedInstanceState.getString("account");
			this.mSavedInstanceInit = savedInstanceState.getBoolean("initMode", false);
		}
		this.binding = DataBindingUtil.setContentView(this, R.layout.activity_edit_account);
		setSupportActionBar((Toolbar) binding.toolbar);
		configureActionBar(getSupportActionBar());
		binding.accountJid.addTextChangedListener(this.mTextWatcher);
		binding.accountJid.setOnFocusChangeListener(this.mEditTextFocusListener);
		this.mAccountJidLayout = (TextInputLayout) findViewById(R.id.account_jid_layout);
		this.mPassword = (EditText) findViewById(R.id.account_password);
		this.mPassword.addTextChangedListener(this.mTextWatcher);
		this.mPasswordLayout = (TextInputLayout) findViewById(R.id.account_password_layout);
		this.mAvatar = (ImageView) findViewById(R.id.avater);
		this.mAvatar.setOnClickListener(this.mAvatarClickListener);
		this.mDisableOsOptimizationsButton = (Button) findViewById(R.id.os_optimization_disable);
		this.getmDisableOsOptimizationsBody = (TextView) findViewById(R.id.os_optimization_body);
		this.mPgpFingerprintBox = (RelativeLayout) findViewById(R.id.pgp_fingerprint_box);
		this.mPgpFingerprint = (TextView) findViewById(R.id.pgp_fingerprint);
		this.getmPgpFingerprintDesc = (TextView) findViewById(R.id.pgp_fingerprint_desc);
		this.mPgpDeleteFingerprintButton = (ImageButton) findViewById(R.id.action_delete_pgp);
		this.mAxolotlFingerprint = (TextView) findViewById(R.id.axolotl_fingerprint);
		this.mAxolotlFingerprintBox = (RelativeLayout) findViewById(R.id.axolotl_fingerprint_box);
		this.mAxolotlFingerprintToClipboardButton = (ImageButton) findViewById(R.id.action_copy_axolotl_to_clipboard);
		this.mOwnFingerprintDesc = (TextView) findViewById(R.id.own_fingerprint_desc);
		this.keys = findViewById(R.id.other_device_keys);
		this.binding.hostname.addTextChangedListener(mTextWatcher);
		this.binding.hostname.setOnFocusChangeListener(mEditTextFocusListener);
		this.mClearDevicesButton = (Button) findViewById(R.id.clear_devices);
		this.mClearDevicesButton.setOnClickListener(v -> showWipePepDialog());
		this.mPort = (EditText) findViewById(R.id.port);
		this.mPort.setText("5222");
		this.mPort.addTextChangedListener(mTextWatcher);
		this.mSaveButton = (Button) findViewById(R.id.save_button);
		this.mCancelButton = (Button) findViewById(R.id.cancel_button);
		this.mSaveButton.setOnClickListener(this.mSaveButtonClickListener);
		this.mCancelButton.setOnClickListener(this.mCancelButtonClickListener);
		this.mMoreTable = (TableLayout) findViewById(R.id.server_info_more);
		if (savedInstanceState != null && savedInstanceState.getBoolean("showMoreTable")) {
			changeMoreTableVisibility(true);
		}
		final OnCheckedChangeListener OnCheckedShowConfirmPassword = new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
				updateSaveButton();
			}
		};
		this.binding.accountRegisterNew.setOnCheckedChangeListener(OnCheckedShowConfirmPassword);
		if (Config.DISALLOW_REGISTRATION_IN_UI) {
			this.binding.accountRegisterNew.setVisibility(View.GONE);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.editaccount, menu);
		final MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
		final MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
		final MenuItem changePassword = menu.findItem(R.id.action_change_password_on_server);
		final MenuItem renewCertificate = menu.findItem(R.id.action_renew_certificate);
		final MenuItem mamPrefs = menu.findItem(R.id.action_mam_prefs);
		final MenuItem changePresence = menu.findItem(R.id.action_change_presence);
		final MenuItem share = menu.findItem(R.id.action_share);
		renewCertificate.setVisible(mAccount != null && mAccount.getPrivateKeyAlias() != null);

		share.setVisible(mAccount != null && !mInitMode);

		if (mAccount != null && mAccount.isOnlineAndConnected()) {
			if (!mAccount.getXmppConnection().getFeatures().blocking()) {
				showBlocklist.setVisible(false);
			}

			if (!mAccount.getXmppConnection().getFeatures().register()) {
				changePassword.setVisible(false);
			}
			mamPrefs.setVisible(mAccount.getXmppConnection().getFeatures().mam());
			changePresence.setVisible(!mInitMode);
		} else {
			showBlocklist.setVisible(false);
			showMoreInfo.setVisible(false);
			changePassword.setVisible(false);
			mamPrefs.setVisible(false);
			changePresence.setVisible(false);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		final MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
		if (showMoreInfo.isVisible()) {
			showMoreInfo.setChecked(mMoreTable.getVisibility() == View.VISIBLE);
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	protected void onStart() {
		super.onStart();
		final Intent intent = getIntent();
		final int theme = findTheme();
		if (this.mTheme != theme) {
			recreate();
		} else if (intent != null) {
			try {
				this.jidToEdit = Jid.of(intent.getStringExtra("jid"));
			} catch (final IllegalArgumentException | NullPointerException ignored) {
				this.jidToEdit = null;
			}
			if (jidToEdit != null && intent.getData() != null && intent.getBooleanExtra("scanned", false)) {
				final XmppUri uri = new XmppUri(intent.getData());
				if (xmppConnectionServiceBound) {
					processFingerprintVerification(uri, false);
				} else {
					this.pendingUri = uri;
				}
			}
			boolean init = intent.getBooleanExtra("init", false);
			this.mInitMode = init || this.jidToEdit == null;
			this.messageFingerprint = intent.getStringExtra("fingerprint");
			if (!mInitMode) {
				this.binding.accountRegisterNew.setVisibility(View.GONE);
				if (getSupportActionBar() != null) {
					getSupportActionBar().setTitle(getString(R.string.account_details));
				}
			} else {
				this.mAvatar.setVisibility(View.GONE);
				ActionBar ab = getSupportActionBar();
				if (ab != null) {
					if (init && Config.MAGIC_CREATE_DOMAIN == null) {
						ab.setDisplayShowHomeEnabled(false);
						ab.setDisplayHomeAsUpEnabled(false);
					}
					ab.setTitle(R.string.action_add_account);
				}
			}
		}
		SharedPreferences preferences = getPreferences();
		mUseTor = Config.FORCE_ORBOT || preferences.getBoolean("use_tor", false);
		this.mShowOptions = mUseTor || preferences.getBoolean("show_connection_options", false);
		this.binding.namePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);
	}

	@Override
	public void onNewIntent(Intent intent) {
		if (intent != null && intent.getData() != null) {
			final XmppUri uri = new XmppUri(intent.getData());
			if (xmppConnectionServiceBound) {
				processFingerprintVerification(uri, false);
			} else {
				this.pendingUri = uri;
			}
		}
	}

	@Override
	public void onSaveInstanceState(final Bundle savedInstanceState) {
		if (mAccount != null) {
			savedInstanceState.putString("account", mAccount.getJid().asBareJid().toString());
			savedInstanceState.putBoolean("initMode", mInitMode);
			savedInstanceState.putBoolean("showMoreTable", mMoreTable.getVisibility() == View.VISIBLE);
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	protected void onBackendConnected() {
		boolean init = true;
		if (mSavedInstanceAccount != null) {
			try {
				this.mAccount = xmppConnectionService.findAccountByJid(Jid.of(mSavedInstanceAccount));
				this.mInitMode = mSavedInstanceInit;
				init = false;
			} catch (IllegalArgumentException e) {
				this.mAccount = null;
			}

		} else if (this.jidToEdit != null) {
			this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
		}

		if (mAccount != null) {
			this.mInitMode |= this.mAccount.isOptionSet(Account.OPTION_REGISTER);
			this.mUsernameMode |= mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) && mAccount.isOptionSet(Account.OPTION_REGISTER);
			if (this.mAccount.getPrivateKeyAlias() != null) {
				this.mPassword.setHint(R.string.authenticate_with_certificate);
				if (this.mInitMode) {
					this.mPassword.requestFocus();
				}
			}
			if (mPendingFingerprintVerificationUri != null) {
				processFingerprintVerification(mPendingFingerprintVerificationUri, false);
				mPendingFingerprintVerificationUri = null;
			}
			updateAccountInformation(init);
		}


		if (Config.MAGIC_CREATE_DOMAIN == null && this.xmppConnectionService.getAccounts().size() == 0) {
			this.mCancelButton.setEnabled(false);
		}
		if (mUsernameMode) {
			this.binding.accountJidLayout.setHint(getString(R.string.username_hint));
			this.binding.accountJid.setHint(R.string.username_hint);
		} else {
			final KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
					R.layout.simple_list_item,
					xmppConnectionService.getKnownHosts());
			this.binding.accountJid.setAdapter(mKnownHostsAdapter);
		}

		if (pendingUri != null) {
			processFingerprintVerification(pendingUri, false);
			pendingUri = null;
		}
		updatePortLayout();
		updateSaveButton();
		invalidateOptionsMenu();
	}

	private String getUserModeDomain() {
		if (mAccount != null && mAccount.getJid().getDomain() != null) {
			return mAccount.getJid().getDomain();
		} else {
			return Config.DOMAIN_LOCK;
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (MenuDoubleTabUtil.shouldIgnoreTap()) {
			return false;
		}
		switch (item.getItemId()) {
			case R.id.action_show_block_list:
				final Intent showBlocklistIntent = new Intent(this, BlocklistActivity.class);
				showBlocklistIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
				startActivity(showBlocklistIntent);
				break;
			case R.id.action_server_info_show_more:
				changeMoreTableVisibility(!item.isChecked());
				break;
			case R.id.action_share_barcode:
				shareBarcode();
				break;
			case R.id.action_share_http:
				shareLink(true);
				break;
			case R.id.action_share_uri:
				shareLink(false);
				break;
			case R.id.action_change_password_on_server:
				gotoChangePassword(null);
				break;
			case R.id.action_mam_prefs:
				editMamPrefs();
				break;
			case R.id.action_renew_certificate:
				renewCertificate();
				break;
			case R.id.action_change_presence:
				changePresence();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void shareBarcode() {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_STREAM, BarcodeProvider.getUriForAccount(this, mAccount));
		intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.setType("image/png");
		startActivity(Intent.createChooser(intent, getText(R.string.share_with)));
	}

	private void changeMoreTableVisibility(boolean visible) {
		mMoreTable.setVisibility(visible ? View.VISIBLE : View.GONE);
	}

	private void gotoChangePassword(String newPassword) {
		final Intent changePasswordIntent = new Intent(this, ChangePasswordActivity.class);
		changePasswordIntent.putExtra(EXTRA_ACCOUNT, mAccount.getJid().toString());
		if (newPassword != null) {
			changePasswordIntent.putExtra("password", newPassword);
		}
		startActivity(changePasswordIntent);
	}

	private void renewCertificate() {
		KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null);
	}

	private void changePresence() {
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean manualStatus = sharedPreferences.getBoolean(SettingsActivity.MANUALLY_CHANGE_PRESENCE, getResources().getBoolean(R.bool.manually_change_presence));
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		final DialogPresenceBinding binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.dialog_presence, null, false);
		String current = mAccount.getPresenceStatusMessage();
		if (current != null && !current.trim().isEmpty()) {
			binding.statusMessage.append(current);
		}
		setAvailabilityRadioButton(mAccount.getPresenceStatus(), binding);
		binding.show.setVisibility(manualStatus ? View.VISIBLE : View.GONE);
		List<PresenceTemplate> templates = xmppConnectionService.getPresenceTemplates(mAccount);
		PresenceTemplateAdapter presenceTemplateAdapter = new PresenceTemplateAdapter(this, R.layout.simple_list_item, templates);
		binding.statusMessage.setAdapter(presenceTemplateAdapter);
		binding.statusMessage.setOnItemClickListener((parent, view, position, id) -> {
			PresenceTemplate template = (PresenceTemplate) parent.getItemAtPosition(position);
			setAvailabilityRadioButton(template.getStatus(), binding);
		});
		builder.setTitle(R.string.edit_status_message_title);
		builder.setView(binding.getRoot());
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.confirm, (dialog, which) -> {
			PresenceTemplate template = new PresenceTemplate(getAvailabilityRadioButton(binding), binding.statusMessage.getText().toString().trim());
			if (mAccount.getPgpId() != 0 && hasPgp()) {
				generateSignature(null, template);
			} else {
				xmppConnectionService.changeStatus(mAccount, template, null);
			}
		});
		builder.create().show();
	}

	private void generateSignature(Intent intent, PresenceTemplate template) {
		xmppConnectionService.getPgpEngine().generateSignature(intent, mAccount, template.getStatusMessage(), new UiCallback<String>() {
			@Override
			public void success(String signature) {
				xmppConnectionService.changeStatus(mAccount, template, signature);
			}

			@Override
			public void error(int errorCode, String object) {

			}

			@Override
			public void userInputRequried(PendingIntent pi, String object) {
				mPendingPresenceTemplate.push(template);
				try {
					startIntentSenderForResult(pi.getIntentSender(), REQUEST_CHANGE_STATUS, null, 0, 0, 0);
				} catch (final IntentSender.SendIntentException ignored) {
				}
			}
		});
	}

	private static void setAvailabilityRadioButton(Presence.Status status, DialogPresenceBinding binding) {
		if (status == null) {
			binding.online.setChecked(true);
			return;
		}
		switch (status) {
			case DND:
				binding.dnd.setChecked(true);
				break;
			case XA:
				binding.xa.setChecked(true);
				break;
			case AWAY:
				binding.xa.setChecked(true);
				break;
			default:
				binding.online.setChecked(true);
		}
	}

	private static Presence.Status getAvailabilityRadioButton(DialogPresenceBinding binding) {
		if (binding.dnd.isChecked()) {
			return Presence.Status.DND;
		} else if (binding.xa.isChecked()) {
			return Presence.Status.XA;
		} else if (binding.away.isChecked()) {
			return Presence.Status.AWAY;
		} else {
			return Presence.Status.ONLINE;
		}
	}

	@Override
	public void alias(String alias) {
		if (alias != null) {
			xmppConnectionService.updateKeyInAccount(mAccount, alias);
		}
	}

	private void updateAccountInformation(boolean init) {
		if (init) {
			this.binding.accountJid.getEditableText().clear();
			if (mUsernameMode) {
				this.binding.accountJid.getEditableText().append(this.mAccount.getJid().getLocal());
			} else {
				this.binding.accountJid.getEditableText().append(this.mAccount.getJid().asBareJid().toString());
			}
			this.mPassword.getEditableText().clear();
			this.mPassword.getEditableText().append(this.mAccount.getPassword());
			this.binding.hostname.setText("");
			this.binding.hostname.getEditableText().append(this.mAccount.getHostname());
			this.mPort.setText("");
			this.mPort.getEditableText().append(String.valueOf(this.mAccount.getPort()));
			this.binding.namePort.setVisibility(mShowOptions ? View.VISIBLE : View.GONE);

		}

		final boolean editable = !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY);
		this.binding.accountJid.setEnabled(editable);
		this.binding.accountJid.setFocusable(editable);
		this.binding.accountJid.setFocusableInTouchMode(editable);


		if (mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE) || !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
			this.binding.accountPasswordLayout.setPasswordVisibilityToggleEnabled(true);
		} else {
			this.binding.accountPasswordLayout.setPasswordVisibilityToggleEnabled(false);
		}

		if (!mInitMode) {
			this.mAvatar.setVisibility(View.VISIBLE);
			this.mAvatar.setImageBitmap(avatarService().get(this.mAccount, (int) getResources().getDimension(R.dimen.avatar_on_details_screen_size)));
		} else {
			this.mAvatar.setVisibility(View.GONE);
		}
		this.binding.accountRegisterNew.setChecked(this.mAccount.isOptionSet(Account.OPTION_REGISTER));
		if (this.mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
			if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
				ActionBar actionBar = getSupportActionBar();
				if (actionBar != null) {
					actionBar.setTitle(R.string.create_account);
				}
			}
			this.binding.accountRegisterNew.setVisibility(View.GONE);
		} else if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
			this.binding.accountRegisterNew.setVisibility(View.VISIBLE);
		} else {
			this.binding.accountRegisterNew.setVisibility(View.GONE);
		}
		if (this.mAccount.isOnlineAndConnected() && !this.mFetchingAvatar) {
			Features features = this.mAccount.getXmppConnection().getFeatures();
			this.binding.stats.setVisibility(View.VISIBLE);
			boolean showBatteryWarning = !xmppConnectionService.getPushManagementService().available(mAccount) && isOptimizingBattery();
			boolean showDataSaverWarning = isAffectedByDataSaver();
			showOsOptimizationWarning(showBatteryWarning, showDataSaverWarning);
			this.binding.sessionEst.setText(UIHelper.readableTimeDifferenceFull(this, this.mAccount.getXmppConnection()
					.getLastSessionEstablished()));
			if (features.rosterVersioning()) {
				this.binding.serverInfoRosterVersion.setText(R.string.server_info_available);
			} else {
				this.binding.serverInfoRosterVersion.setText(R.string.server_info_unavailable);
			}
			if (features.carbons()) {
				this.binding.serverInfoCarbons.setText(R.string.server_info_available);
			} else {
				this.binding.serverInfoCarbons.setText(R.string.server_info_unavailable);
			}
			if (features.mam()) {
				this.binding.serverInfoMam.setText(R.string.server_info_available);
			} else {
				this.binding.serverInfoMam.setText(R.string.server_info_unavailable);
			}
			if (features.csi()) {
				this.binding.serverInfoCsi.setText(R.string.server_info_available);
			} else {
				this.binding.serverInfoCsi.setText(R.string.server_info_unavailable);
			}
			if (features.blocking()) {
				this.binding.serverInfoBlocking.setText(R.string.server_info_available);
			} else {
				this.binding.serverInfoBlocking.setText(R.string.server_info_unavailable);
			}
			if (features.sm()) {
				this.binding.serverInfoSm.setText(R.string.server_info_available);
			} else {
				this.binding.serverInfoSm.setText(R.string.server_info_unavailable);
			}
			if (features.pep()) {
				AxolotlService axolotlService = this.mAccount.getAxolotlService();
				if (axolotlService != null && axolotlService.isPepBroken()) {
					this.binding.serverInfoPep.setText(R.string.server_info_broken);
				} else if (features.pepPublishOptions() || features.pepOmemoWhitelisted()) {
					this.binding.serverInfoPep.setText(R.string.server_info_available);
				} else {
					this.binding.serverInfoPep.setText(R.string.server_info_partial);
				}
			} else {
				this.binding.serverInfoPep.setText(R.string.server_info_unavailable);
			}
			if (features.httpUpload(0)) {
				this.binding.serverInfoHttpUpload.setText(R.string.server_info_available);
			} else if (features.p1S3FileTransfer()) {
				this.binding.serverInfoHttpUploadDescription.setText(R.string.p1_s3_filetransfer);
				this.binding.serverInfoHttpUpload.setText(R.string.server_info_available);
			} else {
				this.binding.serverInfoHttpUpload.setText(R.string.server_info_unavailable);
			}

			this.binding.pushRow.setVisibility(xmppConnectionService.getPushManagementService().isStub() ? View.GONE : View.VISIBLE);

			if (xmppConnectionService.getPushManagementService().available(mAccount)) {
				this.binding.serverInfoPush.setText(R.string.server_info_available);
			} else {
				this.binding.serverInfoPush.setText(R.string.server_info_unavailable);
			}
			final long pgpKeyId = this.mAccount.getPgpId();
			if (pgpKeyId != 0 && Config.supportOpenPgp()) {
				OnClickListener openPgp = view -> launchOpenKeyChain(pgpKeyId);
				OnClickListener delete = view -> showDeletePgpDialog();
				this.mPgpFingerprintBox.setVisibility(View.VISIBLE);
				this.mPgpFingerprint.setText(OpenPgpUtils.convertKeyIdToHex(pgpKeyId));
				this.mPgpFingerprint.setOnClickListener(openPgp);
				if ("pgp".equals(messageFingerprint)) {
					this.getmPgpFingerprintDesc.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption_Highlight);
				}
				this.getmPgpFingerprintDesc.setOnClickListener(openPgp);
				this.mPgpDeleteFingerprintButton.setOnClickListener(delete);
			} else {
				this.mPgpFingerprintBox.setVisibility(View.GONE);
			}
			final String ownAxolotlFingerprint = this.mAccount.getAxolotlService().getOwnFingerprint();
			if (ownAxolotlFingerprint != null && Config.supportOmemo()) {
				this.mAxolotlFingerprintBox.setVisibility(View.VISIBLE);
				if (ownAxolotlFingerprint.equals(messageFingerprint)) {
					this.mOwnFingerprintDesc.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption_Highlight);
					this.mOwnFingerprintDesc.setText(R.string.omemo_fingerprint_selected_message);
				} else {
					this.mOwnFingerprintDesc.setTextAppearance(this, R.style.TextAppearance_Conversations_Caption);
					this.mOwnFingerprintDesc.setText(R.string.omemo_fingerprint);
				}
				this.mAxolotlFingerprint.setText(CryptoHelper.prettifyFingerprint(ownAxolotlFingerprint.substring(2)));
				this.mAxolotlFingerprintToClipboardButton.setVisibility(View.VISIBLE);
				this.mAxolotlFingerprintToClipboardButton.setOnClickListener(v -> copyOmemoFingerprint(ownAxolotlFingerprint));
			} else {
				this.mAxolotlFingerprintBox.setVisibility(View.GONE);
			}
			boolean hasKeys = false;
			keys.removeAllViews();
			for (XmppAxolotlSession session : mAccount.getAxolotlService().findOwnSessions()) {
				if (!session.getTrust().isCompromised()) {
					boolean highlight = session.getFingerprint().equals(messageFingerprint);
					addFingerprintRow(keys, session, highlight);
					hasKeys = true;
				}
			}
			if (hasKeys && Config.supportOmemo()) { //TODO: either the button should be visible if we print an active device or the device list should be fed with reactived devices
				this.binding.otherDeviceKeysCard.setVisibility(View.VISIBLE);
				Set<Integer> otherDevices = mAccount.getAxolotlService().getOwnDeviceIds();
				if (otherDevices == null || otherDevices.isEmpty()) {
					mClearDevicesButton.setVisibility(View.GONE);
				} else {
					mClearDevicesButton.setVisibility(View.VISIBLE);
				}
			} else {
				this.binding.otherDeviceKeysCard.setVisibility(View.GONE);
			}
		} else {
			final TextInputLayout errorLayout;
			if (this.mAccount.errorStatus()) {
				if (this.mAccount.getStatus() == Account.State.UNAUTHORIZED) {
					errorLayout = this.mPasswordLayout;
				} else if (mShowOptions
						&& this.mAccount.getStatus() == Account.State.SERVER_NOT_FOUND
						&& this.binding.hostname.getText().length() > 0) {
					errorLayout = this.binding.hostnameLayout;
				} else {
					errorLayout = this.mAccountJidLayout;
				}
				errorLayout.setError(getString(this.mAccount.getStatus().getReadableId()));
				if (init || !accountInfoEdited()) {
					errorLayout.requestFocus();
				}
			} else {
				errorLayout = null;
			}
			removeErrorsOnAllBut(errorLayout);
			this.binding.stats.setVisibility(View.GONE);
			this.binding.otherDeviceKeysCard.setVisibility(View.GONE);
		}
	}

	private void removeErrorsOnAllBut(TextInputLayout exception) {
		if (this.mAccountJidLayout != exception) {
			this.mAccountJidLayout.setErrorEnabled(false);
			this.mAccountJidLayout.setError(null);
		}
		if (this.mPasswordLayout != exception) {
			this.mPasswordLayout.setErrorEnabled(false);
			this.mPasswordLayout.setError(null);
		}
		if (this.binding.hostnameLayout != exception) {
			this.binding.hostnameLayout.setErrorEnabled(false);
			this.binding.hostnameLayout.setError(null);
		}
		if (this.binding.portLayout != exception) {
			this.binding.portLayout.setErrorEnabled(false);
			this.binding.portLayout.setError(null);
		}
	}

	private void showDeletePgpDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.unpublish_pgp);
		builder.setMessage(R.string.unpublish_pgp_message);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.confirm, (dialogInterface, i) -> {
			mAccount.setPgpSignId(0);
			mAccount.unsetPgpSignature();
			xmppConnectionService.databaseBackend.updateAccount(mAccount);
			xmppConnectionService.sendPresence(mAccount);
			refreshUiReal();
		});
		builder.create().show();
	}

	private void showOsOptimizationWarning(boolean showBatteryWarning, boolean showDataSaverWarning) {
		this.binding.osOptimization.setVisibility(showBatteryWarning || showDataSaverWarning ? View.VISIBLE : View.GONE);
		if (showDataSaverWarning) {
			this.binding.osOptimizationHeadline.setText(R.string.data_saver_enabled);
			this.getmDisableOsOptimizationsBody.setText(R.string.data_saver_enabled_explained);
			this.mDisableOsOptimizationsButton.setText(R.string.allow);
			this.mDisableOsOptimizationsButton.setOnClickListener(v -> {
				Intent intent = new Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS);
				Uri uri = Uri.parse("package:" + getPackageName());
				intent.setData(uri);
				try {
					startActivityForResult(intent, REQUEST_DATA_SAVER);
				} catch (ActivityNotFoundException e) {
					Toast.makeText(EditAccountActivity.this, R.string.device_does_not_support_data_saver, Toast.LENGTH_SHORT).show();
				}
			});
		} else if (showBatteryWarning) {
			this.mDisableOsOptimizationsButton.setText(R.string.disable);
			this.binding.osOptimizationHeadline.setText(R.string.battery_optimizations_enabled);
			this.getmDisableOsOptimizationsBody.setText(R.string.battery_optimizations_enabled_explained);
			this.mDisableOsOptimizationsButton.setOnClickListener(v -> {
				Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
				Uri uri = Uri.parse("package:" + getPackageName());
				intent.setData(uri);
				try {
					startActivityForResult(intent, REQUEST_BATTERY_OP);
				} catch (ActivityNotFoundException e) {
					Toast.makeText(EditAccountActivity.this, R.string.device_does_not_support_battery_op, Toast.LENGTH_SHORT).show();
				}
			});
		}
	}

	public void showWipePepDialog() {
		Builder builder = new Builder(this);
		builder.setTitle(getString(R.string.clear_other_devices));
		builder.setIconAttribute(android.R.attr.alertDialogIcon);
		builder.setMessage(getString(R.string.clear_other_devices_desc));
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.accept),
				(dialog, which) -> mAccount.getAxolotlService().wipeOtherPepDevices());
		builder.create().show();
	}

	private void editMamPrefs() {
		this.mFetchingMamPrefsToast = Toast.makeText(this, R.string.fetching_mam_prefs, Toast.LENGTH_LONG);
		this.mFetchingMamPrefsToast.show();
		xmppConnectionService.fetchMamPreferences(mAccount, this);
	}

	@Override
	public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
		refreshUi();
	}

	@Override
	public void onCaptchaRequested(final Account account, final String id, final Data data, final Bitmap captcha) {
		runOnUiThread(() -> {
			if (mCaptchaDialog != null && mCaptchaDialog.isShowing()) {
				mCaptchaDialog.dismiss();
			}
			final Builder builder = new Builder(EditAccountActivity.this);
			final View view = getLayoutInflater().inflate(R.layout.captcha, null);
			final ImageView imageView = view.findViewById(R.id.captcha);
			final EditText input = view.findViewById(R.id.input);
			imageView.setImageBitmap(captcha);

			builder.setTitle(getString(R.string.captcha_required));
			builder.setView(view);

			builder.setPositiveButton(getString(R.string.ok),
					(dialog, which) -> {
						String rc = input.getText().toString();
						data.put("username", account.getUsername());
						data.put("password", account.getPassword());
						data.put("ocr", rc);
						data.submit();

						if (xmppConnectionServiceBound) {
							xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, id, data);
						}
					});
			builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
				if (xmppConnectionService != null) {
					xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
				}
			});

			builder.setOnCancelListener(dialog -> {
				if (xmppConnectionService != null) {
					xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
				}
			});
			mCaptchaDialog = builder.create();
			mCaptchaDialog.show();
			input.requestFocus();
		});
	}

	public void onShowErrorToast(final int resId) {
		runOnUiThread(() -> Toast.makeText(EditAccountActivity.this, resId, Toast.LENGTH_SHORT).show());
	}

	@Override
	public void onPreferencesFetched(final Element prefs) {
		runOnUiThread(() -> {
			if (mFetchingMamPrefsToast != null) {
				mFetchingMamPrefsToast.cancel();
			}
			Builder builder = new Builder(EditAccountActivity.this);
			builder.setTitle(R.string.server_side_mam_prefs);
			String defaultAttr = prefs.getAttribute("default");
			final List<String> defaults = Arrays.asList("never", "roster", "always");
			final AtomicInteger choice = new AtomicInteger(Math.max(0, defaults.indexOf(defaultAttr)));
			builder.setSingleChoiceItems(R.array.mam_prefs, choice.get(), (dialog, which) -> choice.set(which));
			builder.setNegativeButton(R.string.cancel, null);
			builder.setPositiveButton(R.string.ok, (dialog, which) -> {
				prefs.setAttribute("default", defaults.get(choice.get()));
				xmppConnectionService.pushMamPreferences(mAccount, prefs);
			});
			builder.create().show();
		});
	}

	@Override
	public void onPreferencesFetchFailed() {
		runOnUiThread(() -> {
			if (mFetchingMamPrefsToast != null) {
				mFetchingMamPrefsToast.cancel();
			}
			Toast.makeText(EditAccountActivity.this, R.string.unable_to_fetch_mam_prefs, Toast.LENGTH_LONG).show();
		});
	}

	@Override
	public void OnUpdateBlocklist(Status status) {
		refreshUi();
	}
}
