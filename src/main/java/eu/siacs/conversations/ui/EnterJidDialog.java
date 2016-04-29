package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class EnterJidDialog {
	public interface OnEnterJidDialogPositiveListener {
		boolean onEnterJidDialogPositive(Jid account, Jid contact) throws EnterJidDialog.JidError;
	}

	public static class JidError extends Exception {
		final String msg;

		public JidError(final String msg) {
			this.msg = msg;
		}

		public String toString() {
			return msg;
		}
	}

	protected final AlertDialog dialog;
	protected View.OnClickListener dialogOnClick;
	protected OnEnterJidDialogPositiveListener listener = null;

	public EnterJidDialog(
		final Context context, List<String> knownHosts, final List<String> activatedAccounts,
		final String title, final String positiveButton,
		final String prefilledJid, final String account, boolean allowEditJid
	) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title);
		View dialogView = LayoutInflater.from(context).inflate(R.layout.enter_jid_dialog, null);
		final TextView jabberIdDesc = (TextView) dialogView.findViewById(R.id.jabber_id);
		jabberIdDesc.setText(R.string.account_settings_jabber_id);
		final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
		final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView.findViewById(R.id.jid);
		jid.setAdapter(new KnownHostsAdapter(context, R.layout.simple_list_item, knownHosts));
		if (prefilledJid != null) {
			jid.append(prefilledJid);
			if (!allowEditJid) {
				jid.setFocusable(false);
				jid.setFocusableInTouchMode(false);
				jid.setClickable(false);
				jid.setCursorVisible(false);
			}
		}

		jid.setHint(R.string.account_settings_example_jabber_id);

		if (account == null) {
			StartConversationActivity.populateAccountSpinner(context, activatedAccounts, spinner);
		} else {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
					R.layout.simple_list_item,
					new String[] { account });
			spinner.setEnabled(false);
			adapter.setDropDownViewResource(R.layout.simple_list_item);
			spinner.setAdapter(adapter);
		}

		builder.setView(dialogView);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(positiveButton, null);
		this.dialog = builder.create();

		this.dialogOnClick = new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final Jid accountJid;
				if (!spinner.isEnabled() && account == null) {
					return;
				}
				try {
					if (Config.DOMAIN_LOCK != null) {
						accountJid = Jid.fromParts((String) spinner.getSelectedItem(), Config.DOMAIN_LOCK, null);
					} else {
						accountJid = Jid.fromString((String) spinner.getSelectedItem());
					}
				} catch (final InvalidJidException e) {
					return;
				}
				final Jid contactJid;
				try {
					contactJid = Jid.fromString(jid.getText().toString());
				} catch (final InvalidJidException e) {
					jid.setError(context.getString(R.string.invalid_jid));
					return;
				}

				if(listener != null) {
					try {
						if(listener.onEnterJidDialogPositive(accountJid, contactJid)) {
							dialog.dismiss();
						}
					} catch(JidError error) {
						jid.setError(error.toString());
					}
				}
			}
		};
	}

	public void setOnEnterJidDialogPositiveListener(OnEnterJidDialogPositiveListener listener) {
		this.listener = listener;
	}

	public void show() {
		this.dialog.show();
		this.dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this.dialogOnClick);
	}
}
