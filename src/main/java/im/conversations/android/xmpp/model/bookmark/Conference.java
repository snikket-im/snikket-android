package im.conversations.android.xmpp.model.bookmark;

import com.google.common.base.Strings;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Conference extends Extension {

    public Conference() {
        super(Conference.class);
    }

    public Jid getJid() {
        return this.getAttributeAsJid("jid");
    }

    public String getConferenceName() {
        return this.getAttribute("name");
    }

    public boolean isAutoJoin() {
        return this.getAttributeAsBoolean("autojoin");
    }

    public String getNick() {
        final var nick = this.getExtension(Nick.class);
        return nick == null ? null : nick.getContent();
    }

    public String getPassword() {
        final var password = this.getExtension(Password.class);
        return password == null ? null : password.getContent();
    }

    public void setAutoJoin(final boolean autoJoin) {
        this.setAttribute("autojoin", autoJoin);
    }

    public void setConferenceName(final String name) {
        if (Strings.isNullOrEmpty(name)) {
            this.removeAttribute("name");
            return;
        }
        this.setAttribute("name", name);
    }

    public void setNick(final String nick) {
        if (Strings.isNullOrEmpty(nick)) {
            return;
        }
        this.addExtension(new Nick()).setContent(nick);
    }

    public void setPassword(final String password) {
        if (Strings.isNullOrEmpty(password)) {
            return;
        }
        this.addExtension(new Password()).setContent(password);
    }

    public void setJid(final Jid address) {
        this.setAttribute("jid", address);
    }
}
