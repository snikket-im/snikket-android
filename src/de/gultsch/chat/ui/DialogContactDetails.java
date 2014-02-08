package de.gultsch.chat.ui;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Presences;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
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
		TextView contactJid = (TextView) view.findViewById(R.id.details_contact_jid);
		TextView accountJid = (TextView) view.findViewById(R.id.details_account);
		TextView status = (TextView) view.findViewById(R.id.details_contact_status);
		CheckBox send = (CheckBox) view.findViewById(R.id.details_send_presence);
		CheckBox receive = (CheckBox) view.findViewById(R.id.details_receive_presence);
		
		boolean subscriptionSend = false;
		boolean subscriptionReceive = false;
		if (contact.getSubscription().equals("both")) {
			subscriptionReceive = true;
			subscriptionSend = true;
		} else if (contact.getSubscription().equals("from")) {
			subscriptionSend = true;
		} else if (contact.getSubscription().equals("to")) {
			subscriptionReceive = true;
		}
		
		switch (contact.getMostAvailableStatus()) {
		case Presences.CHAT:
			status.setText("free to chat");
			break;
		case Presences.ONLINE:
			status.setText("online");
			break;
		case Presences.AWAY:
			status.setText("away");
			break;
		case Presences.XA:
			status.setText("extended away");
			break;
		case Presences.DND:
			status.setText("do not disturb");
			break;
		case Presences.OFFLINE:
			status.setText("offline");
			break;
		default:
			status.setText("offline");
			break;
		}
		
		send.setChecked(subscriptionSend);
		receive.setChecked(subscriptionReceive);
		contactJid.setText(contact.getJid());
		accountJid.setText(contact.getAccount().getJid());
		
		builder.setView(view);
		builder.setTitle(contact.getDisplayName());
		
		builder.setNeutralButton("Done", null);
		builder.setPositiveButton("Remove from roster", null);
		return builder.create();
	}
}
