package im.conversations.android.xmpp.model.muc;

import android.util.Log;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.Extension;
import java.util.Locale;

public abstract class Item extends Extension {

    public Item(final Class<? extends Item> clazz) {
        super(clazz);
    }

    public Affiliation getAffiliation() {
        return affiliationOrNone(this.getAttribute("affiliation"));
    }

    public static Affiliation affiliationOrNone(final String affiliation) {
        if (Strings.isNullOrEmpty(affiliation)) {
            return Affiliation.NONE;
        }
        try {
            return Affiliation.valueOf(affiliation.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return Affiliation.NONE;
        }
    }

    public Role getRole() {
        return roleOrNone(this.getAttribute("role"));
    }

    public static Role roleOrNone(final String role) {
        if (Strings.isNullOrEmpty(role)) {
            return Role.NONE;
        }
        try {
            return Role.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            Log.d(Config.LOGTAG, "could not parse role " + role);
            return Role.NONE;
        }
    }

    public String getNick() {
        return this.getAttribute("nick");
    }

    public Jid getJid() {
        return this.getAttributeAsJid("jid");
    }
}
