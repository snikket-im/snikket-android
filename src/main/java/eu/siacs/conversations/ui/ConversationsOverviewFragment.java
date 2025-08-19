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

import static androidx.recyclerview.widget.ItemTouchHelper.LEFT;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.FragmentConversationsOverviewBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.PendingActionHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ScrollState;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.EasyOnboardingInvite;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ConversationsOverviewFragment extends XmppFragment {

    private static final String STATE_SCROLL_POSITION =
            ConversationsOverviewFragment.class.getName() + ".scroll_state";

    private final List<Conversation> conversations = new ArrayList<>();
    private final PendingItem<Conversation> swipedConversation = new PendingItem<>();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private FragmentConversationsOverviewBinding binding;
    private ConversationAdapter conversationsAdapter;
    private XmppActivity activity;
    private final PendingActionHelper pendingActionHelper = new PendingActionHelper();

    private final ItemTouchHelper.SimpleCallback callback =
            new ItemTouchHelper.SimpleCallback(0, LEFT | RIGHT) {
                @Override
                public boolean onMove(
                        @NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder,
                        @NonNull RecyclerView.ViewHolder target) {
                    return false;
                }

                @Override
                public void onChildDraw(
                        @NonNull Canvas c,
                        @NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder,
                        float dX,
                        float dY,
                        int actionState,
                        boolean isCurrentlyActive) {
                    if (viewHolder
                            instanceof
                            ConversationAdapter.ConversationViewHolder conversationViewHolder) {
                        getDefaultUIUtil()
                                .onDraw(
                                        c,
                                        recyclerView,
                                        conversationViewHolder.binding.frame,
                                        dX,
                                        dY,
                                        actionState,
                                        isCurrentlyActive);
                    }
                }

                @Override
                public void clearView(
                        @NonNull RecyclerView recyclerView,
                        @NonNull RecyclerView.ViewHolder viewHolder) {
                    if (viewHolder
                            instanceof
                            ConversationAdapter.ConversationViewHolder conversationViewHolder) {
                        getDefaultUIUtil().clearView(conversationViewHolder.binding.frame);
                    }
                }

                @Override
                public float getSwipeEscapeVelocity(final float defaultEscapeVelocity) {
                    return 32 * defaultEscapeVelocity;
                }

                @Override
                public void onSwiped(
                        final RecyclerView.ViewHolder viewHolder, final int direction) {
                    pendingActionHelper.execute();
                    int position = viewHolder.getLayoutPosition();
                    try {
                        swipedConversation.push(conversations.get(position));
                    } catch (IndexOutOfBoundsException e) {
                        return;
                    }
                    conversationsAdapter.remove(swipedConversation.peek(), position);
                    toggleHintVisibility();
                    activity.xmppConnectionService.markRead(swipedConversation.peek());
                    final boolean formerlySelected =
                            ConversationFragment.getConversation(getActivity())
                                    == swipedConversation.peek();
                    if (getActivity() instanceof OnConversationArchived callback) {
                        callback.onConversationArchived(swipedConversation.peek());
                    }
                    final Conversation c = swipedConversation.peek();
                    final int title;
                    if (c.getMode() == Conversational.MODE_MULTI) {
                        if (c.getMucOptions().isPrivateAndNonAnonymous()) {
                            title = R.string.title_undo_swipe_out_group_chat;
                        } else {
                            title = R.string.title_undo_swipe_out_channel;
                        }
                    } else {
                        title = R.string.title_undo_swipe_out_chat;
                    }

                    final Snackbar snackbar =
                            Snackbar.make(binding.list, title, 5000)
                                    .setAction(
                                            R.string.undo,
                                            v -> {
                                                pendingActionHelper.undo();
                                                final var conversation = swipedConversation.pop();
                                                if (!conversations.contains(conversation)) {
                                                    conversationsAdapter.insert(
                                                            conversation, position);
                                                }
                                                toggleHintVisibility();
                                                if (formerlySelected) {
                                                    if (activity
                                                            instanceof OnConversationSelected on) {
                                                        on.onConversationSelected(c);
                                                    }
                                                }
                                                LinearLayoutManager layoutManager =
                                                        (LinearLayoutManager)
                                                                binding.list.getLayoutManager();
                                                if (position
                                                        > layoutManager
                                                                .findLastVisibleItemPosition()) {
                                                    binding.list.smoothScrollToPosition(position);
                                                }
                                            })
                                    .addCallback(
                                            new Snackbar.Callback() {
                                                @Override
                                                public void onDismissed(
                                                        Snackbar transientBottomBar, int event) {
                                                    switch (event) {
                                                        case DISMISS_EVENT_SWIPE:
                                                        case DISMISS_EVENT_TIMEOUT:
                                                            pendingActionHelper.execute();
                                                            break;
                                                    }
                                                }
                                            });

                    pendingActionHelper.push(
                            () -> {
                                if (snackbar.isShownOrQueued()) {
                                    snackbar.dismiss();
                                }
                                final Conversation conversation = swipedConversation.pop();
                                if (conversation != null) {
                                    if (!conversation.isRead()
                                            && conversation.getMode() == Conversation.MODE_SINGLE) {
                                        return;
                                    }
                                    activity.xmppConnectionService.archiveConversation(c);
                                }
                            });
                    snackbar.show();
                }
            };

    private ItemTouchHelper touchHelper;

    public static Conversation getSuggestion(final FragmentActivity activity) {
        final Conversation exception;
        Fragment fragment =
                activity.getSupportFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment conversationsOverviewFragment) {
            exception = conversationsOverviewFragment.swipedConversation.peek();
        } else {
            exception = null;
        }
        return getSuggestion(activity, exception);
    }

    public static @Nullable Conversation getSuggestion(
            final FragmentActivity activity, final Conversation exception) {
        Fragment fragment =
                activity.getSupportFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment conversationsOverviewFragment) {
            final var conversations = conversationsOverviewFragment.conversations;
            final var filtered = Collections2.filter(conversations, c -> c != exception);
            if (filtered.isEmpty()) {
                return null;
            } else {
                return Iterables.getFirst(filtered, null);
            }
        }
        return null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof XmppActivity) {
            this.activity = (XmppActivity) activity;
        } else {
            throw new IllegalStateException(
                    "Trying to attach fragment to activity that is not an XmppActivity");
        }
    }

    @Override
    public void onDestroyView() {
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onDestroyView()");
        super.onDestroyView();
        this.binding = null;
        this.conversationsAdapter = null;
        this.touchHelper = null;
    }

    @Override
    public void onDestroy() {
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onDestroy()");
        super.onDestroy();
    }

    @Override
    public void onPause() {
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onPause()");
        pendingActionHelper.execute();
        super.onPause();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.binding =
                DataBindingUtil.inflate(
                        inflater, R.layout.fragment_conversations_overview, container, false);
        this.binding.fab.setOnClickListener(
                (view) -> StartConversationActivity.launch(getActivity()));

        this.conversationsAdapter = new ConversationAdapter(this.activity, this.conversations);
        this.conversationsAdapter.setConversationClickListener(
                (view, conversation) -> {
                    if (activity instanceof OnConversationSelected) {
                        ((OnConversationSelected) activity).onConversationSelected(conversation);
                    } else {
                        Log.w(
                                ConversationsOverviewFragment.class.getCanonicalName(),
                                "Activity does not implement OnConversationSelected");
                    }
                });
        this.binding.list.setAdapter(this.conversationsAdapter);
        this.binding.list.setLayoutManager(
                new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        this.binding.list.addOnScrollListener(ExtendedFabSizeChanger.of(binding.fab));
        this.touchHelper = new ItemTouchHelper(this.callback);
        this.touchHelper.attachToRecyclerView(this.binding.list);
        return binding.getRoot();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.fragment_conversations_overview, menu);
        AccountUtils.showHideMenuItems(menu);
        final MenuItem easyOnboardInvite = menu.findItem(R.id.action_easy_invite);
        easyOnboardInvite.setVisible(
                EasyOnboardingInvite.anyHasSupport(
                        activity == null ? null : activity.xmppConnectionService));
        final MenuItem privacyPolicyMenuItem = menu.findItem(R.id.action_privacy_policy);
        privacyPolicyMenuItem.setVisible(
                BuildConfig.PRIVACY_POLICY != null
                        && QuickConversationsService.isPlayStoreFlavor());
    }

    @Override
    public void onBackendConnected() {
        refresh();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        ScrollState scrollState = getScrollState();
        if (scrollState != null) {
            bundle.putParcelable(STATE_SCROLL_POSITION, scrollState);
        }
    }

    private ScrollState getScrollState() {
        if (this.binding == null) {
            return null;
        }
        if (this.binding.list.getLayoutManager()
                instanceof LinearLayoutManager linearLayoutManager) {
            final int position = linearLayoutManager.findFirstVisibleItemPosition();
            final View view = this.binding.list.getChildAt(0);
            if (view != null) {
                return new ScrollState(position, view.getTop());
            } else {
                return new ScrollState(position, 0);
            }
        }
        return null;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onStart()");
        if (activity.xmppConnectionService != null) {
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onResume()");
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case R.id.action_search:
                startActivity(new Intent(getActivity(), SearchActivity.class));
                return true;
            case R.id.action_easy_invite:
                selectAccountToStartEasyInvite();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void selectAccountToStartEasyInvite() {
        final List<Account> accounts =
                EasyOnboardingInvite.getSupportingAccounts(activity.xmppConnectionService);
        if (accounts.isEmpty()) {
            // This can technically happen if opening the menu item races with accounts reconnecting
            // or something
            Toast.makeText(
                            getActivity(),
                            R.string.no_active_accounts_support_this,
                            Toast.LENGTH_LONG)
                    .show();
        } else if (accounts.size() == 1) {
            openEasyInviteScreen(accounts.get(0));
        } else {
            final AtomicReference<Account> selectedAccount = new AtomicReference<>(accounts.get(0));
            final MaterialAlertDialogBuilder alertDialogBuilder =
                    new MaterialAlertDialogBuilder(activity);
            alertDialogBuilder.setTitle(R.string.choose_account);
            final String[] asStrings =
                    Collections2.transform(accounts, a -> a.getJid().asBareJid().toString())
                            .toArray(new String[0]);
            alertDialogBuilder.setSingleChoiceItems(
                    asStrings, 0, (dialog, which) -> selectedAccount.set(accounts.get(which)));
            alertDialogBuilder.setNegativeButton(R.string.cancel, null);
            alertDialogBuilder.setPositiveButton(
                    R.string.ok, (dialog, which) -> openEasyInviteScreen(selectedAccount.get()));
            alertDialogBuilder.create().show();
        }
    }

    private void openEasyInviteScreen(final Account account) {
        EasyOnboardingInviteActivity.launch(account, activity);
    }

    @Override
    void refresh() {
        if (this.binding == null || this.activity == null) {
            Log.d(
                    Config.LOGTAG,
                    "ConversationsOverviewFragment.refresh() skipped updated because view binding"
                            + " or activity was null");
            return;
        }
        this.activity.xmppConnectionService.populateWithOrderedConversations(this.conversations);
        Conversation removed = this.swipedConversation.peek();
        if (removed != null) {
            if (removed.isRead()) {
                this.conversations.remove(removed);
            } else {
                pendingActionHelper.execute();
            }
        }
        if (this.conversations.isEmpty()) {
            this.binding.list.setVisibility(View.GONE);
            this.binding.emptyChatHint.setVisibility(View.VISIBLE);
        } else {
            this.binding.emptyChatHint.setVisibility(View.GONE);
            this.binding.list.setVisibility(View.VISIBLE);
            this.conversationsAdapter.notifyDataSetChanged();
            final var scrollState = pendingScrollState.pop();
            if (scrollState != null) {
                setScrollPosition(scrollState);
            }
        }
    }

    private void toggleHintVisibility() {
        if (this.conversations.isEmpty()) {
            this.binding.list.setVisibility(View.GONE);
            this.binding.emptyChatHint.setVisibility(View.VISIBLE);
        } else {
            this.binding.emptyChatHint.setVisibility(View.GONE);
            this.binding.list.setVisibility(View.VISIBLE);
        }
    }

    private void setScrollPosition(@NonNull final ScrollState scrollPosition) {
        if (binding.list.getLayoutManager() instanceof LinearLayoutManager linearLayoutManager) {
            linearLayoutManager.scrollToPositionWithOffset(
                    scrollPosition.position, scrollPosition.offset);
            if (scrollPosition.position > 0) {
                binding.fab.shrink();
            } else {
                binding.fab.extend();
            }
            binding.fab.clearAnimation();
        }
    }
}
