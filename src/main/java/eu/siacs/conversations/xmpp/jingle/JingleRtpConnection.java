package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.Collection;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import rocks.xmpp.addr.Jid;

public class JingleRtpConnection extends AbstractJingleConnection {

    private static final Map<State, Collection<State>> VALID_TRANSITIONS;

    static {
        final ImmutableMap.Builder<State, Collection<State>> transitionBuilder = new ImmutableMap.Builder<>();
        transitionBuilder.put(State.NULL, ImmutableList.of(State.PROPOSED, State.SESSION_INITIALIZED));
        transitionBuilder.put(State.PROPOSED, ImmutableList.of(State.ACCEPTED, State.PROCEED));
        transitionBuilder.put(State.PROCEED, ImmutableList.of(State.SESSION_INITIALIZED));
        VALID_TRANSITIONS = transitionBuilder.build();
    }

    private State state = State.NULL;


    public JingleRtpConnection(JingleConnectionManager jingleConnectionManager, Id id, Jid initiator) {
        super(jingleConnectionManager, id, initiator);
    }

    @Override
    void deliverPacket(final JinglePacket jinglePacket) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": packet delivered to JingleRtpConnection");
        switch (jinglePacket.getAction()) {
            case SESSION_INITIATE:
                receiveSessionInitiate(jinglePacket);
                break;
            default:
                Log.d(Config.LOGTAG, String.format("%s: received unhandled jingle action %s", id.account.getJid().asBareJid(), jinglePacket.getAction()));
                break;
        }
    }

    private void receiveSessionInitiate(final JinglePacket jinglePacket) {
        if (isInitiator()) {
            Log.d(Config.LOGTAG, String.format("%s: received session-initiate even though we were initiating", id.account.getJid().asBareJid()));
            //TODO respond with out-of-order
            return;
        }
        final Map<String, DescriptionTransport> contents;
        try {
            contents = DescriptionTransport.of(jinglePacket.getJingleContents());
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.d(Config.LOGTAG,id.account.getJid().asBareJid()+": improperly formatted contents",e);
            return;
        }
        Log.d(Config.LOGTAG,"processing session-init with "+contents.size()+" contents");
        final State oldState = this.state;
        if (transition(State.SESSION_INITIALIZED)) {
            if (oldState == State.PROCEED) {
                sendSessionAccept();
            } else {
                //TODO start ringing
            }
        } else {
            Log.d(Config.LOGTAG, String.format("%s: received session-initiate while in state %s", id.account.getJid().asBareJid(), state));
        }
    }

    void deliveryMessage(final Jid from, final Element message) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": delivered message to JingleRtpConnection " + message);
        switch (message.getName()) {
            case "propose":
                receivePropose(from, message);
                break;
            case "proceed":
                receiveProceed(from, message);
            default:
                break;
        }
    }

    private void receivePropose(final Jid from, final Element propose) {
        final boolean originatedFromMyself = from.asBareJid().equals(id.account.getJid().asBareJid());
        if (originatedFromMyself) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": saw proposal from mysql. ignoring");
        } else if (transition(State.PROPOSED)) {
            //TODO start ringing or something
            pickUpCall();
        } else {
            Log.d(Config.LOGTAG, id.account.getJid() + ": ignoring session proposal because already in " + state);
        }
    }

    private void receiveProceed(final Jid from, final Element proceed) {
        if (from.equals(id.with)) {
            if (isInitiator()) {
                if (transition(State.SESSION_INITIALIZED)) {
                    this.sendSessionInitiate();
                } else {
                    Log.d(Config.LOGTAG, String.format("%s: ignoring proceed because already in %s", id.account.getJid().asBareJid(), this.state));
                }
            } else {
                Log.d(Config.LOGTAG, String.format("%s: ignoring proceed because we were not initializing", id.account.getJid().asBareJid()));
            }
        } else {
            Log.d(Config.LOGTAG, String.format("%s: ignoring proceed from %s. was expected from %s", id.account.getJid().asBareJid(), from, id.with));
        }
    }

    private void sendSessionInitiate() {

    }

    private void sendSessionAccept() {
        Log.d(Config.LOGTAG,"sending session-accept");
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
        //Note that Movim needs 'accept', correct is 'proceed' https://github.com/movim/movim/issues/916
        messagePacket.addChild("accept", Namespace.JINGLE_MESSAGE).setAttribute("id", id.sessionId);
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

    private static class DescriptionTransport {
        private final RtpDescription description;
        private final IceUdpTransportInfo transport;

        public DescriptionTransport(final RtpDescription description, final IceUdpTransportInfo transport) {
            this.description = description;
            this.transport = transport;
        }

        public static DescriptionTransport of(final Content content) {
            final GenericDescription description = content.getDescription();
            final GenericTransportInfo transportInfo = content.getTransport();
            final RtpDescription rtpDescription;
            final IceUdpTransportInfo iceUdpTransportInfo;
            if (description instanceof RtpDescription) {
                rtpDescription = (RtpDescription) description;
            } else {
                throw new IllegalArgumentException("Content does not contain RtpDescription");
            }
            if (transportInfo instanceof IceUdpTransportInfo) {
                iceUdpTransportInfo = (IceUdpTransportInfo) transportInfo;
            } else {
                throw new IllegalArgumentException("Content does not contain ICE-UDP transport");
            }
            return new DescriptionTransport(rtpDescription, iceUdpTransportInfo);
        }

        public static Map<String, DescriptionTransport> of(final Map<String,Content> contents) {
            return Maps.transformValues(contents, new Function<Content, DescriptionTransport>() {
                @NullableDecl
                @Override
                public DescriptionTransport apply(@NullableDecl Content content) {
                    return content == null ? null : of(content);
                }
            });
        }
    }

}
