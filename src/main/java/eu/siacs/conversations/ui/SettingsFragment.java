package eu.siacs.conversations.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class SettingsFragment extends PreferenceFragment {

	//http://stackoverflow.com/questions/16374820/action-bar-home-button-not-functional-with-nested-preferencescreen/16800527#16800527
	private void initializeActionBar(PreferenceScreen preferenceScreen) {
		final Dialog dialog = preferenceScreen.getDialog();

		if (dialog != null) {
			View homeBtn = dialog.findViewById(android.R.id.home);

			if (homeBtn != null) {
				View.OnClickListener dismissDialogClickListener = new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						dialog.dismiss();
					}
				};

				ViewParent homeBtnContainer = homeBtn.getParent();

				if (homeBtnContainer instanceof FrameLayout) {
					ViewGroup containerParent = (ViewGroup) homeBtnContainer.getParent();
					if (containerParent instanceof LinearLayout) {
						((LinearLayout) containerParent).setOnClickListener(dismissDialogClickListener);
					} else {
						((FrameLayout) homeBtnContainer).setOnClickListener(dismissDialogClickListener);
					}
				} else {
					homeBtn.setOnClickListener(dismissDialogClickListener);
				}
			}
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);

		// Remove from standard preferences if the flag ONLY_INTERNAL_STORAGE is not true
		if (!Config.ONLY_INTERNAL_STORAGE) {
			PreferenceCategory mCategory = (PreferenceCategory) findPreference("security_options");
			Preference mPref1 = findPreference("clean_cache");
			Preference mPref2 = findPreference("clean_private_storage");
			mCategory.removePreference(mPref1);
			mCategory.removePreference(mPref2);
		}

	}

	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		super.onPreferenceTreeClick(preferenceScreen, preference);
		if (preference instanceof PreferenceScreen) {
			initializeActionBar((PreferenceScreen) preference);
		}
		return false;
	}
}
