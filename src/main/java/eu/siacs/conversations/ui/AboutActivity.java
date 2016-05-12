package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;

import eu.siacs.conversations.R;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Boolean dark = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                        .getString("theme", "light").equals("dark");
        int mTheme = dark ? R.style.ConversationsTheme_Dark : R.style.ConversationsTheme;
        setTheme(mTheme);

        setContentView(R.layout.activity_about);
    }
}
