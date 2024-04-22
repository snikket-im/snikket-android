package eu.siacs.conversations;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.BoolRes;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.common.base.Strings;

public class AppSettings {

    public static final String KEEP_FOREGROUND_SERVICE = "enable_foreground_service";
    public static final String AWAY_WHEN_SCREEN_IS_OFF = "away_when_screen_off";
    public static final String TREAT_VIBRATE_AS_SILENT = "treat_vibrate_as_silent";
    public static final String DND_ON_SILENT_MODE = "dnd_on_silent_mode";
    public static final String MANUALLY_CHANGE_PRESENCE = "manually_change_presence";
    public static final String BLIND_TRUST_BEFORE_VERIFICATION = "btbv";
    public static final String AUTOMATIC_MESSAGE_DELETION = "automatic_message_deletion";
    public static final String BROADCAST_LAST_ACTIVITY = "last_activity";
    public static final String THEME = "theme";
    public static final String DYNAMIC_COLORS = "dynamic_colors";
    public static final String SHOW_DYNAMIC_TAGS = "show_dynamic_tags";
    public static final String OMEMO = "omemo";
    public static final String ALLOW_SCREENSHOTS = "allow_screenshots";
    public static final String RINGTONE = "call_ringtone";
    public static final String BTBV = "btbv";

    public static final String CONFIRM_MESSAGES = "confirm_messages";
    public static final String ALLOW_MESSAGE_CORRECTION = "allow_message_correction";

    public static final String TRUST_SYSTEM_CA_STORE = "trust_system_ca_store";
    public static final String REQUIRE_CHANNEL_BINDING = "channel_binding_required";
    public static final String NOTIFICATION_RINGTONE = "notification_ringtone";
    public static final String NOTIFICATION_HEADS_UP = "notification_headsup";
    public static final String NOTIFICATION_VIBRATE = "vibrate_on_notification";
    public static final String NOTIFICATION_LED = "led";
    public static final String SHOW_CONNECTION_OPTIONS = "show_connection_options";
    public static final String USE_TOR = "use_tor";
    public static final String CHANNEL_DISCOVERY_METHOD = "channel_discovery_method";
    public static final String SEND_CRASH_REPORTS = "send_crash_reports";
    public static final String COLORFUL_CHAT_BUBBLES = "use_green_background";
    public static final String LARGE_FONT = "large_font";

    private static final String ACCEPT_INVITES_FROM_STRANGERS = "accept_invites_from_strangers";

    private final Context context;

    public AppSettings(final Context context) {
        this.context = context;
    }

    public Uri getRingtone() {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final String incomingCallRingtone =
                sharedPreferences.getString(
                        RINGTONE, context.getString(R.string.incoming_call_ringtone));
        return Strings.isNullOrEmpty(incomingCallRingtone) ? null : Uri.parse(incomingCallRingtone);
    }

    public void setRingtone(final Uri uri) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putString(RINGTONE, uri == null ? null : uri.toString()).apply();
    }

    public Uri getNotificationTone() {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        final String incomingCallRingtone =
                sharedPreferences.getString(
                        NOTIFICATION_RINGTONE, context.getString(R.string.notification_ringtone));
        return Strings.isNullOrEmpty(incomingCallRingtone) ? null : Uri.parse(incomingCallRingtone);
    }

    public void setNotificationTone(final Uri uri) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences
                .edit()
                .putString(NOTIFICATION_RINGTONE, uri == null ? null : uri.toString())
                .apply();
    }

    public boolean isBTBVEnabled() {
        return getBooleanPreference(BTBV, R.bool.btbv);
    }

    public boolean isTrustSystemCAStore() {
        return getBooleanPreference(TRUST_SYSTEM_CA_STORE, R.bool.trust_system_ca_store);
    }

    public boolean isAllowScreenshots() {
        return getBooleanPreference(ALLOW_SCREENSHOTS, R.bool.allow_screenshots);
    }

    public boolean isColorfulChatBubbles() {
        return getBooleanPreference(COLORFUL_CHAT_BUBBLES, R.bool.use_green_background);
    }

    public boolean isLargeFont() {
        return getBooleanPreference(LARGE_FONT, R.bool.large_font);
    }

    public boolean isUseTor() {
        return getBooleanPreference(USE_TOR, R.bool.use_tor);
    }

    public boolean isAcceptInvitesFromStrangers() {
        return getBooleanPreference(
                ACCEPT_INVITES_FROM_STRANGERS, R.bool.accept_invites_from_strangers);
    }

    private boolean getBooleanPreference(@NonNull final String name, @BoolRes int res) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getBoolean(name, context.getResources().getBoolean(res));
    }

    public String getOmemo() {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getString(
                OMEMO, context.getString(R.string.omemo_setting_default));
    }

    public boolean isSendCrashReports() {
        return getBooleanPreference(SEND_CRASH_REPORTS, R.bool.send_crash_reports);
    }

    public void setSendCrashReports(boolean value) {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putBoolean(SEND_CRASH_REPORTS, value).apply();
    }

    public boolean isRequireChannelBinding() {
        return getBooleanPreference(REQUIRE_CHANNEL_BINDING, R.bool.require_channel_binding);
    }
}
