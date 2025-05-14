package eu.siacs.conversations.xmpp;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.manager.AbstractManager;
import eu.siacs.conversations.xmpp.manager.BlockingManager;
import eu.siacs.conversations.xmpp.manager.CarbonsManager;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import eu.siacs.conversations.xmpp.manager.EntityTimeManager;
import eu.siacs.conversations.xmpp.manager.PingManager;
import eu.siacs.conversations.xmpp.manager.PresenceManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import eu.siacs.conversations.xmpp.manager.UnifiedPushManager;

public class Managers {

    private Managers() {
        throw new AssertionError("Do not instantiate me");
    }

    public static ClassToInstanceMap<AbstractManager> get(
            final XmppConnectionService context, final XmppConnection connection) {
        return new ImmutableClassToInstanceMap.Builder<AbstractManager>()
                .put(BlockingManager.class, new BlockingManager(context, connection))
                .put(CarbonsManager.class, new CarbonsManager(context, connection))
                .put(DiscoManager.class, new DiscoManager(context, connection))
                .put(EntityTimeManager.class, new EntityTimeManager(context, connection))
                .put(PingManager.class, new PingManager(context, connection))
                .put(PresenceManager.class, new PresenceManager(context, connection))
                .put(RosterManager.class, new RosterManager(context, connection))
                .put(UnifiedPushManager.class, new UnifiedPushManager(context, connection))
                .build();
    }
}
