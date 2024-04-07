package eu.siacs.conversations.ui.fragment.settings;

import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.activity.result.PickRingtone;
import eu.siacs.conversations.utils.Compatibility;

public class NotificationsSettingsFragment extends XmppPreferenceFragment {

    private final ActivityResultLauncher<Uri> pickRingtoneLauncher =
            registerForActivityResult(
                    new PickRingtone(RingtoneManager.TYPE_RINGTONE),
                    result -> {
                        if (result == null) {
                            // do nothing. user aborted
                            return;
                        }
                        final Uri uri = PickRingtone.noneToNull(result);
                        setRingtone(uri);
                        Log.i(Config.LOGTAG, "User set ringtone to " + uri);
                    });

    @Override
    public void onCreatePreferences(
            @Nullable final Bundle savedInstanceState, final @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_notifications, rootKey);
        final var messageNotificationSettings = findPreference("message_notification_settings");
        final var notificationRingtone = findPreference(AppSettings.NOTIFICATION_RINGTONE);
        final var notificationHeadsUp = findPreference(AppSettings.NOTIFICATION_HEADS_UP);
        final var notificationVibrate = findPreference(AppSettings.NOTIFICATION_VIBRATE);
        final var notificationLed = findPreference(AppSettings.NOTIFICATION_LED);
        final var foregroundService = findPreference(AppSettings.KEEP_FOREGROUND_SERVICE);
        if (messageNotificationSettings == null
                || notificationRingtone == null
                || notificationHeadsUp == null
                || notificationVibrate == null
                || notificationLed == null
                || foregroundService == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }
        if (Compatibility.runsTwentySix()) {
            notificationRingtone.setVisible(false);
            notificationHeadsUp.setVisible(false);
            notificationVibrate.setVisible(false);
            notificationLed.setVisible(false);
            foregroundService.setVisible(false);
        } else {
            messageNotificationSettings.setVisible(false);
        }
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        if (key.equals(AppSettings.KEEP_FOREGROUND_SERVICE)) {
            requireService().toggleForegroundService();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.notifications);
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (AppSettings.RINGTONE.equals(preference.getKey())) {
            pickRingtone();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void pickRingtone() {
        final Uri uri = appSettings().getRingtone();
        Log.i(Config.LOGTAG, "current ringtone: " + uri);
        this.pickRingtoneLauncher.launch(uri);
    }

    private void setRingtone(final Uri uri) {
        appSettings().setRingtone(uri);
    }

    private AppSettings appSettings() {
        return new AppSettings(requireContext());
    }
}
