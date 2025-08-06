package im.conversations.android.model;

import com.google.common.base.Strings;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import im.conversations.android.xmpp.model.bookmark2.Extensions;
import im.conversations.android.xmpp.model.roster.Group;
import java.util.Collection;
import java.util.Collections;
import org.immutables.value.Value;
import org.jspecify.annotations.Nullable;

@Value.Immutable
public abstract class Bookmark implements ListItem {

    public abstract Account getAccount();

    @Value.Derived
    public Jid getFullAddress() {
        final var address = getAddress();
        final String nick = Strings.nullToEmpty(getNick()).trim();
        if (address == null || nick.isEmpty()) {
            return address;
        }
        try {
            return address.withResource(nick);
        } catch (final IllegalArgumentException e) {
            return address;
        }
    }

    public abstract boolean isAutoJoin();

    @Nullable
    public abstract String getName();

    @Nullable
    public abstract String getNick();

    @Nullable
    public abstract String getPassword();

    @Nullable
    public abstract Extensions getExtensions();

    @Override
    @Value.Derived
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(this.getAddress().asBareJid().toString());
    }

    @Override
    @Value.Derived
    public String getDisplayName() {
        final var mucOptions =
                getAccount()
                        .getXmppConnection()
                        .getManager(MultiUserChatManager.class)
                        .getState(getAddress().asBareJid());
        final String name = getName();
        if (mucOptions != null) {
            return Conversation.getName(mucOptions, this);
        } else if (printableValue(name)) {
            return name.trim();
        } else {
            final var address = this.getAddress();
            if (address.isDomainJid()) {
                return address.getDomain().toString();
            } else {
                return address.getLocal();
            }
        }
    }

    @Override
    @Value.Derived
    public Collection<Tag> getTags() {
        final var extensions = this.getExtensions();
        if (extensions == null) {
            return Collections.emptyList();
        }
        return Tag.of(Group.getGroups(extensions.getExtensions(Group.class)));
    }

    public static String nickOfAddress(final Account account, final Jid address) {
        final var resource = address.getResource();
        if (Strings.isNullOrEmpty(resource) || resource.equals(MucOptions.defaultNick(account))) {
            return null;
        } else {
            return resource;
        }
    }

    public static boolean printableValue(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }
}
