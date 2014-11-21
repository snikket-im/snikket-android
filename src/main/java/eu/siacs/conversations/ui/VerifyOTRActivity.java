package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class VerifyOTRActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate {

	public static final String ACTION_VERIFY_CONTACT = "verify_contact";

	private RelativeLayout mVerificationAreaOne;
	private RelativeLayout mVerificationAreaTwo;
	private TextView mErrorNoSession;
	private TextView mRemoteJid;
	private TextView mRemoteFingerprint;
	private TextView mYourFingerprint;
	private EditText mSharedSecretHint;
	private EditText mSharedSecretSecret;
	private Button mButtonScanQrCode;
	private Button mButtonShowQrCode;
	private Button mButtonSharedSecretPositive;
	private Button mButtonSharedSecretNegative;
	private TextView mStatusMessage;
	private Account mAccount;
	private Conversation mConversation;

	private DialogInterface.OnClickListener mVerifyFingerprintListener = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialogInterface, int click) {
			mConversation.verifyOtrFingerprint();
			updateView();
			xmppConnectionService.syncRosterToDisk(mConversation.getAccount());
		}
	};

	private View.OnClickListener mShowQrCodeListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			showQrCode();
		}
	};

	private View.OnClickListener mScanQrCodeListener = new View.OnClickListener() {

		@Override
		public void onClick(View view) {
			new IntentIntegrator(VerifyOTRActivity.this).initiateScan();
		}

	};

	private View.OnClickListener mCreateSharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			if (isAccountOnline()) {
				final String question = mSharedSecretHint.getText().toString();
				final String secret = mSharedSecretSecret.getText().toString();
				initSmp(question, secret);
				updateView();
			}
		}
	};
	private View.OnClickListener mCancelSharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			if (isAccountOnline()) {
				abortSmp();
				updateView();
			}
		}
	};
	private View.OnClickListener mRespondSharedSecretListener = new View.OnClickListener() {

		@Override
		public void onClick(View view) {
			if (isAccountOnline()) {
				final String question = mSharedSecretHint.getText().toString();
				final String secret = mSharedSecretSecret.getText().toString();
				respondSmp(question, secret);
				updateView();
			}
		}
	};
	private View.OnClickListener mRetrySharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			mConversation.smp().status = Conversation.Smp.STATUS_NONE;
			mConversation.smp().hint = null;
			mConversation.smp().secret = null;
			updateView();
		}
	};
	private View.OnClickListener mFinishListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			mConversation.smp().status = Conversation.Smp.STATUS_NONE;
			finish();
		}
	};

	private XmppUri mPendingUri = null;

	protected boolean initSmp(final String question, final String secret) {
		final Session session = mConversation.getOtrSession();
		if (session!=null) {
			try {
				session.initSmp(question, secret);
				mConversation.smp().status = Conversation.Smp.STATUS_WE_REQUESTED;
				return true;
			} catch (OtrException e) {
				return false;
			}
		} else {
			return false;
		}
	}

	protected boolean abortSmp() {
		final Session session = mConversation.getOtrSession();
		if (session!=null) {
			try {
				session.abortSmp();
				mConversation.smp().status = Conversation.Smp.STATUS_NONE;
				mConversation.smp().hint = null;
				mConversation.smp().secret = null;
				return true;
			} catch (OtrException e) {
				return false;
			}
		} else {
			return false;
		}
	}

	protected boolean respondSmp(final String question, final String secret) {
		final Session session = mConversation.getOtrSession();
		if (session!=null) {
			try {
				session.respondSmp(question,secret);
				return true;
			} catch (OtrException e) {
				return false;
			}
		} else {
			return false;
		}
	}

	protected void verifyWithUri(XmppUri uri) {
		Contact contact = mConversation.getContact();
		if (this.mConversation.getContact().getJid().equals(uri.getJid()) && uri.getFingerprint() != null) {
			contact.addOtrFingerprint(uri.getFingerprint());
			Toast.makeText(this,R.string.verified,Toast.LENGTH_SHORT).show();
			updateView();
			xmppConnectionService.syncRosterToDisk(contact.getAccount());
		} else {
			Toast.makeText(this,R.string.could_not_verify_fingerprint,Toast.LENGTH_SHORT).show();
		}
	}

	protected boolean isAccountOnline() {
		if (this.mAccount.getStatus() != Account.State.ONLINE) {
			Toast.makeText(this,R.string.not_connected_try_again,Toast.LENGTH_SHORT).show();
			return false;
		} else {
			return true;
		}
	}

	protected boolean handleIntent(Intent intent) {
		if (intent.getAction().equals(ACTION_VERIFY_CONTACT)) {
			try {
				this.mAccount = this.xmppConnectionService.findAccountByJid(Jid.fromString(intent.getExtras().getString("account")));
			} catch (final InvalidJidException ignored) {
				return false;
			}
			try {
				this.mConversation = this.xmppConnectionService.find(this.mAccount,Jid.fromString(intent.getExtras().getString("contact")));
				if (this.mConversation == null) {
					return false;
				}
			} catch (final InvalidJidException ignored) {
				return false;
			}
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if ((requestCode & 0xFFFF) == IntentIntegrator.REQUEST_CODE) {
			IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
			if (scanResult != null && scanResult.getFormatName() != null) {
				String data = scanResult.getContents();
				XmppUri uri = new XmppUri(data);
				if (xmppConnectionServiceBound) {
					verifyWithUri(uri);
				} else {
					this.mPendingUri = uri;
				}
			}
		}
		super.onActivityResult(requestCode, requestCode, intent);
	}

	@Override
	protected void onBackendConnected() {
		if (handleIntent(getIntent())) {
			if (mPendingUri!=null) {
				verifyWithUri(mPendingUri);
				mPendingUri = null;
			}
			updateView();
		}
	}

	protected void updateView() {
		if (this.mConversation.hasValidOtrSession()) {
			invalidateOptionsMenu();
			this.mVerificationAreaOne.setVisibility(View.VISIBLE);
			this.mVerificationAreaTwo.setVisibility(View.VISIBLE);
			this.mErrorNoSession.setVisibility(View.GONE);
			this.mYourFingerprint.setText(CryptoHelper.prettifyFingerprint(this.mAccount.getOtrFingerprint()));
			this.mRemoteFingerprint.setText(this.mConversation.getOtrFingerprint());
			this.mRemoteJid.setText(this.mConversation.getContact().getJid().toBareJid().toString());
			Conversation.Smp smp = mConversation.smp();
			Session session = mConversation.getOtrSession();
			if (mConversation.isOtrFingerprintVerified()) {
				deactivateButton(mButtonScanQrCode, R.string.verified);
			} else {
				activateButton(mButtonScanQrCode, R.string.scan_qr_code, mScanQrCodeListener);
			}
			if (smp.status == Conversation.Smp.STATUS_NONE) {
				activateButton(mButtonSharedSecretPositive, R.string.create, mCreateSharedSecretListener);
				deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
				this.mSharedSecretHint.setFocusableInTouchMode(true);
				this.mSharedSecretSecret.setFocusableInTouchMode(true);
				this.mSharedSecretSecret.setText("");
				this.mSharedSecretHint.setText("");
				this.mSharedSecretHint.setVisibility(View.VISIBLE);
				this.mSharedSecretSecret.setVisibility(View.VISIBLE);
				this.mStatusMessage.setVisibility(View.GONE);
			} else if (smp.status == Conversation.Smp.STATUS_CONTACT_REQUESTED) {
				this.mSharedSecretHint.setFocusable(false);
				this.mSharedSecretHint.setText(smp.hint);
				this.mSharedSecretSecret.setFocusableInTouchMode(true);
				this.mSharedSecretHint.setVisibility(View.VISIBLE);
				this.mSharedSecretSecret.setVisibility(View.VISIBLE);
				this.mStatusMessage.setVisibility(View.GONE);
				deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
				activateButton(mButtonSharedSecretPositive, R.string.respond, mRespondSharedSecretListener);
			} else if (smp.status == Conversation.Smp.STATUS_FAILED) {
				activateButton(mButtonSharedSecretNegative, R.string.cancel, mFinishListener);
				activateButton(mButtonSharedSecretPositive, R.string.try_again, mRetrySharedSecretListener);
				this.mSharedSecretHint.setVisibility(View.GONE);
				this.mSharedSecretSecret.setVisibility(View.GONE);
				this.mStatusMessage.setVisibility(View.VISIBLE);
				this.mStatusMessage.setText(R.string.secrets_do_not_match);
				this.mStatusMessage.setTextColor(getWarningTextColor());
			} else if (smp.status == Conversation.Smp.STATUS_FINISHED) {
				this.mSharedSecretHint.setText("");
				this.mSharedSecretHint.setVisibility(View.GONE);
				this.mSharedSecretSecret.setText("");
				this.mSharedSecretSecret.setVisibility(View.GONE);
				this.mStatusMessage.setVisibility(View.VISIBLE);
				this.mStatusMessage.setTextColor(getPrimaryColor());
				deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
				if (mConversation.isOtrFingerprintVerified()) {
					activateButton(mButtonSharedSecretPositive, R.string.finish, mFinishListener);
					this.mStatusMessage.setText(R.string.verified);
				} else {
					activateButton(mButtonSharedSecretPositive,R.string.reset,mRetrySharedSecretListener);
					this.mStatusMessage.setText(R.string.secret_accepted);
				}
			} else if (session != null && session.isSmpInProgress()) {
				deactivateButton(mButtonSharedSecretPositive, R.string.in_progress);
				activateButton(mButtonSharedSecretNegative, R.string.cancel, mCancelSharedSecretListener);
				this.mSharedSecretHint.setVisibility(View.VISIBLE);
				this.mSharedSecretSecret.setVisibility(View.VISIBLE);
				this.mSharedSecretHint.setFocusable(false);
				this.mSharedSecretSecret.setFocusable(false);
			}
		} else {
			this.mVerificationAreaOne.setVisibility(View.GONE);
			this.mVerificationAreaTwo.setVisibility(View.GONE);
			this.mErrorNoSession.setVisibility(View.VISIBLE);
		}
	}

	protected void activateButton(Button button, int text, View.OnClickListener listener) {
		button.setEnabled(true);
		button.setTextColor(getPrimaryTextColor());
		button.setText(text);
		button.setOnClickListener(listener);
	}

	protected void deactivateButton(Button button, int text) {
		button.setEnabled(false);
		button.setTextColor(getSecondaryTextColor());
		button.setText(text);
		button.setOnClickListener(null);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_verify_otr);
		this.mRemoteFingerprint = (TextView) findViewById(R.id.remote_fingerprint);
		this.mRemoteJid = (TextView) findViewById(R.id.remote_jid);
		this.mYourFingerprint = (TextView) findViewById(R.id.your_fingerprint);
		this.mButtonSharedSecretNegative = (Button) findViewById(R.id.button_shared_secret_negative);
		this.mButtonSharedSecretPositive = (Button) findViewById(R.id.button_shared_secret_positive);
		this.mButtonScanQrCode = (Button) findViewById(R.id.button_scan_qr_code);
		this.mButtonShowQrCode = (Button) findViewById(R.id.button_show_qr_code);
		this.mButtonShowQrCode.setOnClickListener(this.mShowQrCodeListener);
		this.mSharedSecretSecret = (EditText) findViewById(R.id.shared_secret_secret);
		this.mSharedSecretHint = (EditText) findViewById(R.id.shared_secret_hint);
		this.mStatusMessage= (TextView) findViewById(R.id.status_message);
		this.mVerificationAreaOne = (RelativeLayout) findViewById(R.id.verification_area_one);
		this.mVerificationAreaTwo = (RelativeLayout) findViewById(R.id.verification_area_two);
		this.mErrorNoSession = (TextView) findViewById(R.id.error_no_session);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.verify_otr, menu);
		if (mConversation != null && mConversation.isOtrFingerprintVerified()) {
			MenuItem manuallyVerifyItem = menu.findItem(R.id.manually_verify);
			manuallyVerifyItem.setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		if (menuItem.getItemId() == R.id.manually_verify) {
			showManuallyVerifyDialog();
			return true;
		} else {
			return super.onOptionsItemSelected(menuItem);
		}
	}

	private void showManuallyVerifyDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.manually_verify);
		builder.setMessage(R.string.are_you_sure_verify_fingerprint);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.verify, mVerifyFingerprintListener);
		builder.create().show();
	}

	@Override
	protected String getShareableUri() {
		if (mAccount!=null) {
			return mAccount.getShareableUri();
		} else {
			return "";
		}
	}

	public void onConversationUpdate() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateView();
			}
		});
	}
}
