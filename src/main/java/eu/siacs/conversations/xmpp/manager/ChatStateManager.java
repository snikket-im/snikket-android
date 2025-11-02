package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableClassToInstanceMap;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.hints.NoStore;
import im.conversations.android.xmpp.model.stanza.Message;
import im.conversations.android.xmpp.model.state.Active;
import im.conversations.android.xmpp.model.state.ChatStateNotification;
import im.conversations.android.xmpp.model.state.Composing;
import im.conversations.android.xmpp.model.state.Gone;
import im.conversations.android.xmpp.model.state.Inactive;
import im.conversations.android.xmpp.model.state.Paused;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

public class ChatStateManager extends AbstractManager {

    private static final ClassToInstanceMap<ChatStateNotification> CHAT_STATE_NOTIFICATIONS =
            new ImmutableClassToInstanceMap.Builder<ChatStateNotification>()
                    .put(Active.class, new Active())
                    .put(Composing.class, new Composing())
                    .put(Gone.class, new Gone())
                    .put(Inactive.class, new Inactive())
                    .put(Paused.class, new Paused())
                    .build();

    private final XmppConnectionService service;
    private final AppSettings appSettings;
    private final HashMap<Jid, Class<? extends ChatStateNotification>> incoming = new HashMap<>();
    private final HashMap<Jid, Class<? extends ChatStateNotification>> outgoing = new HashMap<>();

    public ChatStateManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
        this.appSettings = new AppSettings(service.getApplicationContext());
    }

    public void process(final Message message) {
        final var from = message.getFrom();
        if (from == null) {
            return;
        }
        final var chatStateNotification = message.getExtension(ChatStateNotification.class);
        if (chatStateNotification == null) {
            return;
        }
        final var chatState = chatStateNotification.getClass();
        if (getManager(MultiUserChatManager.class).isMuc(message)) {
            final var mucOptions =
                    getManager(MultiUserChatManager.class).getState(from.asBareJid());
            if (mucOptions == null) {
                return;
            }
            final var user = mucOptions.getUser(from);
            if (user == null) {
                return;
            }
            if (user.setChatState(chatState)) {}

        } else {
            final var account = getAccount().getJid().asBareJid();
            if (from.asBareJid().equals(account)) {
                final var to = message.getTo();
                if (to == null) {
                    return;
                }
                Log.d(Config.LOGTAG, "put outgoing " + to.asBareJid() + "=" + chatState);
                this.outgoing.put(to.asBareJid(), chatState);
                final var conversation = this.service.find(getAccount(), to);
                final var activity =
                        Arrays.asList(Active.class, Composing.class).contains(chatState);
                if (activity) {
                    getManager(ActivityManager.class)
                            .record(from, ActivityManager.ActivityType.CHAT_STATE);
                }
                if (conversation == null || conversation.getContact().isSelf()) {
                    return;
                }
                if (activity) {
                    this.service.markRead(conversation);
                }
            } else {
                Log.d(Config.LOGTAG, "put incoming " + from.asBareJid() + "=" + chatState);
                final var previous = this.incoming.put(from.asBareJid(), chatState);
                if (!Objects.equals(previous, chatState)) {
                    this.service.updateConversationUi();
                }
            }
        }
    }

    public void resetChatStates() {
        synchronized (this.incoming) {
            this.incoming.clear();
        }
    }

    public void sendChatState(
            final Conversation conversation,
            final Class<? extends ChatStateNotification> chatState) {
        if (this.setOutgoingChatState(conversation, chatState)) {
            this.sendChatState(conversation);
        }
    }

    private void sendChatState(final Conversation conversation) {
        if (!appSettings.isSendChatStates()) {
            return; // do nothing
        }

        final var address = conversation.getAddress().asBareJid();

        final var extension = getOutgoingChatStateExtension(address);

        final var packet = new Message();
        packet.setType(
                conversation.getMode() == Conversation.MODE_MULTI
                        ? Message.Type.GROUPCHAT
                        : Message.Type.CHAT);
        packet.setTo(address);
        packet.addExtension(extension);
        packet.addExtension(new NoStore());
        this.connection.sendMessagePacket(packet);
    }

    public ChatStateNotification getOutgoingChatStateExtension(final Conversation conversation) {
        return getOutgoingChatStateExtension(conversation.getAddress().asBareJid());
    }

    private ChatStateNotification getOutgoingChatStateExtension(final Jid address) {
        final Class<? extends ChatStateNotification> chatState;
        synchronized (this.outgoing) {
            chatState = this.outgoing.get(address);
        }
        final var normalized = chatState == null ? Config.DEFAULT_CHAT_STATE : chatState;
        final var extension = CHAT_STATE_NOTIFICATIONS.get(normalized);
        if (extension == null) {
            throw new AssertionError("Missing Instance of " + normalized.getSimpleName());
        }
        return extension;
    }

    public boolean setOutgoingChatState(
            final Conversation conversation,
            final Class<? extends ChatStateNotification> chatState) {
        if (conversation.getMode() == Conversation.MODE_SINGLE
                        && !conversation.getContact().isSelf()
                || (conversation.isPrivateAndNonAnonymous()
                        && conversation.getNextCounterpart() == null)) {
            synchronized (this.outgoing) {
                final var previous =
                        this.outgoing.put(conversation.getAddress().asBareJid(), chatState);
                final var normalized = previous == null ? Active.class : previous;
                return !Objects.equals(normalized, chatState);
            }
        }
        return false;
    }

    public Class<? extends ChatStateNotification> getIncoming(final Jid address) {
        synchronized (this.incoming) {
            return this.incoming.get(address.asBareJid());
        }
    }

    public static void send(
            final Conversation conversation, final Class<? extends ChatStateNotification> state) {
        final var account = conversation.getAccount();
        final var manager = account.getXmppConnection().getManager(ChatStateManager.class);
        manager.sendChatState(conversation, state);
    }
}
