package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityImportBackupBinding;
import eu.siacs.conversations.databinding.DialogEnterPasswordBinding;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.ui.adapter.BackupFileAdapter;
import eu.siacs.conversations.utils.BackupFile;
import eu.siacs.conversations.utils.BackupFileHeader;
import eu.siacs.conversations.worker.ImportBackupWorker;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ImportBackupActivity extends ActionBarActivity
        implements BackupFileAdapter.OnItemClickedListener {

    private ActivityImportBackupBinding binding;

    private BackupFileAdapter backupFileAdapter;

    private LiveData<Boolean> inProgressImport;
    private Uri currentRestoreDialog;
    private UUID currentWorkRequest;

    private final ActivityResultLauncher<String[]> requestPermissions =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> {
                        if (results.containsValue(Boolean.TRUE)) {
                            loadBackupFiles();
                        }
                    });

    private final ActivityResultLauncher<String> openBackup =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> openBackupFileFromUri(uri, false));

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_import_backup);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);

        final var workManager = WorkManager.getInstance(this);
        final var imports =
                workManager.getWorkInfosByTagLiveData(ImportBackupWorker.TAG_IMPORT_BACKUP);
        this.inProgressImport =
                Transformations.map(
                        imports, infos -> Iterables.any(infos, i -> !i.getState().isFinished()));

        this.inProgressImport.observe(
                this, inProgress -> setLoadingState(Boolean.TRUE.equals(inProgress)));

        if (savedInstanceState != null) {
            final var currentWorkRequest = savedInstanceState.getString("current-work-request");
            if (currentWorkRequest != null) {
                this.currentWorkRequest = UUID.fromString(currentWorkRequest);
            }
            final var currentRestoreDialog = savedInstanceState.getString("current-restore-dialog");
            if (currentRestoreDialog != null) {
                this.currentRestoreDialog = Uri.parse(currentRestoreDialog);
            }
        }
        monitorWorkRequest(this.currentWorkRequest);

        this.backupFileAdapter = new BackupFileAdapter();
        this.binding.list.setAdapter(this.backupFileAdapter);
        this.backupFileAdapter.setOnItemClickedListener(this);
        if (this.currentRestoreDialog != null) {
            openBackupFileFromUri(this.currentRestoreDialog, false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.import_backup, menu);
        final MenuItem openBackup = menu.findItem(R.id.action_open_backup_file);
        final var inProgress =
                this.inProgressImport == null ? null : this.inProgressImport.getValue();
        openBackup.setVisible(!Boolean.TRUE.equals(inProgress));
        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle bundle) {
        if (this.currentWorkRequest != null) {
            bundle.putString("current-work-request", this.currentWorkRequest.toString());
        }
        if (this.currentRestoreDialog != null) {
            bundle.putString("current-restore-dialog", this.currentRestoreDialog.toString());
        }
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onStart() {
        super.onStart();

        final var intent = getIntent();
        final var action = intent == null ? null : intent.getAction();
        final var data = intent == null ? null : intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            openBackupFileFromUri(data, true);
            setIntent(new Intent(Intent.ACTION_MAIN));
            return;
        }

        final List<String> desiredPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            desiredPermission =
                    ImmutableList.of(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU) {
            desiredPermission =
                    ImmutableList.of(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            desiredPermission = ImmutableList.of(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        final Set<String> declaredPermission = getDeclaredPermission();
        if (declaredPermission.containsAll(desiredPermission)) {
            requestPermissions.launch(desiredPermission.toArray(new String[0]));
        } else {
            Log.d(Config.LOGTAG, "Manifest is lacking some desired permission. not requesting");
        }
    }

    private Set<String> getDeclaredPermission() {
        final String[] permissions;
        try {
            permissions =
                    getPackageManager()
                            .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS)
                            .requestedPermissions;
        } catch (final PackageManager.NameNotFoundException e) {
            return Collections.emptySet();
        }
        if (permissions == null) {
            return Collections.emptySet();
        }
        return ImmutableSet.copyOf(permissions);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private void loadBackupFiles() {
        final var future = BackupFile.listAsync(getApplicationContext());
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(List<BackupFile> files) {
                        runOnUiThread(() -> backupFileAdapter.setFiles(files));
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {}
                },
                ContextCompat.getMainExecutor(getApplication()));
    }

    @Override
    public void onClick(final BackupFile backupFile) {
        showEnterPasswordDialog(backupFile, false);
    }

    private void openBackupFileFromUri(final Uri uri, final boolean finishOnCancel) {
        final var backupFileFuture = BackupFile.readAsync(this, uri);
        Futures.addCallback(
                backupFileFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final BackupFile backupFile) {
                        if (QuickConversationsService.isQuicksy()) {
                            if (!backupFile
                                    .getHeader()
                                    .getJid()
                                    .getDomain()
                                    .equals(Config.QUICKSY_DOMAIN)) {
                                Snackbar.make(
                                                binding.coordinator,
                                                R.string.non_quicksy_backup,
                                                Snackbar.LENGTH_LONG)
                                        .show();
                                return;
                            }
                        }
                        showEnterPasswordDialog(backupFile, finishOnCancel);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        Log.d(Config.LOGTAG, "could not open backup file " + uri, throwable);
                        showBackupThrowable(throwable);
                    }
                },
                ContextCompat.getMainExecutor(getApplication()));
    }

    private void showBackupThrowable(final Throwable throwable) {
        if (throwable instanceof BackupFileHeader.OutdatedBackupFileVersion) {
            Snackbar.make(
                            binding.coordinator,
                            R.string.outdated_backup_file_format,
                            Snackbar.LENGTH_LONG)
                    .show();
        } else if (throwable instanceof IOException
                || throwable instanceof IllegalArgumentException) {
            Snackbar.make(binding.coordinator, R.string.not_a_backup_file, Snackbar.LENGTH_LONG)
                    .show();
        } else if (throwable instanceof SecurityException e) {
            Log.d(Config.LOGTAG, "not able to parse backup file", e);
            Snackbar.make(
                            binding.coordinator,
                            R.string.sharing_application_not_grant_permission,
                            Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private void showEnterPasswordDialog(
            final BackupFile backupFile, final boolean finishOnCancel) {
        this.currentRestoreDialog = backupFile.getUri();
        final DialogEnterPasswordBinding enterPasswordBinding =
                DataBindingUtil.inflate(
                        LayoutInflater.from(this), R.layout.dialog_enter_password, null, false);
        Log.d(Config.LOGTAG, "attempting to import " + backupFile.getUri());
        enterPasswordBinding.explain.setText(
                getString(
                        R.string.enter_password_to_restore,
                        backupFile.getHeader().getJid().toString()));
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(enterPasswordBinding.getRoot());
        builder.setTitle(R.string.enter_password);
        builder.setNegativeButton(
                R.string.cancel,
                (dialog, which) -> {
                    this.currentRestoreDialog = null;
                    if (finishOnCancel) {
                        finish();
                    }
                });
        builder.setPositiveButton(R.string.restore, null);
        builder.setCancelable(false);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener((d) -> onDialogShow(backupFile, d, enterPasswordBinding));
        dialog.show();
    }

    private void onDialogShow(
            final BackupFile backupFile,
            final DialogInterface d,
            final DialogEnterPasswordBinding enterPasswordBinding) {
        if (d instanceof AlertDialog alertDialog) {
            alertDialog
                    .getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(v -> onRestoreClick(backupFile, d, enterPasswordBinding));
        }
    }

    private void onRestoreClick(
            final BackupFile backupFile,
            final DialogInterface d,
            final DialogEnterPasswordBinding enterPasswordBinding) {
        final String password = enterPasswordBinding.accountPassword.getEditableText().toString();
        if (password.isEmpty()) {
            enterPasswordBinding.accountPasswordLayout.setError(
                    getString(R.string.please_enter_password));
            return;
        }

        importBackup(backupFile, password, enterPasswordBinding.includeKeys.isChecked());
        d.dismiss();
    }

    private void importBackup(
            final BackupFile backupFile, final String password, final boolean includeOmemo) {
        final OneTimeWorkRequest importBackupWorkRequest =
                new OneTimeWorkRequest.Builder(ImportBackupWorker.class)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setInputData(
                                ImportBackupWorker.data(
                                        password, backupFile.getUri(), includeOmemo))
                        .addTag(ImportBackupWorker.TAG_IMPORT_BACKUP)
                        .build();

        final var id = importBackupWorkRequest.getId();
        this.currentWorkRequest = id;
        monitorWorkRequest(id);

        final var workManager = WorkManager.getInstance(this);
        workManager.enqueue(importBackupWorkRequest);
    }

    private void monitorWorkRequest(final UUID uuid) {
        if (uuid == null) {
            return;
        }
        Log.d(Config.LOGTAG, "monitorWorkRequest(" + uuid + ")");
        final var workInfoLiveData = WorkManager.getInstance(this).getWorkInfoByIdLiveData(uuid);
        workInfoLiveData.observe(
                this,
                workInfo -> {
                    final var state = workInfo.getState();
                    if (state.isFinished()) {
                        this.currentWorkRequest = null;
                    }
                    if (state == WorkInfo.State.FAILED) {
                        final var data = workInfo.getOutputData();
                        final var reason =
                                ImportBackupWorker.Reason.valueOfOrGeneric(
                                        data.getString("reason"));
                        switch (reason) {
                            case DECRYPTION_FAILED -> onBackupDecryptionFailed();
                            case ACCOUNT_ALREADY_EXISTS -> onAccountAlreadySetup();
                            default -> onBackupRestoreFailed();
                        }
                    } else if (state == WorkInfo.State.SUCCEEDED) {
                        onBackupRestored();
                    }
                });
    }

    private void setLoadingState(final boolean loadingState) {
        Log.d(Config.LOGTAG, "setLoadingState(" + loadingState + ")");
        binding.coordinator.setVisibility(loadingState ? View.GONE : View.VISIBLE);
        binding.inProgress.setVisibility(loadingState ? View.VISIBLE : View.GONE);
        setTitle(loadingState ? R.string.restoring_backup : R.string.restore_backup);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        configureActionBar(getSupportActionBar(), !loadingState);
        invalidateOptionsMenu();
    }

    private void onAccountAlreadySetup() {
        Snackbar.make(binding.coordinator, R.string.account_already_setup, Snackbar.LENGTH_LONG)
                .show();
    }

    private void onBackupRestored() {
        final var intent = new Intent(this, ConversationActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void onBackupDecryptionFailed() {
        Snackbar.make(binding.coordinator, R.string.unable_to_decrypt_backup, Snackbar.LENGTH_LONG)
                .show();
    }

    private void onBackupRestoreFailed() {
        Snackbar.make(binding.coordinator, R.string.unable_to_restore_backup, Snackbar.LENGTH_LONG)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_open_backup_file) {
            this.openBackup.launch("*/*");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
