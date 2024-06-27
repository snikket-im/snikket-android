package eu.siacs.conversations.ui.fragment.settings;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.common.base.Optional;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.activity.result.PickRingtone;
import eu.siacs.conversations.utils.Compatibility;

public class NotificationsSettingsFragment extends XmppPreferenceFragment {

    private final ActivityResultLauncher<Uri> pickNotificationToneLauncher =
            registerForActivityResult(
                    new PickRingtone(RingtoneManager.TYPE_NOTIFICATION),
                    result -> {
                        if (result == null) {
                            // do nothing. user aborted
                            return;
                        }
                        final Uri uri = PickRingtone.noneToNull(result);
                        appSettings().setNotificationTone(uri);
                        Log.i(Config.LOGTAG, "User set notification tone to " + uri);
                    });
    private final ActivityResultLauncher<Uri> pickRingtoneLauncher =
            registerForActivityResult(
                    new PickRingtone(RingtoneManager.TYPE_RINGTONE),
                    result -> {
                        if (result == null) {
                            // do nothing. user aborted
                            return;
                        }
                        final Uri uri = PickRingtone.noneToNull(result);
                        appSettings().setRingtone(uri);
                        Log.i(Config.LOGTAG, "User set ringtone to " + uri);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            NotificationService.recreateIncomingCallChannel(requireContext(), uri);
                        }
                    });

    @Override
    public void onCreatePreferences(
            @Nullable final Bundle savedInstanceState, final @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_notifications, rootKey);
        final var messageNotificationSettings = findPreference("message_notification_settings");
        final var fullscreenNotification = findPreference("fullscreen_notification");
        final var notificationRingtone = findPreference(AppSettings.NOTIFICATION_RINGTONE);
        final var notificationHeadsUp = findPreference(AppSettings.NOTIFICATION_HEADS_UP);
        final var notificationVibrate = findPreference(AppSettings.NOTIFICATION_VIBRATE);
        final var notificationLed = findPreference(AppSettings.NOTIFICATION_LED);
        final var foregroundService = findPreference(AppSettings.KEEP_FOREGROUND_SERVICE);
        if (messageNotificationSettings == null
                || fullscreenNotification == null
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
        fullscreenNotification.setOnPreferenceClickListener(this::manageAppUseFullScreen);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || requireContext()
                        .getSystemService(NotificationManager.class)
                        .canUseFullScreenIntent()) {
            fullscreenNotification.setVisible(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final var fullscreenNotification = findPreference("fullscreen_notification");
        if (fullscreenNotification == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || requireContext()
                        .getSystemService(NotificationManager.class)
                        .canUseFullScreenIntent()) {
            fullscreenNotification.setVisible(false);
        }
    }

    private boolean manageAppUseFullScreen(final Preference preference) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }
        final var intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
        intent.setData(Uri.parse(String.format("package:%s", requireContext().getPackageName())));
        try {
            startActivity(intent);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.unsupported_operation, Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        return true;
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
        final var key = preference.getKey();
        if (AppSettings.RINGTONE.equals(key)) {
            pickRingtone();
            return true;
        }
        if (AppSettings.NOTIFICATION_RINGTONE.equals(key)) {
            pickNotificationTone();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void pickNotificationTone() {
        final Uri uri = appSettings().getNotificationTone();
        Log.i(Config.LOGTAG, "current notification tone: " + uri);
        this.pickNotificationToneLauncher.launch(uri);
    }

    private void pickRingtone() {
        final Optional<Uri> channelRingtone;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelRingtone =
                    NotificationService.getCurrentIncomingCallChannel(requireContext())
                            .transform(channel -> PickRingtone.nullToNone(channel.getSound()));
        } else {
            channelRingtone = Optional.absent();
        }
        final Uri uri;
        if (channelRingtone.isPresent()) {
            uri = channelRingtone.get();
            Log.d(Config.LOGTAG, "ringtone came from channel");
        } else {
            uri = appSettings().getRingtone();
        }
        Log.i(Config.LOGTAG, "current ringtone: " + uri);
        try {
            this.pickRingtoneLauncher.launch(uri);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(requireActivity(), R.string.no_application_found, Toast.LENGTH_LONG)
                    .show();
        }
    }

    private AppSettings appSettings() {
        return new AppSettings(requireContext());
    }
}
