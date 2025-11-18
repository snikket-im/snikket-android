package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;

import androidx.appcompat.app.ActionBar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityTosBinding;

public class TosActivity extends XmppActivity {

    @Override
    protected void refreshUiReal() {

    }

    @Override
    public void onBackendConnected() {

    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            setIntent(intent);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        final ActivityTosBinding binding = DataBindingUtil.setContentView(this,R.layout.activity_tos);
        setSupportActionBar(binding.toolbar);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayShowHomeEnabled(false);
            ab.setDisplayHomeAsUpEnabled(false);
        }
        binding.agree.setOnClickListener(v -> {
            final Intent intent = new Intent(this, EnterPhoneNumberActivity.class);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            preferences.edit().putBoolean("tos", true).apply();
            addInviteUri(intent);
            startActivity(intent);
            finish();
        });
        binding.welcomeText.setText(Html.fromHtml(getString(R.string.welcome_text_quicksy_static)));
        binding.welcomeText.setMovementMethod(LinkMovementMethod.getInstance());

    }

    public void addInviteUri(Intent intent) {
        StartConversationActivity.addInviteUri(intent, getIntent());
    }
}
