package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityImportBackupBinding;
import eu.siacs.conversations.databinding.DialogEnterPasswordBinding;
import eu.siacs.conversations.services.ImportBackupService;
import eu.siacs.conversations.ui.adapter.BackupFileAdapter;
import eu.siacs.conversations.utils.BackupFileHeader;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ImportBackupActivity extends ActionBarActivity
        implements ServiceConnection,
                ImportBackupService.OnBackupFilesLoaded,
                BackupFileAdapter.OnItemClickedListener,
                ImportBackupService.OnBackupProcessed {

    private ActivityImportBackupBinding binding;

    private BackupFileAdapter backupFileAdapter;
    private ImportBackupService service;

    private boolean mLoadingState = false;
    private final ActivityResultLauncher<String[]> requestPermissions =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    results -> {
                        if (results.containsValue(Boolean.TRUE)) {
                            final var service = this.service;
                            if (service == null) {
                                return;
                            }
                            service.loadBackupFiles(this);
                        }
                    });

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_import_backup);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);
        setLoadingState(
                savedInstanceState != null
                        && savedInstanceState.getBoolean("loading_state", false));
        this.backupFileAdapter = new BackupFileAdapter();
        this.binding.list.setAdapter(this.backupFileAdapter);
        this.backupFileAdapter.setOnItemClickedListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.import_backup, menu);
        final MenuItem openBackup = menu.findItem(R.id.action_open_backup_file);
        openBackup.setVisible(!this.mLoadingState);
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putBoolean("loading_state", this.mLoadingState);
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onStart() {

        super.onStart();
        bindService(new Intent(this, ImportBackupService.class), this, Context.BIND_AUTO_CREATE);
        final Intent intent = getIntent();
        if (intent != null
                && Intent.ACTION_VIEW.equals(intent.getAction())
                && !this.mLoadingState) {
            Uri uri = intent.getData();
            if (uri != null) {
                openBackupFileFromUri(uri, true);
                return;
            }
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
        return ImmutableSet.copyOf(permissions);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.service != null) {
            this.service.removeOnBackupProcessedListener(this);
        }
        unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ImportBackupService.ImportBackupServiceBinder binder =
                (ImportBackupService.ImportBackupServiceBinder) service;
        this.service = binder.getService();
        this.service.addOnBackupProcessedListener(this);
        setLoadingState(this.service.getLoadingState());
        this.service.loadBackupFiles(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        this.service = null;
    }

    @Override
    public void onBackupFilesLoaded(final List<ImportBackupService.BackupFile> files) {
        runOnUiThread(() -> backupFileAdapter.setFiles(files));
    }

    @Override
    public void onClick(final ImportBackupService.BackupFile backupFile) {
        showEnterPasswordDialog(backupFile, false);
    }

    private void openBackupFileFromUri(final Uri uri, final boolean finishOnCancel) {
        try {
            final ImportBackupService.BackupFile backupFile =
                    ImportBackupService.BackupFile.read(this, uri);
            showEnterPasswordDialog(backupFile, finishOnCancel);
        } catch (final BackupFileHeader.OutdatedBackupFileVersion e) {
            Snackbar.make(
                            binding.coordinator,
                            R.string.outdated_backup_file_format,
                            Snackbar.LENGTH_LONG)
                    .show();
        } catch (final IOException | IllegalArgumentException e) {
            Log.d(Config.LOGTAG, "unable to open backup file " + uri, e);
            Snackbar.make(binding.coordinator, R.string.not_a_backup_file, Snackbar.LENGTH_LONG)
                    .show();
        } catch (final SecurityException e) {
            Snackbar.make(
                            binding.coordinator,
                            R.string.sharing_application_not_grant_permission,
                            Snackbar.LENGTH_LONG)
                    .show();
        }
    }

    private void showEnterPasswordDialog(
            final ImportBackupService.BackupFile backupFile, final boolean finishOnCancel) {
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
                    if (finishOnCancel) {
                        finish();
                    }
                });
        builder.setPositiveButton(R.string.restore, null);
        builder.setCancelable(false);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(
                (d) -> {
                    dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                            .setOnClickListener(
                                    v -> {
                                        final String password =
                                                enterPasswordBinding
                                                        .accountPassword
                                                        .getEditableText()
                                                        .toString();
                                        if (password.isEmpty()) {
                                            enterPasswordBinding.accountPasswordLayout.setError(
                                                    getString(R.string.please_enter_password));
                                            return;
                                        }
                                        final Uri uri = backupFile.getUri();
                                        Intent intent = new Intent(this, ImportBackupService.class);
                                        intent.setAction(Intent.ACTION_SEND);
                                        intent.putExtra("password", password);
                                        if ("file".equals(uri.getScheme())) {
                                            intent.putExtra("file", uri.getPath());
                                        } else {
                                            intent.setData(uri);
                                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        }
                                        setLoadingState(true);
                                        ContextCompat.startForegroundService(this, intent);
                                        d.dismiss();
                                    });
                });
        dialog.show();
    }

    private void setLoadingState(final boolean loadingState) {
        binding.coordinator.setVisibility(loadingState ? View.GONE : View.VISIBLE);
        binding.inProgress.setVisibility(loadingState ? View.VISIBLE : View.GONE);
        setTitle(loadingState ? R.string.restoring_backup : R.string.restore_backup);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        configureActionBar(getSupportActionBar(), !loadingState);
        this.mLoadingState = loadingState;
        invalidateOptionsMenu();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK) {
            if (requestCode == 0xbac) {
                openBackupFileFromUri(intent.getData(), false);
            }
        }
    }

    @Override
    public void onAccountAlreadySetup() {
        runOnUiThread(
                () -> {
                    setLoadingState(false);
                    Snackbar.make(
                                    binding.coordinator,
                                    R.string.account_already_setup,
                                    Snackbar.LENGTH_LONG)
                            .show();
                });
    }

    @Override
    public void onBackupRestored() {
        runOnUiThread(
                () -> {
                    Intent intent = new Intent(this, ConversationActivity.class);
                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    | Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    @Override
    public void onBackupDecryptionFailed() {
        runOnUiThread(
                () -> {
                    setLoadingState(false);
                    Snackbar.make(
                                    binding.coordinator,
                                    R.string.unable_to_decrypt_backup,
                                    Snackbar.LENGTH_LONG)
                            .show();
                });
    }

    @Override
    public void onBackupRestoreFailed() {
        runOnUiThread(
                () -> {
                    setLoadingState(false);
                    Snackbar.make(
                                    binding.coordinator,
                                    R.string.unable_to_restore_backup,
                                    Snackbar.LENGTH_LONG)
                            .show();
                });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_open_backup_file) {
            openBackupFile();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openBackupFile() {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(
                Intent.createChooser(intent, getString(R.string.open_backup)), 0xbac);
    }
}
