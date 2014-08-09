package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class PublishProfilePictureActivity extends XmppActivity {

	private static final int REQUEST_CHOOSE_FILE = 0xac23;

	private ImageView avatar;
	private TextView accountTextView;
	private TextView hintOrWarning;
	private TextView secondaryHint;
	private Button cancelButton;
	private Button publishButton;

	private Uri avatarUri;
	private Uri defaultUri;

	private Account account;
	
	private boolean support = false;

	private UiCallback<Avatar> avatarPublication = new UiCallback<Avatar>() {

		@Override
		public void success(Avatar object) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					finish();
				}
			});
		}

		@Override
		public void error(final int errorCode, Avatar object) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					hintOrWarning.setText(errorCode);
					hintOrWarning.setTextColor(getWarningTextColor());
					publishButton.setText(R.string.publish_avatar);
					enablePublishButton();
				}
			});

		}

		@Override
		public void userInputRequried(PendingIntent pi, Avatar object) {
		}
	};

	private OnLongClickListener backToDefaultListener = new OnLongClickListener() {

		@Override
		public boolean onLongClick(View v) {
			avatarUri = defaultUri;
			loadImageIntoPreview(defaultUri);
			return true;
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_publish_profile_picture);
		this.avatar = (ImageView) findViewById(R.id.account_image);
		this.cancelButton = (Button) findViewById(R.id.cancel_button);
		this.publishButton = (Button) findViewById(R.id.publish_button);
		this.accountTextView = (TextView) findViewById(R.id.account);
		this.hintOrWarning = (TextView) findViewById(R.id.hint_or_warning);
		this.secondaryHint = (TextView) findViewById(R.id.secondary_hint);
		this.publishButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (avatarUri != null) {
					publishButton.setText(R.string.publishing);
					disablePublishButton();
					xmppConnectionService.publishAvatar(account, avatarUri,
							avatarPublication);
				}
			}
		});
		this.cancelButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				finish();
			}
		});
		this.avatar.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent attachFileIntent = new Intent();
				attachFileIntent.setType("image/*");
				attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
				Intent chooser = Intent.createChooser(attachFileIntent,
						getString(R.string.attach_file));
				startActivityForResult(chooser, REQUEST_CHOOSE_FILE);
			}
		});
		this.defaultUri = PhoneHelper.getSefliUri(getApplicationContext());
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_CHOOSE_FILE) {
				this.avatarUri = data.getData();
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		super.onOptionsItemSelected(menuItem);
		switch (menuItem.getItemId()) {
		case android.R.id.home:
			finish();
			break;
		}
		return true;
	}

	@Override
	protected void onBackendConnected() {
		if (getIntent() != null) {
			String jid = getIntent().getStringExtra("account");
			if (jid != null) {
				this.account = xmppConnectionService.findAccountByJid(jid);
				if (this.account.getXmppConnection() != null) {
					this.support = this.account.getXmppConnection().getFeatures().pubsub();
				}
				if (this.avatarUri == null) {
					if (this.account.getAvatar() != null) {
						this.avatar.setImageBitmap(this.account.getImage(
								getApplicationContext(), 384));
						this.avatar
								.setOnLongClickListener(this.backToDefaultListener);
					} else {
						if (this.defaultUri != null) {
							this.avatarUri = this.defaultUri;
							loadImageIntoPreview(this.defaultUri);
						}
					}
				} else {
					loadImageIntoPreview(avatarUri);
				}
				this.accountTextView.setText(this.account.getJid());
			}
		}

	}

	protected void loadImageIntoPreview(Uri uri) {
		Bitmap bm = xmppConnectionService.getFileBackend().cropCenterSquare(
				uri, 384);
		this.avatar.setImageBitmap(bm);
		if (support) {
			enablePublishButton();
			this.publishButton.setText(R.string.publish_avatar);
			this.hintOrWarning.setText(R.string.publish_avatar_explanation);
			this.hintOrWarning.setTextColor(getPrimaryTextColor());
		} else {
			disablePublishButton();
			this.hintOrWarning.setTextColor(getWarningTextColor());
			this.hintOrWarning.setText(R.string.error_publish_avatar_no_server_support);
		}
		if (this.defaultUri != null && uri.equals(this.defaultUri)) {
			this.secondaryHint.setVisibility(View.INVISIBLE);
			this.avatar.setOnLongClickListener(null);
		} else {
			this.secondaryHint.setVisibility(View.VISIBLE);
			this.avatar.setOnLongClickListener(this.backToDefaultListener);
		}
	}

	protected void enablePublishButton() {
		this.publishButton.setEnabled(true);
		this.publishButton.setTextColor(getPrimaryTextColor());
	}

	protected void disablePublishButton() {
		this.publishButton.setEnabled(false);
		this.publishButton.setTextColor(getSecondaryTextColor());
	}

}
