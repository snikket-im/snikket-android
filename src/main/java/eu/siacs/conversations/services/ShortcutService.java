package eu.siacs.conversations.services;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.utils.ReplacingSerialSingleThreadExecutor;
import eu.siacs.conversations.xmpp.Jid;
import java.util.Collection;
import java.util.List;

public class ShortcutService {

    public static final char ID_SEPARATOR = '#';

    private final XmppConnectionService xmppConnectionService;
    private final ReplacingSerialSingleThreadExecutor replacingSerialSingleThreadExecutor =
            new ReplacingSerialSingleThreadExecutor(ShortcutService.class.getSimpleName());

    public ShortcutService(final XmppConnectionService xmppConnectionService) {
        this.xmppConnectionService = xmppConnectionService;
    }

    public void refresh() {
        refresh(false);
    }

    public void refresh(final boolean forceUpdate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            final Runnable r = () -> refreshImpl(forceUpdate);
            replacingSerialSingleThreadExecutor.execute(r);
        }
    }

    @TargetApi(25)
    public void report(Contact contact) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            ShortcutManager shortcutManager =
                    xmppConnectionService.getSystemService(ShortcutManager.class);
            shortcutManager.reportShortcutUsed(getShortcutId(contact));
        }
    }

    @TargetApi(25)
    private void refreshImpl(final boolean forceUpdate) {
        final var frequentContacts = xmppConnectionService.databaseBackend.getFrequentContacts(30);
        final var accounts =
                ImmutableMap.copyOf(
                        Maps.uniqueIndex(xmppConnectionService.getAccounts(), Account::getUuid));
        final var contactBuilder = new ImmutableMap.Builder<FrequentContact, Contact>();
        for (final var frequentContact : frequentContacts) {
            final Account account = accounts.get(frequentContact.account);
            if (account != null) {
                final var contact = account.getRoster().getContact(frequentContact.contact);
                contactBuilder.put(frequentContact, contact);
            }
        }
        final var contacts = contactBuilder.build();
        final var current = ShortcutManagerCompat.getDynamicShortcuts(xmppConnectionService);
        boolean needsUpdate = forceUpdate || contactsChanged(contacts.values(), current);
        if (!needsUpdate) {
            Log.d(Config.LOGTAG, "skipping shortcut update");
            return;
        }
        final var newDynamicShortcuts = new ImmutableList.Builder<ShortcutInfoCompat>();
        for (final var entry : contacts.entrySet()) {
            final var contact = entry.getValue();
            final var conversation = entry.getKey().conversation;
            final var shortcut = getShortcutInfo(contact, conversation);
            newDynamicShortcuts.add(shortcut);
        }
        if (ShortcutManagerCompat.setDynamicShortcuts(
                xmppConnectionService, newDynamicShortcuts.build())) {
            Log.d(Config.LOGTAG, "updated dynamic shortcuts");
        } else {
            Log.d(Config.LOGTAG, "unable to update dynamic shortcuts");
        }
    }

    public ShortcutInfoCompat getShortcutInfo(final Contact contact) {
        final var conversation = xmppConnectionService.find(contact);
        final var uuid = conversation == null ? null : conversation.getUuid();
        return getShortcutInfo(contact, uuid);
    }

    public ShortcutInfoCompat getShortcutInfo(final Contact contact, final String conversation) {
        final ShortcutInfoCompat.Builder builder =
                new ShortcutInfoCompat.Builder(xmppConnectionService, getShortcutId(contact))
                        .setShortLabel(contact.getDisplayName())
                        .setIntent(getShortcutIntent(contact))
                        .setIsConversation();
        builder.setIcon(
                IconCompat.createWithBitmap(
                        xmppConnectionService.getAvatarService().getRoundedShortcut(contact)));
        if (conversation != null) {
            setConversation(builder, conversation);
        }
        return builder.build();
    }

    public ShortcutInfoCompat getShortcutInfo(final MucOptions mucOptions) {
        final ShortcutInfoCompat.Builder builder =
                new ShortcutInfoCompat.Builder(xmppConnectionService, getShortcutId(mucOptions))
                        .setShortLabel(mucOptions.getConversation().getName())
                        .setIntent(getShortcutIntent(mucOptions))
                        .setIsConversation();
        builder.setIcon(
                IconCompat.createWithBitmap(
                        xmppConnectionService.getAvatarService().getRoundedShortcut(mucOptions)));
        setConversation(builder, mucOptions.getConversation().getUuid());
        return builder.build();
    }

    private static void setConversation(
            final ShortcutInfoCompat.Builder builder, @NonNull final String conversation) {
        builder.setCategories(ImmutableSet.of("eu.siacs.conversations.category.SHARE_TARGET"));
        final var extras = new PersistableBundle();
        extras.putString(ConversationsActivity.EXTRA_CONVERSATION, conversation);
        builder.setExtras(extras);
    }

    private static boolean contactsChanged(
            final Collection<Contact> needles, final List<ShortcutInfoCompat> haystack) {
        for (final Contact needle : needles) {
            if (!contactExists(needle, haystack)) {
                return true;
            }
        }
        return needles.size() != haystack.size();
    }

    @TargetApi(25)
    private static boolean contactExists(
            final Contact needle, final List<ShortcutInfoCompat> haystack) {
        for (final ShortcutInfoCompat shortcutInfo : haystack) {
            final var label = shortcutInfo.getShortLabel();
            if (getShortcutId(needle).equals(shortcutInfo.getId())
                    && needle.getDisplayName().equals(label.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String getShortcutId(final Contact contact) {
        return Joiner.on(ID_SEPARATOR)
                .join(
                        contact.getAccount().getJid().asBareJid().toString(),
                        contact.getAddress().asBareJid().toString());
    }

    private static String getShortcutId(final MucOptions mucOptions) {
        final Account account = mucOptions.getAccount();
        final Jid jid = mucOptions.getConversation().getAddress();
        return Joiner.on(ID_SEPARATOR)
                .join(account.getJid().asBareJid().toString(), jid.asBareJid().toString());
    }

    private Intent getShortcutIntent(final MucOptions mucOptions) {
        final Account account = mucOptions.getAccount();
        return getShortcutIntent(
                account,
                Uri.parse(
                        String.format(
                                "xmpp:%s?join",
                                mucOptions.getConversation().getAddress().asBareJid().toString())));
    }

    private Intent getShortcutIntent(final Contact contact) {
        return getShortcutIntent(
                contact.getAccount(),
                Uri.parse("xmpp:" + contact.getAddress().asBareJid().toString()));
    }

    private Intent getShortcutIntent(final Account account, final Uri uri) {
        Intent intent = new Intent(xmppConnectionService, StartConversationActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        intent.putExtra("account", account.getJid().asBareJid().toString());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @NonNull
    public Intent createShortcut(final Contact contact, final boolean legacy) {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !legacy) {
            final var shortcut = getShortcutInfo(contact);
            intent =
                    ShortcutManagerCompat.createShortcutResultIntent(
                            xmppConnectionService, shortcut);
        } else {
            intent = createShortcutResultIntent(contact);
        }
        return intent;
    }

    @NonNull
    private Intent createShortcutResultIntent(final Contact contact) {
        AvatarService avatarService = xmppConnectionService.getAvatarService();
        Bitmap icon = avatarService.getRoundedShortcutWithIcon(contact);
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, contact.getDisplayName());
        intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, getShortcutIntent(contact));
        return intent;
    }

    public static class FrequentContact {
        private final String conversation;
        private final String account;
        private final Jid contact;

        public FrequentContact(final String conversation, final String account, final Jid contact) {
            this.conversation = conversation;
            this.account = account;
            this.contact = contact;
        }
    }
}
