package eu.siacs.conversations.xmpp.jingle;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import rocks.xmpp.addr.Jid;

public abstract class AbstractJingleConnection {

    public static final String JINGLE_MESSAGE_ID_PREFIX = "jm-propose-";

    protected final JingleConnectionManager jingleConnectionManager;
    protected final XmppConnectionService xmppConnectionService;
    protected final Id id;
    protected final Jid initiator;

    AbstractJingleConnection(final JingleConnectionManager jingleConnectionManager, final Id id, final Jid initiator) {
        this.jingleConnectionManager = jingleConnectionManager;
        this.xmppConnectionService = jingleConnectionManager.getXmppConnectionService();
        this.id = id;
        this.initiator = initiator;
    }

    boolean isInitiator() {
        return initiator.equals(id.account.getJid());
    }

    abstract void deliverPacket(JinglePacket jinglePacket);

    public Id getId() {
        return id;
    }


    public static class Id {
        public final Account account;
        public final Jid with;
        public final String sessionId;

        private Id(final Account account, final Jid with, final String sessionId) {
            Preconditions.checkNotNull(with);
            Preconditions.checkArgument(with.isFullJid());
            this.account = account;
            this.with = with;
            this.sessionId = sessionId;
        }

        public static Id of(Account account, JinglePacket jinglePacket) {
            return new Id(account, jinglePacket.getFrom(), jinglePacket.getSessionId());
        }

        public static Id of(Account account, Jid with, final String sessionId) {
            return new Id(account, with, sessionId);
        }

        public static Id of(Message message) {
            return new Id(
                    message.getConversation().getAccount(),
                    message.getCounterpart(),
                    JingleConnectionManager.nextRandomId()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Id id = (Id) o;
            return Objects.equal(account.getJid(), id.account.getJid()) &&
                    Objects.equal(with, id.with) &&
                    Objects.equal(sessionId, id.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(account.getJid(), with, sessionId);
        }
    }


    public enum State {
        NULL, //default value; nothing has been sent or received yet
        PROPOSED,
        ACCEPTED,
        PROCEED,
        REJECTED,
        RETRACTED,
        SESSION_INITIALIZED, //equal to 'PENDING'
        SESSION_INITIALIZED_PRE_APPROVED,
        SESSION_ACCEPTED, //equal to 'ACTIVE'
        TERMINATED_SUCCESS, //equal to 'ENDED' (after successful call) ui will just close
        TERMINATED_DECLINED_OR_BUSY, //equal to 'ENDED' (after other party declined the call)
        TERMINATED_CONNECTIVITY_ERROR, //equal to 'ENDED' (but after network failures; ui will display retry button)
        TERMINATED_CANCEL_OR_TIMEOUT, //more or less the same as retracted; caller pressed end call before session was accepted
        TERMINATED_APPLICATION_FAILURE
    }
}
