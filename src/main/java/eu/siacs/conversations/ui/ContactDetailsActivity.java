package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import org.openintents.openpgp.util.OpenPgpUtils;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ContactDetailsActivity extends XmppActivity implements OnAccountUpdate, OnRosterUpdate, OnUpdateBlocklist {
	public static final String ACTION_VIEW_CONTACT = "view_contact";

	private Contact contact;
	private DialogInterface.OnClickListener removeFromRoster = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			ContactDetailsActivity.this.xmppConnectionService
				.deleteContactOnServer(contact);
			ContactDetailsActivity.this.finish();
		}
	};
	private OnCheckedChangeListener mOnSendCheckedChange = new OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			if (isChecked) {
				if (contact
						.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
					xmppConnectionService.sendPresencePacket(contact
							.getAccount(),
							xmppConnectionService.getPresenceGenerator()
							.sendPresenceUpdatesTo(contact));
				} else {
					contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
				}
			} else {
				contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
				xmppConnectionService.sendPresencePacket(contact.getAccount(),
						xmppConnectionService.getPresenceGenerator()
						.stopPresenceUpdatesTo(contact));
			}
		}
	};
	private OnCheckedChangeListener mOnReceiveCheckedChange = new OnCheckedChangeListener() {

		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			if (isChecked) {
				xmppConnectionService.sendPresencePacket(contact.getAccount(),
						xmppConnectionService.getPresenceGenerator()
						.requestPresenceUpdatesFrom(contact));
			} else {
				xmppConnectionService.sendPresencePacket(contact.getAccount(),
						xmppConnectionService.getPresenceGenerator()
						.stopPresenceUpdatesFrom(contact));
			}
		}
	};
	private Jid accountJid;
	private Jid contactJid;
	private TextView contactJidTv;
	private TextView accountJidTv;
	private TextView lastseen;
	private CheckBox send;
	private CheckBox receive;
	private QuickContactBadge badge;
	private LinearLayout keys;
	private LinearLayout tags;
	private boolean showDynamicTags;

	private DialogInterface.OnClickListener addToPhonebook = new DialogInterface.OnClickListener() {

		@Override
		public void onClick(DialogInterface dialog, int which) {
			Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
			intent.setType(Contacts.CONTENT_ITEM_TYPE);
			intent.putExtra(Intents.Insert.IM_HANDLE, contact.getJid().toString());
			intent.putExtra(Intents.Insert.IM_PROTOCOL,
					CommonDataKinds.Im.PROTOCOL_JABBER);
			intent.putExtra("finishActivityOnSaveCompleted", true);
			ContactDetailsActivity.this.startActivityForResult(intent, 0);
		}
	};

	private OnClickListener onBadgeClick = new OnClickListener() {

		@Override
		public void onClick(View v) {
			AlertDialog.Builder builder = new AlertDialog.Builder(
					ContactDetailsActivity.this);
			builder.setTitle(getString(R.string.action_add_phone_book));
			builder.setMessage(getString(R.string.add_phone_book_text,
						contact.getJid()));
			builder.setNegativeButton(getString(R.string.cancel), null);
			builder.setPositiveButton(getString(R.string.add), addToPhonebook);
			builder.create().show();
		}
	};

	@Override
	public void onRosterUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				populateView();
			}
		});
	}

	@Override
	public void onAccountUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				populateView();
			}
		});
	}

	@Override
	protected String getShareableUri() {
		if (contact != null) {
			return contact.getShareableUri();
		} else {
			return "";
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
			try {
				this.accountJid = Jid.fromString(getIntent().getExtras().getString("account"));
			} catch (final InvalidJidException ignored) {
			}
			try {
				this.contactJid = Jid.fromString(getIntent().getExtras().getString("contact"));
			} catch (final InvalidJidException ignored) {
			}
		}
		setContentView(R.layout.activity_contact_details);

		contactJidTv = (TextView) findViewById(R.id.details_contactjid);
		accountJidTv = (TextView) findViewById(R.id.details_account);
		lastseen = (TextView) findViewById(R.id.details_lastseen);
		send = (CheckBox) findViewById(R.id.details_send_presence);
		receive = (CheckBox) findViewById(R.id.details_receive_presence);
		badge = (QuickContactBadge) findViewById(R.id.details_contact_badge);
		keys = (LinearLayout) findViewById(R.id.details_contact_keys);
		tags = (LinearLayout) findViewById(R.id.tags);
		if (getActionBar() != null) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}

		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		this.showDynamicTags = preferences.getBoolean("show_dynamic_tags",false);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem menuItem) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setNegativeButton(getString(R.string.cancel), null);
		switch (menuItem.getItemId()) {
			case android.R.id.home:
				finish();
				break;
			case R.id.action_delete_contact:
				builder.setTitle(getString(R.string.action_delete_contact))
					.setMessage(
							getString(R.string.remove_contact_text,
								contact.getJid()))
					.setPositiveButton(getString(R.string.delete),
							removeFromRoster).create().show();
				break;
			case R.id.action_edit_contact:
				if (contact.getSystemAccount() == null) {
					quickEdit(contact.getDisplayName(), new OnValueEdited() {

						@Override
						public void onValueEdited(String value) {
							contact.setServerName(value);
							ContactDetailsActivity.this.xmppConnectionService
								.pushContactToServer(contact);
							populateView();
						}
					});
				} else {
					Intent intent = new Intent(Intent.ACTION_EDIT);
					String[] systemAccount = contact.getSystemAccount().split("#");
					long id = Long.parseLong(systemAccount[0]);
					Uri uri = Contacts.getLookupUri(id, systemAccount[1]);
					intent.setDataAndType(uri, Contacts.CONTENT_ITEM_TYPE);
					intent.putExtra("finishActivityOnSaveCompleted", true);
					startActivity(intent);
				}
				break;
		}
		return super.onOptionsItemSelected(menuItem);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.contact_details, menu);
		return true;
	}

	private void populateView() {
		send.setOnCheckedChangeListener(null);
		receive.setOnCheckedChangeListener(null);
		setTitle(contact.getDisplayName());
		if (contact.getOption(Contact.Options.FROM)) {
			send.setText(R.string.send_presence_updates);
			send.setChecked(true);
		} else if (contact
				.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
			send.setChecked(false);
			send.setText(R.string.send_presence_updates);
		} else {
			send.setText(R.string.preemptively_grant);
			if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
				send.setChecked(true);
			} else {
				send.setChecked(false);
			}
		}
		if (contact.getOption(Contact.Options.TO)) {
			receive.setText(R.string.receive_presence_updates);
			receive.setChecked(true);
		} else {
			receive.setText(R.string.ask_for_presence_updates);
			if (contact.getOption(Contact.Options.ASKING)) {
				receive.setChecked(true);
			} else {
				receive.setChecked(false);
			}
		}
		if (contact.getAccount().getStatus() == Account.State.ONLINE) {
			receive.setEnabled(true);
			send.setEnabled(true);
		} else {
			receive.setEnabled(false);
			send.setEnabled(false);
		}

		send.setOnCheckedChangeListener(this.mOnSendCheckedChange);
		receive.setOnCheckedChangeListener(this.mOnReceiveCheckedChange);

		lastseen.setText(UIHelper.lastseen(getApplicationContext(),
					contact.lastseen.time));

		if (contact.getPresences().size() > 1) {
			contactJidTv.setText(contact.getJid() + " ("
					+ contact.getPresences().size() + ")");
		} else {
			contactJidTv.setText(contact.getJid().toString());
		}
		accountJidTv.setText(getString(R.string.using_account, contact
					.getAccount().getJid().toBareJid()));
		prepareContactBadge(badge, contact);
		if (contact.getSystemAccount() == null) {
			badge.setOnClickListener(onBadgeClick);
		}

		keys.removeAllViews();
		boolean hasKeys = false;
		LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		for(final String otrFingerprint : contact.getOtrFingerprints()) {
			hasKeys = true;
			View view = inflater.inflate(R.layout.contact_key, keys, false);
			TextView key = (TextView) view.findViewById(R.id.key);
			TextView keyType = (TextView) view.findViewById(R.id.key_type);
			ImageButton remove = (ImageButton) view
				.findViewById(R.id.button_remove);
			remove.setVisibility(View.VISIBLE);
			keyType.setText("OTR Fingerprint");
			key.setText(otrFingerprint);
			keys.addView(view);
			remove.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					confirmToDeleteFingerprint(otrFingerprint);
				}
			});
		}
		if (contact.getPgpKeyId() != 0) {
			hasKeys = true;
			View view = inflater.inflate(R.layout.contact_key, keys, false);
			TextView key = (TextView) view.findViewById(R.id.key);
			TextView keyType = (TextView) view.findViewById(R.id.key_type);
			keyType.setText("PGP Key ID");
			key.setText(OpenPgpUtils.convertKeyIdToHex(contact.getPgpKeyId()));
			view.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					PgpEngine pgp = ContactDetailsActivity.this.xmppConnectionService
						.getPgpEngine();
					if (pgp != null) {
						PendingIntent intent = pgp.getIntentForKey(contact);
						if (intent != null) {
							try {
								startIntentSenderForResult(
										intent.getIntentSender(), 0, null, 0,
										0, 0);
							} catch (SendIntentException e) {

							}
						}
					}
				}
			});
			keys.addView(view);
		}
		if (hasKeys) {
			keys.setVisibility(View.VISIBLE);
		} else {
			keys.setVisibility(View.GONE);
		}

		List<ListItem.Tag> tagList = contact.getTags();
		if (tagList.size() == 0 || !this.showDynamicTags) {
			tags.setVisibility(View.GONE);
		} else {
			tags.setVisibility(View.VISIBLE);
			tags.removeAllViewsInLayout();
			for(final ListItem.Tag tag : tagList) {
				final TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag,tags,false);
				tv.setText(tag.getName());
				tv.setBackgroundColor(tag.getColor());
				tags.addView(tv);
			}
		}
	}

	private void prepareContactBadge(QuickContactBadge badge, Contact contact) {
		if (contact.getSystemAccount() != null) {
			String[] systemAccount = contact.getSystemAccount().split("#");
			long id = Long.parseLong(systemAccount[0]);
			badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1]));
		}
		badge.setImageBitmap(avatarService().get(contact, getPixel(72)));
	}

	protected void confirmToDeleteFingerprint(final String fingerprint) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.delete_fingerprint);
		builder.setMessage(R.string.sure_delete_fingerprint);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.delete,
				new android.content.DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (contact.deleteOtrFingerprint(fingerprint)) {
							populateView();
							xmppConnectionService.syncRosterToDisk(contact
									.getAccount());
						}
					}

				});
		builder.create().show();
	}

	@Override
	public void onBackendConnected() {
		if ((accountJid != null) && (contactJid != null)) {
			Account account = xmppConnectionService
				.findAccountByJid(accountJid);
			if (account == null) {
				return;
			}
			this.contact = account.getRoster().getContact(contactJid);
			populateView();
		}
	}

	@Override
	public void OnUpdateBlocklist(final Status status) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				populateView();
			}
		});
	}
}
