package eu.siacs.conversations.ui;

import android.app.Activity;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.EnterJidDialogBinding;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.ui.util.DelayedHintHelper;
import rocks.xmpp.addr.Jid;

public class EnterJidDialog extends DialogFragment implements OnBackendConnected {

	private OnEnterJidDialogPositiveListener mListener = null;

	private static final String TITLE_KEY = "title";
	private static final String POSITIVE_BUTTON_KEY = "positive_button";
	private static final String PREFILLED_JID_KEY = "prefilled_jid";
	private static final String ACCOUNT_KEY = "account";
	private static final String ALLOW_EDIT_JID_KEY = "allow_edit_jid";
	private static final String ACCOUNTS_LIST_KEY = "activated_accounts_list";

	private KnownHostsAdapter knownHostsAdapter;

	public static EnterJidDialog newInstance(final List<String> activatedAccounts,
	                                         final String title, final String positiveButton,
	                                         final String prefilledJid, final String account, boolean allowEditJid) {
		EnterJidDialog dialog = new EnterJidDialog();
		Bundle bundle = new Bundle();
		bundle.putString(TITLE_KEY, title);
		bundle.putString(POSITIVE_BUTTON_KEY, positiveButton);
		bundle.putString(PREFILLED_JID_KEY, prefilledJid);
		bundle.putString(ACCOUNT_KEY, account);
		bundle.putBoolean(ALLOW_EDIT_JID_KEY, allowEditJid);
		bundle.putStringArrayList(ACCOUNTS_LIST_KEY, (ArrayList<String>) activatedAccounts);
		dialog.setArguments(bundle);
		return dialog;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public void onStart() {
		super.onStart();
		final Activity activity = getActivity();
		if (activity instanceof XmppActivity && ((XmppActivity) activity).xmppConnectionService != null) {
			refreshKnownHosts();
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getArguments().getString(TITLE_KEY));
		EnterJidDialogBinding binding = DataBindingUtil.inflate(getActivity().getLayoutInflater(), R.layout.enter_jid_dialog, null, false);
		this.knownHostsAdapter = new KnownHostsAdapter(getActivity(), R.layout.simple_list_item);
		binding.jid.setAdapter(this.knownHostsAdapter);
		String prefilledJid = getArguments().getString(PREFILLED_JID_KEY);
		if (prefilledJid != null) {
			binding.jid.append(prefilledJid);
			if (!getArguments().getBoolean(ALLOW_EDIT_JID_KEY)) {
				binding.jid.setFocusable(false);
				binding.jid.setFocusableInTouchMode(false);
				binding.jid.setClickable(false);
				binding.jid.setCursorVisible(false);
			}
		}

		DelayedHintHelper.setHint(R.string.account_settings_example_jabber_id, binding.jid);

		String account = getArguments().getString(ACCOUNT_KEY);
		if (account == null) {
			StartConversationActivity.populateAccountSpinner(getActivity(), getArguments().getStringArrayList(ACCOUNTS_LIST_KEY), binding.account);
		} else {
			ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(),
					R.layout.simple_list_item,
					new String[]{account});
			binding.account.setEnabled(false);
			adapter.setDropDownViewResource(R.layout.simple_list_item);
			binding.account.setAdapter(adapter);
		}



		builder.setView(binding.getRoot());
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(getArguments().getString(POSITIVE_BUTTON_KEY), null);
		AlertDialog dialog = builder.create();

		View.OnClickListener dialogOnClick = v -> {
			handleEnter(binding, account, dialog);
		};

		binding.jid.setOnEditorActionListener((v, actionId, event) -> {
			handleEnter(binding, account, dialog);
			return true;
		});

		dialog.show();
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(dialogOnClick);
		return dialog;
	}

	private void handleEnter(EnterJidDialogBinding binding, String account, Dialog dialog) {
		final Jid accountJid;
		if (!binding.account.isEnabled() && account == null) {
			return;
		}
		try {
			if (Config.DOMAIN_LOCK != null) {
				accountJid = Jid.of((String) binding.account.getSelectedItem(), Config.DOMAIN_LOCK, null);
			} else {
				accountJid = Jid.of((String) binding.account.getSelectedItem());
			}
		} catch (final IllegalArgumentException e) {
			return;
		}
		final Jid contactJid;
		try {
			contactJid = Jid.of(binding.jid.getText().toString());
		} catch (final IllegalArgumentException e) {
			binding.jid.setError(getActivity().getString(R.string.invalid_jid));
			return;
		}

		if (mListener != null) {
			try {
				if (mListener.onEnterJidDialogPositive(accountJid, contactJid)) {
					dialog.dismiss();
				}
			} catch (JidError error) {
				binding.jid.setError(error.toString());
			}
		}
	}

	public void setOnEnterJidDialogPositiveListener(OnEnterJidDialogPositiveListener listener) {
		this.mListener = listener;
	}

	@Override
	public void onBackendConnected() {
		refreshKnownHosts();
	}

	private void refreshKnownHosts() {
		Activity activity = getActivity();
		if (activity instanceof XmppActivity) {
			Collection<String> hosts = ((XmppActivity) activity).xmppConnectionService.getKnownHosts();
			this.knownHostsAdapter.refresh(hosts);
		}
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
