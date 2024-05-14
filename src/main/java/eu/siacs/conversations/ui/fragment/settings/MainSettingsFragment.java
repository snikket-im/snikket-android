package eu.siacs.conversations.ui.fragment.settings;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.google.common.base.Strings;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;

public class MainSettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        final var about = findPreference("about");
        final var connection = findPreference("connection");
        if (about == null || connection == null) {
            throw new IllegalStateException(
                    "The preference resource file is missing some preferences");
        }
        about.setTitle(getString(R.string.title_activity_about_x, BuildConfig.APP_NAME));
        about.setSummary(
                String.format(
                        "%s %s %s @ %s · %s · %s",
                        BuildConfig.APP_NAME,
                        BuildConfig.VERSION_NAME,
                        im.conversations.webrtc.BuildConfig.WEBRTC_VERSION,
                        Strings.nullToEmpty(Build.MANUFACTURER),
                        Strings.nullToEmpty(Build.DEVICE),
                        Strings.nullToEmpty(Build.VERSION.RELEASE)));
        if (ConnectionSettingsFragment.hideChannelDiscovery()) {
            connection.setSummary(R.string.pref_connection_summary);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.title_activity_settings);
    }
}
