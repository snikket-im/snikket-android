package eu.siacs.conversations.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.PhoneHelper;

public class PublishProfilePictureActivity extends XmppActivity {
	
	private static final int REQUEST_CHOOSE_FILE = 0xac23;
	
	private ImageView avatar;
	private TextView explanation;
	private Button cancelButton;
	private Button publishButton;
	
	private Uri avatarUri;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_publish_profile_picture);
		this.avatar = (ImageView) findViewById(R.id.account_image);
		this.explanation = (TextView) findViewById(R.id.explanation);
		this.cancelButton = (Button) findViewById(R.id.cancel_button);
		this.publishButton = (Button) findViewById(R.id.publish_button);
		this.publishButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (avatarUri!=null) {
					xmppConnectionService.pushAvatar(null, avatarUri);
					finish();
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
		Log.d("xmppService","on activity result");
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_CHOOSE_FILE) {
				Log.d("xmppService","bla");
				this.avatarUri = data.getData();
			}
		}
	}

	@Override
	protected void onBackendConnected() {
		Log.d("xmppService","on backend connected");
		if (this.avatarUri == null) {
			avatarUri = PhoneHelper.getSefliUri(getApplicationContext());
		}
		loadImageIntoPreview(avatarUri);
		String explainText = getString(R.string.publish_avatar_explanation,"daniel@gultsch.de");
		this.explanation.setText(explainText);
	}
	
	protected void loadImageIntoPreview(Uri uri) {
		Bitmap bm = xmppConnectionService.getFileBackend().cropCenterSquare(uri, 384);
		this.avatar.setImageBitmap(bm);
		enablePublishButton();
	}
	
	protected void enablePublishButton() {
		this.publishButton.setEnabled(true);
		this.publishButton.setTextColor(getPrimaryTextColor());
	}

}
