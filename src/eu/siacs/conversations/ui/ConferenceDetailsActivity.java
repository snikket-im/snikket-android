package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

import org.openintents.openpgp.util.OpenPgpUtils;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.OnRenameListener;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import android.app.PendingIntent;
import android.content.Context;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ConferenceDetailsActivity extends XmppActivity {
	public static final String ACTION_VIEW_MUC = "view_muc";
	private Conversation conversation;
	private TextView mYourNick;
	private ImageView mYourPhoto;
	private ImageButton mEditNickButton;
	private TextView mRoleAffiliaton;
	private TextView mFullJid;
	private TextView mAccountJid;
	private LinearLayout membersView;
	private LinearLayout mMoreDetails;
	private Button mInviteButton;
	private String uuid = null;

	private OnClickListener inviteListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			inviteToConversation(conversation);
		}
	};

	private List<User> users = new ArrayList<MucOptions.User>();
	private OnConversationUpdate onConvChanged = new OnConversationUpdate() {

		@Override
		public void onConversationUpdate() {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					populateView();
				}
			});
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_muc_details);
		mYourNick = (TextView) findViewById(R.id.muc_your_nick);
		mYourPhoto = (ImageView) findViewById(R.id.your_photo);
		mEditNickButton = (ImageButton) findViewById(R.id.edit_nick_button);
		mFullJid = (TextView) findViewById(R.id.muc_jabberid);
		membersView = (LinearLayout) findViewById(R.id.muc_members);
		mAccountJid = (TextView) findViewById(R.id.details_account);
		mMoreDetails = (LinearLayout) findViewById(R.id.muc_more_details);
		mMoreDetails.setVisibility(View.GONE);
		mInviteButton = (Button) findViewById(R.id.invite);
		mInviteButton.setOnClickListener(inviteListener);
		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		mEditNickButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				quickEdit(conversation.getMucOptions().getActualNick(),
						new OnValueEdited() {

							@Override
							public void onValueEdited(String value) {
								xmppConnectionService.renameInMuc(conversation,
										value);
							}
						});
			}
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem) {
		switch (menuItem.getItemId()) {
		case android.R.id.home:
			finish();
			break;
		case R.id.action_edit_subject:
			if (conversation != null) {
				quickEdit(conversation.getName(), new OnValueEdited() {

					@Override
					public void onValueEdited(String value) {
						MessagePacket packet = xmppConnectionService
								.getMessageGenerator().conferenceSubject(
										conversation, value);
						xmppConnectionService.sendMessagePacket(
								conversation.getAccount(), packet);
					}
				});
			}
			break;
		}
		return super.onOptionsItemSelected(menuItem);
	}

	public String getReadableRole(int role) {
		switch (role) {
		case User.ROLE_MODERATOR:
			return getString(R.string.moderator);
		case User.ROLE_PARTICIPANT:
			return getString(R.string.participant);
		case User.ROLE_VISITOR:
			return getString(R.string.visitor);
		default:
			return "";
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.muc_details, menu);
		return true;
	}

	@Override
	void onBackendConnected() {
		registerListener();
		if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
			this.uuid = getIntent().getExtras().getString("uuid");
		}
		if (uuid != null) {
			this.conversation = xmppConnectionService
					.findConversationByUuid(uuid);
			if (this.conversation != null) {
				populateView();
			}
		}
	}

	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnConversationListChangedListener();
		}
		super.onStop();
	}

	protected void registerListener() {
		xmppConnectionService
				.setOnConversationListChangedListener(this.onConvChanged);
		xmppConnectionService.setOnRenameListener(new OnRenameListener() {

			@Override
			public void onRename(final boolean success) {
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						populateView();
						if (success) {
							Toast.makeText(
									ConferenceDetailsActivity.this,
									getString(R.string.your_nick_has_been_changed),
									Toast.LENGTH_SHORT).show();
						} else {
							Toast.makeText(ConferenceDetailsActivity.this,
									getString(R.string.nick_in_use),
									Toast.LENGTH_SHORT).show();
						}
					}
				});
			}
		});
	}

	private void populateView() {
		mAccountJid.setText(getString(R.string.using_account, conversation
				.getAccount().getJid()));
		mYourPhoto.setImageBitmap(conversation.getAccount().getImage(this, 48));
		setTitle(conversation.getName());
		mFullJid.setText(conversation.getContactJid().split("/", 2)[0]);
		mYourNick.setText(conversation.getMucOptions().getActualNick());
		mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
		if (conversation.getMucOptions().online()) {
			mMoreDetails.setVisibility(View.VISIBLE);
			User self = conversation.getMucOptions().getSelf();
			switch (self.getAffiliation()) {
			case User.AFFILIATION_ADMIN:
				mRoleAffiliaton.setText(getReadableRole(self.getRole()) + " ("
						+ getString(R.string.admin) + ")");
				break;
			case User.AFFILIATION_OWNER:
				mRoleAffiliaton.setText(getReadableRole(self.getRole()) + " ("
						+ getString(R.string.owner) + ")");
				break;
			default:
				mRoleAffiliaton.setText(getReadableRole(self.getRole()));
				break;
			}
		}
		this.users.clear();
		this.users.addAll(conversation.getMucOptions().getUsers());
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		membersView.removeAllViews();
		Account account = conversation.getAccount();
		for (final User user : conversation.getMucOptions().getUsers()) {
			View view = (View) inflater.inflate(R.layout.contact, membersView,
					false);
			TextView name = (TextView) view
					.findViewById(R.id.contact_display_name);
			TextView key = (TextView) view.findViewById(R.id.key);
			TextView role = (TextView) view.findViewById(R.id.contact_jid);
			if (user.getPgpKeyId() != 0) {
				key.setVisibility(View.VISIBLE);
				key.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						viewPgpKey(user);
					}
				});
				key.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
			}
			Bitmap bm;
			if (user.getJid() != null) {
				Contact contact = account.getRoster().getContact(user.getJid());
				if (contact.showInRoster()) {
					bm = contact.getImage(48, this);
					name.setText(contact.getDisplayName());
					role.setText(user.getName() + " \u2022 "
							+ getReadableRole(user.getRole()));
				} else {
					bm = UIHelper.getContactPicture(user.getName(), 48, this,
							false);
					name.setText(user.getName());
					role.setText(getReadableRole(user.getRole()));
				}
			} else {
				bm = UIHelper
						.getContactPicture(user.getName(), 48, this, false);
				name.setText(user.getName());
				role.setText(getReadableRole(user.getRole()));
			}
			ImageView iv = (ImageView) view.findViewById(R.id.contact_photo);
			iv.setImageBitmap(bm);
			membersView.addView(view);
		}
	}

	private void viewPgpKey(User user) {
		PgpEngine pgp = xmppConnectionService.getPgpEngine();
		if (pgp != null) {
			PendingIntent intent = pgp.getIntentForKey(
					conversation.getAccount(), user.getPgpKeyId());
			if (intent != null) {
				try {
					startIntentSenderForResult(intent.getIntentSender(), 0,
							null, 0, 0, 0);
				} catch (SendIntentException e) {

				}
			}
		}
	}
}
