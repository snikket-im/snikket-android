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
import androidx.core.view.MenuProvider;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
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
import eu.siacs.conversations.ui.util.PendingActionHelper;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ScrollState;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.xmpp.manager.EasyOnboardingManager;
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
                    int position = viewHolder.getLayoutPosition();
                    final Conversation conversation;
                    try {
                        conversation = conversations.get(position);
                    } catch (final IndexOutOfBoundsException e) {
                        return;
                    }
                    onConversationSwiped(conversation, position);
                }
            };

    private final MenuProvider menuProvider =
            new MenuProvider() {
                @Override
                public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                    menuInflater.inflate(R.menu.fragment_conversations_overview, menu);
                    AccountUtils.showHideMenuItems(menu);
                    final MenuItem easyOnboardInvite = menu.findItem(R.id.action_easy_invite);
                    easyOnboardInvite.setVisible(
                            EasyOnboardingManager.anyHasSupport(
                                    requireXmppActivity().xmppConnectionService));
                    final MenuItem privacyPolicyMenuItem =
                            menu.findItem(R.id.action_privacy_policy);
                    privacyPolicyMenuItem.setVisible(
                            BuildConfig.PRIVACY_POLICY != null
                                    && QuickConversationsService.isPlayStoreFlavor());
                }

                @Override
                public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                    final var id = menuItem.getItemId();
                    if (id == R.id.action_search) {
                        startActivity(new Intent(getActivity(), SearchActivity.class));
                        return true;
                    } else if (id == R.id.action_easy_invite) {
                        selectAccountToStartEasyInvite();
                        return true;
                    } else {
                        return false;
                    }
                }
            };

    private void onConversationSwiped(final Conversation c, final int position) {
        pendingActionHelper.execute();
        this.swipedConversation.push(c);
        conversationsAdapter.remove(swipedConversation.peek(), position);
        toggleHintVisibility();
        requireXmppActivity().xmppConnectionService.markRead(swipedConversation.peek());
        final boolean formerlySelected =
                ConversationFragment.getConversation(getActivity()) == swipedConversation.peek();
        if (getActivity() instanceof OnConversationArchived callback) {
            callback.onConversationArchived(swipedConversation.peek());
        }
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
                        .setAction(R.string.undo, v -> undoSwipe(formerlySelected, position))
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
                    if (conversation == null) {
                        return;
                    }
                    if (!conversation.isRead()
                            && conversation.getMode() == Conversation.MODE_SINGLE) {
                        return;
                    }
                    requireXmppActivity().xmppConnectionService.archiveConversation(c);
                });
        snackbar.show();
    }

    private void undoSwipe(final boolean formerlySelected, final int position) {
        pendingActionHelper.undo();
        final var c = swipedConversation.pop();
        if (!conversations.contains(c)) {
            conversationsAdapter.insert(c, position);
        }
        toggleHintVisibility();
        if (formerlySelected) {
            if (getActivity() instanceof OnConversationSelected on) {
                on.onConversationSelected(c);
            }
        }
        if (binding.list.getLayoutManager() instanceof LinearLayoutManager llm
                && position > llm.findLastVisibleItemPosition()) {
            binding.list.smoothScrollToPosition(position);
        }
    }

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

    public void onViewCreated(@NonNull final View view, final Bundle savedInstanceState) {
        requireActivity()
                .addMenuProvider(menuProvider, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        if (savedInstanceState == null) {
            return;
        }
        pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        this.binding = null;
        this.conversationsAdapter = null;
        this.touchHelper = null;
    }

    @Override
    public void onPause() {
        pendingActionHelper.execute();
        super.onPause();
    }

    @Override
    public View onCreateView(
            @NonNull final LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {
        this.binding =
                DataBindingUtil.inflate(
                        inflater, R.layout.fragment_conversations_overview, container, false);
        this.binding.fab.setOnClickListener(
                (view) -> StartConversationActivity.launch(getActivity()));

        this.conversationsAdapter =
                new ConversationAdapter(requireXmppActivity(), this.conversations);
        this.conversationsAdapter.setConversationClickListener(
                (view, conversation) -> {
                    if (getActivity() instanceof OnConversationSelected callback) {
                        callback.onConversationSelected(conversation);
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
    public void onBackendConnected() {
        refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {
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
        if (requireXmppActivity().xmppConnectionService != null) {
            refresh();
        }
    }

    private void selectAccountToStartEasyInvite() {
        final var accounts =
                EasyOnboardingManager.getAccounts(requireXmppActivity().xmppConnectionService);
        if (accounts.isEmpty()) {
            // This can technically happen if opening the menu item races with accounts reconnecting
            // or something
            Toast.makeText(
                            requireContext(),
                            R.string.no_active_accounts_support_this,
                            Toast.LENGTH_LONG)
                    .show();
        } else if (accounts.size() == 1) {
            openEasyInviteScreen(accounts.get(0));
        } else {
            final var selectedAccount = new AtomicReference<>(accounts.get(0));
            final var alertDialogBuilder = new MaterialAlertDialogBuilder(requireContext());
            alertDialogBuilder.setTitle(R.string.choose_account);
            final var asStrings = AccountUtils.asStrings(accounts);
            alertDialogBuilder.setSingleChoiceItems(
                    asStrings, 0, (dialog, which) -> selectedAccount.set(accounts.get(which)));
            alertDialogBuilder.setNegativeButton(R.string.cancel, null);
            alertDialogBuilder.setPositiveButton(
                    R.string.ok, (dialog, which) -> openEasyInviteScreen(selectedAccount.get()));
            alertDialogBuilder.create().show();
        }
    }

    private void openEasyInviteScreen(final Account account) {
        EasyOnboardingInviteActivity.launch(account, requireContext());
    }

    @Override
    void refresh() {
        if (this.binding == null) {
            Log.d(
                    Config.LOGTAG,
                    "ConversationsOverviewFragment.refresh() skipped updated because view binding"
                            + " or activity was null");
            return;
        }
        this.requireXmppActivity()
                .xmppConnectionService
                .populateWithOrderedConversations(this.conversations);
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
