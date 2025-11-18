package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;

import com.google.common.base.Strings;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;

public class AboutPreference extends Preference {
    public AboutPreference(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setSummaryAndTitle(context);
    }

    public AboutPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setSummaryAndTitle(context);
    }

    private void setSummaryAndTitle(final Context context) {
        setSummary(String.format("%s %s %s (%s)", BuildConfig.APP_NAME, BuildConfig.VERSION_NAME, im.conversations.webrtc.BuildConfig.WEBRTC_VERSION, Strings.nullToEmpty(Build.DEVICE)));
        setTitle(context.getString(R.string.title_activity_about_x, BuildConfig.APP_NAME));
    }

    @Override
    protected void onClick() {
        super.onClick();
        final Intent intent = new Intent(getContext(), AboutActivity.class);
        getContext().startActivity(intent);
    }
}
