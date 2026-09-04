/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui;

import static eu.siacs.conversations.ui.util.SoftKeyboardUtils.hideSoftKeyboard;
import static eu.siacs.conversations.ui.util.SoftKeyboardUtils.showKeyboard;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivitySearchBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.MessageSearchTask;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.ui.interfaces.OnSearchResultsAvailable;
import eu.siacs.conversations.ui.util.ChangeWatcher;
import eu.siacs.conversations.ui.util.DateSeparator;
import eu.siacs.conversations.ui.util.ListViewUtils;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.utils.FtsUtils;
import eu.siacs.conversations.utils.MessageUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class SearchActivity extends XmppActivity
        implements TextWatcher, OnSearchResultsAvailable, MessageAdapter.OnContactPictureClicked {

    private static final String EXTRA_SEARCH_TERM = "search-term";
    public static final String EXTRA_CONVERSATION_UUID = "uuid";

    private ActivitySearchBinding binding;
    private MessageAdapter messageListAdapter;
    private final List<Message> messages = new ArrayList<>();
    private ConversationAdapter chatAdapter;
    private final List<Conversation> chatResults = new ArrayList<>();
    private ActionMode chatSelectionActionMode;
    private final Set<String> selectedChatUuids = new HashSet<>();

    private enum SearchMode {
        MESSAGES,
        CHATS
    }

    private SearchMode searchMode = SearchMode.MESSAGES;
    private WeakReference<Message> selectedMessageReference = new WeakReference<>(null);
    private String uuid;
    private final ChangeWatcher<List<String>> currentSearch = new ChangeWatcher<>();
    private final PendingItem<String> pendingSearchTerm = new PendingItem<>();
    private final PendingItem<List<String>> pendingSearch = new PendingItem<>();

    @Override
    public void onCreate(final Bundle bundle) {
        final Intent intent = getIntent();
        this.uuid =
                intent == null
                        ? null
                        : Strings.emptyToNull(intent.getStringExtra(EXTRA_CONVERSATION_UUID));
        final String searchTerm = bundle == null ? null : bundle.getString(EXTRA_SEARCH_TERM);
        if (searchTerm != null) {
            pendingSearchTerm.push(searchTerm);
        }
        super.onCreate(bundle);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_search);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(this.binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.messageListAdapter = new MessageAdapter(this, this.messages, uuid == null);
        this.messageListAdapter.setOnContactPictureClicked(this);
        this.binding.searchResults.setAdapter(messageListAdapter);
        registerForContextMenu(this.binding.searchResults);
        this.chatAdapter = new ConversationAdapter(this, this.chatResults);
        this.chatAdapter.setConversationClickListener(
                (view, conversation) -> {
                    if (chatSelectionActionMode != null) {
                        toggleChatSelection(conversation);
                    } else {
                        switchToConversation(conversation);
                    }
                });
        this.chatAdapter.setConversationLongClickListener(
                (view, conversation) -> startChatSelection(conversation));
        this.binding.searchConversations.setAdapter(chatAdapter);
        this.binding.searchConversations.setLayoutManager(new LinearLayoutManager(this));
        if (uuid == null) {
            this.binding.searchModeToggle.addOnButtonCheckedListener(
                    (group, checkedId, isChecked) -> {
                        if (!isChecked) {
                            return;
                        }
                        if (checkedId == R.id.search_mode_chats) {
                            showChatsMode();
                        } else if (checkedId == R.id.search_mode_messages) {
                            showMessagesMode();
                        }
                    });
            this.binding.searchModeToggle.check(R.id.search_mode_messages);
        } else {
            this.binding.searchModeToggle.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.activity_search, menu);
        final MenuItem searchActionMenuItem = menu.findItem(R.id.action_search);
        final EditText searchField =
                searchActionMenuItem.getActionView().findViewById(R.id.search_field);
        final String term = pendingSearchTerm.pop();
        if (term != null) {
            searchField.append(term);
            final List<String> searchTerm = FtsUtils.parse(term);
            if (xmppConnectionService != null) {
                if (currentSearch.watch(searchTerm)) {
                    xmppConnectionService.search(searchTerm, uuid, this);
                }
            } else {
                pendingSearch.push(searchTerm);
            }
        }
        searchField.addTextChangedListener(this);
        searchField.setHint(R.string.search_messages);
        searchField.setContentDescription(getString(R.string.search_messages));
        searchField.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        if (term == null) {
            showKeyboard(searchField);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onCreateContextMenu(
            final ContextMenu menu, final View v, ContextMenu.ContextMenuInfo menuInfo) {
        v.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
        AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
        final Message message = this.messages.get(acmi.position);
        this.selectedMessageReference = new WeakReference<>(message);
        getMenuInflater().inflate(R.menu.search_result_context, menu);
        final MenuItem copy = menu.findItem(R.id.copy_message);
        final MenuItem quote = menu.findItem(R.id.quote_message);
        if (message.isGeoUri()) {
            copy.setVisible(false);
            quote.setVisible(false);
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            hideSoftKeyboard(this);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final Message message = selectedMessageReference.get();
        if (message != null) {
            switch (item.getItemId()) {
                case R.id.open_conversation:
                    switchToConversation(wrap(message.getConversation()));
                    break;
                case R.id.share_with:
                    ShareUtil.share(this, message);
                    break;
                case R.id.copy_message:
                    ShareUtil.copyToClipboard(this, message);
                    break;
                case R.id.quote_message:
                    quote(message);
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        List<String> term = currentSearch.get();
        if (term != null && term.size() > 0) {
            bundle.putString(EXTRA_SEARCH_TERM, FtsUtils.toUserEnteredString(term));
        }
        super.onSaveInstanceState(bundle);
    }

    private void quote(Message message) {
        switchToConversationAndQuote(
                wrap(message.getConversation()), MessageUtils.prepareQuote(message));
    }

    private Conversation wrap(Conversational conversational) {
        if (conversational instanceof Conversation) {
            return (Conversation) conversational;
        } else {
            return xmppConnectionService.findOrCreateConversation(
                    conversational.getAccount(),
                    conversational.getAddress(),
                    conversational.getMode() == Conversational.MODE_MULTI,
                    true,
                    true);
        }
    }

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        final List<String> searchTerm = pendingSearch.pop();
        if (searchTerm != null && currentSearch.watch(searchTerm)) {
            xmppConnectionService.search(searchTerm, uuid, this);
        }
    }

    private void changeBackground(boolean hasSearch, boolean hasResults) {
        if (hasSearch) {
            if (hasResults) {
                binding.searchResults.setBackgroundColor(
                        MaterialColors.getColor(
                                binding.searchResults,
                                com.google.android.material.R.attr.colorSurface));
            } else {
                binding.searchResults.setBackgroundResource(R.drawable.background_no_results);
            }
        } else {
            binding.searchResults.setBackgroundResource(R.drawable.background_search);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        final List<String> term = FtsUtils.parse(s.toString().trim());
        if (!currentSearch.watch(term)) {
            return;
        }
        if (term.isEmpty()) {
            MessageSearchTask.cancelRunningTasks();
            this.messages.clear();
            messageListAdapter.setHighlightedTerm(null);
            messageListAdapter.notifyDataSetChanged();
            this.chatResults.clear();
            if (chatAdapter != null) {
                chatAdapter.notifyDataSetChanged();
            }
            finishChatSelection();
            changeBackground(false, false);
        } else {
            xmppConnectionService.search(term, uuid, this);
        }
    }

    @Override
    public void onSearchResultsAvailable(List<String> term, List<Message> messages) {
        runOnUiThread(
                () -> {
                    this.messages.clear();
                    messageListAdapter.setHighlightedTerm(term);
                    DateSeparator.addAll(messages);
                    this.messages.addAll(messages);
                    messageListAdapter.notifyDataSetChanged();
                    changeBackground(true, !messages.isEmpty());
                    ListViewUtils.scrollToBottom(this.binding.searchResults);
                    updateChatResults(messages);
                });
    }

    private void updateChatResults(final List<Message> messages) {
        this.chatResults.clear();
        final var seen = new LinkedHashMap<String, Conversation>();
        for (final Message message : messages) {
            if (message == null) {
                continue;
            }
            final Conversational conversational = message.getConversation();
            if (conversational == null) {
                continue;
            }
            final String conversationUuid = conversational.getUuid();
            if (conversationUuid == null || seen.containsKey(conversationUuid)) {
                continue;
            }
            final Conversation conversation;
            if (conversational instanceof Conversation c) {
                conversation = c;
            } else if (xmppConnectionService != null) {
                // Memory-only lookup: archived chats resolve to stubs here and are
                // deliberately hidden from Chats mode instead of being restored.
                conversation = xmppConnectionService.findConversationByUuid(conversationUuid);
                if (conversation == null) {
                    continue;
                }
            } else {
                continue;
            }
            seen.put(conversationUuid, conversation);
        }
        this.chatResults.addAll(seen.values());
        // Drop selections whose conversations are no longer listed
        selectedChatUuids.retainAll(seen.keySet());
        if (chatAdapter != null) {
            chatAdapter.setSelection(selectedChatUuids);
            chatAdapter.notifyDataSetChanged();
        }
        if (selectedChatUuids.isEmpty()) {
            finishChatSelection();
        } else {
            updateChatSelectionTitle();
        }
    }

    private void showChatsMode() {
        searchMode = SearchMode.CHATS;
        binding.searchResults.setVisibility(View.GONE);
        binding.searchConversations.setVisibility(View.VISIBLE);
    }

    private void showMessagesMode() {
        searchMode = SearchMode.MESSAGES;
        binding.searchConversations.setVisibility(View.GONE);
        binding.searchResults.setVisibility(View.VISIBLE);
        finishChatSelection();
    }

    private final ActionMode.Callback chatSelectionCallback =
            new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                    final MenuInflater inflater = mode.getMenuInflater();
                    inflater.inflate(R.menu.menu_conversation_selection, menu);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                    if (item.getItemId() == R.id.action_archive_selected) {
                        confirmAndArchiveSelectedChats();
                        return true;
                    } else if (item.getItemId() == R.id.action_select_all) {
                        selectAllChats();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(final ActionMode mode) {
                    clearChatSelection();
                }
            };

    private void startChatSelection(final Conversation conversation) {
        if (conversation == null || chatSelectionActionMode != null) {
            return;
        }
        selectedChatUuids.clear();
        selectedChatUuids.add(conversation.getUuid());
        chatAdapter.setSelection(selectedChatUuids);
        chatAdapter.notifyDataSetChanged();
        chatSelectionActionMode = startActionMode(chatSelectionCallback);
        updateChatSelectionTitle();
    }

    private void toggleChatSelection(final Conversation conversation) {
        if (conversation == null) {
            return;
        }
        final String uuid = conversation.getUuid();
        if (selectedChatUuids.contains(uuid)) {
            selectedChatUuids.remove(uuid);
        } else {
            selectedChatUuids.add(uuid);
        }
        chatAdapter.setSelection(selectedChatUuids);
        chatAdapter.notifyDataSetChanged();
        if (selectedChatUuids.isEmpty()) {
            finishChatSelection();
        } else {
            updateChatSelectionTitle();
        }
    }

    private void selectAllChats() {
        if (chatSelectionActionMode == null) {
            return;
        }
        selectedChatUuids.clear();
        for (final Conversation conversation : chatResults) {
            if (conversation != null) {
                selectedChatUuids.add(conversation.getUuid());
            }
        }
        chatAdapter.setSelection(selectedChatUuids);
        chatAdapter.notifyDataSetChanged();
        updateChatSelectionTitle();
    }

    private void updateChatSelectionTitle() {
        if (chatSelectionActionMode != null) {
            final int count = selectedChatUuids.size();
            if (count == 0) {
                chatSelectionActionMode.setTitle(R.string.select_chats);
            } else {
                chatSelectionActionMode.setTitle(
                        getResources().getQuantityString(R.plurals.x_chats, count, count));
            }
        }
    }

    private void finishChatSelection() {
        if (chatSelectionActionMode != null) {
            chatSelectionActionMode.finish();
        } else {
            clearChatSelection();
        }
    }

    private void clearChatSelection() {
        selectedChatUuids.clear();
        if (chatAdapter != null) {
            chatAdapter.clearSelection();
            try {
                chatAdapter.notifyDataSetChanged();
            } catch (final Exception e) {
                Log.d(Config.LOGTAG, "could not clear search chat selection", e);
            }
        }
        chatSelectionActionMode = null;
    }

    private void confirmAndArchiveSelectedChats() {
        if (selectedChatUuids.isEmpty() || xmppConnectionService == null) {
            return;
        }
        final int count = selectedChatUuids.size();
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.archive_chats);
        builder.setMessage(
                getResources()
                        .getQuantityString(
                                R.plurals.archive_chats_dialog_msg, count, count));
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(
                R.string.confirm, (dialog, which) -> archiveSelectedChats());
        builder.create().show();
    }

    private void archiveSelectedChats() {
        if (selectedChatUuids.isEmpty() || xmppConnectionService == null) {
            return;
        }
        final List<Conversation> toArchive = new ArrayList<>();
        for (final Conversation conversation : chatResults) {
            if (conversation != null && selectedChatUuids.contains(conversation.getUuid())) {
                toArchive.add(conversation);
            }
        }
        if (chatSelectionActionMode != null) {
            chatSelectionActionMode.finish();
        } else {
            clearChatSelection();
        }
        for (final Conversation conversation : toArchive) {
            xmppConnectionService.archiveConversation(conversation);
        }
        // Re-run the current query so archived chats drop out of Chats mode
        final List<String> term = currentSearch.get();
        if (term != null && !term.isEmpty()) {
            xmppConnectionService.search(term, uuid, this);
        } else {
            refreshUi();
        }
    }

    @Override
    protected void onDestroy() {
        if (chatSelectionActionMode != null) {
            chatSelectionActionMode.finish();
            chatSelectionActionMode = null;
        }
        selectedChatUuids.clear();
        super.onDestroy();
    }

    @Override
    public void onContactPictureClicked(Message message) {
        String fingerprint;
        if (message.getEncryption() == Message.ENCRYPTION_PGP
                || message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
            fingerprint = "pgp";
        } else {
            fingerprint = message.getFingerprint();
        }
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            final Contact contact = message.getContact();
            if (contact != null) {
                if (contact.isSelf()) {
                    switchToAccount(message.getConversation().getAccount(), fingerprint);
                } else {
                    switchToContactDetails(contact, fingerprint);
                }
            }
        } else {
            switchToAccount(message.getConversation().getAccount(), fingerprint);
        }
    }
}
