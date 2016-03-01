package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.whispersystems.libaxolotl.IdentityKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class TrustKeysActivity extends XmppActivity implements OnKeyStatusUpdated {
	private List<Jid> contactJids;

	private Account mAccount;
	private Conversation mConversation;
	private TextView keyErrorMessage;
	private LinearLayout keyErrorMessageCard;
	private TextView ownKeysTitle;
	private LinearLayout ownKeys;
	private LinearLayout ownKeysCard;
	private LinearLayout foreignKeys;
	private Button mSaveButton;
	private Button mCancelButton;

	private AxolotlService.FetchStatus lastFetchReport = AxolotlService.FetchStatus.SUCCESS;

	private final Map<String, Boolean> ownKeysToTrust = new HashMap<>();
	private final Map<Jid,Map<String, Boolean>> foreignKeysToTrust = new HashMap<>();

	private final OnClickListener mSaveButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			commitTrusts();
			finishOk();
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
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_trust_keys);
		this.contactJids = new ArrayList<>();
		for(String jid : getIntent().getStringArrayExtra("contacts")) {
			try {
				this.contactJids.add(Jid.fromString(jid));
			} catch (InvalidJidException e) {
				e.printStackTrace();
			}
		}

		keyErrorMessageCard = (LinearLayout) findViewById(R.id.key_error_message_card);
		keyErrorMessage = (TextView) findViewById(R.id.key_error_message);
		ownKeysTitle = (TextView) findViewById(R.id.own_keys_title);
		ownKeys = (LinearLayout) findViewById(R.id.own_keys_details);
		ownKeysCard = (LinearLayout) findViewById(R.id.own_keys_card);
		foreignKeys = (LinearLayout) findViewById(R.id.foreign_keys);
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
			addFingerprintRowWithListeners(ownKeys, mAccount, fingerprint, false,
					XmppAxolotlSession.Trust.fromBoolean(ownKeysToTrust.get(fingerprint)), false,
					new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							ownKeysToTrust.put(fingerprint, isChecked);
							// own fingerprints have no impact on locked status.
						}
					},
					null,
					null
			);
		}

		synchronized (this.foreignKeysToTrust) {
			for (Map.Entry<Jid, Map<String, Boolean>> entry : foreignKeysToTrust.entrySet()) {
				hasForeignKeys = true;
				final LinearLayout layout = (LinearLayout) getLayoutInflater().inflate(R.layout.keys_card, foreignKeys, false);
				final Jid jid = entry.getKey();
				final TextView header = (TextView) layout.findViewById(R.id.foreign_keys_title);
				final LinearLayout keysContainer = (LinearLayout) layout.findViewById(R.id.foreign_keys_details);
				final TextView informNoKeys = (TextView) layout.findViewById(R.id.no_keys_to_accept);
				header.setText(jid.toString());
				final Map<String, Boolean> fingerprints = entry.getValue();
				for (final String fingerprint : fingerprints.keySet()) {
					addFingerprintRowWithListeners(keysContainer, mAccount, fingerprint, false,
							XmppAxolotlSession.Trust.fromBoolean(fingerprints.get(fingerprint)), false,
							new CompoundButton.OnCheckedChangeListener() {
								@Override
								public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
									fingerprints.put(fingerprint, isChecked);
									lockOrUnlockAsNeeded();
								}
							},
							null,
							null
					);
				}
				if (fingerprints.size() == 0) {
					informNoKeys.setVisibility(View.VISIBLE);
					informNoKeys.setText(getString(R.string.no_keys_just_confirm,mAccount.getRoster().getContact(jid).getDisplayName()));
				} else {
					informNoKeys.setVisibility(View.GONE);
				}
				foreignKeys.addView(layout);
			}
		}

		ownKeysTitle.setText(mAccount.getJid().toBareJid().toString());
		ownKeysCard.setVisibility(hasOwnKeys ? View.VISIBLE : View.GONE);
		foreignKeys.setVisibility(hasForeignKeys ? View.VISIBLE : View.GONE);
		if(hasPendingKeyFetches()) {
			setFetching();
			lock();
		} else {
			if (!hasForeignKeys && hasNoOtherTrustedKeys()) {
				keyErrorMessageCard.setVisibility(View.VISIBLE);
				if (lastFetchReport == AxolotlService.FetchStatus.ERROR
						|| mAccount.getAxolotlService().fetchMapHasErrors(contactJids)) {
					keyErrorMessage.setText(R.string.error_no_keys_to_trust_server_error);
				} else {
					keyErrorMessage.setText(R.string.error_no_keys_to_trust);
				}
				ownKeys.removeAllViews();
				ownKeysCard.setVisibility(View.GONE);
				foreignKeys.removeAllViews();
				foreignKeys.setVisibility(View.GONE);
			}
			lockOrUnlockAsNeeded();
			setDone();
		}
	}

	private boolean reloadFingerprints() {
		List<Jid> acceptedTargets = mConversation == null ? new ArrayList<Jid>() : mConversation.getAcceptedCryptoTargets();
		ownKeysToTrust.clear();
		AxolotlService service = this.mAccount.getAxolotlService();
		Set<IdentityKey> ownKeysSet = service.getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED);
		for(final IdentityKey identityKey : ownKeysSet) {
			if(!ownKeysToTrust.containsKey(identityKey)) {
				ownKeysToTrust.put(identityKey.getFingerprint().replaceAll("\\s", ""), false);
			}
		}
		synchronized (this.foreignKeysToTrust) {
			foreignKeysToTrust.clear();
			for (Jid jid : contactJids) {
				Set<IdentityKey> foreignKeysSet = service.getKeysWithTrust(XmppAxolotlSession.Trust.UNDECIDED, jid);
				if (hasNoOtherTrustedKeys(jid) && ownKeysSet.size() == 0) {
					foreignKeysSet.addAll(service.getKeysWithTrust(XmppAxolotlSession.Trust.UNTRUSTED, jid));
				}
				Map<String, Boolean> foreignFingerprints = new HashMap<>();
				for (final IdentityKey identityKey : foreignKeysSet) {
					if (!foreignFingerprints.containsKey(identityKey)) {
						foreignFingerprints.put(identityKey.getFingerprint().replaceAll("\\s", ""), false);
					}
				}
				if (foreignFingerprints.size() > 0 || !acceptedTargets.contains(jid)) {
					foreignKeysToTrust.put(jid, foreignFingerprints);
				}
			}
		}
		return ownKeysSet.size() + foreignKeysToTrust.size() > 0;
	}

	@Override
	public void onBackendConnected() {
		Intent intent = getIntent();
		this.mAccount = extractAccount(intent);
		if (this.mAccount != null && intent != null) {
			String uuid = intent.getStringExtra("conversation");
			this.mConversation = xmppConnectionService.findConversationByUuid(uuid);
			reloadFingerprints();
			populateView();
		}
	}

	private boolean hasNoOtherTrustedKeys() {
		return mAccount == null || mAccount.getAxolotlService().anyTargetHasNoTrustedKeys(contactJids);
	}

	private boolean hasNoOtherTrustedKeys(Jid contact) {
		return mAccount == null || mAccount.getAxolotlService().getNumTrustedKeys(contact) == 0;
	}

	private boolean hasPendingKeyFetches() {
		return mAccount != null && mAccount.getAxolotlService().hasPendingKeyFetches(mAccount, contactJids);
	}


	@Override
	public void onKeyStatusUpdated(final AxolotlService.FetchStatus report) {
		if (report != null) {
			lastFetchReport = report;
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					switch (report) {
						case ERROR:
							Toast.makeText(TrustKeysActivity.this,R.string.error_fetching_omemo_key,Toast.LENGTH_SHORT).show();
							break;
						case SUCCESS_VERIFIED:
							Toast.makeText(TrustKeysActivity.this,R.string.verified_omemo_key_with_certificate,Toast.LENGTH_LONG).show();
							break;
					}
				}
			});

		}
		boolean keysToTrust = reloadFingerprints();
		if (keysToTrust || hasPendingKeyFetches() || hasNoOtherTrustedKeys()) {
			refreshUi();
		} else {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					finishOk();
				}
			});

		}
	}

	private void finishOk() {
		Intent data = new Intent();
		data.putExtra("choice", getIntent().getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID));
		setResult(RESULT_OK, data);
		finish();
	}

	private void commitTrusts() {
		for(final String fingerprint :ownKeysToTrust.keySet()) {
			mAccount.getAxolotlService().setFingerprintTrust(
					fingerprint,
					XmppAxolotlSession.Trust.fromBoolean(ownKeysToTrust.get(fingerprint)));
		}
		List<Jid> acceptedTargets = mConversation == null ? new ArrayList<Jid>() : mConversation.getAcceptedCryptoTargets();
		synchronized (this.foreignKeysToTrust) {
			for (Map.Entry<Jid, Map<String, Boolean>> entry : foreignKeysToTrust.entrySet()) {
				Jid jid = entry.getKey();
				Map<String, Boolean> value = entry.getValue();
				if (!acceptedTargets.contains(jid)) {
					acceptedTargets.add(jid);
				}
				for (final String fingerprint : value.keySet()) {
					mAccount.getAxolotlService().setFingerprintTrust(
							fingerprint,
							XmppAxolotlSession.Trust.fromBoolean(value.get(fingerprint)));
				}
			}
		}
		if (mConversation != null && mConversation.getMode() == Conversation.MODE_MULTI) {
			mConversation.setAcceptedCryptoTargets(acceptedTargets);
			xmppConnectionService.updateConversation(mConversation);
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
		synchronized (this.foreignKeysToTrust) {
			for (Jid jid : contactJids) {
				Map<String, Boolean> fingerprints = foreignKeysToTrust.get(jid);
				if (hasNoOtherTrustedKeys(jid) && (fingerprints == null || !fingerprints.values().contains(true))) {
					lock();
					return;
				}
			}
		}
		unlock();

	}

	private void setDone() {
		mSaveButton.setText(getString(R.string.done));
	}

	private void setFetching() {
		mSaveButton.setText(getString(R.string.fetching_keys));
	}
}
