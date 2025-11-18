package eu.siacs.conversations.ui.activity;

import android.app.Notification;
import android.os.Bundle;

import androidx.databinding.DataBindingUtil;
import androidx.preference.PreferenceFragmentCompat;

import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivitySettingsBinding;
import eu.siacs.conversations.ui.Activities;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.fragment.settings.MainSettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.NotificationsSettingsFragment;
import eu.siacs.conversations.ui.fragment.settings.XmppPreferenceFragment;

import java.util.Collections;

public class SettingsActivity extends XmppActivity {

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        final var fragmentManager = getSupportFragmentManager();
        final var currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (currentFragment instanceof XmppPreferenceFragment xmppPreferenceFragment) {
            xmppPreferenceFragment.onBackendConnected();
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ActivitySettingsBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_settings);
        setSupportActionBar(binding.materialToolbar);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());

        final var intent = getIntent();
        final var categories = intent == null ? Collections.emptySet() : intent.getCategories();
        final PreferenceFragmentCompat preferenceFragment;
        if (ImmutableSet.of(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES)
                .equals(categories)) {
            preferenceFragment = new NotificationsSettingsFragment();
        } else {
            preferenceFragment = new MainSettingsFragment();
        }

        final var fragmentManager = getSupportFragmentManager();
        final var currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);
        if (currentFragment == null) {
            fragmentManager
                    .beginTransaction()
                    .replace(R.id.fragment_container, preferenceFragment)
                    .commit();
        }
        binding.materialToolbar.setNavigationOnClickListener(
                view -> {
                    if (fragmentManager.getBackStackEntryCount() == 0) {
                        finish();
                    } else {
                        fragmentManager.popBackStack();
                    }
                });
    }
}
