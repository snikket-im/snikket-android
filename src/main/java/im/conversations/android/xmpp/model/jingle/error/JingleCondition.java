package im.conversations.android.xmpp.model.jingle.error;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.error.Error;

public abstract class JingleCondition extends Error.Extension {

    private JingleCondition(Class<? extends JingleCondition> clazz) {
        super(clazz);
    }

    @XmlElement(namespace = Namespace.JINGLE_ERRORS)
    public static class OutOfOrder extends JingleCondition {

        public OutOfOrder() {
            super(OutOfOrder.class);
        }
    }

    @XmlElement(namespace = Namespace.JINGLE_ERRORS)
    public static class TieBreak extends JingleCondition {

        public TieBreak() {
            super(TieBreak.class);
        }
    }

    @XmlElement(namespace = Namespace.JINGLE_ERRORS)
    public static class UnknownSession extends JingleCondition {

        public UnknownSession() {
            super(UnknownSession.class);
        }
    }

    @XmlElement(namespace = Namespace.JINGLE_ERRORS)
    public static class UnsupportedInfo extends JingleCondition {

        public UnsupportedInfo() {
            super(UnsupportedInfo.class);
        }
    }
}
