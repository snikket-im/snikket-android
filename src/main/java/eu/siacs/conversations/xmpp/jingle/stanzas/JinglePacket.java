package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import java.util.Map;

public class JinglePacket extends IqPacket {

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
        Preconditions.checkArgument(iqPacket.getType() == TYPE.SET);
        final JinglePacket jinglePacket = new JinglePacket();
        jinglePacket.setAttributes(iqPacket.getAttributes());
        jinglePacket.setChildren(iqPacket.getChildren());
        return jinglePacket;
    }

    // TODO deprecate this somehow and make file transfer fail if there are multiple (or something)
    public Content getJingleContent() {
        final Element content = getJingleChild("content");
        return content == null ? null : Content.upgrade(content);
    }

    public Group getGroup() {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        final Element group = jingle.findChild("group", Namespace.JINGLE_APPS_GROUPING);
        return group == null ? null : Group.upgrade(group);
    }

    public void addGroup(final Group group) {
        this.addJingleChild(group);
    }

    public Map<String, Content> getJingleContents() {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        ImmutableMap.Builder<String, Content> builder = new ImmutableMap.Builder<>();
        for (final Element child : jingle.getChildren()) {
            if ("content".equals(child.getName())) {
                final Content content = Content.upgrade(child);
                builder.put(content.getContentName(), content);
            }
        }
        return builder.build();
    }

    public void addJingleContent(final Content content) { // take content interface
        addJingleChild(content);
    }

    public ReasonWrapper getReason() {
        final Element reasonElement = getJingleChild("reason");
        if (reasonElement == null) {
            return new ReasonWrapper(Reason.UNKNOWN, null);
        }
        String text = null;
        Reason reason = Reason.UNKNOWN;
        for (Element child : reasonElement.getChildren()) {
            if ("text".equals(child.getName())) {
                text = child.getContent();
            } else {
                reason = Reason.of(child.getName());
            }
        }
        return new ReasonWrapper(reason, text);
    }

    public void setReason(final Reason reason, final String text) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        final Element reasonElement = jingle.addChild("reason");
        reasonElement.addChild(reason.toString());
        if (!Strings.isNullOrEmpty(text)) {
            reasonElement.addChild("text").setContent(text);
        }
    }

    // RECOMMENDED for session-initiate, NOT RECOMMENDED otherwise
    public void setInitiator(final Jid initiator) {
        Preconditions.checkArgument(initiator.isFullJid(), "initiator should be a full JID");
        findChild("jingle", Namespace.JINGLE).setAttribute("initiator", initiator);
    }

    // RECOMMENDED for session-accept, NOT RECOMMENDED otherwise
    public void setResponder(Jid responder) {
        Preconditions.checkArgument(responder.isFullJid(), "responder should be a full JID");
        findChild("jingle", Namespace.JINGLE).setAttribute("responder", responder);
    }

    public Element getJingleChild(final String name) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        return jingle == null ? null : jingle.findChild(name);
    }

    public void addJingleChild(final Element child) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        jingle.addChild(child);
    }

    public void setSecurity(final String name, final XmppAxolotlMessage xmppAxolotlMessage) {
        final Element security = new Element("security", Namespace.JINGLE_ENCRYPTED_TRANSPORT);
        security.setAttribute("name", name);
        security.setAttribute("cipher", "urn:xmpp:ciphers:aes-128-gcm-nopadding");
        security.setAttribute("type", AxolotlService.PEP_PREFIX);
        security.addChild(xmppAxolotlMessage.toElement());
        addJingleChild(security);
    }

    public XmppAxolotlMessage getSecurity(final String nameNeedle) {
        final Element jingle = findChild("jingle", Namespace.JINGLE);
        if (jingle == null) {
            return null;
        }
        for (final Element child : jingle.getChildren()) {
            if ("security".equals(child.getName())
                    && Namespace.JINGLE_ENCRYPTED_TRANSPORT.equals(child.getNamespace())) {
                final String name = child.getAttribute("name");
                final String type = child.getAttribute("type");
                final String cipher = child.getAttribute("cipher");
                if (nameNeedle.equals(name)
                        && AxolotlService.PEP_PREFIX.equals(type)
                        && "urn:xmpp:ciphers:aes-128-gcm-nopadding".equals(cipher)) {
                    final var encrypted = child.findChild("encrypted", AxolotlService.PEP_PREFIX);
                    if (encrypted != null) {
                        return XmppAxolotlMessage.fromElement(encrypted, getFrom().asBareJid());
                    }
                }
            }
        }
        return null;
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
            // TODO handle invalid
            return Action.valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, value));
        }

        @Override
        @NonNull
        public String toString() {
            return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, super.toString());
        }
    }

    public static class ReasonWrapper {
        public final Reason reason;
        public final String text;

        public ReasonWrapper(Reason reason, String text) {
            this.reason = reason;
            this.text = text;
        }
    }
}
