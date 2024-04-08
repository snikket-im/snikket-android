package eu.siacs.conversations.ui;

import static eu.siacs.conversations.ui.XmppActivity.configureActionBar;

import android.os.Bundle;

import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityAboutBinding;

public class AboutActivity extends ActionBarActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityAboutBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_about);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());

        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        setTitle(getString(R.string.title_activity_about_x, getString(R.string.app_name)));
    }
}
