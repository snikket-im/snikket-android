package im.conversations.android.xmpp;

import org.jxmpp.jid.Jid;

public abstract class Entity {

    public final Jid address;

    private Entity(final Jid address) {
        this.address = address;
    }

    public static class DiscoItem extends Entity {

        private DiscoItem(Jid address) {
            super(address);
        }
    }

    public static class Presence extends Entity {

        private Presence(Jid address) {
            super(address);
        }
    }

    public static Presence presence(final Jid address) {
        return new Presence(address);
    }

    public static DiscoItem discoItem(final Jid address) {
        return new DiscoItem(address);
    }
}
