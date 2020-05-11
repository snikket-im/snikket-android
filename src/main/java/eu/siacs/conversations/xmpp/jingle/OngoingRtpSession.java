package eu.siacs.conversations.xmpp.jingle;

import com.google.common.base.Optional;

import eu.siacs.conversations.entities.Account;
import rocks.xmpp.addr.Jid;

public interface OngoingRtpSession {
    Account getAccount();
    Jid getWith();
    String getSessionId();
}
