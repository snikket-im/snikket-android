package eu.siacs.conversations.ui;

import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.ui.util.DelayedHintHelper;
import rocks.xmpp.addr.Jid;

public class EnterJidDialog extends DialogFragment{

	private OnEnterJidDialogPositiveListener mListener = null;

	private static final String TITLE_KEY = "title";
	private static final String POSITIVE_BUTTON_KEY = "positive_button";
	private static final String PREFILLED_JID_KEY = "prefilled_jid";
	private static final String ACCOUNT_KEY = "account";
	private static final String ALLOW_EDIT_JID_KEY = "allow_edit_jid";
	private static final String ACCOUNTS_LIST_KEY = "activated_accounts_list";
	private static final String CONFERENCE_HOSTS_KEY = "known_conference_hosts";

	public static EnterJidDialog newInstance(
			Collection<String> knownHosts, final List<String> activatedAccounts,
			final String title, final String positiveButton,
			final String prefilledJid, final String account, boolean allowEditJid) {
		EnterJidDialog dialog = new EnterJidDialog();
		Bundle bundle  = new Bundle();
		bundle.putString(TITLE_KEY, title);
		bundle.putString(POSITIVE_BUTTON_KEY, positiveButton);
		bundle.putString(PREFILLED_JID_KEY, prefilledJid);
		bundle.putString(ACCOUNT_KEY, account);
		bundle.putBoolean(ALLOW_EDIT_JID_KEY, allowEditJid);
		bundle.putStringArrayList(ACCOUNTS_LIST_KEY, (ArrayList<String>) activatedAccounts);
		bundle.putSerializable(CONFERENCE_HOSTS_KEY, (HashSet) knownHosts);
		dialog.setArguments(bundle);
		return dialog;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setRetainInstance(true);
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getArguments().getString(TITLE_KEY));
		View dialogView = getActivity().getLayoutInflater().inflate(R.layout.enter_jid_dialog, null);
		final Spinner spinner = dialogView.findViewById(R.id.account);
		final AutoCompleteTextView jid = dialogView.findViewById(R.id.jid);
		jid.setAdapter(new KnownHostsAdapter(getActivity(), R.layout.simple_list_item, (Collection<String>) getArguments().getSerializable(CONFERENCE_HOSTS_KEY)));
		String prefilledJid = getArguments().getString(PREFILLED_JID_KEY);
		if (prefilledJid != null) {
			jid.append(prefilledJid);
			if (!getArguments().getBoolean(ALLOW_EDIT_JID_KEY)) {
				jid.setFocusable(false);
				jid.setFocusableInTouchMode(false);
				jid.setClickable(false);
				jid.setCursorVisible(false);
			}
		}

		DelayedHintHelper.setHint(R.string.account_settings_example_jabber_id,jid);

		String account = getArguments().getString(ACCOUNT_KEY);
		if (account == null) {
			StartConversationActivity.populateAccountSpinner(getActivity(), getArguments().getStringArrayList(ACCOUNTS_LIST_KEY), spinner);
		} else {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),
					R.layout.simple_list_item,
					new String[] { account });
			spinner.setEnabled(false);
			adapter.setDropDownViewResource(R.layout.simple_list_item);
			spinner.setAdapter(adapter);
		}

		builder.setView(dialogView);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(getArguments().getString(POSITIVE_BUTTON_KEY), null);
		AlertDialog dialog = builder.create();

		View.OnClickListener dialogOnClick = v -> {
			final Jid accountJid;
			if (!spinner.isEnabled() && account == null) {
				return;
			}
			try {
				if (Config.DOMAIN_LOCK != null) {
					accountJid = Jid.of((String) spinner.getSelectedItem(), Config.DOMAIN_LOCK, null);
				} else {
					accountJid = Jid.of((String) spinner.getSelectedItem());
				}
			} catch (final IllegalArgumentException e) {
				return;
			}
			final Jid contactJid;
			try {
				contactJid = Jid.of(jid.getText().toString());
			} catch (final IllegalArgumentException e) {
				jid.setError(getActivity().getString(R.string.invalid_jid));
				return;
			}

			if(mListener != null) {
				try {
					if(mListener.onEnterJidDialogPositive(accountJid, contactJid)) {
						dialog.dismiss();
					}
				} catch(JidError error) {
					jid.setError(error.toString());
				}
			}
		};
		dialog.show();
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(dialogOnClick);
		return dialog;
	}

	public void setOnEnterJidDialogPositiveListener(OnEnterJidDialogPositiveListener listener) {
		this.mListener = listener;
	}

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

	@Override
	public void onDestroyView() {
		Dialog dialog = getDialog();
		if (dialog != null && getRetainInstance()) {
			dialog.setDismissMessage(null);
		}
		super.onDestroyView();
	}
}
