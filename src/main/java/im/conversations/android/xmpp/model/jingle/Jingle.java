package im.conversations.android.xmpp.model.jingle;

import androidx.annotation.NonNull;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

import java.util.Map;

@XmlElement
public class Jingle extends Extension {

    public Jingle() {
        super(Jingle.class);
    }

    public Jingle(final Action action, final String sessionId) {
        this();
        this.setAttribute("sid", sessionId);
        this.setAttribute("action", action.toString());
    }

    public String getSessionId() {
        return this.getAttribute("sid");
    }

    public Action getAction() {
        return Action.of(this.getAttribute("action"));
    }

    public ReasonWrapper getReason() {
        final Element reasonElement = this.findChild("reason");
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
        final Element reasonElement = this.addChild("reason");
        reasonElement.addChild(reason.toString());
        if (!Strings.isNullOrEmpty(text)) {
            reasonElement.addChild("text").setContent(text);
        }
    }

    // RECOMMENDED for session-initiate, NOT RECOMMENDED otherwise
    public void setInitiator(final Jid initiator) {
        Preconditions.checkArgument(initiator.isFullJid(), "initiator should be a full JID");
        this.setAttribute("initiator", initiator);
    }

    // RECOMMENDED for session-accept, NOT RECOMMENDED otherwise
    public void setResponder(final Jid responder) {
        Preconditions.checkArgument(responder.isFullJid(), "responder should be a full JID");
        this.setAttribute("responder", responder);
    }

    public Group getGroup() {
        final Element group = this.findChild("group", Namespace.JINGLE_APPS_GROUPING);
        return group == null ? null : Group.upgrade(group);
    }

    public void addGroup(final Group group) {
        this.addChild(group);
    }

    // TODO deprecate this somehow and make file transfer fail if there are multiple (or something)
    public Content getJingleContent() {
        final Element content = this.findChild("content");
        return content == null ? null : Content.upgrade(content);
    }

    public void addJingleContent(final Content content) { // take content interface
        this.addChild(content);
    }


    public Map<String, Content> getJingleContents() {
        ImmutableMap.Builder<String, Content> builder = new ImmutableMap.Builder<>();
        for (final Element child : this.getChildren()) {
            if ("content".equals(child.getName())) {
                final Content content = Content.upgrade(child);
                builder.put(content.getContentName(), content);
            }
        }
        return builder.build();
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
            if (Strings.isNullOrEmpty(value)) {
                return null;
            }
            try {
                return Action.valueOf(
                        CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, value));
            } catch (final IllegalArgumentException e) {
                return null;
            }
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
