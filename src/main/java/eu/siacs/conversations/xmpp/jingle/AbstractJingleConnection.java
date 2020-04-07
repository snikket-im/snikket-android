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
        SESSION_INITIALIZED, //equal to 'PENDING'
        SESSION_ACCEPTED, //equal to 'ACTIVE'
        TERMINATED //equal to 'ENDED'
    }
}
