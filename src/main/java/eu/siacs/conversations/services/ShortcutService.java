package eu.siacs.conversations.services;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.utils.ReplacingSerialSingleThreadExecutor;
import eu.siacs.conversations.xmpp.jid.Jid;

public class ShortcutService {

    private final XmppConnectionService xmppConnectionService;
    private final ReplacingSerialSingleThreadExecutor replacingSerialSingleThreadExecutor = new ReplacingSerialSingleThreadExecutor(false);

    public ShortcutService(XmppConnectionService xmppConnectionService) {
        this.xmppConnectionService = xmppConnectionService;
    }

    public void refresh() {
        refresh(false);
    }

    public void refresh(final boolean forceUpdate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    refreshImpl(forceUpdate);
                }
            };
            replacingSerialSingleThreadExecutor.execute(r);
        }
    }

    @TargetApi(25)
    public void report(Contact contact) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager = xmppConnectionService.getSystemService(ShortcutManager.class);
            shortcutManager.reportShortcutUsed(getShortcutId(contact));
        }
    }

    @TargetApi(25)
    private void refreshImpl(boolean forceUpdate) {
        List<FrequentContact> frequentContacts = xmppConnectionService.databaseBackend.getFrequentContacts(30);
        HashMap<String,Account> accounts = new HashMap<>();
        for(Account account : xmppConnectionService.getAccounts()) {
            accounts.put(account.getUuid(),account);
        }
        List<Contact> contacts = new ArrayList<>();
        for(FrequentContact frequentContact : frequentContacts) {
            Account account = accounts.get(frequentContact.account);
            if (account != null) {
                contacts.add(account.getRoster().getContact(frequentContact.contact));
            }
        }
        ShortcutManager shortcutManager = xmppConnectionService.getSystemService(ShortcutManager.class);
        boolean needsUpdate = forceUpdate || contactsChanged(contacts,shortcutManager.getDynamicShortcuts());
        if (!needsUpdate) {
            Log.d(Config.LOGTAG,"skipping shortcut update");
            return;
        }
        List<ShortcutInfo> newDynamicShortCuts = new ArrayList<>();
        for (Contact contact : contacts) {
            ShortcutInfo shortcut = new ShortcutInfo.Builder(xmppConnectionService, getShortcutId(contact))
                    .setShortLabel(contact.getDisplayName())
                    .setIntent(getShortcutIntent(contact))
                    .setIcon(Icon.createWithBitmap(xmppConnectionService.getAvatarService().getRoundedShortcut(contact)))
                    .build();
            newDynamicShortCuts.add(shortcut);
        }
        if (shortcutManager.setDynamicShortcuts(newDynamicShortCuts)) {
            Log.d(Config.LOGTAG,"updated dynamic shortcuts");
        } else {
            Log.d(Config.LOGTAG, "unable to update dynamic shortcuts");
        }
    }

    private static boolean contactsChanged(List<Contact> needles, List<ShortcutInfo> haystack) {
        for(Contact needle : needles) {
            if(!contactExists(needle,haystack)) {
                return true;
            }
        }
        return needles.size() != haystack.size();
    }

    @TargetApi(25)
    private static boolean contactExists(Contact needle, List<ShortcutInfo> haystack) {
        for(ShortcutInfo shortcutInfo : haystack) {
            if (getShortcutId(needle).equals(shortcutInfo.getId()) && needle.getDisplayName().equals(shortcutInfo.getShortLabel())) {
                return true;
            }
        }
        return false;
    }

    private static String getShortcutId(Contact contact) {
        return contact.getAccount().getJid().toBareJid().toPreppedString()+"#"+contact.getJid().toBareJid().toPreppedString();
    }

    private Intent getShortcutIntent(Contact contact) {
        Intent intent = new Intent(xmppConnectionService, StartConversationActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("xmpp:"+contact.getJid().toBareJid().toString()));
        intent.putExtra("account",contact.getAccount().getJid().toBareJid().toString());
        return intent;
    }

    public static class FrequentContact {
        private final String account;
        private final Jid contact;

        public FrequentContact(String account, Jid contact) {
            this.account = account;
            this.contact = contact;
        }
    }

}
