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
import android.app.FragmentTransaction;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityConversationsBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationRead;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.interfaces.OnConversationsListItemUpdated;
import eu.siacs.conversations.ui.service.EmojiService;

public class ConversationsMainActivity extends XmppActivity implements OnConversationSelected, OnConversationArchived, OnConversationsListItemUpdated, OnConversationRead {

	private ActivityConversationsBinding binding;

	@Override
	protected void refreshUiReal() {

	}

	@Override
	void onBackendConnected() {
		notifyFragment(R.id.main_fragment);
		notifyFragment(R.id.secondary_fragment);
	}

	private void notifyFragment(@IdRes int id) {
		Fragment mainFragment = getFragmentManager().findFragmentById(id);
		if (mainFragment != null && mainFragment instanceof XmppFragment) {
			((XmppFragment) mainFragment).onBackendConnected();
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		new EmojiService(this).init();
		this.binding = DataBindingUtil.setContentView(this,R.layout.activity_conversations);
		this.initializeFragments();
	}

	@Override
	public void onConversationSelected(Conversation conversation) {
		Log.d(Config.LOGTAG,"selected "+conversation.getName());
		ConversationFragment conversationFragment = (ConversationFragment) getFragmentManager().findFragmentById(R.id.secondary_fragment);
		if (conversationFragment == null) {
			conversationFragment = new ConversationFragment();
			FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
			fragmentTransaction.replace(R.id.main_fragment,conversationFragment);
			fragmentTransaction.commit();
		}
		conversationFragment.reInit(conversation);
	}

	private void initializeFragments() {
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
		if (binding.secondaryFragment != null) {
			transaction.replace(R.id.secondary_fragment, new ConversationFragment());
		}
		transaction.commit();
	}

	@Override
	public void onConversationArchived(Conversation conversation) {

	}

	@Override
	public void onConversationsListItemUpdated() {

	}

	@Override
	public void onConversationRead(Conversation conversation) {
		Log.d(Config.LOGTAG,"read event for "+conversation.getName()+" received");
	}
}
