package eu.siacs.conversations.xmpp;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.manager.AbstractManager;
import eu.siacs.conversations.xmpp.manager.AvatarManager;
import eu.siacs.conversations.xmpp.manager.AxolotlManager;
import eu.siacs.conversations.xmpp.manager.BlockingManager;
import eu.siacs.conversations.xmpp.manager.BookmarkManager;
import eu.siacs.conversations.xmpp.manager.CarbonsManager;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import eu.siacs.conversations.xmpp.manager.EasyOnboardingManager;
import eu.siacs.conversations.xmpp.manager.EntityTimeManager;
import eu.siacs.conversations.xmpp.manager.HttpUploadManager;
import eu.siacs.conversations.xmpp.manager.LegacyBookmarkManager;
import eu.siacs.conversations.xmpp.manager.MessageDisplayedSynchronizationManager;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import eu.siacs.conversations.xmpp.manager.NativeBookmarkManager;
import eu.siacs.conversations.xmpp.manager.NickManager;
import eu.siacs.conversations.xmpp.manager.OfflineMessagesManager;
import eu.siacs.conversations.xmpp.manager.PepManager;
import eu.siacs.conversations.xmpp.manager.PingManager;
import eu.siacs.conversations.xmpp.manager.PresenceManager;
import eu.siacs.conversations.xmpp.manager.PrivateStorageManager;
import eu.siacs.conversations.xmpp.manager.PubSubManager;
import eu.siacs.conversations.xmpp.manager.RegistrationManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import eu.siacs.conversations.xmpp.manager.StreamHostManager;
import eu.siacs.conversations.xmpp.manager.UnifiedPushManager;
import eu.siacs.conversations.xmpp.manager.VCardManager;

public class Managers {

    private Managers() {
        throw new AssertionError("Do not instantiate me");
    }

    public static ClassToInstanceMap<AbstractManager> get(
            final XmppConnectionService context, final XmppConnection connection) {
        return new ImmutableClassToInstanceMap.Builder<AbstractManager>()
                .put(AvatarManager.class, new AvatarManager(context, connection))
                .put(AxolotlManager.class, new AxolotlManager(context, connection))
                .put(BlockingManager.class, new BlockingManager(context, connection))
                .put(BookmarkManager.class, new BookmarkManager(context, connection))
                .put(CarbonsManager.class, new CarbonsManager(context, connection))
                .put(DiscoManager.class, new DiscoManager(context, connection))
                .put(EasyOnboardingManager.class, new EasyOnboardingManager(context, connection))
                .put(EntityTimeManager.class, new EntityTimeManager(context, connection))
                .put(HttpUploadManager.class, new HttpUploadManager(context, connection))
                .put(LegacyBookmarkManager.class, new LegacyBookmarkManager(context, connection))
                .put(
                        MessageDisplayedSynchronizationManager.class,
                        new MessageDisplayedSynchronizationManager(context, connection))
                .put(MultiUserChatManager.class, new MultiUserChatManager(context, connection))
                .put(NativeBookmarkManager.class, new NativeBookmarkManager(context, connection))
                .put(NickManager.class, new NickManager(context, connection))
                .put(OfflineMessagesManager.class, new OfflineMessagesManager(context, connection))
                .put(PepManager.class, new PepManager(context, connection))
                .put(PingManager.class, new PingManager(context, connection))
                .put(PresenceManager.class, new PresenceManager(context, connection))
                .put(PrivateStorageManager.class, new PrivateStorageManager(context, connection))
                .put(PubSubManager.class, new PubSubManager(context, connection))
                .put(RegistrationManager.class, new RegistrationManager(context, connection))
                .put(RosterManager.class, new RosterManager(context, connection))
                .put(StreamHostManager.class, new StreamHostManager(context, connection))
                .put(UnifiedPushManager.class, new UnifiedPushManager(context, connection))
                .put(VCardManager.class, new VCardManager(context, connection))
                .build();
    }
}
