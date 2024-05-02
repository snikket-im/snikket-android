package eu.siacs.conversations.ui.fragment.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.utils.TimeFrameUtils;

public abstract class XmppPreferenceFragment extends PreferenceFragmentCompat {

    private final SharedPreferences.OnSharedPreferenceChangeListener
            sharedPreferenceChangeListener =
                    (sharedPreferences, key) -> {
                        if (key == null) {
                            return;
                        }
                        if (isAdded()) {
                            onSharedPreferenceChanged(key);
                        }
                    };

    protected void onSharedPreferenceChanged(@NonNull String key) {
        Log.d(Config.LOGTAG, "onSharedPreferenceChanged(" + key + ")");
    }

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
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(
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

    protected static String timeframeValueToName(final Context context, final int value) {
        if (value == 0) {
            return context.getString(R.string.never);
        } else {
            return TimeFrameUtils.resolve(context, 1000L * value);
        }
    }

    protected static class TimeframeSummaryProvider
            implements Preference.SummaryProvider<ListPreference> {

        @Nullable
        @Override
        public CharSequence provideSummary(@NonNull ListPreference preference) {
            final Integer value = Ints.tryParse(Strings.nullToEmpty(preference.getValue()));
            return timeframeValueToName(preference.getContext(), value == null ? 0 : value);
        }
    }
}
