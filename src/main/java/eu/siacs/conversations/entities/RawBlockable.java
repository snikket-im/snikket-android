package eu.siacs.conversations.entities;

import androidx.annotation.NonNull;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import java.util.Collections;
import java.util.List;

public class RawBlockable implements ListItem, Blockable {

    private final Account account;
    private final Jid jid;

    public RawBlockable(@NonNull Account account, @NonNull Jid jid) {
        this.account = account;
        this.jid = jid;
    }

    @Override
    public boolean isBlocked() {
        return true;
    }

    @Override
    public boolean isDomainBlocked() {
        throw new AssertionError("not implemented");
    }

    @Override
    @NonNull
    public Jid getBlockedAddress() {
        return this.jid;
    }

    @Override
    public String getDisplayName() {
        if (jid.isFullJid()) {
            return jid.getResource();
        } else {
            return jid.toString();
        }
    }

    @Override
    public Jid getAddress() {
        return this.jid;
    }

    @Override
    public List<Tag> getTags() {
        return Collections.emptyList();
    }

    @Override
    public Account getAccount() {
        return account;
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(jid.toString());
    }

    @Override
    public String getAvatarName() {
        return getDisplayName();
    }
}
