package eu.siacs.conversations.ui;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;

public abstract class ActionBarActivity extends AppCompatActivity {
    public static void configureActionBar(ActionBar actionBar) {
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }
}