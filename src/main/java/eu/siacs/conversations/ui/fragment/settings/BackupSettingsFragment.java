package eu.siacs.conversations.ui.fragment.settings;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.worker.ExportBackupWorker;

import java.util.concurrent.TimeUnit;

public class BackupSettingsFragment extends XmppPreferenceFragment {

    public static final String CREATE_ONE_OFF_BACKUP = "create_one_off_backup";
    private static final String RECURRING_BACKUP = "recurring_backup";

    private final ActivityResultLauncher<String> requestStorageForBackupLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            startOneOffBackup();
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
        setPreferencesFromResource(R.xml.preferences_backup, rootKey);
        final var createOneOffBackup = findPreference(CREATE_ONE_OFF_BACKUP);
        final ListPreference recurringBackup = findPreference(RECURRING_BACKUP);
        final var backupDirectory = findPreference("backup_directory");
        if (createOneOffBackup == null || recurringBackup == null || backupDirectory == null) {
            throw new IllegalStateException(
                    "The preference resource file is missing some preferences");
        }
        backupDirectory.setSummary(
                getString(
                        R.string.pref_create_backup_summary,
                        FileBackend.getBackupDirectory(requireContext()).getAbsolutePath()));
        createOneOffBackup.setOnPreferenceClickListener(this::onBackupPreferenceClicked);
        final int[] choices = getResources().getIntArray(R.array.recurring_backup_values);
        final CharSequence[] entries = new CharSequence[choices.length];
        final CharSequence[] entryValues = new CharSequence[choices.length];
        for (int i = 0; i < choices.length; ++i) {
            entryValues[i] = String.valueOf(choices[i]);
            entries[i] = timeframeValueToName(requireContext(), choices[i]);
        }
        recurringBackup.setEntries(entries);
        recurringBackup.setEntryValues(entryValues);
        recurringBackup.setSummaryProvider(new TimeframeSummaryProvider());
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        if (RECURRING_BACKUP.equals(key)) {
            final var sharedPreferences = getPreferenceManager().getSharedPreferences();
            if (sharedPreferences == null) {
                return;
            }
            final Long recurringBackupInterval =
                    Longs.tryParse(
                            Strings.nullToEmpty(
                                    sharedPreferences.getString(RECURRING_BACKUP, null)));
            if (recurringBackupInterval == null) {
                return;
            }
            Log.d(
                    Config.LOGTAG,
                    "recurring backup interval changed to: " + recurringBackupInterval);
            final var workManager = WorkManager.getInstance(requireContext());
            if (recurringBackupInterval <= 0) {
                workManager.cancelUniqueWork(RECURRING_BACKUP);
            } else {
                final Constraints constraints =
                        new Constraints.Builder()
                                .setRequiresBatteryNotLow(true)
                                .setRequiresStorageNotLow(true)
                                .build();

                final PeriodicWorkRequest periodicWorkRequest =
                        new PeriodicWorkRequest.Builder(
                                        ExportBackupWorker.class,
                                        recurringBackupInterval,
                                        TimeUnit.SECONDS)
                                .setConstraints(constraints)
                                .setInputData(
                                        new Data.Builder()
                                                .putBoolean("recurring_backup", true)
                                                .build())
                                .build();
                workManager.enqueueUniquePeriodicWork(
                        RECURRING_BACKUP, ExistingPeriodicWorkPolicy.UPDATE, periodicWorkRequest);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.backup);
    }

    private boolean onBackupPreferenceClicked(final Preference preference) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestStorageForBackupLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                startOneOffBackup();
            }
        } else {
            startOneOffBackup();
        }
        return true;
    }

    private void startOneOffBackup() {
        final OneTimeWorkRequest exportBackupWorkRequest =
                new OneTimeWorkRequest.Builder(ExportBackupWorker.class)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build();
        WorkManager.getInstance(requireContext())
                .enqueueUniqueWork(
                        CREATE_ONE_OFF_BACKUP, ExistingWorkPolicy.KEEP, exportBackupWorkRequest);
        final MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(requireActivity());
        builder.setMessage(R.string.backup_started_message);
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }
}
