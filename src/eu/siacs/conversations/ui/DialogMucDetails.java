package eu.siacs.conversations.ui;


import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class DialogMucDetails extends DialogFragment {
	private XmppActivity activity;
	private Conversation conversation;
	private EditText mYourNick;
	private OnClickListener changeNickListener = new OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			MucOptions options = conversation.getMucOptions();
			String nick = mYourNick.getText().toString();
			if (!options.getNick().equals(nick)) {
				activity.xmppConnectionService.renameInMuc(conversation,nick,activity);
			}
		}
	};

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		this.activity = (XmppActivity) getActivity();
		AlertDialog.Builder builder = new AlertDialog.Builder(this.activity);
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View view = inflater.inflate(R.layout.muc_options, null);
		builder.setView(view);
		builder.setTitle(getString(R.string.conference_details));
		mYourNick = (EditText) view.findViewById(R.id.muc_your_nick);
		TextView mTextModerators = (TextView) view.findViewById(R.id.muc_moderators);
		TextView mTextParticipants = (TextView) view.findViewById(R.id.muc_participants);
		TextView mTextVisiotors = (TextView) view.findViewById(R.id.muc_visitors);
		TextView mTextModeratorsHead = (TextView) view.findViewById(R.id.muc_moderators_header);
		TextView mTextParticipantsHead = (TextView) view.findViewById(R.id.muc_participants_header);
		TextView mTextVisiotorsHead = (TextView) view.findViewById(R.id.muc_visitors_header);
		StringBuilder mods = new StringBuilder();
		StringBuilder participants = new StringBuilder();
		StringBuilder visitors = new StringBuilder();
		for(MucOptions.User user : conversation.getMucOptions().getUsers()) {
			if (user.getRole() == MucOptions.User.ROLE_MODERATOR) {
				if (mods.length()>=1) {
					mods.append("\n, "+user.getName());
				} else {
					mods.append(user.getName());
				}
			} else if (user.getRole() == MucOptions.User.ROLE_PARTICIPANT) {
				if (participants.length()>=1) {
					participants.append("\n, "+user.getName());
				} else {
					participants.append(user.getName());
				}
			} else {
				if (visitors.length()>=1) {
					visitors.append("\n, "+user.getName());
				} else {
					visitors.append(user.getName());
				}
			}
		}
		if (mods.length()>0) {
			mTextModerators.setText(mods.toString());
		} else {
			mTextModerators.setVisibility(View.GONE);
			mTextModeratorsHead.setVisibility(View.GONE);
		}
		if (participants.length()>0) {
			mTextParticipants.setText(participants.toString());
		} else {
			mTextParticipants.setVisibility(View.GONE);
			mTextParticipantsHead.setVisibility(View.GONE);
		}
		if (visitors.length()>0) {
			mTextVisiotors.setText(visitors.toString());
		} else {
			mTextVisiotors.setVisibility(View.GONE);
			mTextVisiotorsHead.setVisibility(View.GONE);
		}
		mYourNick.setText(conversation.getMucOptions().getNick());
		builder.setPositiveButton("Done", this.changeNickListener );
		builder.setNegativeButton("Cancel", null);
		return builder.create();
	}
	
	public void setConversation(Conversation conversation) {
		this.conversation = conversation;
	}
}
