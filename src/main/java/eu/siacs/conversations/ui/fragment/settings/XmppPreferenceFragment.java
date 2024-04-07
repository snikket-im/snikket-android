package eu.siacs.conversations.ui.fragment.settings;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.XmppActivity;

public abstract class XmppPreferenceFragment extends PreferenceFragmentCompat {

    private final SharedPreferences.OnSharedPreferenceChangeListener
            sharedPreferenceChangeListener =
                    (sharedPreferences, key) -> {
                        if (key == null) {
                            return;
                        }
                        onSharedPreferenceChanged(key);
                    };

    protected void onSharedPreferenceChanged(@NonNull String key) {}

    public void onBackendConnected() {}

    @Override
    public void onResume() {
        super.onResume();
        final var sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(
                    this.sharedPreferenceChangeListener);
        }
        final var xmppActivity = requireXmppActivity();
        if (xmppActivity.xmppConnectionService != null) {
            this.onBackendConnected();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        final var sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null) {
            sharedPreferences.registerOnSharedPreferenceChangeListener(
                    this.sharedPreferenceChangeListener);
        }
    }

    protected void reconnectAccounts() {
        final var service = requireService();
        for (final Account account : service.getAccounts()) {
            if (account.isEnabled()) {
                service.reconnectAccountInBackground(account);
            }
        }
    }

    protected XmppActivity requireXmppActivity() {
        final var activity = requireActivity();
        if (activity instanceof XmppActivity xmppActivity) {
            return xmppActivity;
        }
        throw new IllegalStateException();
    }

    protected XmppConnectionService requireService() {
        final var xmppActivity = requireXmppActivity();
        final var service = xmppActivity.xmppConnectionService;
        if (service != null) {
            return service;
        }
        throw new IllegalStateException();
    }

    protected void runOnUiThread(final Runnable runnable) {
        requireActivity().runOnUiThread(runnable);
    }
}
