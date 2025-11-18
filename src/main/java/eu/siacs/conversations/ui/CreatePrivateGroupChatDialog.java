package eu.siacs.conversations.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.DialogCreateConferenceBinding;
import eu.siacs.conversations.ui.util.DelayedHintHelper;

public class CreatePrivateGroupChatDialog extends DialogFragment {

    private static final String ACCOUNTS_LIST_KEY = "activated_accounts_list";
    private CreateConferenceDialogListener mListener;

    public static CreatePrivateGroupChatDialog newInstance(List<String> accounts) {
        CreatePrivateGroupChatDialog dialog = new CreatePrivateGroupChatDialog();
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(ACCOUNTS_LIST_KEY, (ArrayList<String>) accounts);
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
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        builder.setTitle(R.string.create_private_group_chat);
        final DialogCreateConferenceBinding binding = DataBindingUtil.inflate(getActivity().getLayoutInflater(), R.layout.dialog_create_conference, null, false);
        ArrayList<String> mActivatedAccounts = getArguments().getStringArrayList(ACCOUNTS_LIST_KEY);
        StartConversationActivity.populateAccountSpinner(getActivity(), mActivatedAccounts, binding.account);
        builder.setView(binding.getRoot());
        builder.setPositiveButton(R.string.choose_participants, (dialog, which) -> mListener.onCreateDialogPositiveClick(binding.account, binding.groupChatName.getText().toString().trim()));
        builder.setNegativeButton(R.string.cancel, null);
        DelayedHintHelper.setHint(R.string.providing_a_name_is_optional, binding.groupChatName);
        binding.groupChatName.setOnEditorActionListener((v, actionId, event) -> {
            mListener.onCreateDialogPositiveClick(binding.account, binding.groupChatName.getText().toString().trim());
            return true;
        });
        return builder.create();
    }


    public interface CreateConferenceDialogListener {
        void onCreateDialogPositiveClick(AutoCompleteTextView spinner, String subject);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (CreateConferenceDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement CreateConferenceDialogListener");
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
