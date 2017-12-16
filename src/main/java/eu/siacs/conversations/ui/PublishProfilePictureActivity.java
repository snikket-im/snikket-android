package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.soundcloud.android.crop.Crop;

import java.io.File;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class PublishProfilePictureActivity extends XmppActivity implements XmppConnectionService.OnAccountUpdate {

	private static final int REQUEST_CHOOSE_FILE_AND_CROP = 0xac23;
	private static final int REQUEST_CHOOSE_FILE = 0xac24;
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
	private UiCallback<Avatar> avatarPublication = new UiCallback<Avatar>() {

		@Override
		public void success(Avatar object) {
			runOnUiThread(() -> {
				if (mInitialAccountSetup) {
					Intent intent = new Intent(getApplicationContext(),
							StartConversationActivity.class);
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
		public void error(final int errorCode, Avatar object) {
			runOnUiThread(() -> {
				hintOrWarning.setText(errorCode);
				hintOrWarning.setTextColor(getWarningTextColor());
				hintOrWarning.setVisibility(View.VISIBLE);
				publishing = false;
				togglePublishButton(true,R.string.publish);
			});

		}

		@Override
		public void userInputRequried(PendingIntent pi, Avatar object) {
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_publish_profile_picture);
		this.avatar = findViewById(R.id.account_image);
		this.cancelButton = findViewById(R.id.cancel_button);
		this.publishButton = findViewById(R.id.publish_button);
		this.hintOrWarning = findViewById(R.id.hint_or_warning);
		this.secondaryHint = findViewById(R.id.secondary_hint);
		this.publishButton.setOnClickListener(v -> {
			if (avatarUri != null) {
				publishing = true;
				togglePublishButton(false,R.string.publishing);
				xmppConnectionService.publishAvatar(account, avatarUri, avatarPublication);
			}
		});
		this.cancelButton.setOnClickListener(v -> {
			if (mInitialAccountSetup) {
				Intent intent = new Intent(getApplicationContext(),
						StartConversationActivity.class);
				if (xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 1) {
					intent.putExtra("init", true);
				}
				startActivity(intent);
			}
			finish();
		});
		this.avatar.setOnClickListener(v -> {
			if (hasStoragePermission(REQUEST_CHOOSE_FILE)) {
				chooseAvatar(false);
			}

		});
		this.defaultUri = PhoneHelper.getProfilePictureUri(getApplicationContext());
	}

	private void chooseAvatar(boolean crop) {
		Intent attachFileIntent = new Intent();
		attachFileIntent.setType("image/*");
		attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
		Intent chooser = Intent.createChooser(attachFileIntent, getString(R.string.attach_file));
		startActivityForResult(chooser, crop ? REQUEST_CHOOSE_FILE_AND_CROP : REQUEST_CHOOSE_FILE);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
		if (grantResults.length > 0)
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (requestCode == REQUEST_CHOOSE_FILE_AND_CROP) {
					chooseAvatar(true);
				} else if (requestCode == REQUEST_CHOOSE_FILE) {
					chooseAvatar(false);
				}
			} else {
				Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
			}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.publish_avatar, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == R.id.action_crop_image) {
			if (hasStoragePermission(REQUEST_CHOOSE_FILE_AND_CROP)) {
				chooseAvatar(true);
			}
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			Uri source = data.getData();
			switch (requestCode) {
				case REQUEST_CHOOSE_FILE_AND_CROP:
					if (FileBackend.weOwnFile(this, source)) {
						Toast.makeText(this,R.string.security_error_invalid_file_access,Toast.LENGTH_SHORT).show();
						return;
					}
					String original = FileUtils.getPath(this, source);
					if (original != null) {
						source = Uri.parse("file://"+original);
					}
					Uri destination = Uri.fromFile(new File(getCacheDir(), "croppedAvatar"));
					final int size = getPixel(192);
					Crop.of(source, destination).asSquare().withMaxSize(size, size).start(this);
					break;
				case REQUEST_CHOOSE_FILE:
					if (FileBackend.weOwnFile(this, source)) {
						Toast.makeText(this,R.string.security_error_invalid_file_access,Toast.LENGTH_SHORT).show();
						return;
					}
					this.avatarUri = source;
					if (xmppConnectionServiceBound) {
						loadImageIntoPreview(this.avatarUri);
					}
					break;
				case Crop.REQUEST_CROP:
					this.avatarUri = Uri.fromFile(new File(getCacheDir(), "croppedAvatar"));
					if (xmppConnectionServiceBound) {
						loadImageIntoPreview(this.avatarUri);
					}
					break;
			}
		} else {
			if (requestCode == Crop.REQUEST_CROP  && data != null) {
				Throwable throwable = Crop.getError(data);
				if (throwable != null && throwable instanceof OutOfMemoryError) {
					Toast.makeText(this,R.string.selection_too_large, Toast.LENGTH_SHORT).show();
				}
			}
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
				Log.d(Config.LOGTAG,"unable to load bitmap into image view",e);
			}
		}

		if (bm == null) {
			togglePublishButton(false,R.string.publish);
			this.hintOrWarning.setVisibility(View.VISIBLE);
			this.hintOrWarning.setTextColor(getWarningTextColor());
			this.hintOrWarning.setText(R.string.error_publish_avatar_converting);
			return;
		}
		this.avatar.setImageBitmap(bm);
		if (support) {
			togglePublishButton(uri != null,R.string.publish);
			this.hintOrWarning.setVisibility(View.INVISIBLE);
		} else {
			togglePublishButton(false,R.string.publish);
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
		this.publishButton.setTextColor(status ? getPrimaryTextColor() : getSecondaryTextColor());
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
