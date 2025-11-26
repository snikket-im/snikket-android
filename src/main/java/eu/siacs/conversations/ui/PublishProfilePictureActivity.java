package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.databinding.DataBindingUtil;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityPublishProfilePictureBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.interfaces.OnAvatarPublication;
import eu.siacs.conversations.utils.PhoneHelper;

public class PublishProfilePictureActivity extends XmppActivity
        implements XmppConnectionService.OnAccountUpdate, OnAvatarPublication {

    public static final int REQUEST_CHOOSE_PICTURE = 0x1337;

    private ActivityPublishProfilePictureBinding binding;
    private Uri avatarUri;
    private Uri defaultUri;
    private Account account;
    private boolean support = false;
    private boolean publishing = false;
    private final OnLongClickListener backToDefaultListener =
            new OnLongClickListener() {

                @Override
                public boolean onLongClick(View v) {
                    avatarUri = defaultUri;
                    loadImageIntoPreview(defaultUri);
                    return true;
                }
            };
    private boolean mInitialAccountSetup;

    final ActivityResultLauncher<CropImageContractOptions> cropImage =
            registerForActivityResult(
                    new CropImageContract(),
                    cropResult -> {
                        if (cropResult.isSuccessful()) {
                            onAvatarPicked(cropResult.getUriContent());
                        }
                    });

    @Override
    public void onAvatarPublicationSucceeded() {
        runOnUiThread(
                () -> {
                    if (mInitialAccountSetup) {
                        Intent intent =
                                new Intent(
                                        getApplicationContext(), StartConversationActivity.class);
                        StartConversationActivity.addInviteUri(intent, getIntent());
                        intent.putExtra("init", true);
                        intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toString());
                        startActivity(intent);
                    }
                    Toast.makeText(
                                    PublishProfilePictureActivity.this,
                                    R.string.avatar_has_been_published,
                                    Toast.LENGTH_SHORT)
                            .show();
                    finish();
                });
    }

    @Override
    public void onAvatarPublicationFailed(final int res) {
        runOnUiThread(
                () -> {
                    this.binding.hintOrWarning.setText(res);
                    this.binding.hintOrWarning.setVisibility(View.VISIBLE);
                    this.publishing = false;
                    togglePublishButton(true, R.string.publish);
                });
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        this.binding =
                DataBindingUtil.setContentView(this, R.layout.activity_publish_profile_picture);

        setSupportActionBar(binding.toolbar);

        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());

        this.binding.publishButton.setOnClickListener(
                v -> {
                    final boolean open = !this.binding.contactOnly.isChecked();
                    final var uri = this.avatarUri;
                    if (uri == null) {
                        return;
                    }
                    publishing = true;
                    togglePublishButton(false, R.string.publishing);
                    xmppConnectionService.publishAvatarAsync(account, uri, open, this);
                });
        this.binding.cancelButton.setOnClickListener(
                v -> {
                    if (mInitialAccountSetup) {
                        final Intent intent =
                                new Intent(
                                        getApplicationContext(), StartConversationActivity.class);
                        if (xmppConnectionService != null
                                && xmppConnectionService.getAccounts().size() == 1) {
                            intent.putExtra("init", true);
                        }
                        StartConversationActivity.addInviteUri(intent, getIntent());
                        if (account != null) {
                            intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toString());
                        }
                        startActivity(intent);
                    }
                    finish();
                });
        this.binding.accountImage.setOnClickListener(v -> pickAvatar(null));
        this.defaultUri = PhoneHelper.getProfilePictureUri(getApplicationContext());
        if (savedInstanceState != null) {
            this.avatarUri = savedInstanceState.getParcelable("uri");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        getMenuInflater().inflate(R.menu.activity_publish_profile_picture, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_delete_avatar) {
            if (account != null) {
                deleteAvatar(account);
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void deleteAvatar(final Account account) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_avatar)
                .setMessage(R.string.delete_avatar_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.confirm,
                        (d, v) -> {
                            if (xmppConnectionService != null) {
                                xmppConnectionService.deleteAvatar(account);
                            }
                        })
                .create()
                .show();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (this.avatarUri != null) {
            outState.putParcelable("uri", this.avatarUri);
        }
        super.onSaveInstanceState(outState);
    }

    public void pickAvatar(final Uri image) {
        this.cropImage.launch(new CropImageContractOptions(image, getCropImageOptions()));
    }

    public static CropImageOptions getCropImageOptions() {
        final var cropImageOptions = new CropImageOptions();
        cropImageOptions.aspectRatioX = 1;
        cropImageOptions.aspectRatioY = 1;
        cropImageOptions.fixAspectRatio = true;
        cropImageOptions.outputCompressFormat = Bitmap.CompressFormat.PNG;
        cropImageOptions.imageSourceIncludeCamera = false;
        cropImageOptions.minCropResultHeight = Config.AVATAR_SIZE;
        cropImageOptions.minCropResultWidth = Config.AVATAR_SIZE;
        return cropImageOptions;
    }

    private void onAvatarPicked(final Uri uri) {
        Log.d(Config.LOGTAG, "onAvatarPicked(" + uri + ")");
        this.avatarUri = uri;
        if (xmppConnectionServiceBound) {
            loadImageIntoPreview(uri);
        } else {
            Log.d(Config.LOGTAG, "not ready during avatarPick");
        }
    }

    @Override
    protected void onBackendConnected() {
        this.account = extractAccount(getIntent());
        if (this.account != null) {
            reloadAvatar();
        }
    }

    private void reloadAvatar() {
        this.support =
                this.account.getXmppConnection() != null
                        && this.account.getXmppConnection().getFeatures().pep();
        if (this.avatarUri == null) {
            if (this.account.getAvatar() != null || this.defaultUri == null) {
                loadImageIntoPreview(null);
            } else {
                this.avatarUri = this.defaultUri;
                loadImageIntoPreview(this.defaultUri);
            }
        } else {
            loadImageIntoPreview(avatarUri);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        final Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        this.mInitialAccountSetup = intent.getBooleanExtra("setup", false);

        final var data = intent.getData();
        final var account = intent.getStringExtra(EXTRA_ACCOUNT);
        if (Intent.ACTION_ATTACH_DATA.equals(intent.getAction())
                && data != null
                && account != null) {
            pickAvatar(data);
            final var replacement = new Intent(Intent.ACTION_MAIN);
            replacement.putExtra(EXTRA_ACCOUNT, account);
            setIntent(replacement);
            return;
        }

        if (this.mInitialAccountSetup) {
            this.binding.cancelButton.setText(R.string.skip);
        }
        configureActionBar(getSupportActionBar(), !this.mInitialAccountSetup);
    }

    protected void loadImageIntoPreview(final Uri uri) {
        Log.d(Config.LOGTAG, "loadImageIntoPreview(" + uri + ")");
        final Bitmap bitmap;
        if (uri == null) {
            bitmap =
                    avatarService()
                            .get(
                                    account,
                                    (int) getResources().getDimension(R.dimen.publish_avatar_size));
        } else {
            bitmap =
                    xmppConnectionService
                            .getFileBackend()
                            .cropCenterSquare(
                                    uri,
                                    (int) getResources().getDimension(R.dimen.publish_avatar_size));
        }

        if (bitmap == null) {
            togglePublishButton(false, R.string.publish);
            this.binding.hintOrWarning.setVisibility(View.VISIBLE);
            this.binding.hintOrWarning.setText(R.string.error_publish_avatar_converting);
            return;
        }
        this.binding.accountImage.setImageBitmap(bitmap);
        if (support) {
            togglePublishButton(uri != null, R.string.publish);
            this.binding.hintOrWarning.setVisibility(View.INVISIBLE);
        } else {
            togglePublishButton(false, R.string.publish);
            this.binding.hintOrWarning.setVisibility(View.VISIBLE);
            if (account.getStatus() == Account.State.ONLINE) {
                this.binding.hintOrWarning.setText(R.string.error_publish_avatar_no_server_support);
            } else {
                this.binding.hintOrWarning.setText(R.string.error_publish_avatar_offline);
            }
        }
        if (this.defaultUri == null || this.defaultUri.equals(uri)) {
            this.binding.secondaryHint.setVisibility(View.INVISIBLE);
            this.binding.accountImage.setOnLongClickListener(null);
        } else if (this.defaultUri != null) {
            this.binding.secondaryHint.setVisibility(View.VISIBLE);
            this.binding.accountImage.setOnLongClickListener(this.backToDefaultListener);
        }
    }

    protected void togglePublishButton(boolean enabled, @StringRes int res) {
        final boolean status = enabled && !publishing;
        this.binding.publishButton.setText(publishing ? R.string.publishing : res);
        this.binding.publishButton.setEnabled(status);
    }

    public void refreshUiReal() {
        if (this.account != null) {
            reloadAvatar();
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }
}
