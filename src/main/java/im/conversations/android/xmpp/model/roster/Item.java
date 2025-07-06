package im.conversations.android.xmpp.model.roster;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@XmlElement
public class Item extends Extension {

    public static final List<Subscription> RESULT_SUBSCRIPTIONS =
            Arrays.asList(Subscription.NONE, Subscription.TO, Subscription.FROM, Subscription.BOTH);

    public Item() {
        super(Item.class);
    }

    public Jid getJid() {
        return getAttributeAsJid("jid");
    }

    public void setJid(final Jid jid) {
        this.setAttribute("jid", jid);
    }

    public String getItemName() {
        return this.getAttribute("name");
    }

    public void setItemName(final String serverName) {
        this.setAttribute("name", serverName);
    }

    public boolean isPendingOut() {
        return "subscribe".equalsIgnoreCase(this.getAttribute("ask"));
    }

    public Subscription getSubscription() {
        final String value = this.getAttribute("subscription");
        try {
            return value == null ? null : Subscription.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public void setSubscription(final Subscription subscription) {
        if (subscription == null) {
            this.removeAttribute("subscription");
        } else {
            this.setAttribute("subscription", subscription.toString().toLowerCase(Locale.ROOT));
        }
    }

    public Set<String> getGroups() {
        return Group.getGroups(this.getExtensions(Group.class));
    }

    public void setGroups(final Collection<String> groups) {
        for (final String group : groups) {
            this.addExtension(new Group(group));
        }
    }

    public enum Subscription {
        NONE,
        TO,
        FROM,
        BOTH,
        REMOVE
    }
}
