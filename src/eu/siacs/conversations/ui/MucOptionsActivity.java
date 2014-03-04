package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.utils.UIHelper;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MucOptionsActivity extends XmppActivity {
	public static final String ACTION_VIEW_MUC = "view_muc";
	private XmppActivity activity = this;
	private Conversation conversation;
	private EditText mYourNick;
	private TextView mRoleAffiliaton;
	private TextView mFullJid;
	private LinearLayout membersView;
	private TextView mTextParticipantsHead;
	private LinearLayout mMoreDetails;
	private String uuid = null;
	private ArrayAdapter<User> contactsAdapter;
	private OnClickListener changeNickListener = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			Log.d("gultsch","on click change muc");
			MucOptions options = conversation.getMucOptions();
			String nick = mYourNick.getText().toString();
			if (!options.getNick().equals(nick)) {
				Log.d("gultsch","call to change muc");
				xmppConnectionService.renameInMuc(conversation, nick);
				finish();
			}
		}
	};
	private List<User> users = new ArrayList<MucOptions.User>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent().getAction().equals(ACTION_VIEW_MUC)) {
			this.uuid = getIntent().getExtras().getString("uuid");
		}
		setContentView(R.layout.muc_options);
		mYourNick = (EditText) findViewById(R.id.muc_your_nick);
		mTextParticipantsHead = (TextView) findViewById(R.id.muc_participants_header);
		mFullJid = (TextView) findViewById(R.id.muc_jabberid);
		ImageButton imageButton = (ImageButton) findViewById(R.id.muc_edit_nick);
		imageButton.setOnClickListener(this.changeNickListener);
		membersView = (LinearLayout) findViewById(R.id.muc_members);
		mMoreDetails = (LinearLayout) findViewById(R.id.muc_more_details);
		mMoreDetails.setVisibility(View.GONE);
		contactsAdapter = new ArrayAdapter<MucOptions.User>(this,
				R.layout.contact, users) {
			@Override
			public View getView(int position, View view, ViewGroup parent) {
				LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				User contact = getItem(position);
				if (view == null) {
					view = (View) inflater.inflate(R.layout.contact, null);
				}

				((TextView) view.findViewById(R.id.contact_display_name))
						.setText(getItem(position).getName());
				TextView role = (TextView) view.findViewById(R.id.contact_jid);
				role.setText(getReadableRole(contact.getRole()));
				ImageView imageView = (ImageView) view
						.findViewById(R.id.contact_photo);
				imageView.setImageBitmap(UIHelper.getUnknownContactPicture(
						getItem(position).getName(), 90));
				return view;
			}
		};
	}

	public void setConversation(Conversation conversation) {
		this.conversation = conversation;
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
	void onBackendConnected() {
		if (uuid != null) {
			for (Conversation mConv : xmppConnectionService.getConversations()) {
				if (mConv.getUuid().equals(uuid)) {
					this.conversation = mConv;
				}
			}
			if (this.conversation != null) {
				mFullJid.setText(conversation.getContactJid().split("/")[0]);
				mYourNick.setText(conversation.getMucOptions().getNick());
				mRoleAffiliaton = (TextView) findViewById(R.id.muc_role);
				if (conversation.getMucOptions().online()) {
					mMoreDetails.setVisibility(View.VISIBLE);
					User self = conversation.getMucOptions().getSelf();
					switch (self.getAffiliation()) {
					case User.AFFILIATION_ADMIN:
						mRoleAffiliaton.setText(getReadableRole(self.getRole())
								+ " (Admin)");
						break;
					case User.AFFILIATION_OWNER:
						mRoleAffiliaton.setText(getReadableRole(self.getRole())
								+ " (Owner)");
						break;
					default:
						mRoleAffiliaton
								.setText(getReadableRole(self.getRole()));
						break;
					}
				}
				this.users.clear();
				this.users.addAll(conversation.getMucOptions().getUsers());
				contactsAdapter.notifyDataSetChanged();
				LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				for(User contact : conversation.getMucOptions().getUsers()) {
					View view = (View) inflater.inflate(R.layout.contact, null);

					((TextView) view.findViewById(R.id.contact_display_name))
							.setText(contact.getName());
					TextView role = (TextView) view.findViewById(R.id.contact_jid);
					role.setText(getReadableRole(contact.getRole()));
					ImageView imageView = (ImageView) view
							.findViewById(R.id.contact_photo);
					imageView.setImageBitmap(UIHelper.getUnknownContactPicture(contact.getName(), 90));
					membersView.addView(view);
				}
			}
		}
	}
}
