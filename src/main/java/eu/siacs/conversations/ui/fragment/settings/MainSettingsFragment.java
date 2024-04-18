package eu.siacs.conversations.ui.fragment.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.ExportBackupService;

public class MainSettingsFragment extends PreferenceFragmentCompat {

    private static final String CREATE_BACKUP = "create_backup";

    private final ActivityResultLauncher<String> requestStorageForBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            startBackup();
                        } else {
                            Toast.makeText(
                                            requireActivity(),
                                            getString(
                                                    R.string.no_storage_permission,
                                                    getString(R.string.app_name)),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_main, rootKey);
        final var about = findPreference("about");
        final var connection = findPreference("connection");
        final var backup = findPreference(CREATE_BACKUP);
        if (about == null || connection == null || backup == null) {
            throw new IllegalStateException(
                    "The preference resource file is missing some preferences");
        }
        backup.setSummary(
                getString(
                        R.string.pref_create_backup_summary,
                        FileBackend.getBackupDirectory(requireContext()).getAbsolutePath()));
        backup.setOnPreferenceClickListener(this::onBackupPreferenceClicked);
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

    private boolean onBackupPreferenceClicked(final Preference preference) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestStorageForBackupLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                startBackup();
            }
        } else {
            startBackup();
        }
        return true;
    }

    private void startBackup() {
        ContextCompat.startForegroundService(
                requireContext(), new Intent(requireContext(), ExportBackupService.class));
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setMessage(R.string.backup_started_message);
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.title_activity_settings);
    }
}
