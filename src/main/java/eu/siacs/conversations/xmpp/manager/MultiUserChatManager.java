package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import de.gultsch.common.FutureMerger;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.StringUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.model.ImmutableBookmark;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.IqErrorException;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.conference.DirectInvite;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.hints.NoCopy;
import im.conversations.android.xmpp.model.hints.NoStore;
import im.conversations.android.xmpp.model.jabber.Subject;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.History;
import im.conversations.android.xmpp.model.muc.MultiUserChat;
import im.conversations.android.xmpp.model.muc.Password;
import im.conversations.android.xmpp.model.muc.Role;
import im.conversations.android.xmpp.model.muc.admin.Item;
import im.conversations.android.xmpp.model.muc.admin.MucAdmin;
import im.conversations.android.xmpp.model.muc.owner.Destroy;
import im.conversations.android.xmpp.model.muc.owner.MucOwner;
import im.conversations.android.xmpp.model.muc.user.Invite;
import im.conversations.android.xmpp.model.muc.user.MucUser;
import im.conversations.android.xmpp.model.occupant.OccupantId;
import im.conversations.android.xmpp.model.pgp.Signed;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;
import im.conversations.android.xmpp.model.stanza.Presence;
import im.conversations.android.xmpp.model.vcard.update.VCardUpdate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiUserChatManager extends AbstractManager {

    private final XmppConnectionService service;

    private final Set<Conversation> inProgressConferenceJoins = new HashSet<>();
    private final Set<Conversation> inProgressConferencePings = new HashSet<>();
    private final HashMap<Jid, MucOptions> states = new HashMap<>();

    public MultiUserChatManager(final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public ListenableFuture<Void> join(final Conversation conversation) {
        Log.d(Config.LOGTAG, "join(" + conversation.getAddress() + ")");
        return join(conversation, true);
    }

    private ListenableFuture<Void> join(
            final Conversation conversation, final boolean autoPushConfiguration) {
        synchronized (this.inProgressConferenceJoins) {
            this.inProgressConferenceJoins.add(conversation);
        }
        if (Config.MUC_LEAVE_BEFORE_JOIN) {
            unavailable(conversation);
        }
        resetState(conversation);
        this.getOrCreateState(conversation).setAutoPushConfiguration(autoPushConfiguration);
        conversation.setHasMessagesLeftOnServer(false);
        final var disco = fetchDiscoInfo(conversation);

        final var caughtDisco =
                Futures.catchingAsync(
                        disco,
                        IqErrorException.class,
                        ex -> {
                            if (conversation.getStatus() == Conversation.STATUS_ARCHIVED) {
                                return Futures.immediateFailedFuture(
                                        new IllegalStateException(
                                                "conversation got archived before disco returned"));
                            }
                            Log.d(Config.LOGTAG, "error fetching disco#info", ex);
                            final var iqError = ex.getError();
                            if (iqError != null
                                    && iqError.getCondition()
                                            instanceof Condition.RemoteServerNotFound) {
                                synchronized (this.inProgressConferenceJoins) {
                                    this.inProgressConferenceJoins.remove(conversation);
                                }
                                getOrCreateState(conversation)
                                        .setError(MucOptions.Error.SERVER_NOT_FOUND);
                                service.updateConversationUi();
                                return Futures.immediateFailedFuture(ex);
                            } else {
                                return Futures.immediateFuture(new InfoQuery());
                            }
                        },
                        MoreExecutors.directExecutor());

        return Futures.transform(
                caughtDisco,
                v -> {
                    checkConfigurationSendPresenceFetchHistory(conversation);
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    public MucOptions getOrCreateState(final Conversation conversation) {
        final var address = conversation.getAddress().asBareJid();
        synchronized (this.states) {
            final var existing = this.states.get(address);
            if (existing != null) {
                return existing;
            }
            final var fresh = new MucOptions(conversation);
            final var caps2Hash = fresh.getCaps2Hash();
            if (caps2Hash != null) {
                final var infoQuery = getDatabase().getInfoQuery(caps2Hash);
                if (infoQuery != null
                        && getManager(DiscoManager.class)
                                .loadFromCache(Entity.discoItem(address), null, caps2Hash)) {
                    Log.d(Config.LOGTAG, address + " muc#info came from cache");
                }
            }
            this.states.put(address, fresh);
            return fresh;
        }
    }

    public MucOptions getState(final Jid address) {
        synchronized (this.states) {
            return this.states.get(address);
        }
    }

    private void resetState(final Conversation conversation) {
        synchronized (this.states) {
            this.states.remove(conversation.getAddress().asBareJid());
        }
    }

    public ListenableFuture<Void> joinFollowingInvite(final Conversation conversation) {
        // TODO this special treatment is probably unnecessary; just always make sure the bookmark
        // exists
        return Futures.transform(
                join(conversation),
                v -> {
                    // we used to do this only for private groups
                    final var bookmark =
                            getManager(BookmarkManager.class)
                                    .getBookmark(conversation.getAddress().asBareJid());
                    if (bookmark != null) {
                        if (bookmark.isAutoJoin()) {
                            return null;
                        }
                        getManager(BookmarkManager.class)
                                .create(
                                        ImmutableBookmark.builder()
                                                .from(bookmark)
                                                .isAutoJoin(true)
                                                .build());
                    } else {
                        getManager(BookmarkManager.class).save(conversation, null);
                    }
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    private void checkConfigurationSendPresenceFetchHistory(final Conversation conversation) {
        final Account account = conversation.getAccount();
        final MucOptions mucOptions = getOrCreateState(conversation);
        Log.d(
                Config.LOGTAG,
                "checkConfigurationSendPresenceFetchHistory(" + conversation.getAddress() + ")");

        if (mucOptions.nonanonymous()
                && !mucOptions.membersOnly()
                && !conversation.getBooleanAttribute("accept_non_anonymous", false)) {
            synchronized (this.inProgressConferenceJoins) {
                this.inProgressConferenceJoins.remove(conversation);
            }
            mucOptions.setError(MucOptions.Error.NON_ANONYMOUS);
            service.updateConversationUi();
            return;
        }

        Log.d(
                Config.LOGTAG,
                "moderation: "
                        + mucOptions.moderation()
                        + " ("
                        + mucOptions.getConversation().getAddress().asBareJid()
                        + ")");

        final Jid joinJid = mucOptions.getSelf().getFullJid();
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid().toString()
                        + ": joining conversation "
                        + joinJid.toString());

        final var x = new MultiUserChat();

        if (mucOptions.getPassword() != null) {
            x.addExtension(new Password(mucOptions.getPassword()));
        }

        final var history = x.addExtension(new History());

        if (mucOptions.mamSupport()) {
            // Use MAM instead of the limited muc history to get history
            history.setMaxStanzas(0);
        } else {
            // Fallback to muc history
            history.setSince(conversation.getLastMessageTransmitted().getTimestamp());
        }
        available(joinJid, mucOptions.nonanonymous(), x);
        if (!joinJid.equals(conversation.getAddress())) {
            conversation.setContactJid(joinJid);
            getDatabase().updateConversation(conversation);
        }

        if (mucOptions.mamSupport()) {
            this.service.getMessageArchiveService().catchupMUC(conversation);
        }
        if (mucOptions.isPrivateAndNonAnonymous()) {
            fetchMembers(conversation);
        }
        synchronized (this.inProgressConferenceJoins) {
            this.inProgressConferenceJoins.remove(conversation);
            this.service.sendUnsentMessages(conversation);
        }
    }

    public ListenableFuture<Conversation> createPrivateGroupChat(
            final String name, final Collection<Jid> addresses) {
        final var service = getService();
        if (service == null) {
            return Futures.immediateFailedFuture(new IllegalStateException("No MUC service found"));
        }
        final var address = Jid.ofLocalAndDomain(CryptoHelper.pronounceable(), service);
        final var conversation =
                this.service.findOrCreateConversation(getAccount(), address, true, false, true);
        final var join = this.join(conversation, false);
        final var configured =
                Futures.transformAsync(
                        join,
                        v -> {
                            final var options =
                                    configWithName(defaultGroupChatConfiguration(), name);
                            return pushConfiguration(conversation, options);
                        },
                        MoreExecutors.directExecutor());

        // TODO add catching to 'configured' to archive the chat again

        return Futures.transform(
                configured,
                c -> {
                    for (var invitee : addresses) {
                        this.service.invite(conversation, invitee);
                    }
                    final var account = getAccount();
                    for (final var resource :
                            account.getSelfContact().getPresences().toResourceArray()) {
                        Jid other = getAccount().getJid().withResource(resource);
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": sending direct invite to "
                                        + other);
                        this.service.directInvite(conversation, other);
                    }
                    getManager(BookmarkManager.class).save(conversation, name);
                    return conversation;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Conversation> createPublicChannel(
            final Jid address, final String name) {

        final var conversation =
                this.service.findOrCreateConversation(getAccount(), address, true, false, true);

        final var join = this.join(conversation, false);
        final var configuration =
                Futures.transformAsync(
                        join,
                        v -> {
                            final var options = configWithName(defaultChannelConfiguration(), name);
                            return pushConfiguration(conversation, options);
                        },
                        MoreExecutors.directExecutor());

        // TODO mostly ignore configuration error

        return Futures.transform(
                configuration,
                v -> {
                    getManager(BookmarkManager.class).save(conversation, name);
                    return conversation;
                },
                MoreExecutors.directExecutor());
    }

    public void leave(final Conversation conversation) {
        getManager(DiscoManager.class).clear(conversation.getAddress().asBareJid());
        resetState(conversation);
        unavailable(conversation);
    }

    public void handlePresence(final Presence presence) {
        final var type = presence.getType();
        final var from = presence.getFrom();
        if (from == null || from.isBareJid()) {
            Log.d(Config.LOGTAG, "found invalid from in muc presence " + from);
            return;
        }
        final var mucOptions = getState(from.asBareJid());
        if (mucOptions == null) {
            Log.d(Config.LOGTAG, "received MUC presence but conversation was not joined " + from);
            return;
        }
        final boolean before = mucOptions.online();
        final int count = mucOptions.getUserCount();
        final var isGeneratedAvatar = Strings.isNullOrEmpty(mucOptions.getAvatar());
        final var tileUserBefore =
                isGeneratedAvatar ? mucOptions.getUsers(5) : Collections.emptyList();
        if (type == null) {
            handleAvailablePresence(presence);
        } else if (type == Presence.Type.UNAVAILABLE) {
            handleUnavailablePresence(presence);
        } else {
            throw new AssertionError("presences of this type should not be routed here");
        }
        final var tileUserAfter =
                isGeneratedAvatar ? mucOptions.getUsers(5) : Collections.emptyList();
        if (isGeneratedAvatar && !tileUserAfter.equals(tileUserBefore)) {
            // TODO test that this is doing something
            service.getAvatarService().clear(mucOptions);
        }
        if (before != mucOptions.online()
                || (mucOptions.online() && count != mucOptions.getUserCount())) {
            service.updateConversationUi();
        } else if (mucOptions.online()) {
            service.updateMucRosterUi();
        }
    }

    private void handleAvailablePresence(final Presence presence) {
        final var from = presence.getFrom();
        final var mucUser = presence.getExtension(MucUser.class);
        final var vCardUpdate = presence.getExtension(VCardUpdate.class);
        final var item = mucUser == null ? null : mucUser.getItem();

        if (item == null) {
            Log.d(Config.LOGTAG, "received muc#user presence w/o item");
            return;
        }

        final var mucOptions = getState(from.asBareJid());
        if (mucOptions == null) {
            return;
        }
        final var codes = mucUser.getStatus();
        final var account = getAccount();
        final Jid jid = account.getJid();
        final var conversation = mucOptions.getConversation();

        mucOptions.setError(MucOptions.Error.NONE);
        final var occupant = presence.getOnlyExtension(OccupantId.class);
        final String occupantId =
                mucOptions.occupantId() && occupant != null ? occupant.getId() : null;
        final MucOptions.User user =
                MultiUserChatManager.itemToUser(conversation, item, from, occupantId);
        if (codes.contains(MucUser.STATUS_CODE_SELF_PRESENCE)
                || (codes.contains(MucUser.STATUS_CODE_ROOM_CREATED)
                        && jid.equals(
                                Jid.Invalid.getNullForInvalid(item.getAttributeAsJid("jid"))))) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": got self-presence from "
                            + user.getFullJid()
                            + ". occupant-id="
                            + occupantId);
            if (mucOptions.setOnline()) {
                service.getAvatarService().clear(mucOptions);
            }
            final var current = mucOptions.getSelf().getFullJid();
            if (mucOptions.setSelf(user)) {
                Log.d(Config.LOGTAG, "role or affiliation changed");
                getDatabase().updateConversation(conversation);
            }
            final var modified = current == null || !current.equals(user.getFullJid());
            service.persistSelfNick(user, modified);
            invokeRenameListener(mucOptions, true);
        }
        boolean isNew = mucOptions.updateUser(user);
        final AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
        Contact contact = user.getContact();
        if (isNew
                && user.getRealJid() != null
                && mucOptions.isPrivateAndNonAnonymous()
                && (contact == null || !contact.mutualPresenceSubscription())
                && axolotlService.hasEmptyDeviceList(user.getRealJid())) {
            axolotlService.fetchDeviceIds(user.getRealJid());
        }
        if (codes.contains(MucUser.STATUS_CODE_ROOM_CREATED)
                && mucOptions.autoPushConfiguration()) {
            final var address = mucOptions.getConversation().getAddress().asBareJid();
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": room '"
                            + address
                            + "' created. pushing default configuration");
            getManager(MultiUserChatManager.class)
                    .pushConfiguration(
                            conversation, MultiUserChatManager.defaultChannelConfiguration());
        }
        final var pgpEngine = service.getPgpEngine();
        if (pgpEngine != null) {
            final var signed = presence.getExtension(Signed.class);
            if (signed != null) {
                final var status = presence.getStatus();
                final long keyId =
                        pgpEngine.fetchKeyId(mucOptions.getAccount(), status, signed.getContent());
                if (keyId != 0) {
                    user.setPgpKeyId(keyId);
                }
            }
        }
        if (vCardUpdate != null) {
            getManager(AvatarManager.class).handleVCardUpdate(from, vCardUpdate);
        }
    }

    private void handleUnavailablePresence(final Presence presence) {
        final var from = presence.getFrom();
        final var x = presence.getExtension(MucUser.class);
        Preconditions.checkArgument(from.isFullJid(), "from should be a full jid");
        Preconditions.checkNotNull(x, "only presences with muc#user element are handled here");

        final var mucOptions = getState(from.asBareJid());
        if (mucOptions == null) {
            return;
        }
        final var account = getAccount();
        final var conversation = mucOptions.getConversation();
        final var codes = x.getStatus();
        final boolean fullJidMatches = from.equals(mucOptions.getSelf().getFullJid());
        if (x.hasExtension(im.conversations.android.xmpp.model.muc.user.Destroy.class)
                && fullJidMatches) {
            final var destroy =
                    x.getExtension(im.conversations.android.xmpp.model.muc.user.Destroy.class);
            final Jid alternate = destroy.getJid();
            mucOptions.setError(MucOptions.Error.DESTROYED);
            if (alternate != null) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": muc destroyed. alternate location "
                                + alternate);
            }
        } else if (codes.contains(MucUser.STATUS_CODE_SHUTDOWN) && fullJidMatches) {
            mucOptions.setError(MucOptions.Error.SHUTDOWN);
        } else if (codes.contains(MucUser.STATUS_CODE_SELF_PRESENCE)) {
            if (codes.contains(MucUser.STATUS_CODE_TECHNICAL_REASONS)) {
                final boolean wasOnline = mucOptions.online();
                mucOptions.setError(MucOptions.Error.TECHNICAL_PROBLEMS);
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": received status code 333 in room "
                                + mucOptions.getConversation().getAddress().asBareJid()
                                + " online="
                                + wasOnline);
                if (wasOnline) {
                    this.pingAndRejoin(conversation);
                }
            } else if (codes.contains(MucUser.STATUS_CODE_KICKED)) {
                mucOptions.setError(MucOptions.Error.KICKED);
            } else if (codes.contains(MucUser.STATUS_CODE_BANNED)) {
                mucOptions.setError(MucOptions.Error.BANNED);
            } else if (codes.contains(MucUser.STATUS_CODE_LOST_MEMBERSHIP)) {
                mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
            } else if (codes.contains(MucUser.STATUS_CODE_AFFILIATION_CHANGE)) {
                mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
            } else if (codes.contains(MucUser.STATUS_CODE_SHUTDOWN)) {
                mucOptions.setError(MucOptions.Error.SHUTDOWN);
            } else if (!codes.contains(MucUser.STATUS_CODE_CHANGED_NICK)) {
                mucOptions.setError(MucOptions.Error.UNKNOWN);
                Log.d(Config.LOGTAG, "unknown unavailable in MUC: " + presence);
            }
        } else {
            final var item = x.getItem();
            if (item != null) {
                final var occupant = presence.getOnlyExtension(OccupantId.class);
                final String occupantId =
                        mucOptions.occupantId() && occupant != null ? occupant.getId() : null;
                mucOptions.updateUser(
                        MultiUserChatManager.itemToUser(conversation, item, from, occupantId));
            }
            final var user = mucOptions.deleteUser(from);
            if (user != null) {
                service.getAvatarService().clear(user);
            }
        }
    }

    public void handleErrorPresence(final Presence presence) {
        final var from = presence.getFrom();
        final var error = presence.getError();
        if (from == null || error == null) {
            Log.d(Config.LOGTAG, "received invalid error presence for MUC. Missing error or from");
            return;
        }

        final var mucOptions = getState(from.asBareJid());
        if (mucOptions == null) {
            return;
        }
        final var conversation = mucOptions.getConversation();

        final var condition = error.getCondition();

        if (condition instanceof Condition.Conflict) {
            if (mucOptions.online()) {
                invokeRenameListener(mucOptions, false);
            } else {
                mucOptions.setError(MucOptions.Error.NICK_IN_USE);
            }
        } else if (condition instanceof Condition.NotAuthorized) {
            mucOptions.setError(MucOptions.Error.PASSWORD_REQUIRED);
        } else if (condition instanceof Condition.Forbidden) {
            mucOptions.setError(MucOptions.Error.BANNED);
        } else if (condition instanceof Condition.RegistrationRequired) {
            mucOptions.setError(MucOptions.Error.MEMBERS_ONLY);
        } else if (condition instanceof Condition.ResourceConstraint) {
            mucOptions.setError(MucOptions.Error.RESOURCE_CONSTRAINT);
        } else if (condition instanceof Condition.RemoteServerTimeout) {
            mucOptions.setError(MucOptions.Error.REMOTE_SERVER_TIMEOUT);
        } else if (condition instanceof Condition.Gone conditionGone) {
            final String gone = conditionGone.getContent();
            final Jid alternate;
            if (gone != null) {
                final XmppUri xmppUri = new XmppUri(gone);
                if (xmppUri.isValidJid()) {
                    alternate = xmppUri.getJid();
                } else {
                    alternate = null;
                }
            } else {
                alternate = null;
            }
            mucOptions.setError(MucOptions.Error.DESTROYED);
            if (alternate != null) {
                Log.d(
                        Config.LOGTAG,
                        conversation.getAccount().getJid().asBareJid()
                                + ": muc destroyed. alternate location "
                                + alternate);
            }
        } else {
            final var text = error.getTextAsString();
            if (text != null && text.contains("attribute 'to'")) {
                if (mucOptions.online()) {
                    invokeRenameListener(mucOptions, false);
                } else {
                    mucOptions.setError(MucOptions.Error.INVALID_NICK);
                }
            } else {
                mucOptions.setError(MucOptions.Error.UNKNOWN);
                Log.d(Config.LOGTAG, "unknown error in conference: " + presence);
            }
        }
    }

    private static void invokeRenameListener(final MucOptions options, final boolean success) {
        if (options.onRenameListener != null) {
            if (success) {
                options.onRenameListener.onSuccess();
            } else {
                options.onRenameListener.onFailure();
            }
        }
        options.onRenameListener = null;
    }

    public void handleStatusMessage(final Message message) {
        final var from = Jid.Invalid.getNullForInvalid(message.getFrom());
        final var mucUser = message.getExtension(MucUser.class);
        if (from == null || from.isFullJid() || mucUser == null) {
            return;
        }
        final var conversation = this.service.find(getAccount(), from);
        if (conversation == null || conversation.getMode() != Conversation.MODE_MULTI) {
            return;
        }
        final var status = mucUser.getStatus();
        final var configurationChange =
                Iterables.any(status, s -> (s >= 170 && s <= 174) || (s >= 102 && s <= 104));
        if (configurationChange) {
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid().asBareJid()
                            + ": received configuration change "
                            + status
                            + " in "
                            + from);
            getManager(MultiUserChatManager.class).fetchDiscoInfo(conversation);
        }
        final var item = mucUser.getItem();
        if (item == null) {
            return;
        }
        final var user = itemToUser(conversation, item);
        this.handleAffiliationChange(conversation, user);
    }

    private void handleAffiliationChange(
            final Conversation conversation, final MucOptions.User user) {
        final var account = getAccount();
        Log.d(
                Config.LOGTAG,
                account.getJid()
                        + ": changing affiliation for "
                        + user.getRealJid()
                        + " to "
                        + user.getAffiliation()
                        + " in "
                        + conversation.getAddress().asBareJid());
        if (user.realJidMatchesAccount()) {
            return;
        }
        final var mucOptions = getOrCreateState(conversation);
        final boolean isNew = mucOptions.updateUser(user);
        final var avatarService = this.service.getAvatarService();
        if (Strings.isNullOrEmpty(mucOptions.getAvatar())) {
            avatarService.clear(mucOptions);
        }
        avatarService.clear(user);
        this.service.updateMucRosterUi();
        this.service.updateConversationUi();
        if (user.ranks(Affiliation.MEMBER)) {
            fetchDeviceIdsIfNeeded(isNew, user);
        } else {
            final var jid = user.getRealJid();
            final var cryptoTargets = conversation.getAcceptedCryptoTargets();
            if (cryptoTargets.remove(user.getRealJid())) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": removed "
                                + jid
                                + " from crypto targets of "
                                + conversation.getName());
                conversation.setAcceptedCryptoTargets(cryptoTargets);
                getDatabase().updateConversation(conversation);
            }
        }
    }

    private void fetchDeviceIdsIfNeeded(final boolean isNew, final MucOptions.User user) {
        final var contact = user.getContact();
        final var mucOptions = user.getMucOptions();
        final var axolotlService = connection.getAxolotlService();
        if (isNew
                && user.getRealJid() != null
                && mucOptions.isPrivateAndNonAnonymous()
                && (contact == null || !contact.mutualPresenceSubscription())
                && axolotlService.hasEmptyDeviceList(user.getRealJid())) {
            axolotlService.fetchDeviceIds(user.getRealJid());
        }
    }

    public ListenableFuture<Void> fetchDiscoInfo(final Conversation conversation) {
        final var address = conversation.getAddress().asBareJid();
        final var bookmark = getManager(BookmarkManager.class).getBookmark(address);
        final MucOptions mucOptions = getOrCreateState(conversation);
        final var mucConfig =
                new MucConfigSummary(
                        mucOptions.occupantId(),
                        StringUtils.equals(
                                bookmark == null ? null : bookmark.getName(),
                                mucOptions.getName()));

        final var future =
                connection.getManager(DiscoManager.class).info(Entity.discoItem(address), null);
        return Futures.transform(
                future,
                infoQuery -> {
                    setDiscoInfo(conversation, infoQuery, mucConfig);
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    private void setDiscoInfo(
            final Conversation conversation,
            final InfoQuery infoQuery,
            final MucConfigSummary previousMucConfig) {
        final var caps = EntityCapabilities.hash(infoQuery);
        final var caps2 = EntityCapabilities2.hash(infoQuery);
        final var account = conversation.getAccount();
        final var address = conversation.getAddress().asBareJid();
        getDatabase().insertCapsCache(caps, caps2, infoQuery);
        final MucOptions mucOptions = getOrCreateState(conversation);
        if (mucOptions.setCaps2Hash(caps2.encoded())) {
            Log.d(Config.LOGTAG, "caps hash has changed. persisting");
            getDatabase().updateConversation(conversation);
        }
        final var avatarHash =
                infoQuery.getServiceDiscoveryExtension(
                        Namespace.MUC_ROOM_INFO, "muc#roominfo_avatarhash");
        if (VCardUpdate.isValidSHA1(avatarHash)) {
            connection.getManager(AvatarManager.class).handleVCardUpdate(address, avatarHash);
        }
        final var bookmark =
                getManager(BookmarkManager.class)
                        .getBookmark(conversation.getAddress().asBareJid());

        final var hasOccupantId = mucOptions.occupantId();

        if (!previousMucConfig.occupantId && hasOccupantId && mucOptions.online()) {
            final var me = mucOptions.getSelf().getFullJid();
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": gained support for occupant-id in "
                            + me
                            + ". resending presence");
            this.available(me, mucOptions.nonanonymous());
        }

        if (bookmark != null
                && (previousMucConfig.mucNameMatchesBookmark
                        || Strings.isNullOrEmpty(bookmark.getName()))) {
            if (StringUtils.changed(bookmark.getName(), mucOptions.getName())) {
                Log.d(
                        Config.LOGTAG,
                        getAccount().getJid().asBareJid()
                                + ": name of MUC changed. pushing bookmark for: "
                                + address);
                final var modifiedBookmark =
                        ImmutableBookmark.builder()
                                .from(bookmark)
                                .name(Strings.emptyToNull(mucOptions.getName()))
                                .build();
                getManager(BookmarkManager.class).create(modifiedBookmark);
            }
        }
        this.service.updateConversationUi();
    }

    public void resendPresence(final Conversation conversation) {
        final MucOptions mucOptions = getOrCreateState(conversation);
        if (mucOptions.online()) {
            available(mucOptions.getSelf().getFullJid(), mucOptions.nonanonymous());
        }
    }

    private void available(
            final Jid address, final boolean nonAnonymous, final Extension... extensions) {
        final var presenceManager = getManager(PresenceManager.class);
        final var account = getAccount();
        final String pgpSignature = account.getPgpSignature();
        if (nonAnonymous && pgpSignature != null) {
            final String message = account.getPresenceStatusMessage();
            presenceManager.available(
                    address, message, combine(extensions, new Signed(pgpSignature)));
        } else {
            presenceManager.available(address, extensions);
        }
    }

    public void unavailable(final Conversation conversation) {
        final var mucOptions = getOrCreateState(conversation);
        getManager(PresenceManager.class).unavailable(mucOptions.getSelf().getFullJid());
    }

    private static Extension[] combine(final Extension[] extensions, final Extension extension) {
        return new ImmutableList.Builder<Extension>()
                .addAll(Arrays.asList(extensions))
                .add(extension)
                .build()
                .toArray(new Extension[0]);
    }

    public ListenableFuture<Void> pushConfiguration(
            final Conversation conversation, final Map<String, Object> input) {
        final var address = conversation.getAddress().asBareJid();
        final var configuration = modifyBestInteroperability(input);

        if (configuration.get("muc#roomconfig_whois") instanceof String whois
                && whois.equals("anyone")) {
            conversation.setAttribute("accept_non_anonymous", true);
            getDatabase().updateConversation(conversation);
        }

        final var future = fetchConfigurationForm(address);
        return Futures.transformAsync(
                future,
                current -> {
                    final var modified = current.submit(configuration);
                    return submitConfigurationForm(address, modified);
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Data> fetchConfigurationForm(final Jid address) {
        final var iq = new Iq(Iq.Type.GET, new MucOwner());
        iq.setTo(address);
        return Futures.transform(
                connection.sendIqPacket(iq),
                response -> {
                    final var mucOwner = response.getExtension(MucOwner.class);
                    if (mucOwner == null) {
                        throw new IllegalStateException("Missing MucOwner element in response");
                    }
                    return mucOwner.getConfiguration();
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> submitConfigurationForm(final Jid address, final Data data) {
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var mucOwner = iq.addExtension(new MucOwner());
        mucOwner.addExtension(data);
        return Futures.transform(
                this.connection.sendIqPacket(iq), response -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> fetchMembers(final Conversation conversation) {
        final var futures =
                Collections2.transform(
                        Arrays.asList(Affiliation.OWNER, Affiliation.ADMIN, Affiliation.MEMBER),
                        a -> fetchAffiliations(conversation, a));
        ListenableFuture<List<MucOptions.User>> future = FutureMerger.allAsList(futures);
        return Futures.transform(
                future,
                members -> {
                    setMembers(conversation, members);
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    private void setMembers(final Conversation conversation, final List<MucOptions.User> users) {
        final var mucOptions = this.getOrCreateState(conversation);
        for (final var user : users) {
            if (user.realJidMatchesAccount()) {
                continue;
            }
            boolean isNew = mucOptions.updateUser(user);
            fetchDeviceIdsIfNeeded(isNew, user);
        }
        final var members = mucOptions.getMembers(true);
        final var cryptoTargets = conversation.getAcceptedCryptoTargets();
        boolean changed = false;
        for (final var iterator = cryptoTargets.listIterator(); iterator.hasNext(); ) {
            final var jid = iterator.next();
            if (!members.contains(jid) && !members.contains(jid.getDomain())) {
                iterator.remove();
                Log.d(
                        Config.LOGTAG,
                        getAccount().getJid().asBareJid()
                                + ": removed "
                                + jid
                                + " from crypto targets of "
                                + conversation.getName());
                changed = true;
            }
        }
        if (changed) {
            conversation.setAcceptedCryptoTargets(cryptoTargets);
            getDatabase().updateConversation(conversation);
        }
        if (Strings.isNullOrEmpty(mucOptions.getAvatar())) {
            this.service.getAvatarService().clear(mucOptions);
        }
        this.service.updateMucRosterUi();
        this.service.updateConversationUi();
    }

    private ListenableFuture<Collection<MucOptions.User>> fetchAffiliations(
            final Conversation conversation, final Affiliation affiliation) {
        final var iq = new Iq(Iq.Type.GET);
        iq.setTo(conversation.getAddress().asBareJid());
        iq.addExtension(new MucAdmin()).addExtension(new Item()).setAffiliation(affiliation);
        return Futures.transform(
                this.connection.sendIqPacket(iq),
                response -> {
                    final var mucAdmin = response.getExtension(MucAdmin.class);
                    if (mucAdmin == null) {
                        throw new IllegalStateException("No query in response");
                    }
                    return Collections2.transform(
                            mucAdmin.getItems(), i -> itemToUser(conversation, i));
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> changeUsername(
            final Conversation conversation, final String username) {
        final var bookmark =
                getManager(BookmarkManager.class)
                        .getBookmark(conversation.getAddress().asBareJid());
        final MucOptions options = getOrCreateState(conversation);
        final Jid joinJid = options.createJoinJid(username);
        if (joinJid == null) {
            return Futures.immediateFailedFuture(new IllegalArgumentException());
        }

        if (options.online()) {
            final SettableFuture<Void> renameFuture = SettableFuture.create();
            options.setOnRenameListener(
                    new MucOptions.OnRenameListener() {

                        @Override
                        public void onSuccess() {
                            renameFuture.set(null);
                        }

                        @Override
                        public void onFailure() {
                            renameFuture.setException(new IllegalStateException());
                        }
                    });

            available(joinJid, options.nonanonymous());

            if (username.equals(MucOptions.defaultNick(getAccount()))
                    && bookmark != null
                    && bookmark.getNick() != null) {
                Log.d(
                        Config.LOGTAG,
                        getAccount().getJid().asBareJid()
                                + ": removing nick from bookmark for "
                                + bookmark.getAddress());
                getManager(BookmarkManager.class)
                        .create(ImmutableBookmark.builder().from(bookmark).nick(null).build());
            }
            return renameFuture;
        } else {
            conversation.setContactJid(joinJid);
            getDatabase().updateConversation(conversation);
            if (bookmark != null) {
                getManager(BookmarkManager.class)
                        .create(ImmutableBookmark.builder().from(bookmark).nick(username).build());
            }
            join(conversation);
            return Futures.immediateVoidFuture();
        }
    }

    public void checkMucRequiresRename(final Conversation conversation) {
        final var options = getOrCreateState(conversation);
        if (!options.online()) {
            return;
        }
        final String current = options.getActualNick();
        final String proposed = options.getProposedNickPure();
        if (current == null || current.equals(proposed)) {
            return;
        }
        final Jid joinJid = options.createJoinJid(proposed);
        Log.d(
                Config.LOGTAG,
                String.format(
                        "%s: muc rename required %s (was: %s)",
                        getAccount().getJid().asBareJid(), joinJid, current));
        available(joinJid, options.nonanonymous());
    }

    public void setPassword(final Conversation conversation, final String password) {
        final var bookmark =
                getManager(BookmarkManager.class)
                        .getBookmark(conversation.getAddress().asBareJid());
        this.getOrCreateState(conversation).setPassword(password);
        if (bookmark != null) {
            getManager(BookmarkManager.class)
                    .create(
                            ImmutableBookmark.builder()
                                    .from(bookmark)
                                    .isAutoJoin(true)
                                    .password(password)
                                    .build());
        }
        getDatabase().updateConversation(conversation);
        this.join(conversation);
    }

    public void pingAndRejoin(final Conversation conversation) {
        final Account account = getAccount();
        synchronized (this.inProgressConferenceJoins) {
            if (this.inProgressConferenceJoins.contains(conversation)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": canceling muc self ping because join is already under way");
                return;
            }
        }
        synchronized (this.inProgressConferencePings) {
            if (!this.inProgressConferencePings.add(conversation)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": canceling muc self ping because ping is already under way");
                return;
            }
        }
        final Jid self = this.getOrCreateState(conversation).getSelf().getFullJid();
        final var future = this.getManager(PingManager.class).ping(self);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Iq result) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": ping to "
                                        + self
                                        + " came back fine");
                        synchronized (MultiUserChatManager.this.inProgressConferencePings) {
                            MultiUserChatManager.this.inProgressConferencePings.remove(
                                    conversation);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        synchronized (MultiUserChatManager.this.inProgressConferencePings) {
                            MultiUserChatManager.this.inProgressConferencePings.remove(
                                    conversation);
                        }
                        if (throwable instanceof IqErrorException iqErrorException) {
                            final var condition = iqErrorException.getErrorCondition();
                            if (condition instanceof Condition.ServiceUnavailable
                                    || condition instanceof Condition.FeatureNotImplemented
                                    || condition instanceof Condition.ItemNotFound) {
                                Log.d(
                                        Config.LOGTAG,
                                        account.getJid().asBareJid()
                                                + ": ping to "
                                                + self
                                                + " came back as ignorable error");
                            } else {
                                Log.d(
                                        Config.LOGTAG,
                                        account.getJid().asBareJid()
                                                + ": ping to "
                                                + self
                                                + " failed. attempting rejoin");
                                join(conversation);
                            }
                        }
                    }
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> destroy(final Jid address) {
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var mucOwner = iq.addExtension(new MucOwner());
        mucOwner.addExtension(new Destroy());
        return Futures.transform(
                connection.sendIqPacket(iq), result -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> setAffiliation(
            final Conversation conversation, final Affiliation affiliation, Jid user) {
        return setAffiliation(conversation, affiliation, Collections.singleton(user));
    }

    public ListenableFuture<Void> setAffiliation(
            final Conversation conversation,
            final Affiliation affiliation,
            final Collection<Jid> users) {
        final var address = conversation.getAddress().asBareJid();
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var admin = iq.addExtension(new MucAdmin());
        for (final var user : users) {
            final var item = admin.addExtension(new Item());
            item.setJid(user);
            item.setAffiliation(affiliation);
        }
        return Futures.transform(
                this.connection.sendIqPacket(iq),
                response -> {
                    // TODO figure out what this was meant to do
                    // is this a work around for some servers not sending notifications when
                    // changing the affiliation of people not in the room? this would explain this
                    // firing only when getRole == None
                    final var mucOptions = getOrCreateState(conversation);
                    for (final var user : users) {
                        mucOptions.changeAffiliation(user, affiliation);
                    }
                    service.getAvatarService().clear(mucOptions);
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> setRole(final Jid address, final Role role, final String user) {
        return setRole(address, role, Collections.singleton(user));
    }

    public ListenableFuture<Void> setRole(
            final Jid address, final Role role, final Collection<String> users) {
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var admin = iq.addExtension(new MucAdmin());
        for (final var user : users) {
            final var item = admin.addExtension(new Item());
            item.setNick(user);
            item.setRole(role);
        }
        return Futures.transform(
                this.connection.sendIqPacket(iq), response -> null, MoreExecutors.directExecutor());
    }

    public void setSubject(final Conversation conversation, final String subject) {
        final var message = new Message();
        message.setType(Message.Type.GROUPCHAT);
        message.setTo(conversation.getAddress().asBareJid());
        message.addExtension(new Subject(subject));
        connection.sendMessagePacket(message);
    }

    public void invite(final Conversation conversation, final Jid address) {
        Log.d(
                Config.LOGTAG,
                conversation.getAccount().getJid().asBareJid()
                        + ": inviting "
                        + address
                        + " to "
                        + conversation.getAddress().asBareJid());
        final MucOptions.User user =
                getOrCreateState(conversation).findUserByRealJid(address.asBareJid());
        if (user == null || user.getAffiliation() == Affiliation.OUTCAST) {
            this.setAffiliation(conversation, Affiliation.NONE, address);
        }

        final var packet = new Message();
        packet.setTo(conversation.getAddress().asBareJid());
        final var x = packet.addExtension(new MucUser());
        final var invite = x.addExtension(new Invite());
        invite.setTo(address.asBareJid());
        connection.sendMessagePacket(packet);
    }

    public void directInvite(final Conversation conversation, final Jid address) {
        final var message = new Message();
        message.setTo(address);
        final var directInvite = message.addExtension(new DirectInvite());
        directInvite.setJid(conversation.getAddress().asBareJid());
        final var password = getOrCreateState(conversation).getPassword();
        if (password != null) {
            directInvite.setPassword(password);
        }
        if (address.isFullJid()) {
            message.addExtension(new NoStore());
            message.addExtension(new NoCopy());
        }
        this.connection.sendMessagePacket(message);
    }

    public boolean isMuc(final Jid address) {
        final var state = address == null ? null : getState(address.asBareJid());
        return state != null && state.getConversation().getMode() == Conversational.MODE_MULTI;
    }

    public boolean isJoinInProgress(final Conversation conversation) {
        synchronized (this.inProgressConferenceJoins) {
            if (conversation.getMode() == Conversational.MODE_MULTI) {
                final boolean inProgress = this.inProgressConferenceJoins.contains(conversation);
                if (inProgress) {
                    Log.d(
                            Config.LOGTAG,
                            getAccount().getJid().asBareJid()
                                    + ": holding back message to group. join in progress");
                }
                return inProgress;
            } else {
                return false;
            }
        }
    }

    public void clearInProgress() {
        synchronized (this.inProgressConferenceJoins) {
            this.inProgressConferenceJoins.clear();
        }
        synchronized (this.inProgressConferencePings) {
            this.inProgressConferencePings.clear();
        }
    }

    public Jid getService() {
        return Iterables.getFirst(this.getServices(), null);
    }

    public List<Jid> getServices() {
        final var builder = new ImmutableList.Builder<Jid>();
        for (final var entry : getManager(DiscoManager.class).getServerItems().entrySet()) {
            final var value = entry.getValue();
            if (value.getFeatureStrings().contains(Namespace.MUC)
                    && value.hasIdentityWithCategoryAndType("conference", "text")
                    && !value.getFeatureStrings().contains("jabber:iq:gateway")
                    && !value.hasIdentityWithCategoryAndType("conference", "irc")) {
                builder.add(entry.getKey());
            }
        }
        return builder.build();
    }

    public static MucOptions.User itemToUser(
            final Conversation conference,
            final im.conversations.android.xmpp.model.muc.Item item) {
        return itemToUser(conference, item, null, null);
    }

    public static MucOptions.User itemToUser(
            final Conversation conference,
            final im.conversations.android.xmpp.model.muc.Item item,
            final Jid from,
            final String occupantId) {
        final var affiliation = item.getAffiliation();
        final var role = item.getRole();
        final var nick = item.getNick();
        final Jid fullAddress;
        if (from != null && from.isFullJid()) {
            fullAddress = from;
        } else if (Strings.isNullOrEmpty(nick)) {
            fullAddress = null;
        } else {
            fullAddress = ofNick(conference, nick);
        }
        final Jid realJid = Jid.Invalid.getNullForInvalid(item.getAttributeAsJid("jid"));
        return new MucOptions.User(
                conference.getMucOptions(), fullAddress, realJid, occupantId, role, affiliation);
    }

    private static Jid ofNick(final Conversation conversation, final String nick) {
        try {
            return conversation.getAddress().withResource(nick);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    private static Map<String, Object> modifyBestInteroperability(
            final Map<String, Object> unmodified) {
        final var builder = new ImmutableMap.Builder<String, Object>();
        builder.putAll(unmodified);

        if (unmodified.get("muc#roomconfig_moderatedroom") instanceof Boolean moderated) {
            builder.put("members_by_default", !moderated);
        }
        if (unmodified.get("muc#roomconfig_allowpm") instanceof String allowPm) {
            // ejabberd :-/
            final boolean allow = "anyone".equals(allowPm);
            builder.put("allow_private_messages", allow);
            builder.put("allow_private_messages_from_visitors", allow ? "anyone" : "nobody");
        }

        if (unmodified.get("muc#roomconfig_allowinvites") instanceof Boolean allowInvites) {
            // TODO check that this actually does something useful?
            builder.put(
                    "{http://prosody.im/protocol/muc}roomconfig_allowmemberinvites", allowInvites);
        }

        return builder.buildOrThrow();
    }

    private static final class MucConfigSummary {
        private final boolean occupantId;
        private final boolean mucNameMatchesBookmark;

        private MucConfigSummary(boolean occupantId, boolean mucNameMatchesBookmark) {
            this.occupantId = occupantId;
            this.mucNameMatchesBookmark = mucNameMatchesBookmark;
        }
    }

    private static Map<String, Object> configWithName(
            final Map<String, Object> unmodified, final String name) {
        if (Strings.isNullOrEmpty(name)) {
            return unmodified;
        }
        return new ImmutableMap.Builder<String, Object>()
                .putAll(unmodified)
                .put("muc#roomconfig_roomname", name)
                .buildKeepingLast();
    }

    public static Map<String, Object> defaultGroupChatConfiguration() {
        return new ImmutableMap.Builder<String, Object>()
                .put("muc#roomconfig_persistentroom", true)
                .put("muc#roomconfig_membersonly", true)
                .put("muc#roomconfig_publicroom", false)
                .put("muc#roomconfig_whois", "anyone")
                .put("muc#roomconfig_changesubject", false)
                .put("muc#roomconfig_allowinvites", false)
                .put("muc#roomconfig_enablearchiving", true) // prosody
                .put("mam", true) // ejabberd community
                .put("muc#roomconfig_mam", true) // ejabberd saas
                .put("muc#roomconfig_enablelogging", false) // public logging
                .buildOrThrow();
    }

    public static Map<String, Object> defaultChannelConfiguration() {
        return new ImmutableMap.Builder<String, Object>()
                .put("muc#roomconfig_persistentroom", true)
                .put("muc#roomconfig_membersonly", false)
                .put("muc#roomconfig_publicroom", true)
                .put("muc#roomconfig_whois", "moderators")
                .put("muc#roomconfig_changesubject", false)
                .put("muc#roomconfig_enablearchiving", true) // prosody
                .put("mam", true) // ejabberd community
                .put("muc#roomconfig_mam", true) // ejabberd saas
                .buildOrThrow();
    }
}
