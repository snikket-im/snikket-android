package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.preference.Preference;
import android.util.AttributeSet;

public class AboutPreference extends Preference {
	public AboutPreference(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
		setSummary();
	}

	public AboutPreference(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		setSummary();
	}

    @Override
    protected void onClick() {
        super.onClick();
        final Intent intent = new Intent(getContext(), AboutActivity.class);
        getContext().startActivity(intent);
    }

    private void setSummary() {
		if (getContext() != null && getContext().getPackageManager() != null) {
			final String packageName = getContext().getPackageName();
			final String versionName;
			try {
				versionName = getContext().getPackageManager().getPackageInfo(packageName, 0).versionName;
				setSummary("Conversations " + versionName);
			} catch (final PackageManager.NameNotFoundException e) {
				// Using try/catch as part of the logic is sort of like this:
				// https://xkcd.com/292/
			}
		}
	}
}

