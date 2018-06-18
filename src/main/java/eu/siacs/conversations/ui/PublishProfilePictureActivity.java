package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.util.Log;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.theartofdev.edmodo.cropper.CropImage;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.interfaces.OnAvatarPublication;
import eu.siacs.conversations.utils.PhoneHelper;

public class PublishProfilePictureActivity extends XmppActivity implements XmppConnectionService.OnAccountUpdate, OnAvatarPublication {

    private ImageView avatar;
    private TextView hintOrWarning;
    private TextView secondaryHint;
    private Button cancelButton;
    private Button publishButton;
    private Uri avatarUri;
    private Uri defaultUri;
    private Account account;
    private boolean support = false;
    private boolean publishing = false;
    private OnLongClickListener backToDefaultListener = new OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            avatarUri = defaultUri;
            loadImageIntoPreview(defaultUri);
            return true;
        }
    };
    private boolean mInitialAccountSetup;

    @Override
    public void onAvatarPublicationSucceeded() {
        runOnUiThread(() -> {
            if (mInitialAccountSetup) {
                Intent intent = new Intent(getApplicationContext(), StartConversationActivity.class);
                WelcomeActivity.addInviteUri(intent, getIntent());
                intent.putExtra("init", true);
                startActivity(intent);
            }
            Toast.makeText(PublishProfilePictureActivity.this,
                    R.string.avatar_has_been_published,
                    Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public void onAvatarPublicationFailed(int res) {
        runOnUiThread(() -> {
            hintOrWarning.setText(res);
            hintOrWarning.setTextColor(getWarningTextColor());
            hintOrWarning.setVisibility(View.VISIBLE);
            publishing = false;
            togglePublishButton(true, R.string.publish);
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publish_profile_picture);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());

        this.avatar = findViewById(R.id.account_image);
        this.cancelButton = findViewById(R.id.cancel_button);
        this.publishButton = findViewById(R.id.publish_button);
        this.hintOrWarning = findViewById(R.id.hint_or_warning);
        this.secondaryHint = findViewById(R.id.secondary_hint);
        this.publishButton.setOnClickListener(v -> {
            if (avatarUri != null) {
                publishing = true;
                togglePublishButton(false, R.string.publishing);
                xmppConnectionService.publishAvatar(account, avatarUri, this);
            }
        });
        this.cancelButton.setOnClickListener(v -> {
            if (mInitialAccountSetup) {
                Intent intent = new Intent(getApplicationContext(), StartConversationActivity.class);
                if (xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1) {
                    WelcomeActivity.addInviteUri(intent, getIntent());
                    intent.putExtra("init", true);
                }
                startActivity(intent);
            }
            finish();
        });
        this.avatar.setOnClickListener(v -> chooseAvatar());
        this.defaultUri = PhoneHelper.getProfilePictureUri(getApplicationContext());
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                this.avatarUri = result.getUri();
                if (xmppConnectionServiceBound) {
                    loadImageIntoPreview(this.avatarUri);
                }
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Exception error = result.getError();
                if (error != null) {
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void chooseAvatar() {
        CropImage.activity()
                .setOutputCompressFormat(Bitmap.CompressFormat.PNG)
                .setAspectRatio(1, 1)
                .setMinCropResultSize(Config.AVATAR_SIZE, Config.AVATAR_SIZE)
                .start(this);
    }

    @Override
    protected void onBackendConnected() {
        this.account = extractAccount(getIntent());
        if (this.account != null) {
            reloadAvatar();
        }
    }

    private void reloadAvatar() {
        this.support = this.account.getXmppConnection() != null && this.account.getXmppConnection().getFeatures().pep();
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
    protected void onStart() {
        super.onStart();
        if (getIntent() != null) {
            this.mInitialAccountSetup = getIntent().getBooleanExtra("setup", false);
        }
        if (this.mInitialAccountSetup) {
            this.cancelButton.setText(R.string.skip);
        }
    }

    protected void loadImageIntoPreview(Uri uri) {

        Bitmap bm = null;
        if (uri == null) {
            bm = avatarService().get(account, getPixel(192));
        } else {
            try {
                bm = xmppConnectionService.getFileBackend().cropCenterSquare(uri, getPixel(192));
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to load bitmap into image view", e);
            }
        }

        if (bm == null) {
            togglePublishButton(false, R.string.publish);
            this.hintOrWarning.setVisibility(View.VISIBLE);
            this.hintOrWarning.setTextColor(getWarningTextColor());
            this.hintOrWarning.setText(R.string.error_publish_avatar_converting);
            return;
        }
        this.avatar.setImageBitmap(bm);
        if (support) {
            togglePublishButton(uri != null, R.string.publish);
            this.hintOrWarning.setVisibility(View.INVISIBLE);
        } else {
            togglePublishButton(false, R.string.publish);
            this.hintOrWarning.setVisibility(View.VISIBLE);
            this.hintOrWarning.setTextColor(getWarningTextColor());
            if (account.getStatus() == Account.State.ONLINE) {
                this.hintOrWarning.setText(R.string.error_publish_avatar_no_server_support);
            } else {
                this.hintOrWarning.setText(R.string.error_publish_avatar_offline);
            }
        }
        if (this.defaultUri == null || this.defaultUri.equals(uri)) {
            this.secondaryHint.setVisibility(View.INVISIBLE);
            this.avatar.setOnLongClickListener(null);
        } else if (this.defaultUri != null) {
            this.secondaryHint.setVisibility(View.VISIBLE);
            this.avatar.setOnLongClickListener(this.backToDefaultListener);
        }
    }

    protected void togglePublishButton(boolean enabled, @StringRes int res) {
        final boolean status = enabled && !publishing;
        this.publishButton.setText(publishing ? R.string.publishing : res);
        this.publishButton.setEnabled(status);
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
