package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityChannelDiscoveryBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.ChannelSearchResult;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.ChannelSearchResultAdapter;
import eu.siacs.conversations.ui.util.SoftKeyboardUtils;
import eu.siacs.conversations.utils.AccountUtils;
import rocks.xmpp.addr.Jid;

public class ChannelDiscoveryActivity extends XmppActivity implements MenuItem.OnActionExpandListener, TextView.OnEditorActionListener, XmppConnectionService.OnChannelSearchResultsFound, ChannelSearchResultAdapter.OnChannelSearchResultSelected {

    private static final String CHANNEL_DISCOVERY_OPT_IN = "channel_discovery_opt_in";

    private final ChannelSearchResultAdapter adapter = new ChannelSearchResultAdapter();

    private EditText mSearchEditText;

    private boolean optedIn = false;

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        if (optedIn) {
            xmppConnectionService.discoverChannels(null, this);
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityChannelDiscoveryBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_channel_discovery);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar(), true);
        binding.list.setAdapter(this.adapter);
        this.adapter.setOnChannelSearchResultSelectedListener(this);
        optedIn = getPreferences().getBoolean(CHANNEL_DISCOVERY_OPT_IN, false);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.muc_users_activity, menu);
        final MenuItem menuSearchView = menu.findItem(R.id.action_search);
        final View mSearchView = menuSearchView.getActionView();
        mSearchEditText = mSearchView.findViewById(R.id.search_field);
        mSearchEditText.setHint(R.string.search_channels);
        mSearchEditText.setOnEditorActionListener(this);
        menuSearchView.setOnActionExpandListener(this);
        return true;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        mSearchEditText.post(() -> {
            mSearchEditText.requestFocus();
            final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT);
        });
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
        mSearchEditText.setText("");
        adapter.submitList(Collections.emptyList());
        if (optedIn) {
            xmppConnectionService.discoverChannels(null, this);
        }
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!optedIn) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.channel_discovery_opt_in_title);
            builder.setMessage(Html.fromHtml(getString(R.string.channel_discover_opt_in_message)));
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> finish());
            builder.setPositiveButton(R.string.confirm, (dialog, which) -> optIn());
            builder.setOnCancelListener(dialog -> finish());
            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
    }

    private void optIn() {
        SharedPreferences preferences = getPreferences();
        preferences.edit().putBoolean(CHANNEL_DISCOVERY_OPT_IN,true).apply();
        optedIn = true;
        xmppConnectionService.discoverChannels(null, this);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (optedIn) {
            xmppConnectionService.discoverChannels(v.getText().toString(), this);
        }
        adapter.submitList(Collections.emptyList());
        SoftKeyboardUtils.hideSoftKeyboard(this);
        return true;
    }

    @Override
    public void onChannelSearchResultsFound(List<ChannelSearchResult> results) {
        runOnUiThread(() -> adapter.submitList(results));

    }

    @Override
    public void onChannelSearchResult(final ChannelSearchResult result) {
        List<String> accounts = AccountUtils.getEnabledAccounts(xmppConnectionService);
        if (accounts.size() == 1) {
            joinChannelSearchResult(accounts.get(0),result);
        } else if (accounts.size() > 0){
            final AtomicReference<String> account = new AtomicReference<>(accounts.get(0));
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.choose_account);
            builder.setSingleChoiceItems(accounts.toArray(new CharSequence[0]), 0, (dialog, which) -> account.set(accounts.get(which)));
            builder.setPositiveButton(R.string.join, (dialog, which) -> joinChannelSearchResult(account.get(), result));
            builder.setNegativeButton(R.string.cancel, null);
            builder.create().show();
        }

    }

    public void joinChannelSearchResult(String accountJid, ChannelSearchResult result) {
        Account account = xmppConnectionService.findAccountByJid(Jid.of(accountJid));
        final Conversation conversation = xmppConnectionService.findOrCreateConversation(account, result.getRoom(), true, true, true);
        switchToConversation(conversation);
    }
}
