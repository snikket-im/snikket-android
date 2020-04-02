package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collection;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import rocks.xmpp.addr.Jid;

public class JingleRtpConnection extends AbstractJingleConnection {

    private static final Map<State, Collection<State>> VALID_TRANSITIONS;

    static {
        final ImmutableMap.Builder<State, Collection<State>> transitionBuilder = new ImmutableMap.Builder<>();
        transitionBuilder.put(State.NULL, ImmutableList.of(State.PROPOSED, State.SESSION_INITIALIZED));
        transitionBuilder.put(State.PROPOSED, ImmutableList.of(State.ACCEPTED, State.PROCEED));
        VALID_TRANSITIONS = transitionBuilder.build();
    }

    private State state = State.NULL;


    public JingleRtpConnection(JingleConnectionManager jingleConnectionManager, Id id) {
        super(jingleConnectionManager, id);
    }

    @Override
    void deliverPacket(final JinglePacket jinglePacket) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": packet delivered to JingleRtpConnection");
        Log.d(Config.LOGTAG, jinglePacket.toString());
    }

    void deliveryMessage(final Jid to, final Jid from, final Element message) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": delivered message to JingleRtpConnection " + message);
        final boolean originatedFromMyself = from.asBareJid().equals(id.account.getJid().asBareJid());
        switch (message.getName()) {
            case "propose":
                if (originatedFromMyself) {
                    Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": saw proposal from mysql. ignoring");
                } else if (transition(State.PROPOSED)) {
                    //TODO start ringing or something
                    pickUpCall();
                } else {
                    Log.d(Config.LOGTAG, id.account.getJid() + ": ignoring session proposal because already in " + state);
                }
                break;
            default:
                break;
        }
    }

    public void pickUpCall() {
        switch (this.state) {
            case PROPOSED:
                pickupCallFromProposed();
                break;
            case SESSION_INITIALIZED:
                pickupCallFromSessionInitialized();
                break;
            default:
                throw new IllegalStateException("Can not pick up call from " + this.state);
        }
    }

    private void pickupCallFromProposed() {
        transitionOrThrow(State.PROCEED);
        final MessagePacket messagePacket = new MessagePacket();
        messagePacket.setTo(id.with);
        //Note that Movim needs 'accept'
        messagePacket.addChild("proceed", Namespace.JINGLE_MESSAGE).setAttribute("id", id.sessionId);
        Log.d(Config.LOGTAG, messagePacket.toString());
        xmppConnectionService.sendMessagePacket(id.account, messagePacket);
    }

    private void pickupCallFromSessionInitialized() {

    }

    private synchronized boolean transition(final State target) {
        final Collection<State> validTransitions = VALID_TRANSITIONS.get(this.state);
        if (validTransitions != null && validTransitions.contains(target)) {
            this.state = target;
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": transitioned into " + target);
            return true;
        } else {
            return false;
        }
    }

    private void transitionOrThrow(final State target) {
        if (!transition(target)) {
            throw new IllegalStateException(String.format("Unable to transition from %s to %s", this.state, target));
        }
    }

}
