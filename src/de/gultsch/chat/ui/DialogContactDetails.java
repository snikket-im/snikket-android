package de.gultsch.chat.ui;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Presences;
import de.gultsch.chat.utils.UIHelper;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

public class DialogContactDetails extends DialogFragment {
	
	private Contact contact = null;
	boolean displayingInRoster = false;
	
	public void setContact(Contact contact) {
		this.contact = contact;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_contact_details, null);
		TextView contactJid = (TextView) view.findViewById(R.id.details_contactjid);
		TextView accountJid = (TextView) view.findViewById(R.id.details_account);
		TextView status = (TextView) view.findViewById(R.id.details_contactstatus);
		CheckBox send = (CheckBox) view.findViewById(R.id.details_send_presence);
		CheckBox receive = (CheckBox) view.findViewById(R.id.details_receive_presence);
		ImageView contactPhoto = (ImageView) view.findViewById(R.id.details_contact_picture);
		
		boolean subscriptionSend = false;
		boolean subscriptionReceive = false;
		if (contact.getSubscription()!=null) {
			if (contact.getSubscription().equals("both")) {
				subscriptionReceive = true;
				subscriptionSend = true;
			} else if (contact.getSubscription().equals("from")) {
				subscriptionSend = true;
			} else if (contact.getSubscription().equals("to")) {
				subscriptionReceive = true;
			}
		}
		
		switch (contact.getMostAvailableStatus()) {
		case Presences.CHAT:
			status.setText("free to chat");
			status.setTextColor(0xFF83b600);
			break;
		case Presences.ONLINE:
			status.setText("online");
			status.setTextColor(0xFF83b600);
			break;
		case Presences.AWAY:
			status.setText("away");
			status.setTextColor(0xFFffa713);
			break;
		case Presences.XA:
			status.setText("extended away");
			status.setTextColor(0xFFffa713);
			break;
		case Presences.DND:
			status.setText("do not disturb");
			status.setTextColor(0xFFe92727);
			break;
		case Presences.OFFLINE:
			status.setText("offline");
			status.setTextColor(0xFFe92727);
			break;
		default:
			status.setText("offline");
			status.setTextColor(0xFFe92727);
			break;
		}
		
		send.setChecked(subscriptionSend);
		receive.setChecked(subscriptionReceive);
		contactJid.setText(contact.getJid());
		accountJid.setText(contact.getAccount().getJid());
		
		if (contact.getProfilePhoto()!=null) {
			contactPhoto.setImageURI(Uri.parse(contact.getProfilePhoto()));
		} else {
			contactPhoto.setImageBitmap(UIHelper.getUnknownContactPicture(contact.getDisplayName(), 300));
		}
		
		builder.setView(view);
		builder.setTitle(contact.getDisplayName());
		
		builder.setNeutralButton("Done", null);
		builder.setPositiveButton("Remove from roster", null);
		return builder.create();
	}
}
