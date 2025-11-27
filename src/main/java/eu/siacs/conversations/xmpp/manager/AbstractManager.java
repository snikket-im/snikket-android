package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import eu.siacs.conversations.xmpp.XmppConnection;

public abstract class AbstractManager extends XmppConnection.Delegate {

    protected AbstractManager(final Context context, final XmppConnection connection) {
        super(context.getApplicationContext(), connection);
    }
}
