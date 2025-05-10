package eu.siacs.conversations.xmpp;

import android.content.Context;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import eu.siacs.conversations.xmpp.manager.AbstractManager;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import eu.siacs.conversations.xmpp.manager.PresenceManager;

public class Managers {

    private Managers() {
        throw new AssertionError("Do not instantiate me");
    }

    public static ClassToInstanceMap<AbstractManager> get(
            final Context context, final XmppConnection connection) {
        return new ImmutableClassToInstanceMap.Builder<AbstractManager>()
                .put(DiscoManager.class, new DiscoManager(context, connection))
                .put(PresenceManager.class, new PresenceManager(context, connection))
                .build();
    }
}
