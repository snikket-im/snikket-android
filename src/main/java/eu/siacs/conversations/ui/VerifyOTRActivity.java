package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
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
	private Button mButtonVerifyFingerprint;
	private Button mButtonSharedSecretPositive;
	private Button mButtonSharedSecretNegative;
	private TextView mStatusMessage;
	private Account mAccount;
	private Conversation mConversation;

	private View.OnClickListener mVerifyFingerprintListener = new View.OnClickListener() {

		@Override
		public void onClick(View view) {
			mConversation.verifyOtrFingerprint();
			finish();
		}
	};

	private View.OnClickListener mCreateSharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(final View view) {
			final String question = mSharedSecretHint.getText().toString();
			final String secret = mSharedSecretSecret.getText().toString();
			if (!initSmp(question,secret)) {
				Toast.makeText(getApplicationContext(),"smp failed",Toast.LENGTH_SHORT).show();
			}
			updateView();
		}
	};
	private View.OnClickListener mCancelSharedSecretListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			abortSmp();
			updateView();
		}
	};
	private View.OnClickListener mRespondSharedSecretListener = new View.OnClickListener() {

		@Override
		public void onClick(View view) {
			final String question = mSharedSecretHint.getText().toString();
			final String secret = mSharedSecretSecret.getText().toString();
			respondSmp(question,secret);
			updateView();
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
	protected void onBackendConnected() {
		if (handleIntent(getIntent())) {
			updateView();
		}
	}

	protected void updateView() {
		if (this.mConversation.hasValidOtrSession()) {
			this.mVerificationAreaOne.setVisibility(View.VISIBLE);
			this.mVerificationAreaTwo.setVisibility(View.VISIBLE);
			this.mErrorNoSession.setVisibility(View.GONE);
			this.mYourFingerprint.setText(this.mAccount.getOtrFingerprint(xmppConnectionService));
			this.mRemoteFingerprint.setText(this.mConversation.getOtrFingerprint());
			this.mRemoteJid.setText(this.mConversation.getContact().getJid().toBareJid().toString());
			Conversation.Smp smp = mConversation.smp();
			Session session = mConversation.getOtrSession();
			if (mConversation.isOtrFingerprintVerified()) {
				deactivateButton(mButtonVerifyFingerprint, R.string.verified);
			} else {
				activateButton(mButtonVerifyFingerprint, R.string.verify, mVerifyFingerprintListener);
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
			} else if (smp.status == Conversation.Smp.STATUS_VERIFIED) {
				this.mSharedSecretHint.setVisibility(View.GONE);
				this.mSharedSecretSecret.setVisibility(View.GONE);
				this.mStatusMessage.setVisibility(View.VISIBLE);
				this.mStatusMessage.setText(R.string.verified);
				this.mStatusMessage.setTextColor(getPrimaryColor());
				deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
				activateButton(mButtonSharedSecretPositive, R.string.finish, mFinishListener);
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
		this.mButtonVerifyFingerprint = (Button) findViewById(R.id.button_verify_fingerprint);
		this.mSharedSecretSecret = (EditText) findViewById(R.id.shared_secret_secret);
		this.mSharedSecretHint = (EditText) findViewById(R.id.shared_secret_hint);
		this.mStatusMessage= (TextView) findViewById(R.id.status_message);
		this.mVerificationAreaOne = (RelativeLayout) findViewById(R.id.verification_area_one);
		this.mVerificationAreaTwo = (RelativeLayout) findViewById(R.id.verification_area_two);
		this.mErrorNoSession = (TextView) findViewById(R.id.error_no_session);
	}

	@Override
	protected String getShareableUri() {
		if (mAccount!=null) {
			return "xmpp:"+mAccount.getJid().toBareJid();
		} else {
			return "";
		}
	}

	@Override
	public void onConversationUpdate() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updateView();
			}
		});
	}
}
