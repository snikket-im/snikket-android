package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;

public class SettingsActivity extends XmppActivity implements
		OnSharedPreferenceChangeListener {
	private SettingsFragment mSettingsFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSettingsFragment = new SettingsFragment();
		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, mSettingsFragment).commit();
	}

	@Override
	void onBackendConnected() {

	}

	@Override
	public void onStart() {
		super.onStart();
		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);
		ListPreference resources = (ListPreference) mSettingsFragment
				.findPreference("resource");
		if (resources != null) {
			ArrayList<CharSequence> entries = new ArrayList<CharSequence>(
					Arrays.asList(resources.getEntries()));
			entries.add(0, Build.MODEL);
			resources.setEntries(entries.toArray(new CharSequence[entries
					.size()]));
			resources.setEntryValues(entries.toArray(new CharSequence[entries
					.size()]));
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences,
			String name) {
		if (name.equals("resource")) {
			String resource = preferences.getString("resource", "mobile")
					.toLowerCase(Locale.US);
			if (xmppConnectionServiceBound) {
				for (Account account : xmppConnectionService.getAccounts()) {
                    account.setResource(resource);
                    if (!account.isOptionSet(Account.OPTION_DISABLED)) {
						xmppConnectionService.reconnectAccount(account, false);
					}
				}
			}
		} else if (name.equals("keep_foreground_service")) {
			xmppConnectionService.toggleForegroundService();
		}
	}

}
