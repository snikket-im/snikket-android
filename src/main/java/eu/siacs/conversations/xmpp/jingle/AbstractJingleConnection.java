package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractJingleConnection {

    public static final String JINGLE_MESSAGE_PROPOSE_ID_PREFIX = "jm-propose-";
    public static final String JINGLE_MESSAGE_PROCEED_ID_PREFIX = "jm-proceed-";

    protected static final List<State> TERMINATED =
            Arrays.asList(
                    State.ACCEPTED,
                    State.REJECTED,
                    State.REJECTED_RACED,
                    State.RETRACTED,
                    State.RETRACTED_RACED,
                    State.TERMINATED_SUCCESS,
                    State.TERMINATED_DECLINED_OR_BUSY,
                    State.TERMINATED_CONNECTIVITY_ERROR,
                    State.TERMINATED_CANCEL_OR_TIMEOUT,
                    State.TERMINATED_APPLICATION_FAILURE,
                    State.TERMINATED_SECURITY_ERROR);

    private static final Map<State, Collection<State>> VALID_TRANSITIONS;

    static {
        final ImmutableMap.Builder<State, Collection<State>> transitionBuilder =
                new ImmutableMap.Builder<>();
        transitionBuilder.put(
                State.NULL,
                ImmutableList.of(
                        State.PROPOSED,
                        State.SESSION_INITIALIZED,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR));
        transitionBuilder.put(
                State.PROPOSED,
                ImmutableList.of(
                        State.ACCEPTED,
                        State.PROCEED,
                        State.REJECTED,
                        State.RETRACTED,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR,
                        State.TERMINATED_CONNECTIVITY_ERROR // only used when the xmpp connection
                        // rebinds
                        ));
        transitionBuilder.put(
                State.PROCEED,
                ImmutableList.of(
                        State.REJECTED_RACED,
                        State.RETRACTED_RACED,
                        State.SESSION_INITIALIZED_PRE_APPROVED,
                        State.TERMINATED_SUCCESS,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR,
                        State.TERMINATED_CONNECTIVITY_ERROR // at this state used for error
                        // bounces of the proceed message
                        ));
        transitionBuilder.put(
                State.SESSION_INITIALIZED,
                ImmutableList.of(
                        State.SESSION_ACCEPTED,
                        State.TERMINATED_SUCCESS,
                        State.TERMINATED_DECLINED_OR_BUSY,
                        State.TERMINATED_CONNECTIVITY_ERROR, // at this state used for IQ errors
                        // and IQ timeouts
                        State.TERMINATED_CANCEL_OR_TIMEOUT,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR));
        transitionBuilder.put(
                State.SESSION_INITIALIZED_PRE_APPROVED,
                ImmutableList.of(
                        State.SESSION_ACCEPTED,
                        State.TERMINATED_SUCCESS,
                        State.TERMINATED_DECLINED_OR_BUSY,
                        State.TERMINATED_CONNECTIVITY_ERROR, // at this state used for IQ errors
                        // and IQ timeouts
                        State.TERMINATED_CANCEL_OR_TIMEOUT,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR));
        transitionBuilder.put(
                State.SESSION_ACCEPTED,
                ImmutableList.of(
                        State.TERMINATED_SUCCESS,
                        State.TERMINATED_DECLINED_OR_BUSY,
                        State.TERMINATED_CONNECTIVITY_ERROR,
                        State.TERMINATED_CANCEL_OR_TIMEOUT,
                        State.TERMINATED_APPLICATION_FAILURE,
                        State.TERMINATED_SECURITY_ERROR));
        VALID_TRANSITIONS = transitionBuilder.build();
    }

    final JingleConnectionManager jingleConnectionManager;
    protected final XmppConnectionService xmppConnectionService;
    protected final Id id;
    private final Jid initiator;

    protected State state = State.NULL;

    AbstractJingleConnection(
            final JingleConnectionManager jingleConnectionManager,
            final Id id,
            final Jid initiator) {
        this.jingleConnectionManager = jingleConnectionManager;
        this.xmppConnectionService = jingleConnectionManager.getXmppConnectionService();
        this.id = id;
        this.initiator = initiator;
    }

    public Id getId() {
        return id;
    }

    boolean isInitiator() {
        return initiator.equals(id.account.getJid());
    }

    boolean isResponder() {
        return !initiator.equals(id.account.getJid());
    }

    public State getState() {
        return this.state;
    }

    protected synchronized boolean isInState(State... state) {
        return Arrays.asList(state).contains(this.state);
    }

    protected boolean transition(final State target) {
        return transition(target, null);
    }

    protected synchronized boolean transition(final State target, final Runnable runnable) {
        final Collection<State> validTransitions = VALID_TRANSITIONS.get(this.state);
        if (validTransitions != null && validTransitions.contains(target)) {
            this.state = target;
            if (runnable != null) {
                runnable.run();
            }
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": transitioned into " + target);
            return true;
        } else {
            return false;
        }
    }

    protected void transitionOrThrow(final State target) {
        if (!transition(target)) {
            throw new IllegalStateException(
                    String.format("Unable to transition from %s to %s", this.state, target));
        }
    }

    boolean isTerminated() {
        return TERMINATED.contains(this.state);
    }

    abstract void deliverPacket(JinglePacket jinglePacket);

    protected void receiveOutOfOrderAction(
            final JinglePacket jinglePacket, final JinglePacket.Action action) {
        Log.d(
                Config.LOGTAG,
                String.format(
                        "%s: received %s even though we are in state %s",
                        id.account.getJid().asBareJid(), action, getState()));
        if (isTerminated()) {
            Log.d(
                    Config.LOGTAG,
                    String.format(
                            "%s: got a reason to terminate with out-of-order. but already in state %s",
                            id.account.getJid().asBareJid(), getState()));
            respondWithOutOfOrder(jinglePacket);
        } else {
            terminateWithOutOfOrder(jinglePacket);
        }
    }

    protected void terminateWithOutOfOrder(final JinglePacket jinglePacket) {
        Log.d(
                Config.LOGTAG,
                id.account.getJid().asBareJid() + ": terminating session with out-of-order");
        terminateTransport();
        transitionOrThrow(State.TERMINATED_APPLICATION_FAILURE);
        respondWithOutOfOrder(jinglePacket);
        this.finish();
    }

    protected void finish() {
        if (isTerminated()) {
            this.jingleConnectionManager.finishConnectionOrThrow(this);
        } else {
            throw new AssertionError(String.format("Unable to call finish from %s", this.state));
        }
    }

    protected abstract void terminateTransport();

    abstract void notifyRebound();

    protected void sendSessionTerminate(
            final Reason reason, final String text, final Consumer<State> trigger) {
        final State previous = this.state;
        final State target = reasonToState(reason);
        transitionOrThrow(target);
        if (previous != State.NULL && trigger != null) {
            trigger.accept(target);
        }
        final JinglePacket jinglePacket =
                new JinglePacket(JinglePacket.Action.SESSION_TERMINATE, id.sessionId);
        jinglePacket.setReason(reason, text);
        send(jinglePacket);
        finish();
    }

    protected void send(final JinglePacket jinglePacket) {
        jinglePacket.setTo(id.with);
        xmppConnectionService.sendIqPacket(id.account, jinglePacket, this::handleIqResponse);
    }

    protected void respondOk(final JinglePacket jinglePacket) {
        xmppConnectionService.sendIqPacket(
                id.account, jinglePacket.generateResponse(IqPacket.TYPE.RESULT), null);
    }

    protected void respondWithTieBreak(final JinglePacket jinglePacket) {
        respondWithJingleError(jinglePacket, "tie-break", "conflict", "cancel");
    }

    protected void respondWithOutOfOrder(final JinglePacket jinglePacket) {
        respondWithJingleError(jinglePacket, "out-of-order", "unexpected-request", "wait");
    }

    protected void respondWithItemNotFound(final JinglePacket jinglePacket) {
        respondWithJingleError(jinglePacket, null, "item-not-found", "cancel");
    }

    private void respondWithJingleError(
            final IqPacket original,
            String jingleCondition,
            String condition,
            String conditionType) {
        jingleConnectionManager.respondWithJingleError(
                id.account, original, jingleCondition, condition, conditionType);
    }

    private synchronized void handleIqResponse(final Account account, final IqPacket response) {
        if (response.getType() == IqPacket.TYPE.ERROR) {
            handleIqErrorResponse(response);
            return;
        }
        if (response.getType() == IqPacket.TYPE.TIMEOUT) {
            handleIqTimeoutResponse(response);
        }
    }

    protected void handleIqErrorResponse(final IqPacket response) {
        Preconditions.checkArgument(response.getType() == IqPacket.TYPE.ERROR);
        final String errorCondition = response.getErrorCondition();
        Log.d(
                Config.LOGTAG,
                id.account.getJid().asBareJid()
                        + ": received IQ-error from "
                        + response.getFrom()
                        + " in RTP session. "
                        + errorCondition);
        if (isTerminated()) {
            Log.i(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid()
                            + ": ignoring error because session was already terminated");
            return;
        }
        this.terminateTransport();
        final State target;
        if (Arrays.asList(
                        "service-unavailable",
                        "recipient-unavailable",
                        "remote-server-not-found",
                        "remote-server-timeout")
                .contains(errorCondition)) {
            target = State.TERMINATED_CONNECTIVITY_ERROR;
        } else {
            target = State.TERMINATED_APPLICATION_FAILURE;
        }
        transitionOrThrow(target);
        this.finish();
    }

    protected void handleIqTimeoutResponse(final IqPacket response) {
        Preconditions.checkArgument(response.getType() == IqPacket.TYPE.TIMEOUT);
        Log.d(
                Config.LOGTAG,
                id.account.getJid().asBareJid()
                        + ": received IQ timeout in RTP session with "
                        + id.with
                        + ". terminating with connectivity error");
        if (isTerminated()) {
            Log.i(
                    Config.LOGTAG,
                    id.account.getJid().asBareJid()
                            + ": ignoring error because session was already terminated");
            return;
        }
        this.terminateTransport();
        transitionOrThrow(State.TERMINATED_CONNECTIVITY_ERROR);
        this.finish();
    }

    protected boolean remoteHasFeature(final String feature) {
        final Contact contact = id.getContact();
        final Presence presence =
                contact.getPresences().get(Strings.nullToEmpty(id.with.getResource()));
        final ServiceDiscoveryResult serviceDiscoveryResult =
                presence == null ? null : presence.getServiceDiscoveryResult();
        final List<String> features =
                serviceDiscoveryResult == null ? null : serviceDiscoveryResult.getFeatures();
        return features != null && features.contains(feature);
    }

    public static class Id {
        public final Account account;
        public final Jid with;
        public final String sessionId;

        private Id(final Account account, final Jid with, final String sessionId) {
            Preconditions.checkNotNull(account);
            Preconditions.checkNotNull(with);
            Preconditions.checkNotNull(sessionId);
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

        public static Id of(Account account, Jid with) {
            return new Id(account, with, JingleConnectionManager.nextRandomId());
        }

        public static Id of(Message message) {
            return new Id(
                    message.getConversation().getAccount(),
                    message.getCounterpart(),
                    JingleConnectionManager.nextRandomId());
        }

        public Contact getContact() {
            return account.getRoster().getContact(with);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Id id = (Id) o;
            return Objects.equal(account.getUuid(), id.account.getUuid())
                    && Objects.equal(with, id.with)
                    && Objects.equal(sessionId, id.sessionId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(account.getUuid(), with, sessionId);
        }

        public Account getAccount() {
            return account;
        }

        public Jid getWith() {
            return with;
        }

        public String getSessionId() {
            return sessionId;
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("account", account.getJid())
                    .add("with", with)
                    .add("sessionId", sessionId)
                    .toString();
        }
    }

    protected static State reasonToState(Reason reason) {
        return switch (reason) {
            case SUCCESS -> State.TERMINATED_SUCCESS;
            case DECLINE, BUSY -> State.TERMINATED_DECLINED_OR_BUSY;
            case CANCEL, TIMEOUT -> State.TERMINATED_CANCEL_OR_TIMEOUT;
            case SECURITY_ERROR -> State.TERMINATED_SECURITY_ERROR;
            case FAILED_APPLICATION, UNSUPPORTED_TRANSPORTS, UNSUPPORTED_APPLICATIONS -> State
                    .TERMINATED_APPLICATION_FAILURE;
            default -> State.TERMINATED_CONNECTIVITY_ERROR;
        };
    }

    public enum State {
        NULL, // default value; nothing has been sent or received yet
        PROPOSED,
        ACCEPTED,
        PROCEED,
        REJECTED,
        REJECTED_RACED, // used when we want to reject but haven’t received session init yet
        RETRACTED,
        RETRACTED_RACED, // used when receiving a retract after we already asked to proceed
        SESSION_INITIALIZED, // equal to 'PENDING'
        SESSION_INITIALIZED_PRE_APPROVED,
        SESSION_ACCEPTED, // equal to 'ACTIVE'
        TERMINATED_SUCCESS, // equal to 'ENDED' (after successful call) ui will just close
        TERMINATED_DECLINED_OR_BUSY, // equal to 'ENDED' (after other party declined the call)
        TERMINATED_CONNECTIVITY_ERROR, // equal to 'ENDED' (but after network failures; ui will
        // display retry button)
        TERMINATED_CANCEL_OR_TIMEOUT, // more or less the same as retracted; caller pressed end call
        // before session was accepted
        TERMINATED_APPLICATION_FAILURE,
        TERMINATED_SECURITY_ERROR
    }
}
