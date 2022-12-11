package eu.siacs.conversations.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.utils.Compatibility;

@SuppressLint("Deprecated")
@TargetApi(Build.VERSION_CODES.M)
public class ContactChooserTargetService extends ChooserTargetService implements ServiceConnection {

    private final Object lock = new Object();
    private static final int MAX_TARGETS = 5;
    private XmppConnectionService mXmppConnectionService;

    private static boolean textOnly(IntentFilter filter) {
        for (int i = 0; i < filter.countDataTypes(); ++i) {
            if (!"text/plain".equals(filter.getDataType(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<ChooserTarget> onGetChooserTargets(
            final ComponentName targetActivityName, final IntentFilter matchedFilter) {
        if (!EventReceiver.hasEnabledAccounts(this)) {
            return Collections.emptyList();
        }
        final Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction("contact_chooser");
        Compatibility.startService(this, intent);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
        try {
            waitForService();
            if (!mXmppConnectionService.areMessagesInitialized()) {
                return Collections.emptyList();
            }
            final ArrayList<Conversation> conversations = new ArrayList<>();
            mXmppConnectionService.populateWithOrderedConversations(
                    conversations, textOnly(matchedFilter));
            final ComponentName componentName =
                    new ComponentName(this, ConversationsActivity.class);
            final int pixel = AvatarService.getSystemUiAvatarSize(this);
            final ArrayList<ChooserTarget> chooserTargets = new ArrayList<>();
            for (final Conversation conversation : conversations) {
                if (conversation.sentMessagesCount() == 0) {
                    continue;
                }
                final String name = conversation.getName().toString();
                final Icon icon =
                        Icon.createWithBitmap(
                                mXmppConnectionService.getAvatarService().get(conversation, pixel));
                final float score = 1 - (1.0f / MAX_TARGETS) * chooserTargets.size();
                final Bundle extras = new Bundle();
                extras.putString(ConversationsActivity.EXTRA_CONVERSATION, conversation.getUuid());
                chooserTargets.add(new ChooserTarget(name, icon, score, componentName, extras));
                if (chooserTargets.size() >= MAX_TARGETS) {
                    return chooserTargets;
                }
            }
            return chooserTargets;
        } catch (final InterruptedException e) {
            Log.d(
                    Config.LOGTAG,
                    "Thread got interrupted before binding to XmppConnectionService",
                    e);
        } finally {
            unbindService(this);
        }
        return Collections.emptyList();
    }

    @Override
    public void onServiceConnected(final ComponentName name, final IBinder service) {
        XmppConnectionService.XmppConnectionBinder binder =
                (XmppConnectionService.XmppConnectionBinder) service;
        mXmppConnectionService = binder.getService();
        synchronized (this.lock) {
            lock.notifyAll();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mXmppConnectionService = null;
    }

    private void waitForService() throws InterruptedException {
        if (mXmppConnectionService == null) {
            synchronized (this.lock) {
                lock.wait();
            }
        }
    }
}
