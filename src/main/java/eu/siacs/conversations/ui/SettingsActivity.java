package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.duenndns.ssl.MemorizingTrustManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.ExportLogsService;
import eu.siacs.conversations.xmpp.XmppConnection;

public class SettingsActivity extends XmppActivity implements
		OnSharedPreferenceChangeListener {

	public static final int REQUEST_WRITE_LOGS = 0xbf8701;
	private SettingsFragment mSettingsFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FragmentManager fm = getFragmentManager();
		mSettingsFragment = (SettingsFragment) fm.findFragmentById(android.R.id.content);
		if (mSettingsFragment == null || !mSettingsFragment.getClass().equals(SettingsFragment.class)) {
			mSettingsFragment = new SettingsFragment();
			fm.beginTransaction().replace(android.R.id.content, mSettingsFragment).commit();
		}

		this.mTheme = findTheme();
		setTheme(this.mTheme);

		int bgcolor = getPrimaryBackgroundColor();
		getWindow().getDecorView().setBackgroundColor(bgcolor);

	}

	@Override
	void onBackendConnected() {

	}

	@Override
	public void onStart() {
		super.onStart();
		PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
		ListPreference resources = (ListPreference) mSettingsFragment.findPreference("resource");
		if (resources != null) {
			ArrayList<CharSequence> entries = new ArrayList<>(Arrays.asList(resources.getEntries()));
			if (!entries.contains(Build.MODEL)) {
				entries.add(0, Build.MODEL);
				resources.setEntries(entries.toArray(new CharSequence[entries.size()]));
				resources.setEntryValues(entries.toArray(new CharSequence[entries.size()]));
			}
		}

		if (Config.FORCE_ORBOT) {
			PreferenceCategory connectionOptions = (PreferenceCategory) mSettingsFragment.findPreference("connection_options");
			PreferenceScreen expert = (PreferenceScreen) mSettingsFragment.findPreference("expert");
			if (connectionOptions != null) {
				expert.removePreference(connectionOptions);
			}
		}

		final Preference removeCertsPreference = mSettingsFragment.findPreference("remove_trusted_certificates");
		removeCertsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				final MemorizingTrustManager mtm = xmppConnectionService.getMemorizingTrustManager();
				final ArrayList<String> aliases = Collections.list(mtm.getCertificates());
				if (aliases.size() == 0) {
					displayToast(getString(R.string.toast_no_trusted_certs));
					return true;
				}
				final ArrayList selectedItems = new ArrayList<Integer>();
				final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(SettingsActivity.this);
				dialogBuilder.setTitle(getResources().getString(R.string.dialog_manage_certs_title));
				dialogBuilder.setMultiChoiceItems(aliases.toArray(new CharSequence[aliases.size()]), null,
						new DialogInterface.OnMultiChoiceClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int indexSelected,
												boolean isChecked) {
								if (isChecked) {
									selectedItems.add(indexSelected);
								} else if (selectedItems.contains(indexSelected)) {
									selectedItems.remove(Integer.valueOf(indexSelected));
								}
								if (selectedItems.size() > 0)
									((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
								else {
									((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
								}
							}
						});

				dialogBuilder.setPositiveButton(
						getResources().getString(R.string.dialog_manage_certs_positivebutton), new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								int count = selectedItems.size();
								if (count > 0) {
									for (int i = 0; i < count; i++) {
										try {
											Integer item = Integer.valueOf(selectedItems.get(i).toString());
											String alias = aliases.get(item);
											mtm.deleteCertificate(alias);
										} catch (KeyStoreException e) {
											e.printStackTrace();
											displayToast("Error: " + e.getLocalizedMessage());
										}
									}
									if (xmppConnectionServiceBound) {
										reconnectAccounts();
									}
									displayToast(getResources().getQuantityString(R.plurals.toast_delete_certificates, count, count));
								}
							}
						});
				dialogBuilder.setNegativeButton(getResources().getString(R.string.dialog_manage_certs_negativebutton), null);
				AlertDialog removeCertsDialog = dialogBuilder.create();
				removeCertsDialog.show();
				removeCertsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
				return true;
			}
		});

		final Preference exportLogsPreference = mSettingsFragment.findPreference("export_logs");
		exportLogsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				hasStoragePermission(REQUEST_WRITE_LOGS);
				return true;
			}
		});
	}

	@Override
	public void onStop() {
		super.onStop();
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences, String name) {
		final List<String> resendPresence = Arrays.asList(
				"confirm_messages",
				"xa_on_silent_mode",
				"away_when_screen_off",
				"allow_message_correction",
				"treat_vibrate_as_silent",
				"manually_change_presence",
				"last_activity");
		if (name.equals("resource")) {
			String resource = preferences.getString("resource", "mobile")
					.toLowerCase(Locale.US);
			if (xmppConnectionServiceBound) {
				for (Account account : xmppConnectionService.getAccounts()) {
					if (account.setResource(resource)) {
						if (!account.isOptionSet(Account.OPTION_DISABLED)) {
							XmppConnection connection = account.getXmppConnection();
							if (connection != null) {
								connection.resetStreamId();
							}
							xmppConnectionService.reconnectAccountInBackground(account);
						}
					}
				}
			}
		} else if (name.equals("keep_foreground_service")) {
			xmppConnectionService.toggleForegroundService();
		} else if (resendPresence.contains(name)) {
			if (xmppConnectionServiceBound) {
				if (name.equals("away_when_screen_off")
						|| name.equals("manually_change_presence")) {
					xmppConnectionService.toggleScreenEventReceiver();
				}
				if (name.equals("manually_change_presence") && !noAccountUsesPgp()) {
					Toast.makeText(this, R.string.republish_pgp_keys, Toast.LENGTH_LONG).show();
				}
				xmppConnectionService.refreshAllPresences();
			}
		} else if (name.equals("dont_trust_system_cas")) {
			xmppConnectionService.updateMemorizingTrustmanager();
			reconnectAccounts();
		} else if (name.equals("use_tor")) {
			reconnectAccounts();
		}

	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (grantResults.length > 0)
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (requestCode == REQUEST_WRITE_LOGS) {
					getApplicationContext().startService(new Intent(getApplicationContext(), ExportLogsService.class));
				}
			} else {
				Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
			}
	}

	private void displayToast(final String msg) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show();
			}
		});
	}

	private void reconnectAccounts() {
		for (Account account : xmppConnectionService.getAccounts()) {
			if (!account.isOptionSet(Account.OPTION_DISABLED)) {
				xmppConnectionService.reconnectAccountInBackground(account);
			}
		}
	}

	public void refreshUiReal() {
		//nothing to do. This Activity doesn't implement any listeners
	}

}
