package im.conversations.android.xmpp.model.commands;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.data.Data;

@XmlElement
public class Command extends Extension {

    public Command() {
        super(Command.class);
    }

    public Command(final String node, final Action action) {
        this();
        this.setNode(node);
        this.setAction(action);
    }

    public void setAction(final Action action) {
        this.setAttribute("action", action);
    }

    public void setNode(final String node) {
        this.setAttribute("node", node);
    }

    public Data getData() {
        return this.getOnlyExtension(Data.class);
    }

    public enum Action {
        EXECUTE,
        CANCEL,
        PREV,
        NEXT,
        COMPLETE
    }
}
