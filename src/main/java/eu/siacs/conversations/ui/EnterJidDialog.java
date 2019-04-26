package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.Dialog;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.EnterJidDialogBinding;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.ui.util.DelayedHintHelper;
import rocks.xmpp.addr.Jid;

public class EnterJidDialog extends DialogFragment implements OnBackendConnected, TextWatcher {


	private static final List<String> SUSPICIOUS_DOMAINS = Arrays.asList("conference","muc","room","rooms","chat");

	private OnEnterJidDialogPositiveListener mListener = null;

	private static final String TITLE_KEY = "title";
	private static final String POSITIVE_BUTTON_KEY = "positive_button";
	private static final String PREFILLED_JID_KEY = "prefilled_jid";
	private static final String ACCOUNT_KEY = "account";
	private static final String ALLOW_EDIT_JID_KEY = "allow_edit_jid";
	private static final String ACCOUNTS_LIST_KEY = "activated_accounts_list";
	private static final String SANITY_CHECK_JID = "sanity_check_jid";

	private KnownHostsAdapter knownHostsAdapter;

	private EnterJidDialogBinding binding;
	private AlertDialog dialog;
	private boolean sanityCheckJid = false;


	private boolean issuedWarning = false;

	public static EnterJidDialog newInstance(final List<String> activatedAccounts,
	                                         final String title, final String positiveButton,
	                                         final String prefilledJid, final String account,
											 boolean allowEditJid, final boolean sanity_check_jid) {
		EnterJidDialog dialog = new EnterJidDialog();
		Bundle bundle = new Bundle();
		bundle.putString(TITLE_KEY, title);
		bundle.putString(POSITIVE_BUTTON_KEY, positiveButton);
		bundle.putString(PREFILLED_JID_KEY, prefilledJid);
		bundle.putString(ACCOUNT_KEY, account);
		bundle.putBoolean(ALLOW_EDIT_JID_KEY, allowEditJid);
		bundle.putStringArrayList(ACCOUNTS_LIST_KEY, (ArrayList<String>) activatedAccounts);
		bundle.putBoolean(SANITY_CHECK_JID, sanity_check_jid);
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
		binding = DataBindingUtil.inflate(getActivity().getLayoutInflater(), R.layout.enter_jid_dialog, null, false);
		this.knownHostsAdapter = new KnownHostsAdapter(getActivity(), R.layout.simple_list_item);
		binding.jid.setAdapter(this.knownHostsAdapter);
		binding.jid.addTextChangedListener(this);
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
		sanityCheckJid = getArguments().getBoolean(SANITY_CHECK_JID, false);

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
		this.dialog = builder.create();

		View.OnClickListener dialogOnClick = v -> {
			handleEnter(binding, account);
		};

		binding.jid.setOnEditorActionListener((v, actionId, event) -> {
			handleEnter(binding, account);
			return true;
		});

		dialog.show();
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(dialogOnClick);
		return dialog;
	}

	private void handleEnter(EnterJidDialogBinding binding, String account) {
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

		if (!issuedWarning && sanityCheckJid) {
			if (contactJid.isDomainJid()) {
				binding.jid.setError(getActivity().getString(R.string.this_looks_like_a_domain));
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.add_anway);
				issuedWarning = true;
				return;
			}
			if (suspiciousSubDomain(contactJid.getDomain())) {
				binding.jid.setError(getActivity().getString(R.string.this_looks_like_channel));
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.add_anway);
				issuedWarning = true;
				return;
			}
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

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {

	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	@Override
	public void afterTextChanged(Editable s) {
		if (issuedWarning) {
			dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.add);
			issuedWarning = false;
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

	private static boolean suspiciousSubDomain(String domain) {
		final String[] parts = domain.split("\\.");
		return parts.length >= 3 && SUSPICIOUS_DOMAINS.contains(parts[0]);
	}
}
