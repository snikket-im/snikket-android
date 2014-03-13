package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.Validator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;

public class EditAccount extends DialogFragment {

	protected Account account;

	public void setAccount(Account account) {
		this.account = account;
	}

	public interface EditAccountListener {
		public void onAccountEdited(Account account);
	}

	protected EditAccountListener listener = null;

	public void setEditAccountListener(EditAccountListener listener) {
		this.listener = listener;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View view = inflater.inflate(R.layout.edit_account_dialog, null);
		final EditText jidText = (EditText) view.findViewById(R.id.account_jid);
		final TextView confirmPwDesc = (TextView) view
				.findViewById(R.id.account_confirm_password_desc);
		CheckBox useTLS = (CheckBox) view.findViewById(R.id.account_usetls);

		final EditText password = (EditText) view
				.findViewById(R.id.account_password);
		final EditText passwordConfirm = (EditText) view
				.findViewById(R.id.account_password_confirm2);
		final CheckBox registerAccount = (CheckBox) view
				.findViewById(R.id.edit_account_register_new);

		final String okButtonDesc;

		if (account != null) {
			jidText.setText(account.getJid());
			password.setText(account.getPassword());
			if (account.isOptionSet(Account.OPTION_USETLS)) {
				useTLS.setChecked(true);
			} else {
				useTLS.setChecked(false);
			}
			Log.d("xmppService","mein debugger. account != null");
			if (account.isOptionSet(Account.OPTION_REGISTER)) {
				registerAccount.setChecked(true);
				builder.setTitle("Add account");
				okButtonDesc = "Register";
				passwordConfirm.setVisibility(View.VISIBLE);
			} else {
				registerAccount.setVisibility(View.GONE);
				builder.setTitle("Edit account");
				okButtonDesc = "Edit";
			}
		} else {
			builder.setTitle("Add account");
			okButtonDesc = "Add";
		}

		registerAccount
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						AlertDialog d = (AlertDialog) getDialog();
						Button positiveButton = (Button) d
								.getButton(Dialog.BUTTON_POSITIVE);
						if (isChecked) {
							positiveButton.setText("Register");
							passwordConfirm.setVisibility(View.VISIBLE);
							confirmPwDesc.setVisibility(View.VISIBLE);
						} else {
							passwordConfirm.setVisibility(View.GONE);
							positiveButton.setText("Add");
							confirmPwDesc.setVisibility(View.GONE);
						}
					}
				});

		builder.setView(view);
		builder.setNeutralButton("Cancel", null);
		builder.setPositiveButton(okButtonDesc, null);
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
				String password = passwordEdit.getText().toString();
				CheckBox useTLS = (CheckBox) d.findViewById(R.id.account_usetls);
				CheckBox register = (CheckBox) d.findViewById(R.id.edit_account_register_new);
				String username;
				String server;
				if (Validator.isValidJid(jid)) {
					String[] parts = jid.split("@");
					username = parts[0];
					server = parts[1];
				} else {
					jidEdit.setError("Invalid Jabber ID");
					return;
				}
				if (account != null) {
					account.setPassword(password);
					account.setUsername(username);
					account.setServer(server);
				} else {
					account = new Account(username, server, password);
				}
				account.setOption(Account.OPTION_USETLS, useTLS.isChecked());
				account.setOption(Account.OPTION_REGISTER, register.isChecked());
				if (listener != null) {
					listener.onAccountEdited(account);
					d.dismiss();
				}
			}
		});
	}
}
