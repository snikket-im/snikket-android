package im.conversations.android.xmpp.model.stanza;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.capabilties.EntityCapabilities;
import im.conversations.android.xmpp.model.jabber.Show;
import im.conversations.android.xmpp.model.jabber.Status;

@XmlElement
public class Presence extends Stanza implements EntityCapabilities {

    public Presence() {
        super(Presence.class);
    }

    public Availability getAvailability() {
        final var show = getExtension(Show.class);
        if (show == null) {
            return Availability.ONLINE;
        }
        return Availability.valueOfShown(show.getContent());
    }

    public void setAvailability(final Availability availability) {
        if (availability == null || availability == Availability.ONLINE) {
            return;
        }
        this.addExtension(new Show()).setContent(availability.toShowString());
    }

    public void setStatus(final String status) {
        if (Strings.isNullOrEmpty(status)) {
            return;
        }
        this.addExtension(new Status()).setContent(status);
    }

    public String getStatus() {
        final var status = getExtension(Status.class);
        return status == null ? null : status.getContent();
    }

    public enum Availability {
        CHAT,
        ONLINE,
        AWAY,
        XA,
        DND,
        OFFLINE;

        public String toShowString() {
            return switch (this) {
                case CHAT -> "chat";
                case AWAY -> "away";
                case XA -> "xa";
                case DND -> "dnd";
                default -> null;
            };
        }

        public static Availability valueOfShown(final String content) {
            if (Strings.isNullOrEmpty(content)) {
                return Availability.ONLINE;
            }
            return switch (content) {
                case "away" -> Availability.AWAY;
                case "xa" -> Availability.XA;
                case "dnd" -> Availability.DND;
                case "chat" -> Availability.CHAT;
                default -> Availability.ONLINE;
            };
        }
    }
}
