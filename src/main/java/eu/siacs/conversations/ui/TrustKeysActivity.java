package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.whispersystems.libaxolotl.IdentityKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class TrustKeysActivity extends XmppActivity implements OnKeyStatusUpdated {
	private Jid accountJid;
	private Jid contactJid;
	private boolean hasOtherTrustedKeys = false;
	private boolean hasPendingFetches = false;
	private boolean hasNoTrustedKeys = true;

	private Contact contact;
	private TextView keyErrorMessage;
	private LinearLayout keyErrorMessageCard;
	private TextView ownKeysTitle;
	private LinearLayout ownKeys;
	private LinearLayout ownKeysCard;
	private TextView foreignKeysTitle;
	private LinearLayout foreignKeys;
	private LinearLayout foreignKeysCard;
	private Button mSaveButton;
	private Button mCancelButton;

	private final Map<String, Boolean> ownKeysToTrust = new HashMap<>();
	private final Map<String, Boolean> foreignKeysToTrust = new HashMap<>();

	private final OnClickListener mSaveButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			commitTrusts();
			Intent data = new Intent();
			data.putExtra("choice", getIntent().getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID));
			setResult(RESULT_OK, data);
			finish();
		}
	};

	private final OnClickListener mCancelButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			setResult(RESULT_CANCELED);
			finish();
		}
	};

	@Override
	protected void refreshUiReal() {
		invalidateOptionsMenu();
		populateView();
	}

	@Override
	protected String getShareableUri() {
		if (contact != null) {
			return contact.getShareableUri();
		} else {
			return "";
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_trust_keys);
		try {
			this.accountJid = Jid.fromString(getIntent().getExtras().getString("account"));
		} catch (final InvalidJidException ignored) {
		}
		try {
			this.contactJid = Jid.fromString(getIntent().getExtras().getString("contact"));
		} catch (final InvalidJidException ignored) {
		}
		hasNoTrustedKeys = getIntent().getBooleanExtra("has_no_trusted", false);

		keyErrorMessageCard = (LinearLayout) findViewById(R.id.key_error_message_card);
		keyErrorMessage = (TextView) findViewById(R.id.key_error_message);
		ownKeysTitle = (TextView) findViewById(R.id.own_keys_title);
		ownKeys = (LinearLayout) findViewById(R.id.own_keys_details);
		ownKeysCard = (LinearLayout) findViewById(R.id.own_keys_card);
		foreignKeysTitle = (TextView) findViewById(R.id.foreign_keys_title);
		foreignKeys = (LinearLayout) findViewById(R.id.foreign_keys_details);
		foreignKeysCard = (LinearLayout) findViewById(R.id.foreign_keys_card);
		mCancelButton = (Button) findViewById(R.id.cancel_button);
		mCancelButton.setOnClickListener(mCancelButtonListener);
		mSaveButton = (Button) findViewById(R.id.save_button);
		mSaveButton.setOnClickListener(mSaveButtonListener);


		if (getActionBar() != null) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	private void populateView() {
		setTitle(getString(R.string.trust_omemo_fingerprints));
		ownKeys.removeAllViews();
		foreignKeys.removeAllViews();
		boolean hasOwnKeys = false;
		boolean hasForeignKeys = false;
		for(final String fingerprint : ownKeysToTrust.keySet()) {
			hasOwnKeys = true;
			addFingerprintRowWithListeners(ownKeys, contact.getAccount(), fingerprint, false,
					XmppAxolotlSession.Trust.fromBoolean(ownKeysToTrust.get(fingerprint)), false,
					new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							ownKeysToTrust.put(fingerprint, isChecked);
							// own fingerprints have no impact on locked status.
						}
					},
					null
			);
		}
		for(final String fingerprint : foreignKeysToTrust.keySet()) {
			hasForeignKeys = true;
			addFingerprintRowWithListeners(foreignKeys, contact.getAccount(), fingerprint, false,
					XmppAxolotlSession.Trust.fromBoolean(foreignKeysToTrust.get(fingerprint)), false,
					new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							foreignKeysToTrust.put(fingerprint, isChecked);
							lockOrUnlockAsNeeded();
						}
					},
					null
			);
		}

		if(hasOwnKeys) {
			ownKeysTitle.setText(accountJid.toString());
			ownKeysCard.setVisibility(View.VISIBLE);
		}
		if(hasForeignKeys) {
			foreignKeysTitle.setText(contactJid.toString());
			foreignKeysCard.setVisibility(View.VISIBLE);
		}
		if(hasPendingFetches) {
			setFetching();
			lock();
		} else {
			if (!hasForeignKeys && !hasOtherTrustedKeys) {
				keyErrorMessageCard.setVisibility(View.VISIBLE);
				keyErrorMessage.setText(R.string.error_no_keys_to_trust);
				ownKeys.removeAllViews(); ownKeysCard.setVisibility(View.GONE);
				foreignKeys.removeAllViews(); foreignKeysCard.setVisibility(View.GONE);
			}
			lockOrUnlockAsNeeded();
			setDone();
		}
	}

	private void getFingerprints(final Account account) {
		Set<IdentityKey> ownKeysSet = account.getAxolotlService().getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED);
		Set<IdentityKey> foreignKeysSet = account.getAxolotlService().getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED, contact);
		if (hasNoTrustedKeys) {
			ownKeysSet.addAll(account.getAxolotlService().getKeysWithTrust(XmppAxolotlSession.Trust.UNTRUSTED));
			foreignKeysSet.addAll(account.getAxolotlService().getKeysWithTrust(XmppAxolotlSession.Trust.UNTRUSTED, contact));
		}
		for(final IdentityKey identityKey : ownKeysSet) {
			if(!ownKeysToTrust.containsKey(identityKey)) {
				ownKeysToTrust.put(identityKey.getFingerprint().replaceAll("\\s", ""), false);
			}
		}
		for(final IdentityKey identityKey : foreignKeysSet) {
			if(!foreignKeysToTrust.containsKey(identityKey)) {
				foreignKeysToTrust.put(identityKey.getFingerprint().replaceAll("\\s", ""), false);
			}
		}
	}

	@Override
	public void onBackendConnected() {
		if ((accountJid != null) && (contactJid != null)) {
			final Account account = xmppConnectionService
				.findAccountByJid(accountJid);
			if (account == null) {
				return;
			}
			this.contact = account.getRoster().getContact(contactJid);
			ownKeysToTrust.clear();
			foreignKeysToTrust.clear();
			getFingerprints(account);

			if(account.getAxolotlService().getNumTrustedKeys(contact) > 0) {
				hasOtherTrustedKeys = true;
			}
			Conversation conversation = xmppConnectionService.findOrCreateConversation(account, contactJid, false);
			if(account.getAxolotlService().hasPendingKeyFetches(conversation)) {
				hasPendingFetches = true;
			}

			populateView();
		}
	}

	@Override
	public void onKeyStatusUpdated() {
		final Account account = xmppConnectionService.findAccountByJid(accountJid);
		hasPendingFetches = false;
		getFingerprints(account);
		refreshUi();
	}

	private void commitTrusts() {
		for(final String fingerprint :ownKeysToTrust.keySet()) {
			contact.getAccount().getAxolotlService().setFingerprintTrust(
					fingerprint,
					XmppAxolotlSession.Trust.fromBoolean(ownKeysToTrust.get(fingerprint)));
		}
		for(final String fingerprint:foreignKeysToTrust.keySet()) {
			contact.getAccount().getAxolotlService().setFingerprintTrust(
					fingerprint,
					XmppAxolotlSession.Trust.fromBoolean(foreignKeysToTrust.get(fingerprint)));
		}
	}

	private void unlock() {
		mSaveButton.setEnabled(true);
		mSaveButton.setTextColor(getPrimaryTextColor());
	}

	private void lock() {
		mSaveButton.setEnabled(false);
		mSaveButton.setTextColor(getSecondaryTextColor());
	}

	private void lockOrUnlockAsNeeded() {
		if (!hasOtherTrustedKeys && !foreignKeysToTrust.values().contains(true)){
			lock();
		} else {
			unlock();
		}
	}

	private void setDone() {
		mSaveButton.setText(getString(R.string.done));
	}

	private void setFetching() {
		mSaveButton.setText(getString(R.string.fetching_keys));
	}
}
