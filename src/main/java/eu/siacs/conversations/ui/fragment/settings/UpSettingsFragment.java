package eu.siacs.conversations.ui.fragment.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import eu.siacs.conversations.R;
import eu.siacs.conversations.receiver.UnifiedPushDistributor;
import eu.siacs.conversations.xmpp.Jid;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

public class UpSettingsFragment extends XmppPreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_up, rootKey);
    }

    @Override
    public void onBackendConnected() {
        final ListPreference upAccounts = findPreference(UnifiedPushDistributor.PREFERENCE_ACCOUNT);
        final EditTextPreference pushServer = findPreference(UnifiedPushDistributor.PREFERENCE_PUSH_SERVER);
        if (upAccounts == null || pushServer == null) {
            throw new IllegalStateException();
        }
        pushServer.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof String string) {
                if (Strings.isNullOrEmpty(string) || isJidInvalid(string) || isHttpUri(string)) {
                    Toast.makeText(requireActivity(),R.string.invalid_jid,Toast.LENGTH_LONG).show();
                    return false;
                } else {
                    return true;
                }
            } else {
                Toast.makeText(requireActivity(),R.string.invalid_jid,Toast.LENGTH_LONG).show();
                return false;
            }
        });
        reconfigureUpAccountPreference(upAccounts);
    }

    private static boolean isJidInvalid(final String input) {
        try {
            final var jid = Jid.ofEscaped(input);
            return !jid.isBareJid();
        } catch (final IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean isHttpUri(final String input) {
        final URI uri;
        try {
            uri = new URI(input);
        } catch (final URISyntaxException e) {
            return false;
        }
        return Arrays.asList("http","https").contains(uri.getScheme());
    }


    private void reconfigureUpAccountPreference(final ListPreference listPreference) {
        final List<CharSequence> accounts =
                ImmutableList.copyOf(
                        Lists.transform(
                                requireService().getAccounts(),
                                a -> a.getJid().asBareJid().toEscapedString()));
        final ImmutableList.Builder<CharSequence> entries = new ImmutableList.Builder<>();
        final ImmutableList.Builder<CharSequence> entryValues = new ImmutableList.Builder<>();
        entries.add(getString(R.string.no_account_deactivated));
        entryValues.add("none");
        entries.addAll(accounts);
        entryValues.addAll(accounts);
        listPreference.setEntries(entries.build().toArray(new CharSequence[0]));
        listPreference.setEntryValues(entryValues.build().toArray(new CharSequence[0]));
        if (!accounts.contains(listPreference.getValue())) {
            listPreference.setValue("none");
        }
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        if (UnifiedPushDistributor.PREFERENCES.contains(key)) {
            final var service = requireService();
            if (service.reconfigurePushDistributor()) {
                service.renewUnifiedPushEndpoints();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.unified_push_distributor);
    }
}
