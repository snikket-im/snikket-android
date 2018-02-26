package eu.siacs.conversations.ui;

import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.security.cert.X509Certificate;
import java.util.Arrays;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.databinding.ContactKeyBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.XmppUri;

public abstract class OmemoActivity extends XmppActivity {

	private Account mSelectedAccount;
	private String mSelectedFingerprint;

	protected XmppUri mPendingFingerprintVerificationUri = null;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		Object account = v.getTag(R.id.TAG_ACCOUNT);
		Object fingerprint = v.getTag(R.id.TAG_FINGERPRINT);
		Object fingerprintStatus = v.getTag(R.id.TAG_FINGERPRINT_STATUS);
		if (account != null
				&& fingerprint != null
				&& account instanceof Account
				&& fingerprintStatus != null
				&& fingerprint instanceof String
				&& fingerprintStatus instanceof FingerprintStatus) {
			getMenuInflater().inflate(R.menu.omemo_key_context, menu);
			MenuItem distrust = menu.findItem(R.id.distrust_key);
			MenuItem verifyScan = menu.findItem(R.id.verify_scan);
			if (this instanceof TrustKeysActivity) {
				distrust.setVisible(false);
				verifyScan.setVisible(false);
			} else {
				FingerprintStatus status = (FingerprintStatus) fingerprintStatus;
				if (!status.isActive() || status.isVerified()) {
					verifyScan.setVisible(false);
				}
				distrust.setVisible(status.isVerified() || (!status.isActive() && status.isTrusted()));
			}
			this.mSelectedAccount = (Account) account;
			this.mSelectedFingerprint = (String) fingerprint;
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.distrust_key:
				showPurgeKeyDialog(mSelectedAccount, mSelectedFingerprint);
				break;
			case R.id.copy_omemo_key:
				copyOmemoFingerprint(mSelectedFingerprint);
				break;
			case R.id.verify_scan:
				ScanActivity.scan(this);
				break;
		}
		return true;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, requestCode, intent);
		if (requestCode == ScanActivity.REQUEST_SCAN_QR_CODE && resultCode == RESULT_OK) {
			String result = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
			XmppUri uri = new XmppUri(result == null ? "" : result);
			if (xmppConnectionServiceBound) {
				processFingerprintVerification(uri);
			} else {
				this.mPendingFingerprintVerificationUri = uri;
			}
		}
	}

	protected abstract void processFingerprintVerification(XmppUri uri);

	protected void copyOmemoFingerprint(String fingerprint) {
		if (copyTextToClipboard(CryptoHelper.prettifyFingerprint(fingerprint.substring(2)), R.string.omemo_fingerprint)) {
			Toast.makeText(
					this,
					R.string.toast_message_omemo_fingerprint,
					Toast.LENGTH_SHORT).show();
		}
	}

	protected void addFingerprintRow(LinearLayout keys, final XmppAxolotlSession session, boolean highlight) {
		final Account account = session.getAccount();
		final String fingerprint = session.getFingerprint();
		addFingerprintRowWithListeners(keys,
				session.getAccount(),
				fingerprint,
				highlight,
				session.getTrust(),
				true,
				true,
				(buttonView, isChecked) -> account.getAxolotlService().setFingerprintTrust(fingerprint, FingerprintStatus.createActive(isChecked)));
	}

	protected void addFingerprintRowWithListeners(LinearLayout keys, final Account account,
	                                              final String fingerprint,
	                                              boolean highlight,
	                                              FingerprintStatus status,
	                                              boolean showTag,
	                                              boolean undecidedNeedEnablement,
	                                              CompoundButton.OnCheckedChangeListener
			                                              onCheckedChangeListener) {
		ContactKeyBinding binding = DataBindingUtil.inflate(getLayoutInflater(), R.layout.contact_key, keys, true);
		if (Config.X509_VERIFICATION && status.getTrust() == FingerprintStatus.Trust.VERIFIED_X509) {
			binding.key.setOnClickListener(v -> showX509Certificate(account, fingerprint));
			binding.keyType.setOnClickListener(v -> showX509Certificate(account, fingerprint));
		}
		binding.tglTrust.setVisibility(View.VISIBLE);
		registerForContextMenu(binding.getRoot());
		binding.getRoot().setTag(R.id.TAG_ACCOUNT, account);
		binding.getRoot().setTag(R.id.TAG_FINGERPRINT, fingerprint);
		binding.getRoot().setTag(R.id.TAG_FINGERPRINT_STATUS, status);
		boolean x509 = Config.X509_VERIFICATION && status.getTrust() == FingerprintStatus.Trust.VERIFIED_X509;
		final View.OnClickListener toast;
		binding.tglTrust.setChecked(status.isTrusted());

		if (status.isActive()) {
			binding.key.setTextColor(getPrimaryTextColor());
			binding.keyType.setTextColor(getSecondaryTextColor());
			if (status.isVerified()) {
				binding.verifiedFingerprint.setVisibility(View.VISIBLE);
				binding.verifiedFingerprint.setAlpha(1.0f);
				binding.tglTrust.setVisibility(View.GONE);
				binding.verifiedFingerprint.setOnClickListener(v -> replaceToast(getString(R.string.this_device_has_been_verified), false));
				toast = null;
			} else {
				binding.verifiedFingerprint.setVisibility(View.GONE);
				binding.tglTrust.setVisibility(View.VISIBLE);
				binding.tglTrust.setOnCheckedChangeListener(onCheckedChangeListener);
				if (status.getTrust() == FingerprintStatus.Trust.UNDECIDED && undecidedNeedEnablement) {
					binding.buttonEnableDevice.setVisibility(View.VISIBLE);
					binding.buttonEnableDevice.setOnClickListener(v -> {
						account.getAxolotlService().setFingerprintTrust(fingerprint, FingerprintStatus.createActive(false));
						binding.buttonEnableDevice.setVisibility(View.GONE);
						binding.tglTrust.setVisibility(View.VISIBLE);
					});
					binding.tglTrust.setVisibility(View.GONE);
				} else {
					binding.tglTrust.setOnClickListener(null);
					binding.tglTrust.setEnabled(true);
				}
				toast = v -> hideToast();
			}
		} else {
			binding.key.setTextColor(getTertiaryTextColor());
			binding.keyType.setTextColor(getTertiaryTextColor());
			toast = v -> replaceToast(getString(R.string.this_device_is_no_longer_in_use), false);
			if (status.isVerified()) {
				binding.tglTrust.setVisibility(View.GONE);
				binding.verifiedFingerprint.setVisibility(View.VISIBLE);
				binding.verifiedFingerprint.setAlpha(0.4368f);
				binding.verifiedFingerprint.setOnClickListener(toast);
			} else {
				binding.tglTrust.setVisibility(View.VISIBLE);
				binding.verifiedFingerprint.setVisibility(View.GONE);
				binding.tglTrust.setEnabled(false);
			}
		}

		binding.getRoot().setOnClickListener(toast);
		binding.key.setOnClickListener(toast);
		binding.keyType.setOnClickListener(toast);
		if (showTag) {
			binding.keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509 : R.string.omemo_fingerprint));
		} else {
			binding.keyType.setVisibility(View.GONE);
		}
		if (highlight) {
			binding.keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
			binding.keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509_selected_message : R.string.omemo_fingerprint_selected_message));
		} else {
			binding.keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509 : R.string.omemo_fingerprint));
		}

		binding.key.setText(CryptoHelper.prettifyFingerprint(fingerprint.substring(2)));
	}

	public void showPurgeKeyDialog(final Account account, final String fingerprint) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.distrust_omemo_key);
		builder.setMessage(R.string.distrust_omemo_key_text);
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(R.string.confirm,
				(dialog, which) -> {
					account.getAxolotlService().distrustFingerprint(fingerprint);
					refreshUi();
				});
		builder.create().show();
	}

	private void showX509Certificate(Account account, String fingerprint) {
		X509Certificate x509Certificate = account.getAxolotlService().getFingerprintCertificate(fingerprint);
		if (x509Certificate != null) {
			showCertificateInformationDialog(CryptoHelper.extractCertificateInformation(x509Certificate));
		} else {
			Toast.makeText(this, R.string.certificate_not_found, Toast.LENGTH_SHORT).show();
		}
	}

	private void showCertificateInformationDialog(Bundle bundle) {
		View view = getLayoutInflater().inflate(R.layout.certificate_information, null);
		final String not_available = getString(R.string.certicate_info_not_available);
		TextView subject_cn = (TextView) view.findViewById(R.id.subject_cn);
		TextView subject_o = (TextView) view.findViewById(R.id.subject_o);
		TextView issuer_cn = (TextView) view.findViewById(R.id.issuer_cn);
		TextView issuer_o = (TextView) view.findViewById(R.id.issuer_o);
		TextView sha1 = (TextView) view.findViewById(R.id.sha1);

		subject_cn.setText(bundle.getString("subject_cn", not_available));
		subject_o.setText(bundle.getString("subject_o", not_available));
		issuer_cn.setText(bundle.getString("issuer_cn", not_available));
		issuer_o.setText(bundle.getString("issuer_o", not_available));
		sha1.setText(bundle.getString("sha1", not_available));

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.certificate_information);
		builder.setView(view);
		builder.setPositiveButton(R.string.ok, null);
		builder.create().show();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		ScanActivity.onRequestPermissionResult(this, requestCode, grantResults);
	}
}
