package im.conversations.android.xmpp.model.muc.user;

import android.util.Log;

import com.google.common.base.Strings;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Jid;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.Role;

import java.util.Locale;

@XmlElement
public class Item extends Extension {


    public Item() {
        super(Item.class);
    }

    public Affiliation getAffiliation() {
        final var affiliation = this.getAttribute("affiliation");
        if (Strings.isNullOrEmpty(affiliation)) {
            return Affiliation.NONE;
        }
        try {
            return Affiliation.valueOf(affiliation.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            Log.d(Config.LOGTAG,"could not parse affiliation "+affiliation);
            return Affiliation.NONE;
        }
    }

    public Role getRole() {
        final var role = this.getAttribute("role");
        if (Strings.isNullOrEmpty(role)) {
            return Role.NONE;
        }
        try {
            return Role.valueOf(role.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            Log.d(Config.LOGTAG,"could not parse role "+ role);
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
