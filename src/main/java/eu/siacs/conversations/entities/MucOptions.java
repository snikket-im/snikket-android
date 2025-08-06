package eu.siacs.conversations.entities;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Longs;
import de.gultsch.common.IntMap;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.utils.JidHelper;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.model.Hash;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.data.Field;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.Item;
import im.conversations.android.xmpp.model.muc.Role;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class MucOptions {

    private static final IntMap<Affiliation> AFFILIATION_RANKS =
            new IntMap<>(
                    new ImmutableMap.Builder<Affiliation, Integer>()
                            .put(Affiliation.OWNER, 4)
                            .put(Affiliation.ADMIN, 3)
                            .put(Affiliation.MEMBER, 2)
                            .put(Affiliation.NONE, 1)
                            .put(Affiliation.OUTCAST, 0)
                            .build());

    private static final IntMap<Role> ROLE_RANKS =
            new IntMap<>(
                    new ImmutableMap.Builder<Role, Integer>()
                            .put(Role.MODERATOR, 3)
                            .put(Role.PARTICIPANT, 2)
                            .put(Role.VISITOR, 1)
                            .put(Role.NONE, 0)
                            .build());

    private static final Ordering<User> VISUAL_ORDERING =
            new Ordering<User>() {
                @Override
                public int compare(User a, User b) {
                    if (b.outranks(a.getAffiliation())) {
                        return 1;
                    } else if (a.outranks(b.getAffiliation())) {
                        return -1;
                    } else {
                        if (a.getAvatar() != null && b.getAvatar() == null) {
                            return -1;
                        } else if (a.getAvatar() == null && b.getAvatar() != null) {
                            return 1;
                        } else {
                            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
                        }
                    }
                }
            };

    private final Map<AddressableId, User> users = new HashMap<>();
    private final Map<Id, User> usersByOccupantId = new HashMap<>();
    private final Conversation conversation;
    private final Account account;
    public OnRenameListener onRenameListener = null;
    private boolean mAutoPushConfiguration = true;
    private Error error = Error.NONE;
    private Self self;
    // TODO get rid of password; access password through attributes
    private String password = null;

    public MucOptions(final Conversation conversation) {
        this.account = conversation.getAccount();
        this.conversation = conversation;
        final var affiliation = Item.affiliationOrNone(conversation.getAttribute("affiliation"));
        final var role = Item.roleOrNone(conversation.getAttribute("role"));
        this.self =
                new Self(
                        this,
                        createJoinJid(getProposedNick()),
                        this.account.getJid().asBareJid(),
                        null,
                        role,
                        affiliation,
                        false);
    }

    public Account getAccount() {
        return this.conversation.getAccount();
    }

    public boolean setSelf(final Self user) {
        Log.d(Config.LOGTAG, "setSelf(" + user + ")");
        synchronized (this.users) {
            // on same nick merges we need to remove the other device
            this.users.remove(Id.resource(user.getFullJid()));
            // this should not be happening but attempting to remove it does not hurt
            this.users.remove(Id.realAddress(user.getRealJid()));
            this.self = user.asConnectedSelf();
            this.resetOccupantIdMap();
        }
        final boolean roleChanged =
                this.conversation.setAttribute("role", user.getRole().toString());
        final boolean affiliationChanged =
                this.conversation.setAttribute("affiliation", user.getAffiliation().toString());
        return roleChanged || affiliationChanged;
    }

    public void changeAffiliation(final Jid jid, final Affiliation affiliation) {
        synchronized (this.users) {
            final var user = this.users.get(Id.realAddress(jid));
            if (user != null && user.getRole() == Role.NONE) {
                final var modifiedUser = user.withAffiliation(affiliation);
                this.users.put(modifiedUser.asId(), modifiedUser);
            }
        }
    }

    public void setAutoPushConfiguration(final boolean auto) {
        this.mAutoPushConfiguration = auto;
    }

    public boolean autoPushConfiguration() {
        return mAutoPushConfiguration;
    }

    // TODO rework isSelf to use User and match either occupantId or self
    public boolean isSelf(final Jid counterpart) {
        return counterpart.equals(self.getFullJid());
    }

    public boolean isOurAccount(@NonNull final User user) {
        final var self = getSelf();
        if (self == null) {
            return false;
        }
        return self.getRealJid().equals(user.getRealJid())
                || (self.getOccupantId() != null
                        && self.getOccupantId().equals(user.getOccupantId()));
    }

    public void resetChatState() {
        synchronized (users) {
            for (final var user : users.values()) {
                user.chatState = Config.DEFAULT_CHAT_STATE;
            }
        }
    }

    public boolean mamSupport() {
        return MessageArchiveService.Version.has(getFeatures());
    }

    private InfoQuery getServiceDiscoveryResult() {
        return this.account
                .getXmppConnection()
                .getManager(DiscoManager.class)
                .get(getConversation().getAddress().asBareJid());
    }

    public String getName() {
        final var serviceDiscoveryResult = getServiceDiscoveryResult();
        if (serviceDiscoveryResult == null) {
            return null;
        }
        final var roomInfo =
                serviceDiscoveryResult.getServiceDiscoveryExtension(
                        "http://jabber.org/protocol/muc#roominfo");
        final Field roomConfigName =
                roomInfo == null ? null : roomInfo.getFieldByName("muc#roomconfig_roomname");
        if (roomConfigName != null) {
            return roomConfigName.getValue();
        } else {
            final var identities = serviceDiscoveryResult.getIdentities();
            final String identityName =
                    !identities.isEmpty()
                            ? Iterables.getFirst(identities, null).getIdentityName()
                            : null;
            final Jid jid = conversation.getAddress();
            if (identityName != null && !identityName.equals(jid == null ? null : jid.getLocal())) {
                return identityName;
            } else {
                return null;
            }
        }
    }

    public String getRoomConfigName() {
        final var serviceDiscoveryResult = getServiceDiscoveryResult();
        if (serviceDiscoveryResult == null) {
            return null;
        }
        final var roomInfo =
                serviceDiscoveryResult.getServiceDiscoveryExtension(
                        "http://jabber.org/protocol/muc#roominfo");
        final var roomConfigName =
                roomInfo == null ? null : roomInfo.getFieldByName("muc#roomconfig_roomname");
        return roomConfigName == null ? null : roomConfigName.getValue();
    }

    private Data getRoomInfoForm() {
        final var serviceDiscoveryResult = getServiceDiscoveryResult();
        return serviceDiscoveryResult == null
                ? null
                : serviceDiscoveryResult.getServiceDiscoveryExtension(Namespace.MUC_ROOM_INFO);
    }

    public String getAvatar() {
        return account.getRoster().getContact(conversation.getAddress()).getAvatar();
    }

    public boolean hasFeature(final String feature) {
        final var serviceDiscoveryResult = getServiceDiscoveryResult();
        return serviceDiscoveryResult != null
                && serviceDiscoveryResult.getFeatureStrings().contains(feature);
    }

    public boolean hasVCards() {
        return hasFeature("vcard-temp");
    }

    public boolean canInvite() {
        final boolean hasPermission =
                !membersOnly() || self.ranks(Role.MODERATOR) || allowInvites();
        return hasPermission && online();
    }

    public boolean allowInvites() {
        final var roomInfo = getRoomInfoForm();
        if (roomInfo == null) {
            return false;
        }
        final var field = roomInfo.getFieldByName("muc#roomconfig_allowinvites");
        return field != null && "1".equals(field.getValue());
    }

    public boolean canChangeSubject() {
        return self.ranks(Role.MODERATOR) || participantsCanChangeSubject();
    }

    public boolean participantsCanChangeSubject() {
        final var roomInfo = getRoomInfoForm();
        if (roomInfo == null) {
            return false;
        }
        final Field configField = roomInfo.getFieldByName("muc#roomconfig_changesubject");
        final Field infoField = roomInfo.getFieldByName("muc#roominfo_changesubject");
        final Field field = configField != null ? configField : infoField;
        return field != null && "1".equals(field.getValue());
    }

    public boolean allowPm() {
        final var roomInfo = getRoomInfoForm();
        if (roomInfo == null) {
            return true;
        }
        final Field field = roomInfo.getFieldByName("muc#roomconfig_allowpm");
        if (field == null) {
            return true; // fall back if field does not exists
        }
        if ("anyone".equals(field.getValue())) {
            return true;
        } else if ("participants".equals(field.getValue())) {
            return self.ranks(Role.PARTICIPANT);
        } else if ("moderators".equals(field.getValue())) {
            return self.ranks(Role.MODERATOR);
        } else {
            return false;
        }
    }

    public boolean allowPmRaw() {
        final var roomInfo = getRoomInfoForm();
        final Field field =
                roomInfo == null ? null : roomInfo.getFieldByName("muc#roomconfig_allowpm");
        return field == null || Arrays.asList("anyone", "participants").contains(field.getValue());
    }

    public boolean participating() {
        return self.ranks(Role.PARTICIPANT) || !moderated();
    }

    public boolean membersOnly() {
        return this.hasFeature("muc_membersonly");
    }

    public Collection<String> getFeatures() {
        final var serviceDiscoveryResult = getServiceDiscoveryResult();
        return serviceDiscoveryResult != null
                ? serviceDiscoveryResult.getFeatureStrings()
                : Collections.emptyList();
    }

    public boolean nonanonymous() {
        return this.hasFeature("muc_nonanonymous");
    }

    public boolean isPrivateAndNonAnonymous() {
        return membersOnly() && nonanonymous();
    }

    public boolean moderated() {
        return this.hasFeature("muc_moderated");
    }

    public boolean stableId() {
        return getFeatures().contains("http://jabber.org/protocol/muc#stable_id");
    }

    public boolean occupantId() {
        final var features = getFeatures();
        return features.contains(Namespace.OCCUPANT_ID);
    }

    public boolean moderation() {
        final var features = getFeatures();
        return features.contains(Namespace.MODERATION);
    }

    public User deleteUser(final Jid jid) {
        synchronized (this.users) {
            final var user = this.users.remove(Id.resource(jid));
            this.resetOccupantIdMap();
            return user;
        }
    }

    public void updateUser(final User user) {
        updateUser(user, null);
    }

    public void updateUser(final User user, final Presence.Type type) {
        synchronized (this.users) {
            final var real = user.getRealJid();
            final var resource = user.getFullJid();
            if (real != null) {
                this.users.remove(Id.realAddress(real));
            }
            if (resource != null) {
                this.users.remove(Id.resource(resource));
            } else {
                if (real != null && isOnline(real, this.users)) {
                    this.resetOccupantIdMap();
                    return;
                }
            }

            // if type null add normal; if type == unavailable add as real jid
            if (type == null) {
                this.users.put(user.asId(), user);
            } else if (type == Presence.Type.UNAVAILABLE
                    && real != null
                    && membersOnly()
                    && user.ranks(Affiliation.MEMBER)) {
                if (isOurAccount(user) || isOnline(real, this.users)) {
                    // for our account and users that are online with a second device do not keep
                    // offline variant
                    this.resetOccupantIdMap();
                    return;
                }
                final var offline = user.asOfflineUser();
                this.users.put(offline.asId(), offline);
            }
            // TODO support nick changes so we don't go from
            this.resetOccupantIdMap();
        }
    }

    private static boolean isOnline(final Jid address, final Map<AddressableId, User> users) {
        return Iterables.any(
                users.values(), u -> u.getFullJid() != null && address.equals(u.getRealJid()));
    }

    private void resetOccupantIdMap() {
        synchronized (this.usersByOccupantId) {
            this.usersByOccupantId.clear();
            final var builder = new ImmutableMap.Builder<Id, User>();
            for (final var user : this.users.values()) {
                // exclude our account in occupant id map. This map will be used for getUsersPreview
                // and we generally don't want our account in there
                if (isOurAccount(user)) {
                    continue;
                }
                if (user.occupantId != null) {
                    builder.put(Id.occupantId(user.occupantId), user);
                } else if (user.realJid != null && user.fullJid == null) {
                    // we include offline users here as well in order to make getUsersPreview work
                    builder.put(Id.realAddress(user.realJid), user);
                }
            }
            this.usersByOccupantId.putAll(builder.buildKeepingLast());
        }
    }

    @Nullable
    public User getUser(final Jid address) {
        final var self = this.self;
        if (self != null && address != null && address.equals(self.getFullJid())) {
            return self;
        }
        synchronized (this.users) {
            return this.users.get(Id.resource(address));
        }
    }

    @Nullable
    public User getUser(final String occupantId) {
        final var self = this.self;
        if (self != null && occupantId.equals(self.getOccupantId())) {
            return self;
        }
        synchronized (this.usersByOccupantId) {
            return this.usersByOccupantId.get(Id.occupantId(occupantId));
        }
    }

    @Nullable
    public User getUser(final IdentifiableUser identifiableUser) {
        final var occupantId = identifiableUser.mucUserOccupantId();
        final var realAddress = identifiableUser.mucUserRealAddress();
        final var address = identifiableUser.mucUserAddress();
        synchronized (this.users) {
            if (occupantId != null) {
                // online users will be found by occupant id. offline users by real address
                // there is no need for a deep seek
                final var byOccupantId = this.usersByOccupantId.get(Id.occupantId(occupantId));
                if (byOccupantId != null) {
                    return byOccupantId;
                }
                final var self = getSelf();
                final var bySelf =
                        self != null && occupantId.equals(self.getOccupantId()) ? self : null;
                if (bySelf != null) {
                    return bySelf;
                }
                if (realAddress != null) {
                    return this.users.get(Id.realAddress(realAddress));
                }
                return null;
            } else if (realAddress != null) {
                // if we have a real address we first try to look up offline users because that look
                // up is cheap before iterating through all online users
                final var offline = this.users.get(Id.realAddress(realAddress));
                if (offline != null) {
                    return offline;
                }
                return Iterables.find(
                        this.users.values(), u -> realAddress.equals(u.realJid), null);
            } else if (address != null) {
                return this.users.get(Id.resource(address));
            } else {
                return null;
            }
        }
    }

    public User getUserOrStub(final IdentifiableUser identifiableUser) {
        final var existing = getUser(identifiableUser);
        if (existing != null) {
            return existing;
        }
        Log.d(
                Config.LOGTAG,
                "creating stub for getUser("
                        + identifiableUser.getClass()
                        + ") "
                        + identifiableUser.mucUserAddress()
                        + ","
                        + identifiableUser.mucUserRealAddress());
        return new Stub(
                this,
                identifiableUser.mucUserAddress(),
                identifiableUser.mucUserRealAddress(),
                identifiableUser.mucUserOccupantId());
    }

    public Collection<User> getUsersOrStubs(
            final Collection<? extends IdentifiableUser> identifiableUsers) {
        return Collections2.transform(identifiableUsers, this::getUserOrStub);
    }

    // always all users (including offline)
    public List<User> getUsers() {
        synchronized (this.users) {
            return ImmutableList.copyOf(Iterables.filter(this.users.values(), u -> !u.isDomain()));
        }
    }

    public List<User> getOnlineUsers() {
        synchronized (this.users) {
            return ImmutableList.copyOf(
                    Iterables.filter(
                            this.users.values(), u -> !u.isDomain() && u.ranks(Role.PARTICIPANT)));
        }
    }

    public List<User> getUsersWithChatState(final ChatState state, final int max) {
        synchronized (this.users) {
            synchronized (this.users) {
                return ImmutableList.copyOf(
                        Iterables.limit(
                                Iterables.filter(this.users.values(), u -> u.chatState == state),
                                max));
            }
        }
    }

    public List<User> getUsersPreview(final int max) {
        synchronized (this.users) {
            final Collection<User> users;
            if (this.usersByOccupantId.isEmpty()) {
                users = this.users.values();
            } else {
                users = this.usersByOccupantId.values();
            }
            return ImmutableList.copyOf(Iterables.limit(VISUAL_ORDERING.sortedCopy(users), max));
        }
    }

    public int getUserCount() {
        synchronized (users) {
            return users.size();
        }
    }

    private String getProposedNick() {
        final var bookmark = this.conversation.getBookmark();
        if (bookmark != null) {
            // if we already have a bookmark we consider this the source of truth
            return getProposedNickPure();
        }
        final var storedJid = conversation.getAddress();
        if (storedJid.isBareJid()) {
            return defaultNick(account);
        } else {
            return storedJid.getResource();
        }
    }

    public String getProposedNickPure() {
        final var bookmark = this.conversation.getBookmark();
        final String bookmarkedNick =
                normalize(account.getJid(), bookmark == null ? null : bookmark.getNick());
        if (bookmarkedNick != null) {
            return bookmarkedNick;
        } else {
            return defaultNick(account);
        }
    }

    public static String defaultNick(final Account account) {
        final String displayName = normalize(account.getJid(), account.getDisplayName());
        if (displayName == null) {
            return JidHelper.localPartOrFallback(account.getJid());
        } else {
            return displayName;
        }
    }

    private static String normalize(final Jid account, final String nick) {
        if (account == null || Strings.isNullOrEmpty(nick)) {
            return null;
        }
        try {
            return account.withResource(nick).getResource();
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public String getActualNick() {
        if (this.self.resource() != null) {
            return this.self.resource();
        } else {
            return this.getProposedNick();
        }
    }

    public boolean online() {
        final var self = getSelf();
        return self != null && self.connected;
    }

    public Error getError() {
        return this.error;
    }

    public void setError(final Error error) {
        // TODO flip self to not connected
        // this.isOnline = isOnline && error == Error.NONE;
        this.error = error;
    }

    public void setOnRenameListener(OnRenameListener listener) {
        this.onRenameListener = listener;
    }

    public Self getSelf() {
        return self;
    }

    public boolean setSubject(String subject) {
        return this.conversation.setAttribute("subject", subject);
    }

    public String getSubject() {
        return this.conversation.getAttribute("subject");
    }

    private List<User> getFallbackUsersFromCryptoTargets() {
        return ImmutableList.copyOf(
                Collections2.transform(
                        conversation.getAcceptedCryptoTargets(),
                        jid -> new Stub(this, null, jid, null)));
    }

    public List<User> getUsersPreviewWithFallback() {
        final List<User> users;
        if (online()) {
            users = getUsersPreview(5);
        } else {
            users = getFallbackUsersFromCryptoTargets();
        }
        return users;
    }

    public long[] getPgpKeyIds() {
        synchronized (this.users) {
            return Longs.toArray(
                    Collections2.filter(
                            Collections2.transform(this.users.values(), User::getPgpKeyId),
                            id -> id != 0));
        }
    }

    public boolean pgpKeysInUse() {
        synchronized (users) {
            return Iterables.any(this.users.values(), u -> u.getPgpKeyId() != 0);
        }
    }

    public boolean missingPgpKeys() {
        synchronized (this.users) {
            return Iterables.any(this.users.values(), u -> u.getPgpKeyId() == 0);
        }
    }

    public Jid createJoinJid(final String nick) {
        try {
            return conversation.getAddress().withResource(nick);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public String getPassword() {
        this.password = conversation.getAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD);
        if (this.password == null
                && conversation.getBookmark() != null
                && conversation.getBookmark().getPassword() != null) {
            return conversation.getBookmark().getPassword();
        } else {
            return this.password;
        }
    }

    public void setPassword(final String password) {
        this.password = password;
        conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
    }

    public boolean setCaps2Hash(final String hash) {
        return this.conversation.setAttribute(Conversation.ATTRIBUTE_CAPS2_HASH, hash);
    }

    public EntityCapabilities2.EntityCaps2Hash getCaps2Hash() {
        final var caps2Hash = this.conversation.getAttribute(Conversation.ATTRIBUTE_CAPS2_HASH);
        if (Strings.isNullOrEmpty(caps2Hash)) {
            return null;
        }
        return EntityCapabilities2.EntityCaps2Hash.of(Hash.Algorithm.SHA_256, caps2Hash);
    }

    public Conversation getConversation() {
        return this.conversation;
    }

    public ImmutableSet<Jid> getMembers() {
        synchronized (this.users) {
            return ImmutableSet.copyOf(
                    Collections2.transform(
                            Collections2.filter(
                                    this.users.values(),
                                    u ->
                                            u.ranks(Affiliation.MEMBER)
                                                    && u.realJid != null
                                                    && !u.realJid.isDomainJid()
                                                    && !u.realJidMatchesAccount()),
                            u -> u.realJid));
        }
    }

    public ImmutableSet<Jid> getMembersWithDomains() {
        synchronized (this.users) {
            return ImmutableSet.copyOf(
                    Collections2.transform(
                            Collections2.filter(
                                    this.users.values(),
                                    u ->
                                            u.ranks(Affiliation.MEMBER)
                                                    && u.realJid != null
                                                    && !u.realJidMatchesAccount()),
                            u -> u.realJid));
        }
    }

    public enum Error {
        NO_RESPONSE,
        SERVER_NOT_FOUND,
        REMOTE_SERVER_TIMEOUT,
        NONE,
        NICK_IN_USE,
        PASSWORD_REQUIRED,
        BANNED,
        MEMBERS_ONLY,
        RESOURCE_CONSTRAINT,
        KICKED,
        SHUTDOWN,
        DESTROYED,
        INVALID_NICK,
        TECHNICAL_PROBLEMS,
        UNKNOWN,
        NON_ANONYMOUS
    }

    private interface OnEventListener {
        void onSuccess();

        void onFailure();
    }

    public interface OnRenameListener extends OnEventListener {}

    public static class User implements Comparable<User>, AvatarService.Avatar {
        private final MucOptions options;
        private final Jid fullJid;
        private final Jid realJid;
        private final String occupantId;
        private final Role role;
        private final Affiliation affiliation;
        private Long pgpKeyId;
        private String avatar;
        private ChatState chatState = Config.DEFAULT_CHAT_STATE;

        public User(
                final MucOptions options,
                final Jid fullJid,
                final Jid realJid,
                final String occupantId,
                final Role role,
                final Affiliation affiliation) {
            Preconditions.checkNotNull(options, "MucOptions must not be null");
            Preconditions.checkNotNull(role, "Role must not be null. Use NONE instead");
            Preconditions.checkNotNull(
                    affiliation, "Affiliation must not be null. Use NONE instead");
            this.options = options;
            this.fullJid = fullJid;
            this.realJid = realJid != null ? realJid.asBareJid() : null;
            this.occupantId = occupantId;
            this.role = role;
            this.affiliation = affiliation;
        }

        public AddressableId asId() {
            if (fullJid != null) {
                return Id.resource(fullJid);
            } else if (realJid != null) {
                return Id.realAddress(realJid);
            } else {
                throw new IllegalStateException("can not create id");
            }
        }

        public String resource() {
            return fullJid == null ? null : fullJid.getResource();
        }

        public Role getRole() {
            return this.role;
        }

        public Affiliation getAffiliation() {
            return this.affiliation;
        }

        public long getPgpKeyId() {
            if (this.pgpKeyId != null) {
                return this.pgpKeyId;
            } else if (realJid != null) {
                return getAccount().getRoster().getContact(realJid).getPgpKeyId();
            } else {
                return 0;
            }
        }

        public void setPgpKeyId(final Long id) {
            this.pgpKeyId = id;
        }

        public Contact getContact() {
            if (fullJid != null) {
                return realJid == null
                        ? null
                        : getAccount().getRoster().getContactFromContactList(realJid);
            } else if (realJid != null) {
                return getAccount().getRoster().getContact(realJid);
            } else {
                return null;
            }
        }

        public boolean setAvatar(final String avatar) {
            if (this.avatar != null && this.avatar.equals(avatar)) {
                return false;
            } else {
                this.avatar = avatar;
                return true;
            }
        }

        public String getAvatar() {
            final var contact = getContact();
            if (contact != null && contact.getAvatar() != null) {
                return contact.getAvatar();
            }
            return this.avatar;
        }

        public Account getAccount() {
            return options.getAccount();
        }

        public MucOptions getMucOptions() {
            return this.options;
        }

        public Conversation getConversation() {
            return options.getConversation();
        }

        public Jid getFullJid() {
            return fullJid;
        }

        public boolean isDomain() {
            return realJid != null && realJid.getLocal() == null && role == Role.NONE;
        }

        public boolean realJidMatchesAccount() {
            return realJid != null && realJid.equals(options.account.getJid().asBareJid());
        }

        @Override
        public int compareTo(@NonNull User another) {
            if (another.outranks(getAffiliation())) {
                return 1;
            } else if (outranks(another.getAffiliation())) {
                return -1;
            } else {
                return getDisplayName().compareToIgnoreCase(another.getDisplayName());
            }
        }

        @NonNull
        public String getDisplayName() {
            final var contact = this.getContact();
            if (contact != null) {
                return contact.getDisplayName();
            } else {
                final String resource = this.resource();
                if (resource != null) {
                    return resource;
                }
                if (realJid != null) {
                    return JidHelper.localPartOrFallback(realJid);
                } else {
                    return fullJid.toString();
                }
            }
        }

        public Jid getRealJid() {
            return realJid;
        }

        public boolean setChatState(ChatState chatState) {
            if (this.chatState == chatState) {
                return false;
            }
            this.chatState = chatState;
            return true;
        }

        @Override
        public int getAvatarBackgroundColor() {
            final String seed = realJid != null ? realJid.asBareJid().toString() : null;
            return UIHelper.getColorForName(seed == null ? resource() : seed);
        }

        @Override
        public String getAvatarName() {
            return getConversation().getName().toString();
        }

        public String getOccupantId() {
            return this.occupantId;
        }

        public boolean ranks(final Role role) {
            return ROLE_RANKS.getInt(this.role) >= ROLE_RANKS.getInt(role);
        }

        public boolean ranks(final Affiliation affiliation) {
            return AFFILIATION_RANKS.getInt(this.affiliation)
                    >= AFFILIATION_RANKS.getInt(affiliation);
        }

        public boolean outranks(final Affiliation affiliation) {
            return AFFILIATION_RANKS.getInt(this.affiliation)
                    > AFFILIATION_RANKS.getInt(affiliation);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof User user)) return false;
            return role == user.role
                    && affiliation == user.affiliation
                    && Objects.equal(realJid, user.realJid)
                    && Objects.equal(fullJid, user.fullJid)
                    && Objects.equal(pgpKeyId, user.pgpKeyId)
                    && Objects.equal(avatar, user.avatar)
                    && Objects.equal(occupantId, user.occupantId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(
                    role, affiliation, realJid, fullJid, pgpKeyId, avatar, occupantId);
        }

        public User asOfflineUser() {
            return new User(
                    this.options, null, this.realJid, this.occupantId, Role.NONE, affiliation);
        }

        public User withAffiliation(final Affiliation affiliation) {
            return new User(
                    this.options,
                    this.fullJid,
                    this.realJid,
                    this.occupantId,
                    this.role,
                    affiliation);
        }

        public Self asConnectedSelf() {
            final var address =
                    this.realJid == null
                            ? this.options.getAccount().getJid().asBareJid()
                            : this.realJid;
            return new Self(
                    this.options,
                    this.fullJid,
                    address,
                    this.occupantId,
                    this.role,
                    this.affiliation,
                    true);
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("fullJid", fullJid)
                    .add("realJid", realJid)
                    .add("occupantId", occupantId)
                    .add("role", role)
                    .add("affiliation", affiliation)
                    .add("pgpKeyId", pgpKeyId)
                    .add("avatar", avatar)
                    .add("chatState", chatState)
                    .toString();
        }
    }

    public static final class Self extends User {

        private final boolean connected;

        private Self(
                final MucOptions options,
                final Jid fullJid,
                final Jid realJid,
                final String occupantId,
                final Role role,
                final Affiliation affiliation,
                final boolean connected) {
            super(options, fullJid, realJid, occupantId, role, affiliation);
            Preconditions.checkNotNull(
                    realJid, "The self muc user should not have a null real jid");
            Preconditions.checkArgument(
                    fullJid != null && fullJid.isFullJid(), "the full jid needs to be a full jid");
            this.connected = connected;
        }
    }

    public static final class Stub extends User {

        private Stub(
                final MucOptions options,
                final Jid fullJid,
                final Jid realJid,
                final String occupantId) {
            super(options, fullJid, realJid, occupantId, Role.NONE, Affiliation.NONE);
        }
    }

    public sealed interface Id permits AddressableId, OccupantId {

        static OccupantId occupantId(final String occupantId) {
            return new OccupantId(occupantId);
        }

        static AddressableId realAddress(final Jid address) {
            return new RealAddress(address);
        }

        static AddressableId resource(final Jid address) {
            return new Resource(address);
        }
    }

    public sealed interface AddressableId extends Id permits RealAddress, Resource {}

    public record OccupantId(String occupantId) implements Id {}

    public record RealAddress(Jid address) implements AddressableId {}

    public record Resource(Jid address) implements AddressableId {}

    public interface IdentifiableUser {
        Jid mucUserAddress();

        Jid mucUserRealAddress();

        String mucUserOccupantId();

        static IdentifiableUser realAddress(final Jid address) {
            return new IdentifiableUser() {
                @Override
                public Jid mucUserAddress() {
                    return null;
                }

                @Override
                public Jid mucUserRealAddress() {
                    return address;
                }

                @Override
                public String mucUserOccupantId() {
                    return null;
                }
            };
        }
    }
}
