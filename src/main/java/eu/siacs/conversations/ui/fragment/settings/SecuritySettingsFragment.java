package eu.siacs.conversations.ui.fragment.settings;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.services.MemorizingTrustManager;
import eu.siacs.conversations.utils.TimeFrameUtils;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collections;

public class SecuritySettingsFragment extends XmppPreferenceFragment {

    private static final String REMOVE_TRUSTED_CERTIFICATES = "remove_trusted_certificates";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_security, rootKey);
        final ListPreference omemo = findPreference(AppSettings.OMEMO);
        final ListPreference automaticMessageDeletion =
                findPreference(AppSettings.AUTOMATIC_MESSAGE_DELETION);
        if (omemo == null || automaticMessageDeletion == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }
        omemo.setSummaryProvider(new OmemoSummaryProvider());
        final int[] choices = getResources().getIntArray(R.array.automatic_message_deletion_values);
        final CharSequence[] entries = new CharSequence[choices.length];
        final CharSequence[] entryValues = new CharSequence[choices.length];
        for (int i = 0; i < choices.length; ++i) {
            entryValues[i] = String.valueOf(choices[i]);
            entries[i] = messageDeletionValueToName(requireContext(), choices[i]);
        }
        automaticMessageDeletion.setEntries(entries);
        automaticMessageDeletion.setEntryValues(entryValues);
        automaticMessageDeletion.setSummaryProvider(new MessageDeletionSummaryProvider());
    }

    private static String messageDeletionValueToName(final Context context, final int value) {
        if (value == 0) {
            return context.getString(R.string.never);
        } else {
            return TimeFrameUtils.resolve(context, 1000L * value);
        }
    }

    @Override
    protected void onSharedPreferenceChanged(@NonNull String key) {
        super.onSharedPreferenceChanged(key);
        switch (key) {
            case AppSettings.OMEMO -> {
                OmemoSetting.load(requireContext());
            }
            case AppSettings.TRUST_SYSTEM_CA_STORE -> {
                requireService().updateMemorizingTrustManager();
                reconnectAccounts();
            }
            case AppSettings.REQUIRE_CHANNEL_BINDING -> {
                reconnectAccounts();
            }
            case AppSettings.AUTOMATIC_MESSAGE_DELETION -> {
                requireService().expireOldMessages(true);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_title_security);
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (REMOVE_TRUSTED_CERTIFICATES.equals(preference.getKey())) {
            showRemoveCertificatesDialog();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void showRemoveCertificatesDialog() {
        final MemorizingTrustManager mtm = requireService().getMemorizingTrustManager();
        final ArrayList<String> aliases = Collections.list(mtm.getCertificates());
        if (aliases.isEmpty()) {
            Toast.makeText(requireActivity(), R.string.toast_no_trusted_certs, Toast.LENGTH_LONG)
                    .show();
            return;
        }
        final ArrayList<Integer> selectedItems = new ArrayList<>();
        final MaterialAlertDialogBuilder dialogBuilder =
                new MaterialAlertDialogBuilder(requireActivity());
        dialogBuilder.setTitle(getString(R.string.dialog_manage_certs_title));
        dialogBuilder.setMultiChoiceItems(
                aliases.toArray(new CharSequence[0]),
                null,
                (dialog, indexSelected, isChecked) -> {
                    if (isChecked) {
                        selectedItems.add(indexSelected);
                    } else if (selectedItems.contains(indexSelected)) {
                        selectedItems.remove(Integer.valueOf(indexSelected));
                    }
                    if (dialog instanceof AlertDialog alertDialog) {
                        alertDialog
                                .getButton(DialogInterface.BUTTON_POSITIVE)
                                .setEnabled(!selectedItems.isEmpty());
                    }
                });

        dialogBuilder.setPositiveButton(
                getString(R.string.dialog_manage_certs_positivebutton),
                (dialog, which) -> confirmCertificateDeletion(aliases, selectedItems));
        dialogBuilder.setNegativeButton(R.string.cancel, null);
        final AlertDialog removeCertsDialog = dialogBuilder.create();
        removeCertsDialog.show();
        removeCertsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    private void confirmCertificateDeletion(
            final ArrayList<String> aliases, final ArrayList<Integer> selectedItems) {
        final int count = selectedItems.size();
        if (count == 0) {
            return;
        }
        final MemorizingTrustManager mtm = requireService().getMemorizingTrustManager();
        for (int i = 0; i < count; i++) {
            try {
                final int item = Integer.parseInt(selectedItems.get(i).toString());
                final String alias = aliases.get(item);
                mtm.deleteCertificate(alias);
            } catch (final KeyStoreException e) {
                Toast.makeText(
                                requireActivity(),
                                "Error: " + e.getLocalizedMessage(),
                                Toast.LENGTH_LONG)
                        .show();
            }
        }
        reconnectAccounts();
        Toast.makeText(
                        requireActivity(),
                        getResources()
                                .getQuantityString(
                                        R.plurals.toast_delete_certificates, count, count),
                        Toast.LENGTH_LONG)
                .show();
    }

    private static class MessageDeletionSummaryProvider
            implements Preference.SummaryProvider<ListPreference> {

        @Nullable
        @Override
        public CharSequence provideSummary(@NonNull ListPreference preference) {
            final Integer value = Ints.tryParse(Strings.nullToEmpty(preference.getValue()));
            return messageDeletionValueToName(preference.getContext(), value == null ? 0 : value);
        }
    }

    private static class OmemoSummaryProvider
            implements Preference.SummaryProvider<ListPreference> {

        @Nullable
        @Override
        public CharSequence provideSummary(@NonNull ListPreference preference) {
            final var context = preference.getContext();
            final var sharedPreferences = preference.getSharedPreferences();
            final String value;
            if (sharedPreferences == null) {
                value = null;
            } else {
                value =
                        sharedPreferences.getString(
                                preference.getKey(),
                                context.getString(R.string.omemo_setting_default));
            }
            return switch (Strings.nullToEmpty(value)) {
                case "always" -> context.getString(R.string.pref_omemo_setting_summary_always);
                case "default_off" -> context.getString(
                        R.string.pref_omemo_setting_summary_default_off);
                default -> context.getString(R.string.pref_omemo_setting_summary_default_on);
            };
        }
    }
}
