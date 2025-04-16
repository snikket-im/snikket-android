package eu.siacs.conversations.ui;

import static eu.siacs.conversations.ui.XmppActivity.configureActionBar;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import androidx.databinding.DataBindingUtil;
import de.gultsch.common.Linkify;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityAboutBinding;
import eu.siacs.conversations.ui.text.FixedURLSpan;

public class AboutActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityAboutBinding binding =
                DataBindingUtil.setContentView(this, R.layout.activity_about);
        final var text = new SpannableString(getString(R.string.pref_about_message));
        Linkify.addLinks(text);
        FixedURLSpan.fix(text);
        binding.about.setText(text);
        binding.about.setMovementMethod(LinkMovementMethod.getInstance());
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());

        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        setTitle(getString(R.string.title_activity_about_x, getString(R.string.app_name)));
    }
}
