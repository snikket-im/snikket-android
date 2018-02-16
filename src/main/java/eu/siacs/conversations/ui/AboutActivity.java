package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import eu.siacs.conversations.R;

public class AboutActivity extends AppCompatActivity {

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
