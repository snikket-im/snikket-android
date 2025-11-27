package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpDecryptionService;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.crypto.sasl.ChannelBinding;
import eu.siacs.conversations.crypto.sasl.ChannelBindingMechanism;
import eu.siacs.conversations.crypto.sasl.HashedToken;
import eu.siacs.conversations.crypto.sasl.HashedTokenSha256;
import eu.siacs.conversations.crypto.sasl.HashedTokenSha512;
import eu.siacs.conversations.crypto.sasl.SaslMechanism;
import eu.siacs.conversations.http.ServiceOutageStatus;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.utils.Resolver;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.RtpCapability;
import eu.siacs.conversations.xmpp.manager.BlockingManager;
import eu.siacs.conversations.xmpp.manager.HttpUploadManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

public class Account extends AbstractEntity implements AvatarService.Avatar {

    public static final String TABLENAME = "accounts";

    public static final String USERNAME = "username";
    public static final String SERVER = "server";
    public static final String PASSWORD = "password";
    public static final String OPTIONS = "options";
    public static final String ROSTERVERSION = "rosterversion";
    public static final String KEYS = "keys";
    public static final String AVATAR = "avatar";
    public static final String DISPLAY_NAME = "display_name";
    public static final String HOSTNAME = "hostname";
    public static final String PORT = "port";
    public static final String STATUS = "status";
    public static final String STATUS_MESSAGE = "status_message";
    public static final String RESOURCE = "resource";
    public static final String PINNED_MECHANISM = "pinned_mechanism";
    public static final String PINNED_CHANNEL_BINDING = "pinned_channel_binding";
    public static final String FAST_MECHANISM = "fast_mechanism";
    public static final String FAST_TOKEN = "fast_token";

    public static final int OPTION_DISABLED = 1;
    public static final int OPTION_REGISTER = 2;
    public static final int OPTION_MAGIC_CREATE = 4;
    public static final int OPTION_REQUIRES_ACCESS_MODE_CHANGE = 5;
    public static final int OPTION_LOGGED_IN_SUCCESSFULLY = 6;
    public static final int OPTION_HTTP_UPLOAD_AVAILABLE = 7;
    public static final int OPTION_UNVERIFIED = 8;
    public static final int OPTION_FIXED_USERNAME = 9;
    public static final int OPTION_QUICKSTART_AVAILABLE = 10;
    public static final int OPTION_SOFT_DISABLED = 11;

    private static final String KEY_PGP_SIGNATURE = "pgp_signature";
    private static final String KEY_PGP_ID = "pgp_id";
    private static final String KEY_PINNED_MECHANISM = "pinned_mechanism";
    public static final String KEY_SOS_URL = "sos_url";
    public static final String KEY_PRE_AUTH_REGISTRATION_TOKEN = "pre_auth_registration";
    protected final JSONObject keys;
    protected Jid jid;
    protected String password;
    protected int options = 0;
    protected State status = State.OFFLINE;
    private State lastErrorStatus = State.OFFLINE;
    protected String resource;
    protected String avatar;
    protected String hostname = null;
    protected int port = 5222;
    protected boolean online = false;
    private String rosterVersion;
    private String displayName = null;
    private XmppConnection xmppConnection = null;
    private long mEndGracePeriod = 0L;
    private im.conversations.android.xmpp.model.stanza.Presence.Availability presenceStatus;
    private String presenceStatusMessage;
    private String pinnedMechanism;
    private String pinnedChannelBinding;
    private String fastMechanism;
    private String fastToken;
    private ServiceOutageStatus serviceOutageStatus;

    public Account(final Jid jid, final String password) {
        this(
                java.util.UUID.randomUUID().toString(),
                jid,
                password,
                0,
                null,
                "",
                null,
                null,
                null,
                Resolver.XMPP_PORT_STARTTLS,
                im.conversations.android.xmpp.model.stanza.Presence.Availability.ONLINE,
                null,
                null,
                null,
                null,
                null);
    }

