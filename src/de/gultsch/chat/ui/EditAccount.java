package de.gultsch.chat.ui;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.utils.Validator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class EditAccount extends DialogFragment {

	protected Account account;

	public void setAccount(Account account) {
		this.account = account;
	}

	public interface EditAccountListener {
		public void onAccountEdited(Account account);

		public void onAccountDelete(Account account);
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
		final EditText usernameText = (EditText) view
				.findViewById(R.id.account_username);
		final EditText serverText = (EditText) view
				.findViewById(R.id.account_server);
		final TextView usernameDesc = (TextView) view
				.findViewById(R.id.textView2);
		final TextView confirmPwDesc = (TextView) view.findViewById(R.id.account_confirm_password_desc);
		CheckBox showAdvanced = (CheckBox) view
				.findViewById(R.id.account_show_advanced);
		final RelativeLayout advancedOptions = (RelativeLayout) view
				.findViewById(R.id.advanced_options);
		showAdvanced.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				if (isChecked) {
					advancedOptions.setVisibility(View.VISIBLE);
					usernameDesc.setText("Username");
					usernameText.setVisibility(View.VISIBLE);
					jidText.setVisibility(View.GONE);
				} else {
					advancedOptions.setVisibility(View.GONE);
					usernameDesc.setText("Jabber ID");
					usernameText.setVisibility(View.GONE);
					jidText.setVisibility(View.VISIBLE);
				}
			}
		});

		final EditText password = (EditText) view
				.findViewById(R.id.account_password);
		final EditText passwordConfirm = (EditText) view
				.findViewById(R.id.account_password_confirm2);
		final CheckBox registerAccount = (CheckBox) view
				.findViewById(R.id.edit_account_register_new);

		final String okButtonDesc;

		if (account != null) {
			builder.setTitle("Edit account");
			registerAccount.setVisibility(View.GONE);
			jidText.setText(account.getJid());
			password.setText(account.getPassword());
			usernameText.setText(account.getUsername());
			serverText.setText(account.getServer());
			okButtonDesc = "Edit";
			/*builder.setNegativeButton("Delete Account", new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
					builder.setTitle("Are you sure?");
					builder.setIconAttribute(android.R.attr.alertDialogIcon);
					builder.setMessage("If you delete your account your entire conversation history will be lost");
					builder.setPositiveButton("Delete", new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							if (listener!=null) {
								listener.onAccountDelete(account);
							}
						}
					});
					builder.setNegativeButton("Cancel",null);
					builder.create().show();
				}
			});*/
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
						Button positiveButton = (Button) d.getButton(Dialog.BUTTON_POSITIVE);
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
				boolean showAdvanced = ((CheckBox) d.findViewById(R.id.account_show_advanced)).isChecked();
				EditText jidEdit = (EditText) d.findViewById(R.id.account_jid);
				String jid = jidEdit.getText().toString();
				EditText usernameEdit = (EditText) d.findViewById(R.id.account_username);
				String username = usernameEdit.getText().toString();
				EditText serverEdit = (EditText) d.findViewById(R.id.account_server);
				String server = serverEdit.getText().toString();
				EditText passwordEdit = (EditText) d.findViewById(R.id.account_password);
				String password = passwordEdit.getText().toString();
				if (!showAdvanced) {
					if (Validator.isValidJid(jid)) {
						String[] parts = jid.split("@");
						username = parts[0];
						server = parts[1];
					} else {
						jidEdit.setError("Invalid Jabber ID");
						return;
					}
				} else {
					if (username.length()==0) {
						usernameEdit.setError("username is too short");
						return;
					} else if (server.length()==0) {
						serverEdit.setError("server is too short");
						return;
					}
				}
				if (account!=null) {
					account.setPassword(password);
					account.setUsername(username);
					account.setServer(server);
				} else {
					account = new Account(username, server, password);
				}
				if (listener!=null) {
					listener.onAccountEdited(account);
					d.dismiss();
				}
			}
		});
	}
}
