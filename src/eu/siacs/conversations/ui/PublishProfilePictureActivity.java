package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
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
	private Button cancelButton;
	private Button publishButton;

	private Uri avatarUri;

	private Account account;

	private UiCallback<Avatar> avatarPublication = new UiCallback<Avatar>() {

		@Override
		public void success(Avatar object) {
			finish();
		}

		@Override
		public void error(int errorCode, Avatar object) {
			// TODO Auto-generated method stub

		}

		@Override
		public void userInputRequried(PendingIntent pi, Avatar object) {
			// TODO Auto-generated method stub

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
		this.publishButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (avatarUri != null) {
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
				if (this.avatarUri == null) {
					avatarUri = PhoneHelper
							.getSefliUri(getApplicationContext());
				}
				loadImageIntoPreview(avatarUri);
				this.accountTextView.setText(this.account.getJid());
			}
		}

	}

	protected void loadImageIntoPreview(Uri uri) {
		Bitmap bm = xmppConnectionService.getFileBackend().cropCenterSquare(
				uri, 384);
		this.avatar.setImageBitmap(bm);
		enablePublishButton();
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
