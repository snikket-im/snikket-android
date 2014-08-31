package eu.siacs.conversations.ui;

import android.os.Bundle;

public class SettingsActivity extends XmppActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Display the fragment as the main content.
		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, new SettingsFragment()).commit();
	}

	@Override
	void onBackendConnected() {

	}

}
