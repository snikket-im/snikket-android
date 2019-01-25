package eu.siacs.conversations.ui;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.Collections;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMucUsersBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.adapter.UserAdapter;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;

public class MucUsersActivity extends XmppActivity implements XmppConnectionService.OnRosterUpdate {

    private UserAdapter userAdapter;

    private Conversation mConversation = null;

    @Override
    protected void refreshUiReal() {
    }

    @Override
    void onBackendConnected() {
        final Intent intent = getIntent();
        final String uuid = intent == null ? null : intent.getStringExtra("uuid");
        if (uuid != null) {
            mConversation = xmppConnectionService.findConversationByUuid(uuid);
        }
        loadAndSubmitUsers();
    }

    private void loadAndSubmitUsers() {
        if (mConversation != null) {
            ArrayList<MucOptions.User> users = mConversation.getMucOptions().getUsers();
            Collections.sort(users);
            userAdapter.submitList(users);
        }
    }

     @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (!MucDetailsContextMenuHelper.onContextItemSelected(item, userAdapter.getSelectedUser(), mConversation, this)) {
            return super.onContextItemSelected(item);
        }
        return true;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMucUsersBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_muc_users);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar(), true);
        this.userAdapter = new UserAdapter(true);
        binding.list.setAdapter(this.userAdapter);
    }


    @Override
    public void onRosterUpdate() {
        loadAndSubmitUsers();
    }
}
