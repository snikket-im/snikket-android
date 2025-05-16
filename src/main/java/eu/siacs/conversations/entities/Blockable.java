package eu.siacs.conversations.entities;

import androidx.annotation.NonNull;
import eu.siacs.conversations.xmpp.Jid;

public interface Blockable {
    boolean isBlocked();

    boolean isDomainBlocked();

    @NonNull
    Jid getBlockedJid();

    Jid getJid();

    Account getAccount();
}
