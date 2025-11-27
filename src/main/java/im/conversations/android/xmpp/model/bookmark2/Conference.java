package im.conversations.android.xmpp.model.bookmark2;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import org.jspecify.annotations.Nullable;

@XmlElement
public class Conference extends Extension {

    public Conference() {
        super(Conference.class);
    }

    public boolean isAutoJoin() {
        return this.getAttributeAsBoolean("autojoin");
    }

    public String getConferenceName() {
        return this.getAttribute("name");
    }

    public void setAutoJoin(boolean autoJoin) {
        setAttribute("autojoin", autoJoin);
    }

    public String getNick() {
        final var nick = this.getExtension(Nick.class);
        return nick == null ? null : Strings.emptyToNull(nick.getContent());
    }

    public String getPassword() {
        final var password = this.getExtension(Password.class);
        return password == null ? null : Strings.nullToEmpty(password.getContent());
    }

    public Extensions getExtensions() {
        return this.getExtension(Extensions.class);
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

    public void setExtensions(@Nullable Extensions extensions) {
        if (extensions == null) {
            return;
        }
        this.addExtension(extensions);
    }
}
