package eu.siacs.conversations.ui.fragment.settings;

import android.os.Bundle;
import androidx.annotation.Nullable;
import eu.siacs.conversations.R;

public class InterfaceBubblesSettingsFragment extends XmppPreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_interface_bubbles, rootKey);
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_title_bubbles);
    }
}
