package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.CallIntegration;
import eu.siacs.conversations.xmpp.Jid;

import java.util.Set;

public interface OngoingRtpSession {
    Account getAccount();

    Jid getWith();

    String getSessionId();

    CallIntegration getCallIntegration();

    Set<Media> getMedia();
}
