package eu.siacs.conversations.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.support.annotation.BoolRes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.SettingsActivity;
import eu.siacs.conversations.ui.SettingsFragment;

public class Compatibility {

    private static final List<String> UNUSED_SETTINGS_POST_TWENTYSIX = Arrays.asList(
            SettingsActivity.KEEP_FOREGROUND_SERVICE,
            "led",
            "notification_ringtone",
            "notification_headsup",
            "vibrate_on_notification");
    private static final List<String> UNUESD_SETTINGS_PRE_TWENTYSIX = Collections.singletonList("more_notification_settings");


    public static boolean twentySix() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    private static boolean getBooleanPreference(Context context, String name, @BoolRes int res) {
        return getPreferences(context).getBoolean(name, context.getResources().getBoolean(res));
    }

    private static SharedPreferences getPreferences(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static boolean keepForegroundService(Context context) {
        return twentySix() || getBooleanPreference(context, SettingsActivity.KEEP_FOREGROUND_SERVICE, R.bool.enable_foreground_service);
    }

    public static void removeUnusedPreferences(SettingsFragment settingsFragment) {
        List<PreferenceCategory> categories = Arrays.asList(
                (PreferenceCategory) settingsFragment.findPreference("notification_category"),
                (PreferenceCategory) settingsFragment.findPreference("other_expert_category"));
        for (String key : (twentySix() ? UNUSED_SETTINGS_POST_TWENTYSIX : UNUESD_SETTINGS_PRE_TWENTYSIX)) {
            Preference preference = settingsFragment.findPreference(key);
            if (preference != null) {
                for (PreferenceCategory category : categories) {
                    if (category != null) {
                        category.removePreference(preference);
                    }
                }
            }
        }
    }
}
