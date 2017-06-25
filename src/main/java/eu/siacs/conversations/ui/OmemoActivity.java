package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.security.cert.X509Certificate;
import java.util.Arrays;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.widget.Switch;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.XmppUri;


public abstract class OmemoActivity extends XmppActivity {

    private Account mSelectedAccount;
    private String mSelectedFingerprint;

    protected XmppUri mPendingFingerprintVerificationUri = null;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu,v,menuInfo);
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
                showPurgeKeyDialog(mSelectedAccount,mSelectedFingerprint);
                break;
            case R.id.copy_omemo_key:
                copyOmemoFingerprint(mSelectedFingerprint);
                break;
            case R.id.verify_scan:
                new IntentIntegrator(this).initiateScan(Arrays.asList("AZTEC","QR_CODE"));
                break;
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null && scanResult.getFormatName() != null) {
            String data = scanResult.getContents();
            XmppUri uri = new XmppUri(data);
            if (xmppConnectionServiceBound) {
                processFingerprintVerification(uri);
            } else {
                this.mPendingFingerprintVerificationUri =uri;
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
                new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                account.getAxolotlService().setFingerprintTrust(fingerprint, FingerprintStatus.createActive(isChecked));
            }
        });
    }

    protected void addFingerprintRowWithListeners(LinearLayout keys, final Account account,
                                                     final String fingerprint,
                                                     boolean highlight,
                                                     FingerprintStatus status,
                                                     boolean showTag,
                                                     boolean undecidedNeedEnablement,
                                                     CompoundButton.OnCheckedChangeListener
                                                             onCheckedChangeListener) {
        View view = getLayoutInflater().inflate(R.layout.contact_key, keys, false);
        TextView key = (TextView) view.findViewById(R.id.key);
        TextView keyType = (TextView) view.findViewById(R.id.key_type);
        if (Config.X509_VERIFICATION && status.getTrust() == FingerprintStatus.Trust.VERIFIED_X509) {
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showX509Certificate(account,fingerprint);
                }
            };
            key.setOnClickListener(listener);
            keyType.setOnClickListener(listener);
        }
        Switch trustToggle = (Switch) view.findViewById(R.id.tgl_trust);
        ImageView verifiedFingerprintSymbol = (ImageView) view.findViewById(R.id.verified_fingerprint);
        trustToggle.setVisibility(View.VISIBLE);
        registerForContextMenu(view);
        view.setTag(R.id.TAG_ACCOUNT,account);
        view.setTag(R.id.TAG_FINGERPRINT,fingerprint);
        view.setTag(R.id.TAG_FINGERPRINT_STATUS,status);
        boolean x509 = Config.X509_VERIFICATION && status.getTrust() == FingerprintStatus.Trust.VERIFIED_X509;
        final View.OnClickListener toast;
        trustToggle.setChecked(status.isTrusted(), false);

        if (status.isActive()){
            key.setTextColor(getPrimaryTextColor());
            keyType.setTextColor(getSecondaryTextColor());
            if (status.isVerified()) {
                verifiedFingerprintSymbol.setVisibility(View.VISIBLE);
                verifiedFingerprintSymbol.setAlpha(1.0f);
                trustToggle.setVisibility(View.GONE);
                verifiedFingerprintSymbol.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        replaceToast(getString(R.string.this_device_has_been_verified), false);
                    }
                });
                toast = null;
            } else {
                verifiedFingerprintSymbol.setVisibility(View.GONE);
                trustToggle.setVisibility(View.VISIBLE);
                trustToggle.setOnCheckedChangeListener(onCheckedChangeListener);
                if (status.getTrust() == FingerprintStatus.Trust.UNDECIDED && undecidedNeedEnablement) {
                    trustToggle.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            account.getAxolotlService().setFingerprintTrust(fingerprint,FingerprintStatus.createActive(false));
                            v.setEnabled(true);
                            v.setOnClickListener(null);
                        }
                    });
                    trustToggle.setEnabled(false);
                } else {
                    trustToggle.setOnClickListener(null);
                    trustToggle.setEnabled(true);
                }
                toast = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hideToast();
                    }
                };
            }
        } else {
            key.setTextColor(getTertiaryTextColor());
            keyType.setTextColor(getTertiaryTextColor());
            toast = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    replaceToast(getString(R.string.this_device_is_no_longer_in_use), false);
                }
            };
            if (status.isVerified()) {
                trustToggle.setVisibility(View.GONE);
                verifiedFingerprintSymbol.setVisibility(View.VISIBLE);
                verifiedFingerprintSymbol.setAlpha(0.4368f);
                verifiedFingerprintSymbol.setOnClickListener(toast);
            } else {
                trustToggle.setVisibility(View.VISIBLE);
                verifiedFingerprintSymbol.setVisibility(View.GONE);
                trustToggle.setOnClickListener(null);
                trustToggle.setEnabled(false);
                trustToggle.setOnClickListener(toast);
            }
        }

        view.setOnClickListener(toast);
        key.setOnClickListener(toast);
        keyType.setOnClickListener(toast);
        if (showTag) {
            keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509 : R.string.omemo_fingerprint));
        } else {
            keyType.setVisibility(View.GONE);
        }
        if (highlight) {
            keyType.setTextColor(ContextCompat.getColor(this, R.color.accent));
            keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509_selected_message : R.string.omemo_fingerprint_selected_message));
        } else {
            keyType.setText(getString(x509 ? R.string.omemo_fingerprint_x509 : R.string.omemo_fingerprint));
        }

        key.setText(CryptoHelper.prettifyFingerprint(fingerprint.substring(2)));

        keys.addView(view);
    }

    public void showPurgeKeyDialog(final Account account, final String fingerprint) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.distrust_omemo_key);
        builder.setMessage(R.string.distrust_omemo_key_text);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(R.string.confirm,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        account.getAxolotlService().distrustFingerprint(fingerprint);
                        refreshUi();
                    }
                });
        builder.create().show();
    }

    private void showX509Certificate(Account account, String fingerprint) {
        X509Certificate x509Certificate = account.getAxolotlService().getFingerprintCertificate(fingerprint);
        if (x509Certificate != null) {
            showCertificateInformationDialog(CryptoHelper.extractCertificateInformation(x509Certificate));
        } else {
            Toast.makeText(this,R.string.certificate_not_found, Toast.LENGTH_SHORT).show();
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
}
