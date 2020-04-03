package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.support.annotation.NonNull;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class JinglePacket extends IqPacket {

    //TODO add support for groups: https://xmpp.org/extensions/xep-0338.html

    private JinglePacket() {
        super();
    }

    public JinglePacket(final Action action, final String sessionId) {
        super(TYPE.SET);
        final Element jingle = addChild("jingle", Namespace.JINGLE);
        jingle.setAttribute("sid", sessionId);
        jingle.setAttribute("action", action.toString());
    }

    public static JinglePacket upgrade(final IqPacket iqPacket) {
        Preconditions.checkArgument(iqPacket.hasChild("jingle", Namespace.JINGLE));
        final JinglePacket jinglePacket = new JinglePacket();
        jinglePacket.setAttributes(iqPacket.getAttributes());
        jinglePacket.setChildren(iqPacket.getChildren());
        return jinglePacket;
    }

    //TODO can have multiple contents
    public Content getJingleContent() {
        final Element content = getJingleChild("content");
        return content == null ? null : Content.upgrade(content);
    }

    public void setJingleContent(final Content content) { //take content interface
        setJingleChild(content);
    }

    public Reason getReason() {
        final Element reason = getJingleChild("reason");
        return reason == null ? null : Reason.upgrade(reason);
    }

    public void setReason(final Reason reason) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        jingle.addChild(reason);
    }

    //RECOMMENDED for session-initiate, NOT RECOMMENDED otherwise
    public void setInitiator(final Jid initiator) {
        Preconditions.checkArgument(initiator.isFullJid(), "initiator should be a full JID");
        findChild("jingle", Namespace.JINGLE).setAttribute("initiator", initiator.toEscapedString());
    }

    //RECOMMENDED for session-accept, NOT RECOMMENDED otherwise
    public void setResponder(Jid responder) {
        Preconditions.checkArgument(responder.isFullJid(), "responder should be a full JID");
        findChild("jingle", Namespace.JINGLE).setAttribute("responder", responder.toEscapedString());
    }

    public Element getJingleChild(final String name) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        return jingle == null ? null : jingle.findChild(name);
    }

    public void setJingleChild(final Element child) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        jingle.addChild(child);
    }

    public String getSessionId() {
        return findChild("jingle", Namespace.JINGLE).getAttribute("sid");
    }

    public Action getAction() {
        return Action.of(findChild("jingle", Namespace.JINGLE).getAttribute("action"));
    }

    public enum Action {
        CONTENT_ACCEPT,
        CONTENT_ADD,
        CONTENT_MODIFY,
        CONTENT_REJECT,
        CONTENT_REMOVE,
        DESCRIPTION_INFO,
        SECURITY_INFO,
        SESSION_ACCEPT,
        SESSION_INFO,
        SESSION_INITIATE,
        SESSION_TERMINATE,
        TRANSPORT_ACCEPT,
        TRANSPORT_INFO,
        TRANSPORT_REJECT,
        TRANSPORT_REPLACE;

        public static Action of(final String value) {
            //TODO handle invalid
            return Action.valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, value));
        }

        @Override
        @NonNull
        public String toString() {
            return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, super.toString());
        }
    }
}
