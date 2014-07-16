package eu.siacs.conversations.ui;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.utils.Validator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

public class EditAccountDialog extends DialogFragment {

	protected Account account;
	
	protected AutoCompleteTextView mAccountJid;

	public void setAccount(Account account) {
		this.account = account;
	}

	public interface EditAccountListener {
		public void onAccountEdited(Account account);
	}

	protected EditAccountListener listener = null;

	private KnownHostsAdapter mKnownHostsAdapter;

	public void setEditAccountListener(EditAccountListener listener) {
		this.listener = listener;
	}
	
	public void setKnownHosts(List<String> hosts, Context context) {
		this.mKnownHostsAdapter = new KnownHostsAdapter(context, android.R.layout.simple_list_item_1, hosts);
		if (this.mAccountJid != null) {
			this.mAccountJid.setAdapter(this.mKnownHostsAdapter);
		}
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View view = inflater.inflate(R.layout.edit_account_dialog, null);
		mAccountJid = (AutoCompleteTextView) view.findViewById(R.id.account_jid);
		if (this.mKnownHostsAdapter!=null) {
			mAccountJid.setAdapter(this.mKnownHostsAdapter);
		}
		final TextView confirmPwDesc = (TextView) view
				.findViewById(R.id.account_confirm_password_desc);

		final EditText password = (EditText) view
				.findViewById(R.id.account_password);
		final EditText passwordConfirm = (EditText) view
				.findViewById(R.id.account_password_confirm2);
		final CheckBox registerAccount = (CheckBox) view
				.findViewById(R.id.edit_account_register_new);

		if (account != null) {
			mAccountJid.setText(account.getJid());
			password.setText(account.getPassword());
			if (account.isOptionSet(Account.OPTION_REGISTER)) {
				registerAccount.setChecked(true);
				passwordConfirm.setVisibility(View.VISIBLE);
				passwordConfirm.setText(account.getPassword());
			} else {
				registerAccount.setVisibility(View.GONE);
			}
		}
		builder.setTitle(R.string.account_settings);
		

		registerAccount
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						if (isChecked) {
							passwordConfirm.setVisibility(View.VISIBLE);
							confirmPwDesc.setVisibility(View.VISIBLE);
						} else {
							passwordConfirm.setVisibility(View.GONE);
							confirmPwDesc.setVisibility(View.GONE);
						}
					}
				});

		builder.setView(view);
		builder.setNeutralButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.save), null);
		return builder.create();
	}

	@Override
	public void onStart() {
		super.onStart();
		final AlertDialog d = (AlertDialog) getDialog();
		Button positiveButton = (Button) d.getButton(Dialog.BUTTON_POSITIVE);
		positiveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				EditText jidEdit = (EditText) d.findViewById(R.id.account_jid);
				String jid = jidEdit.getText().toString();
				EditText passwordEdit = (EditText) d
						.findViewById(R.id.account_password);
				EditText passwordConfirmEdit = (EditText) d.findViewById(R.id.account_password_confirm2);
				String password = passwordEdit.getText().toString();
				String passwordConfirm = passwordConfirmEdit.getText().toString();
				CheckBox register = (CheckBox) d.findViewById(R.id.edit_account_register_new);
				String username;
				String server;
				if (Validator.isValidJid(jid)) {
					String[] parts = jid.split("@");
					username = parts[0];
					server = parts[1];
				} else {
					jidEdit.setError(getString(R.string.invalid_jid));
					return;
				}
				if (register.isChecked()) {
					if (!passwordConfirm.equals(password)) {
						passwordConfirmEdit.setError(getString(R.string.passwords_do_not_match));
						return;
					}
				}
				if (account != null) {
					account.setPassword(password);
					account.setUsername(username);
					account.setServer(server);
				} else {
					account = new Account(username, server, password);
					account.setOption(Account.OPTION_USETLS, true);
					account.setOption(Account.OPTION_USECOMPRESSION, true);
				}
				account.setOption(Account.OPTION_REGISTER, register.isChecked());
				if (listener != null) {
					listener.onAccountEdited(account);
					d.dismiss();
				}
			}
		});
	}
}
