package eu.siacs.conversations.ui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
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

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnMucRosterUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class ConferenceDetailsActivity extends XmppActivity implements OnConversationUpdate, OnMucRosterUpdate, XmppConnectionService.OnAffiliationChanged {
	public static final String ACTION_VIEW_MUC = "view_muc";
	private Conversation mConversation;
	private OnClickListener inviteListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			inviteToConversation(mConversation);
		}
	};
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
	private List<User> users = new ArrayList<>();
	private User mSelectedUser = null;

	private boolean mAdvancedMode = false;

	private UiCallback<Conversation> renameCallback = new UiCallback<Conversation>() {
		@Override
		public void success(Conversation object) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(ConferenceDetailsActivity.this,getString(R.string.your_nick_has_been_changed),Toast.LENGTH_SHORT).show();
					updateView();
				}
			});

		}

		@Override
		public void error(final int errorCode, Conversation object) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(ConferenceDetailsActivity.this,getString(errorCode),Toast.LENGTH_SHORT).show();
				}
			});
		}

		@Override
		public void userInputRequried(PendingIntent pi, Conversation object) {

		}
	};

	@Override
	public void onConversationUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				updateView();
			}
		});
	}

	@Override
	public void onMucRosterUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				updateView();
			}
		});
	}

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
		if (getActionBar() != null) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
		mEditNickButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				quickEdit(mConversation.getMucOptions().getActualNick(),
						new OnValueEdited() {

							@Override
							public void onValueEdited(String value) {
								xmppConnectionService.renameInMuc(mConversation,value,renameCallback);
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
				if (mConversation != null) {
					quickEdit(mConversation.getName(), new OnValueEdited() {

						@Override
						public void onValueEdited(String value) {
							MessagePacket packet = xmppConnectionService
								.getMessageGenerator().conferenceSubject(
										mConversation, value);
							xmppConnectionService.sendMessagePacket(
									mConversation.getAccount(), packet);
						}
					});
				}
				break;
			case R.id.action_save_as_bookmark:
				saveAsBookmark();
				break;
			case R.id.action_delete_bookmark:
				deleteBookmark();
				break;
			case R.id.action_advanced_mode:
				this.mAdvancedMode = !menuItem.isChecked();
				menuItem.setChecked(this.mAdvancedMode);
				invalidateOptionsMenu();
				updateView();
				break;
		}
		return super.onOptionsItemSelected(menuItem);
	}

	@Override
	protected String getShareableUri() {
		if (mConversation != null) {
			return "xmpp:" + mConversation.getJid().toBareJid().toString() + "?join";
		} else {
			return "";
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem menuItemSaveBookmark = menu.findItem(R.id.action_save_as_bookmark);
		MenuItem menuItemDeleteBookmark = menu.findItem(R.id.action_delete_bookmark);
		MenuItem menuItemAdvancedMode = menu.findItem(R.id.action_advanced_mode);
		menuItemAdvancedMode.setChecked(mAdvancedMode);
		Account account = mConversation.getAccount();
		if (account.hasBookmarkFor(mConversation.getJid().toBareJid())) {
			menuItemSaveBookmark.setVisible(false);
			menuItemDeleteBookmark.setVisible(true);
		} else {
			menuItemDeleteBookmark.setVisible(false);
			menuItemSaveBookmark.setVisible(true);
		}
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.muc_details, menu);
		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		Object tag = v.getTag();
		if (tag instanceof User) {
			getMenuInflater().inflate(R.menu.muc_details_context,menu);
			final User user = (User) tag;
			final User self = mConversation.getMucOptions().getSelf();
			this.mSelectedUser = user;
			String name;
			if (user.getJid() != null) {
				final Contact contact = user.getContact();
				if (contact != null) {
					name = contact.getDisplayName();
				} else if (user.getJid() != null) {
					name = user.getJid().toBareJid().toString();
				} else {
					name = user.getName();
				}
				menu.setHeaderTitle(name);
				MenuItem startConversation = menu.findItem(R.id.start_conversation);
				MenuItem giveMembership = menu.findItem(R.id.give_membership);
				MenuItem removeMembership = menu.findItem(R.id.remove_membership);
				MenuItem giveAdminPrivileges = menu.findItem(R.id.give_admin_privileges);
				MenuItem removeAdminPrivileges = menu.findItem(R.id.remove_admin_privileges);
				MenuItem removeFromRoom = menu.findItem(R.id.remove_from_room);
				startConversation.setVisible(true);
				if (self.getAffiliation().ranks(MucOptions.Affiliation.ADMIN) &&
						self.getAffiliation().outranks(user.getAffiliation())) {
					if (mAdvancedMode) {
						if (user.getAffiliation() == MucOptions.Affiliation.NONE) {
							giveMembership.setVisible(true);
						} else {
							removeMembership.setVisible(true);
						}
					}
					if (user.getAffiliation() != MucOptions.Affiliation.ADMIN) {
						giveAdminPrivileges.setVisible(true);
					} else {
						removeAdminPrivileges.setVisible(true);
					}
					removeFromRoom.setVisible(true);
				}
			}

		}
		super.onCreateContextMenu(menu,v,menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.start_conversation:
				startConversation(mSelectedUser);
				return true;
			case R.id.give_admin_privileges:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.ADMIN,this);
				return true;
			case R.id.give_membership:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.MEMBER,this);
				return true;
			case R.id.remove_membership:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.NONE,this);
				return true;
			case R.id.remove_admin_privileges:
				xmppConnectionService.changeAffiliationInConference(mConversation,mSelectedUser.getJid(), MucOptions.Affiliation.MEMBER,this);
				return true;
			case R.id.remove_from_room:
				removeFromRoom(mSelectedUser);
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}

	private void removeFromRoom(final User user) {
		if (mConversation.getMucOptions().membersOnly()) {
			xmppConnectionService.changeAffiliationInConference(mConversation,user.getJid(), MucOptions.Affiliation.NONE,this);
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.ban_user_from_conference);
			builder.setMessage(getString(R.string.removing_from_public_conference,user.getName()));
			builder.setNegativeButton(R.string.cancel,null);
			builder.setPositiveButton(R.string.ban_now,new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					xmppConnectionService.changeAffiliationInConference(mConversation,user.getJid(), MucOptions.Affiliation.OUTCAST,ConferenceDetailsActivity.this);
				}
			});
			builder.create().show();
		}
	}

	protected void startConversation(User user) {
		if (user.getJid() != null) {
			Conversation conversation = xmppConnectionService.findOrCreateConversation(this.mConversation.getAccount(),user.getJid().toBareJid(),false);
			switchToConversation(conversation);
		}
	}

	protected void saveAsBookmark() {
		Account account = mConversation.getAccount();
		Bookmark bookmark = new Bookmark(account, mConversation.getJid().toBareJid());
		if (!mConversation.getJid().isBareJid()) {
			bookmark.setNick(mConversation.getJid().getResourcepart());
		}
		bookmark.setAutojoin(true);
		account.getBookmarks().add(bookmark);
		xmppConnectionService.pushBookmarks(account);
		mConversation.setBookmark(bookmark);
	}

	protected void deleteBookmark() {
		Account account = mConversation.getAccount();
		Bookmark bookmark = mConversation.getBookmark();
		bookmark.unregisterConversation();
		account.getBookmarks().remove(bookmark);
		xmppConnectionService.pushBookmarks(account);
	}

	@Override
	void onBackendConnected() {
		if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
			this.uuid = getIntent().getExtras().getString("uuid");
		}
		if (uuid != null) {
			this.mConversation = xmppConnectionService
				.findConversationByUuid(uuid);
			if (this.mConversation != null) {
				updateView();
			}
		}
	}

	private void updateView() {
		mAccountJid.setText(getString(R.string.using_account, mConversation
					.getAccount().getJid().toBareJid()));
		mYourPhoto.setImageBitmap(avatarService().get(mConversation.getAccount(), getPixel(48)));
		setTitle(mConversation.getName());
		mFullJid.setText(mConversation.getJid().toBareJid().toString());
		mYourNick.setText(mConversation.getMucOptions().getActualNick());
		mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
		if (mConversation.getMucOptions().online()) {
			mMoreDetails.setVisibility(View.VISIBLE);
			User self = mConversation.getMucOptions().getSelf();
			final String status = getStatus(self);
			if (status != null) {
				mRoleAffiliaton.setVisibility(View.VISIBLE);
				mRoleAffiliaton.setText(status);
			} else {
				mRoleAffiliaton.setVisibility(View.GONE);
			}
		}
		this.users.clear();
		this.users.addAll(mConversation.getMucOptions().getUsers());
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		membersView.removeAllViews();
		for (final User user : mConversation.getMucOptions().getUsers()) {
			View view = inflater.inflate(R.layout.contact, membersView,
					false);
			this.setListItemBackgroundOnView(view);
			view.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					highlightInMuc(mConversation, user.getName());
				}
			});
			registerForContextMenu(view);
			view.setTag(user);
			TextView tvDisplayName = (TextView) view.findViewById(R.id.contact_display_name);
			TextView tvKey = (TextView) view.findViewById(R.id.key);
			TextView tvStatus = (TextView) view.findViewById(R.id.contact_jid);
			if (mAdvancedMode && user.getPgpKeyId() != 0) {
				tvKey.setVisibility(View.VISIBLE);
				tvKey.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						viewPgpKey(user);
					}
				});
				tvKey.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
			}
			Bitmap bm;
			Contact contact = user.getContact();
			if (contact != null) {
				bm = avatarService().get(contact, getPixel(48));
				tvDisplayName.setText(contact.getDisplayName());
				tvStatus.setText(user.getName() + " \u2022 " + getStatus(user));
			} else {
				bm = avatarService().get(user.getName(), getPixel(48));
				tvDisplayName.setText(user.getName());
				tvStatus.setText(getStatus(user));

			}
			ImageView iv = (ImageView) view.findViewById(R.id.contact_photo);
			iv.setImageBitmap(bm);
			membersView.addView(view);
			if (mConversation.getMucOptions().canInvite()) {
				mInviteButton.setVisibility(View.VISIBLE);
			} else {
				mInviteButton.setVisibility(View.GONE);
			}
		}
	}

	private String getStatus(User user) {
		if (mAdvancedMode) {
			StringBuilder builder = new StringBuilder();
			builder.append(getString(user.getAffiliation().getResId()));
			builder.append(" (");
			builder.append(getString(user.getRole().getResId()));
			builder.append(')');
			return builder.toString();
		} else {
			return getString(user.getAffiliation().getResId());
		}
	}

	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void setListItemBackgroundOnView(View view) {
		int sdk = android.os.Build.VERSION.SDK_INT;
		if (sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
			view.setBackgroundDrawable(getResources().getDrawable(R.drawable.greybackground));
		} else {
			view.setBackground(getResources().getDrawable(R.drawable.greybackground));
		}
	}

	private void viewPgpKey(User user) {
		PgpEngine pgp = xmppConnectionService.getPgpEngine();
		if (pgp != null) {
			PendingIntent intent = pgp.getIntentForKey(
					mConversation.getAccount(), user.getPgpKeyId());
			if (intent != null) {
				try {
					startIntentSenderForResult(intent.getIntentSender(), 0,
							null, 0, 0, 0);
				} catch (SendIntentException ignored) {

				}
			}
		}
	}

	@Override
	public void onAffiliationChangedSuccessful(Jid jid) {

	}

	@Override
	public void onAffiliationChangeFailed(Jid jid, int resId) {

	}
}