    private Account(
            final String uuid,
            final Jid jid,
            final String password,
            final int options,
            final String rosterVersion,
            final String keys,
            final String avatar,
            String displayName,
            String hostname,
            int port,
            final im.conversations.android.xmpp.model.stanza.Presence.Availability status,
            String statusMessage,
            final String pinnedMechanism,
            final String pinnedChannelBinding,
            final String fastMechanism,
            final String fastToken) {
        this.uuid = uuid;
        this.jid = jid;
        this.password = password;
        this.options = options;
        this.rosterVersion = rosterVersion;
        this.keys = parseKeys(keys);
        this.avatar = avatar;
        this.displayName = displayName;
        this.hostname = hostname;
        this.port = port;
        this.presenceStatus = status;
        this.presenceStatusMessage = statusMessage;
        this.pinnedMechanism = pinnedMechanism;
        this.pinnedChannelBinding = pinnedChannelBinding;
        this.fastMechanism = fastMechanism;
        this.fastToken = fastToken;
    }

    public static JSONObject parseKeys(final String keys) {
        if (Strings.isNullOrEmpty(keys)) {
            return new JSONObject();
        }
        try {
            return new JSONObject(keys);
        } catch (final JSONException e) {
            return new JSONObject();
        }
    }

    public static Account fromCursor(final Cursor cursor) {
        final Jid jid;
        try {
            final String resource = cursor.getString(cursor.getColumnIndexOrThrow(RESOURCE));
            jid =
                    Jid.of(
                            cursor.getString(cursor.getColumnIndexOrThrow(USERNAME)),
                            cursor.getString(cursor.getColumnIndexOrThrow(SERVER)),
                            resource == null || resource.trim().isEmpty() ? null : resource);
        } catch (final IllegalArgumentException e) {
            Log.d(
                    Config.LOGTAG,
                    cursor.getString(cursor.getColumnIndexOrThrow(USERNAME))
                            + "@"
                            + cursor.getString(cursor.getColumnIndexOrThrow(SERVER)));
            throw new AssertionError(e);
        }
        return new Account(
                cursor.getString(cursor.getColumnIndexOrThrow(UUID)),
                jid,
                cursor.getString(cursor.getColumnIndexOrThrow(PASSWORD)),
                cursor.getInt(cursor.getColumnIndexOrThrow(OPTIONS)),
                cursor.getString(cursor.getColumnIndexOrThrow(ROSTERVERSION)),
                cursor.getString(cursor.getColumnIndexOrThrow(KEYS)),
                cursor.getString(cursor.getColumnIndexOrThrow(AVATAR)),
                cursor.getString(cursor.getColumnIndexOrThrow(DISPLAY_NAME)),
                cursor.getString(cursor.getColumnIndexOrThrow(HOSTNAME)),
                cursor.getInt(cursor.getColumnIndexOrThrow(PORT)),
                im.conversations.android.xmpp.model.stanza.Presence.Availability.valueOfShown(
                        cursor.getString(cursor.getColumnIndexOrThrow(STATUS))),
                cursor.getString(cursor.getColumnIndexOrThrow(STATUS_MESSAGE)),
                cursor.getString(cursor.getColumnIndexOrThrow(PINNED_MECHANISM)),
                cursor.getString(cursor.getColumnIndexOrThrow(PINNED_CHANNEL_BINDING)),
                cursor.getString(cursor.getColumnIndexOrThrow(FAST_MECHANISM)),
                cursor.getString(cursor.getColumnIndexOrThrow(FAST_TOKEN)));
    }

    // TODO remove this method and call HttpUploadManager directly i
    public boolean httpUploadAvailable(final long fileSize) {
        return xmppConnection.getManager(HttpUploadManager.class).isAvailableForSize(fileSize);
    }

