package de.gultsch.chat.ui;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Presences;
import de.gultsch.chat.utils.UIHelper;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.QuickContactBadge;
import android.widget.TextView;

public class DialogContactDetails extends DialogFragment {
	
	private Contact contact = null;
	boolean displayingInRoster = false;
	
	private DialogContactDetails mDetailsDialog = this;
	private XmppActivity activity;
	
	private CheckBox send;
	private CheckBox receive;
	
	private DialogInterface.OnClickListener askRemoveFromRoster = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle("Delete from roster");
			builder.setMessage("Do you want to delete "+contact.getJid()+" from your roster. The conversation assoziated with this account will not be removed.");
			builder.setNegativeButton("Cancel", null);
			builder.setPositiveButton("Delete",removeFromRoster);
			builder.create().show();
		}
	};
	
	private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			activity.xmppConnectionService.deleteContact(contact);
			mDetailsDialog.dismiss();
		}
	};
	
	private DialogInterface.OnClickListener addToPhonebook = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
			intent.setType(Contacts.CONTENT_ITEM_TYPE);
			intent.putExtra(Intents.Insert.IM_HANDLE,contact.getJid());
			intent.putExtra(Intents.Insert.IM_PROTOCOL,CommonDataKinds.Im.PROTOCOL_JABBER);
			intent.putExtra("finishActivityOnSaveCompleted", true);
			getActivity().startActivityForResult(intent,ConversationActivity.INSERT_CONTACT);
			mDetailsDialog.dismiss();
		}
	};
	
	private DialogInterface.OnClickListener updateSubscriptions = new DialogInterface.OnClickListener() {
		
		@Override
		public void onClick(DialogInterface dialog, int which) {
			boolean needsUpdating = false;
			if (contact.getSubscriptionOption(Contact.Subscription.FROM)) {
				if (!send.isChecked()) {
					contact.resetSubscriptionOption(Contact.Subscription.FROM);
					contact.resetSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
					activity.xmppConnectionService.stopPresenceUpdatesTo(contact);
					needsUpdating=true;
				}
			} else {
				if (contact.getSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT)) {
					if (!send.isChecked()) {
						contact.resetSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
						needsUpdating=true;
					}
				} else {
					if (send.isChecked()) {
						contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
						needsUpdating=true;
					}
				}
			}
			if (contact.getSubscriptionOption(Contact.Subscription.TO)) {
				if (!receive.isChecked()) {
					contact.resetSubscriptionOption(Contact.Subscription.TO);
					activity.xmppConnectionService.stopPresenceUpdatesFrom(contact);
					needsUpdating=true;
				}
			} else {
				if (contact.getSubscriptionOption(Contact.Subscription.ASKING)) {
					if (!receive.isChecked()) {
						contact.resetSubscriptionOption(Contact.Subscription.ASKING);
						activity.xmppConnectionService.stopPresenceUpdatesFrom(contact);
						needsUpdating=true;
					}
				} else {
					if (receive.isChecked()) {
						contact.setSubscriptionOption(Contact.Subscription.ASKING);
						activity.xmppConnectionService.requestPresenceUpdatesFrom(contact);
						needsUpdating=true;
					}
				}
			}
			if (needsUpdating) {
				activity.xmppConnectionService.updateContact(contact);
			}
		}
	};

	public void setContact(Contact contact) {
		this.contact = contact;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		this.activity = (XmppActivity) getActivity();
		AlertDialog.Builder builder = new AlertDialog.Builder(this.activity);
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View view = inflater.inflate(R.layout.dialog_contact_details, null);
		TextView contactJid = (TextView) view.findViewById(R.id.details_contactjid);
		TextView accountJid = (TextView) view.findViewById(R.id.details_account);
		TextView status = (TextView) view.findViewById(R.id.details_contactstatus);
		send = (CheckBox) view.findViewById(R.id.details_send_presence);
		receive = (CheckBox) view.findViewById(R.id.details_receive_presence);
		//ImageView contactPhoto = (ImageView) view.findViewById(R.id.details_contact_picture);
		QuickContactBadge badge = (QuickContactBadge) view.findViewById(R.id.details_contact_badge);
		
		if (contact.getSubscriptionOption(Contact.Subscription.FROM)) {
			send.setChecked(true);
		} else {
			send.setText("Preemptively grant subscription request");
			if (contact.getSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT)) {
				send.setChecked(true);
			} else {
				send.setChecked(false);
			}
		}
		if (contact.getSubscriptionOption(Contact.Subscription.TO)) {
			receive.setChecked(true);
		} else {
			receive.setText("Request presence updates");
			if (contact.getSubscriptionOption(Contact.Subscription.ASKING)) {
				receive.setChecked(true);
			} else {
				receive.setChecked(false);
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
		contactJid.setText(contact.getJid());
		accountJid.setText(contact.getAccount().getJid());

		UIHelper.prepareContactBadge(getActivity(), badge, contact);
		
		if (contact.getSystemAccount()==null) {
			badge.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
					builder.setTitle("Add to phone book");
					builder.setMessage("Do you want to add "+contact.getJid()+" to your phones contact list?");
					builder.setNegativeButton("Cancel", null);
					builder.setPositiveButton("Add",addToPhonebook);
					builder.create().show();
				}
			});
		}
		
		builder.setView(view);
		builder.setTitle(contact.getDisplayName());
		
		builder.setNeutralButton("Done", this.updateSubscriptions);
		builder.setPositiveButton("Remove from roster", this.askRemoveFromRoster);
		return builder.create();
	}
}
