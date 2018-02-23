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


import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityConversationsBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationRead;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.interfaces.OnConversationsListItemUpdated;
import eu.siacs.conversations.ui.service.EmojiService;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;

public class ConversationActivity extends XmppActivity implements OnConversationSelected, OnConversationArchived, OnConversationsListItemUpdated, OnConversationRead, XmppConnectionService.OnAccountUpdate, XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnRosterUpdate, OnUpdateBlocklist, XmppConnectionService.OnShowErrorToast {

	public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW";
	public static final String EXTRA_CONVERSATION = "conversationUuid";
	public static final String EXTRA_DOWNLOAD_UUID = "eu.siacs.conversations.download_uuid";
	public static final String EXTRA_TEXT = "text";
	public static final String EXTRA_NICK = "nick";
	public static final String EXTRA_IS_PRIVATE_MESSAGE = "pm";


	//secondary fragment (when holding the conversation, must be initialized before refreshing the overview fragment
	private static final @IdRes
	int[] FRAGMENT_ID_NOTIFICATION_ORDER = {R.id.secondary_fragment, R.id.main_fragment};
	private final PendingItem<Intent> pendingViewIntent = new PendingItem<>();
	private ActivityConversationsBinding binding;

	private static boolean isViewIntent(Intent i) {
		return i != null && ACTION_VIEW_CONVERSATION.equals(i.getAction()) && i.hasExtra(EXTRA_CONVERSATION);
	}

	private static Intent createLauncherIntent(Context context) {
		final Intent intent = new Intent(context, ConversationActivity.class);
		intent.setAction(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		return intent;
	}

	@Override
	protected void refreshUiReal() {
		for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
			refreshFragment(id);
		}
	}

	@Override
	void onBackendConnected() {
		for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
			notifyFragmentOfBackendConnected(id);
		}
		invalidateActionBarTitle();
		Intent intent = pendingViewIntent.pop();
		if (intent != null) {
			processViewIntent(intent);
		}
	}

	private void notifyFragmentOfBackendConnected(@IdRes int id) {
		final Fragment fragment = getFragmentManager().findFragmentById(id);
		if (fragment != null && fragment instanceof XmppFragment) {
			((XmppFragment) fragment).onBackendConnected();
		}
	}

	private void refreshFragment(@IdRes int id) {
		final Fragment fragment = getFragmentManager().findFragmentById(id);
		if (fragment != null && fragment instanceof XmppFragment) {
			((XmppFragment) fragment).refresh();
		}
	}

	private void processViewIntent(Intent intent) {
		String uuid = intent.getStringExtra(EXTRA_CONVERSATION);
		Conversation conversation = uuid != null ? xmppConnectionService.findConversationByUuid(uuid) : null;
		if (conversation == null) {
			Log.d(Config.LOGTAG, "unable to view conversation with uuid:" + uuid);
			return;
		}
		openConversation(conversation, intent.getExtras());
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		new EmojiService(this).init();
		this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
		this.getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
		this.initializeFragments();
		this.invalidateActionBarTitle();
		final Intent intent = getIntent();
		if (isViewIntent(intent)) {
			pendingViewIntent.push(intent);
			setIntent(createLauncherIntent(this));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_conversations, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onConversationSelected(Conversation conversation) {
		Log.d(Config.LOGTAG, "selected " + conversation.getName());
		openConversation(conversation, null);
	}

	private void openConversation(Conversation conversation, Bundle extras) {
		ConversationFragment conversationFragment = (ConversationFragment) getFragmentManager().findFragmentById(R.id.secondary_fragment);
		final boolean mainNeedsRefresh;
		if (conversationFragment == null) {
			mainNeedsRefresh = false;
			conversationFragment = new ConversationFragment();
			FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
			fragmentTransaction.replace(R.id.main_fragment, conversationFragment);
			fragmentTransaction.addToBackStack(null);
			fragmentTransaction.commit();
		} else {
			mainNeedsRefresh = true;
		}
		conversationFragment.reInit(conversation);
		if (mainNeedsRefresh) {
			refreshFragment(R.id.main_fragment);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				FragmentManager fm = getFragmentManager();
				if (fm.getBackStackEntryCount() > 0) {
					fm.popBackStack();
					return true;
				}
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		if (isViewIntent(intent)) {
			if (xmppConnectionService != null) {
				processViewIntent(intent);
			} else {
				pendingViewIntent.push(intent);
			}
		}
	}

	private void initializeFragments() {
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
		Fragment secondaryFragment = getFragmentManager().findFragmentById(R.id.secondary_fragment);
		if (mainFragment != null) {
			Log.d(Config.LOGTAG, "initializeFragment(). main fragment exists");
			if (binding.secondaryFragment != null) {
				if (mainFragment instanceof ConversationFragment) {
					Log.d(Config.LOGTAG, "gained secondary fragment. moving...");
					getFragmentManager().popBackStack();
					transaction.remove(mainFragment);
					transaction.commit();
					getFragmentManager().executePendingTransactions();
					transaction = getFragmentManager().beginTransaction();
					transaction.replace(R.id.secondary_fragment, mainFragment);
					transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
					transaction.commit();
					return;
				}
			} else {
				if (secondaryFragment != null && secondaryFragment instanceof ConversationFragment) {
					Log.d(Config.LOGTAG, "lost secondary fragment. moving...");
					transaction.remove(secondaryFragment);
					transaction.commit();
					getFragmentManager().executePendingTransactions();
					transaction = getFragmentManager().beginTransaction();
					transaction.replace(R.id.main_fragment, secondaryFragment);
					transaction.addToBackStack(null);
					transaction.commit();
					return;
				}
			}
		} else {
			transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
		}
		if (binding.secondaryFragment != null && secondaryFragment == null) {
			transaction.replace(R.id.secondary_fragment, new ConversationFragment());
		}
		transaction.commit();
	}

	private void invalidateActionBarTitle() {
		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
			if (mainFragment != null && mainFragment instanceof ConversationFragment) {
				final Conversation conversation = ((ConversationFragment) mainFragment).getConversation();
				if (conversation != null) {
					actionBar.setTitle(conversation.getName());
					actionBar.setDisplayHomeAsUpEnabled(true);
					return;
				}
			}
			actionBar.setTitle(R.string.app_name);
			actionBar.setDisplayHomeAsUpEnabled(false);
		}
	}

	@Override
	public void onConversationArchived(Conversation conversation) {

	}

	@Override
	public void onConversationsListItemUpdated() {
		Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
		if (fragment != null && fragment instanceof ConversationsOverviewFragment) {
			((ConversationsOverviewFragment) fragment).refresh();
		}
	}

	@Override
	public void onConversationRead(Conversation conversation) {
		Log.d(Config.LOGTAG, "read event for " + conversation.getName() + " received");
	}

	@Override
	public void onAccountUpdate() {
		this.refreshUi();
	}

	@Override
	public void onConversationUpdate() {
		this.refreshUi();
	}

	@Override
	public void onRosterUpdate() {
		this.refreshUi();
	}

	@Override
	public void OnUpdateBlocklist(OnUpdateBlocklist.Status status) {
		this.refreshUi();
	}

	@Override
	public void onShowErrorToast(int resId) {
		runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
	}
}
