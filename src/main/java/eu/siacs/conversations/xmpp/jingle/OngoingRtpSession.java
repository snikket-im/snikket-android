package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.Jid;

public interface OngoingRtpSession {
    Account getAccount();
    Jid getWith();
    String getSessionId();
}
