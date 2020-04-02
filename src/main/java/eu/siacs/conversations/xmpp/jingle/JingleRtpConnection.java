package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import rocks.xmpp.addr.Jid;

public class JingleRtpConnection extends AbstractJingleConnection {


    public JingleRtpConnection(JingleConnectionManager jingleConnectionManager, Id id) {
        super(jingleConnectionManager, id);
    }

    @Override
    void deliverPacket(final JinglePacket jinglePacket) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": packet delivered to JingleRtpConnection");
    }

    void deliveryMessage(final Jid to, Jid from, Element message) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": delivered message to JingleRtpConnection " + message);
    }
}