    public boolean httpUploadAvailable() {
        return isOptionSet(OPTION_HTTP_UPLOAD_AVAILABLE)
                || xmppConnection.getManager(HttpUploadManager.class).isAvailableForSize(0);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Contact getSelfContact() {
        return getRoster().getContact(jid);
    }

    public boolean hasPendingPgpIntent(Conversation conversation) {
        return getPgpDecryptionService().hasPendingIntent(conversation);
    }

    public boolean isPgpDecryptionServiceConnected() {
        return getPgpDecryptionService().isConnected();
    }

    public boolean setShowErrorNotification(boolean newValue) {
        boolean oldValue = showErrorNotification();
        setKey("show_error", Boolean.toString(newValue));
        return newValue != oldValue;
    }

    public boolean showErrorNotification() {
        String key = getKey("show_error");
        return key == null || Boolean.parseBoolean(key);
    }

    public boolean isEnabled() {
        return !isOptionSet(Account.OPTION_DISABLED);
    }

    public boolean isConnectionEnabled() {
        return !isOptionSet(Account.OPTION_DISABLED) && !isOptionSet(Account.OPTION_SOFT_DISABLED);
    }

    public boolean isOptionSet(final int option) {
        return ((options & (1 << option)) != 0);
    }

    public boolean setOption(final int option, final boolean value) {
        if (value && (option == OPTION_DISABLED || option == OPTION_SOFT_DISABLED)) {
            this.setStatus(State.OFFLINE);
        }
        final int before = this.options;
        if (value) {
            this.options |= 1 << option;
        } else {
            this.options &= ~(1 << option);
        }
        return before != this.options;
    }

    public String getUsername() {
        return jid.getLocal();
    }

    public boolean setJid(final Jid next) {
        final Jid previousFull = this.jid;
        final Jid prev = this.jid != null ? this.jid.asBareJid() : null;
        final boolean changed = prev == null || (next != null && !prev.equals(next.asBareJid()));
        if (changed) {
            final AxolotlService oldAxolotlService = xmppConnection.getAxolotlService();
            // TODO check that changing JID and recreating the AxolotlService still works
            if (oldAxolotlService != null) {
                oldAxolotlService.destroy();
                this.jid = next;
                xmppConnection.setAxolotlService(oldAxolotlService.makeNew());
            }
        }
        this.jid = next;
        return next != null && !next.equals(previousFull);
    }

    public Jid getDomain() {
        return jid.getDomain();
    }

    public String getServer() {
        return jid.getDomain().toString();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    @NonNull
    public String getHostname() {
        return Strings.nullToEmpty(this.hostname);
    }

    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    public boolean isOnion() {
        final String server = getServer();
        return server != null && server.endsWith(".onion");
    }

    public boolean isDirectToOnion() {
        final var hostname = Strings.nullToEmpty(this.hostname).trim();
        return isOnion() && (hostname.isEmpty() || hostname.endsWith(".onion"));
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public State getStatus() {
        if (isOptionSet(OPTION_DISABLED)) {
            return State.DISABLED;
        } else if (isOptionSet(OPTION_SOFT_DISABLED)) {
            return State.LOGGED_OUT;
        } else {
            return this.status;
        }
    }

    public boolean unauthorized() {
        return this.status == State.UNAUTHORIZED || this.lastErrorStatus == State.UNAUTHORIZED;
    }

    public State getLastErrorStatus() {
        return this.lastErrorStatus;
    }

    public void setStatus(final State status) {
        this.status = status;
        if (status.isError || status == State.ONLINE) {
            this.lastErrorStatus = status;
        }
    }

    public void setPinnedMechanism(final SaslMechanism mechanism) {
        this.pinnedMechanism = mechanism.getMechanism();
        if (mechanism instanceof ChannelBindingMechanism) {
            this.pinnedChannelBinding =
                    ((ChannelBindingMechanism) mechanism).getChannelBinding().toString();
        } else {
            this.pinnedChannelBinding = null;
        }
    }

    public void setFastToken(final HashedToken.Mechanism mechanism, final String token) {
        this.fastMechanism = mechanism.name();
        this.fastToken = token;
    }

    public void resetFastToken() {
        this.fastMechanism = null;
        this.fastToken = null;
    }

    public void resetPinnedMechanism() {
        this.pinnedMechanism = null;
        this.pinnedChannelBinding = null;
        setKey(Account.KEY_PINNED_MECHANISM, String.valueOf(-1));
    }

    public int getPinnedMechanismPriority() {
        final int fallback = getKeyAsInt(KEY_PINNED_MECHANISM, -1);
        if (Strings.isNullOrEmpty(this.pinnedMechanism)) {
            return fallback;
        }
        final SaslMechanism saslMechanism = getPinnedMechanism();
        if (saslMechanism == null) {
            return fallback;
        } else {
            return saslMechanism.getPriority();
        }
    }

    private SaslMechanism getPinnedMechanism() {
        final String mechanism = Strings.nullToEmpty(this.pinnedMechanism);
        final ChannelBinding channelBinding = ChannelBinding.get(this.pinnedChannelBinding);
        return new SaslMechanism.Factory(this).of(mechanism, channelBinding);
    }

    public HashedToken getFastMechanism() {
        final HashedToken.Mechanism fastMechanism =
                HashedToken.Mechanism.ofOrNull(this.fastMechanism);
        final String token = this.fastToken;
        if (fastMechanism == null || Strings.isNullOrEmpty(token)) {
            return null;
        }
        if (fastMechanism.hashFunction.equals("SHA-256")) {
            return new HashedTokenSha256(this, fastMechanism.channelBinding);
        } else if (fastMechanism.hashFunction.equals("SHA-512")) {
            return new HashedTokenSha512(this, fastMechanism.channelBinding);
        } else {
            return null;
        }
    }

    public SaslMechanism getQuickStartMechanism() {
        final HashedToken hashedTokenMechanism = getFastMechanism();
        if (hashedTokenMechanism != null) {
            return hashedTokenMechanism;
        }
        return getPinnedMechanism();
    }

    public String getFastToken() {
        return this.fastToken;
    }

    public State getTrueStatus() {
        return this.status;
    }

    public boolean errorStatus() {
        return getStatus().isError();
    }

    public boolean hasErrorStatus() {
        return getXmppConnection() != null
                && (getStatus().isError() || getStatus() == State.CONNECTING)
                && getXmppConnection().getAttempt() >= 3;
    }

    public im.conversations.android.xmpp.model.stanza.Presence.Availability getPresenceStatus() {
        return this.presenceStatus;
    }

    public void setPresenceStatus(
            im.conversations.android.xmpp.model.stanza.Presence.Availability status) {
        this.presenceStatus = status;
    }

    public String getPresenceStatusMessage() {
        return this.presenceStatusMessage;
    }

    public void setPresenceStatusMessage(String message) {
        this.presenceStatusMessage = message;
    }

    public String getResource() {
        return jid.getResource();
    }

    public void setResource(final String resource) {
        this.jid = this.jid.withResource(resource);
    }

    public Jid getJid() {
        return jid;
    }

    public JSONObject getKeys() {
        return keys;
    }

    public String getKey(final String name) {
        synchronized (this.keys) {
            return this.keys.optString(name, null);
        }
    }

    public int getKeyAsInt(final String name, int defaultValue) {
        String key = getKey(name);
        try {
            return key == null ? defaultValue : Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean setKey(final String keyName, final String keyValue) {
        synchronized (this.keys) {
            try {
                this.keys.put(keyName, keyValue);
                return true;
            } catch (final JSONException e) {
                return false;
            }
        }
    }

    public void setPrivateKeyAlias(final String alias) {
        setKey("private_key_alias", alias);
    }

    public String getPrivateKeyAlias() {
        return getKey("private_key_alias");
    }

    @Override
    public ContentValues getContentValues() {
        final ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(USERNAME, jid.getLocal());
        values.put(SERVER, jid.getDomain().toString());
        values.put(PASSWORD, password);
        values.put(OPTIONS, options);
        synchronized (this.keys) {
            values.put(KEYS, this.keys.toString());
        }
        values.put(ROSTERVERSION, rosterVersion);
        values.put(AVATAR, avatar);
        values.put(DISPLAY_NAME, displayName);
        values.put(HOSTNAME, hostname);
        values.put(PORT, port);
        values.put(STATUS, presenceStatus.toShowString());
        values.put(STATUS_MESSAGE, presenceStatusMessage);
        values.put(RESOURCE, jid.getResource());
        values.put(PINNED_MECHANISM, pinnedMechanism);
        values.put(PINNED_CHANNEL_BINDING, pinnedChannelBinding);
        values.put(FAST_MECHANISM, this.fastMechanism);
        values.put(FAST_TOKEN, this.fastToken);
        return values;
    }

    public AxolotlService getAxolotlService() {
        return this.xmppConnection.getAxolotlService();
    }

    public PgpDecryptionService getPgpDecryptionService() {
        return this.xmppConnection.getPgpDecryptionService();
    }

    public XmppConnection getXmppConnection() {
        return this.xmppConnection;
    }

    public String getRosterVersion() {
        return Strings.emptyToNull(this.rosterVersion);
    }

    public void setRosterVersion(final String version) {
        this.rosterVersion = version;
    }

    public int countPresences() {
        return this.getSelfContact().getPresences().size();
    }

    public int activeDevicesWithRtpCapability() {
        final var connection = getXmppConnection();
        if (connection == null) {
            return 0;
        }
        int i = 0;
        for (final var optionalInfoQuery : getSelfContact().getCapabilities()) {
            if (RtpCapability.check(optionalInfoQuery.orNull()) != RtpCapability.Capability.NONE) {
                i++;
            }
        }
        return i;
    }

    public String getPgpSignature() {
        return getKey(KEY_PGP_SIGNATURE);
    }

    public boolean setPgpSignature(String signature) {
        return setKey(KEY_PGP_SIGNATURE, signature);
    }

    public boolean unsetPgpSignature() {
        synchronized (this.keys) {
            return keys.remove(KEY_PGP_SIGNATURE) != null;
        }
    }

    public long getPgpId() {
        synchronized (this.keys) {
            if (keys.has(KEY_PGP_ID)) {
                try {
                    return keys.getLong(KEY_PGP_ID);
                } catch (JSONException e) {
                    return 0;
                }
            } else {
                return 0;
            }
        }
    }

    public boolean setPgpSignId(long pgpID) {
        synchronized (this.keys) {
            try {
                if (pgpID == 0) {
                    keys.remove(KEY_PGP_ID);
                } else {
                    keys.put(KEY_PGP_ID, pgpID);
                }
            } catch (JSONException e) {
                return false;
            }
            return true;
        }
    }

    public Roster getRoster() {
        return xmppConnection.getManager(RosterManager.class);
    }

    public boolean setAvatar(final String filename) {
        if (this.avatar != null && this.avatar.equals(filename)) {
            return false;
        } else {
            this.avatar = filename;
            return true;
        }
    }

    public String getAvatar() {
        return this.avatar;
    }

    public void activateGracePeriod(final long duration) {
        if (duration > 0) {
            this.mEndGracePeriod = SystemClock.elapsedRealtime() + duration;
        }
    }

    public void deactivateGracePeriod() {
        this.mEndGracePeriod = 0L;
    }

    public boolean inGracePeriod() {
        return SystemClock.elapsedRealtime() < this.mEndGracePeriod;
    }

    public String getShareableUri() {
        List<XmppUri.Fingerprint> fingerprints = this.getFingerprints();
        final String uri = "xmpp:" + this.getJid().asBareJid().toString();
        if (fingerprints.isEmpty()) {
            return uri;
        } else {
            return XmppUri.getFingerprintUri(uri, fingerprints, ';');
        }
    }

    public String getShareableLink() {
        List<XmppUri.Fingerprint> fingerprints = this.getFingerprints();
        String uri =
                "https://conversations.im/i/"
                        + XmppUri.lameUrlEncode(this.getJid().asBareJid().toString());
        if (fingerprints.isEmpty()) {
            return uri;
        } else {
            return XmppUri.getFingerprintUri(uri, fingerprints, '&');
        }
    }

    private List<XmppUri.Fingerprint> getFingerprints() {
        ArrayList<XmppUri.Fingerprint> fingerprints = new ArrayList<>();
        final var axolotlService = getAxolotlService();
        fingerprints.add(
                new XmppUri.Fingerprint(
                        XmppUri.FingerprintType.OMEMO,
                        axolotlService.getOwnFingerprint().substring(2),
                        axolotlService.getOwnDeviceId()));
        for (XmppAxolotlSession session : axolotlService.findOwnSessions()) {
            if (session.getTrust().isVerified() && session.getTrust().isActive()) {
                fingerprints.add(
                        new XmppUri.Fingerprint(
                                XmppUri.FingerprintType.OMEMO,
                                session.getFingerprint().substring(2).replaceAll("\\s", ""),
                                session.getRemoteAddress().getDeviceId()));
            }
        }
        return fingerprints;
    }

    public boolean isBlocked(final ListItem contact) {
        final Jid jid = contact.getAddress();
        final var blocklist = getBlocklist();
        return jid != null
                && (blocklist.contains(jid.asBareJid()) || blocklist.contains(jid.getDomain()));
    }

    public boolean isBlocked(final Jid jid) {
        final var blocklist = getBlocklist();
        return jid != null && blocklist.contains(jid.asBareJid());
    }

    // TODO get rid of this method in favor of calling manager directly
    public Set<Jid> getBlocklist() {
        final var connection = this.xmppConnection;
        if (connection == null) {
            return Collections.emptySet();
        }
        return connection.getManager(BlockingManager.class).getBlocklist();
    }

    public boolean isOnlineAndConnected() {
        return this.getStatus() == State.ONLINE && this.getXmppConnection() != null;
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(jid.asBareJid().toString());
    }

    public void setServiceOutageStatus(final ServiceOutageStatus sos) {
        this.serviceOutageStatus = sos;
    }

    public ServiceOutageStatus getServiceOutageStatus() {
        return this.serviceOutageStatus;
    }

    public boolean isServiceOutage() {
        final var sos = this.serviceOutageStatus;
        if (sos != null
                && isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)
                && ServiceOutageStatus.isPossibleOutage(this.status)) {
            return sos.isNow();
        }
        return false;
    }

    public void setXmppConnection(final XmppConnection connection) {
        this.xmppConnection = connection;
    }

    public enum State {
        DISABLED(false, false),
        LOGGED_OUT(false, false),
        OFFLINE(false),
        CONNECTING(false),
        ONLINE(false),
        NO_INTERNET(false),
        CONNECTION_TIMEOUT,
        UNAUTHORIZED,
        TEMPORARY_AUTH_FAILURE,
        SERVER_NOT_FOUND,
        REGISTRATION_SUCCESSFUL(false),
        REGISTRATION_FAILED(true, false),
        REGISTRATION_WEB(true, false),
        REGISTRATION_CONFLICT(true, false),
        REGISTRATION_NOT_SUPPORTED(true, false),
        REGISTRATION_PLEASE_WAIT(true, false),
        REGISTRATION_INVALID_TOKEN(true, false),
        REGISTRATION_PASSWORD_TOO_WEAK(true, false),
        TLS_ERROR,
        TLS_ERROR_DOMAIN,
        TLS_ERROR_UNTRUSTED,
        TLS_ERROR_PROTOCOL,
        CHANNEL_BINDING,
        INCOMPATIBLE_SERVER,
        INCOMPATIBLE_CLIENT,
        TOR_NOT_AVAILABLE,
        DOWNGRADE_ATTACK,
        SESSION_FAILURE,
        BIND_FAILURE,
        HOST_UNKNOWN,
        STREAM_ERROR,
        SEE_OTHER_HOST,
        STREAM_OPENING_ERROR,
        POLICY_VIOLATION,
        PAYMENT_REQUIRED,
        MISSING_INTERNET_PERMISSION(false);

        private final boolean isError;
        private final boolean attemptReconnect;

        State(final boolean isError) {
            this(isError, true);
        }

        State(final boolean isError, final boolean reconnect) {
            this.isError = isError;
            this.attemptReconnect = reconnect;
        }

        State() {
            this(true, true);
        }

        public boolean isError() {
            return this.isError;
        }

        public boolean isAttemptReconnect() {
            return this.attemptReconnect;
        }

        public int getReadableId() {
            return switch (this) {
                case DISABLED -> R.string.account_status_disabled;
                case LOGGED_OUT -> R.string.account_state_logged_out;
                case ONLINE -> R.string.account_status_online;
                case CONNECTING -> R.string.account_status_connecting;
                case OFFLINE -> R.string.account_status_offline;
                case UNAUTHORIZED -> R.string.account_status_unauthorized;
                case SERVER_NOT_FOUND -> R.string.account_status_not_found;
                case NO_INTERNET -> R.string.account_status_no_internet;
                case CONNECTION_TIMEOUT -> R.string.account_status_connection_timeout;
                case REGISTRATION_FAILED -> R.string.account_status_regis_fail;
                case REGISTRATION_WEB -> R.string.account_status_regis_web;
                case REGISTRATION_CONFLICT -> R.string.account_status_regis_conflict;
                case REGISTRATION_SUCCESSFUL -> R.string.account_status_regis_success;
                case REGISTRATION_NOT_SUPPORTED -> R.string.account_status_regis_not_sup;
                case REGISTRATION_INVALID_TOKEN -> R.string.account_status_regis_invalid_token;
                case TLS_ERROR -> R.string.account_status_tls_error;
                case TLS_ERROR_DOMAIN -> R.string.account_status_tls_error_domain;
                case TLS_ERROR_PROTOCOL -> R.string.account_status_tls_error_protocol;
                case TLS_ERROR_UNTRUSTED -> R.string.account_status_tls_error_untrusted;
                case INCOMPATIBLE_SERVER -> R.string.account_status_incompatible_server;
                case INCOMPATIBLE_CLIENT -> R.string.account_status_incompatible_client;
                case CHANNEL_BINDING -> R.string.account_status_channel_binding;
                case TOR_NOT_AVAILABLE -> R.string.account_status_tor_unavailable;
                case BIND_FAILURE -> R.string.account_status_bind_failure;
                case SESSION_FAILURE -> R.string.session_failure;
                case DOWNGRADE_ATTACK -> R.string.sasl_downgrade;
                case HOST_UNKNOWN -> R.string.account_status_host_unknown;
                case POLICY_VIOLATION -> R.string.account_status_policy_violation;
                case REGISTRATION_PLEASE_WAIT -> R.string.registration_please_wait;
                case REGISTRATION_PASSWORD_TOO_WEAK -> R.string.registration_password_too_weak;
                case STREAM_ERROR -> R.string.account_status_stream_error;
                case STREAM_OPENING_ERROR -> R.string.account_status_stream_opening_error;
                case PAYMENT_REQUIRED -> R.string.payment_required;
                case SEE_OTHER_HOST -> R.string.reconnect_on_other_host;
                case MISSING_INTERNET_PERMISSION -> R.string.missing_internet_permission;
                case TEMPORARY_AUTH_FAILURE -> R.string.account_status_temporary_auth_failure;
                default -> R.string.account_status_unknown;
            };
        }
    }
}
