package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;
import android.util.Pair;

import eu.siacs.conversations.crypto.PgpDecryptionService;

import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrService;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class Account extends AbstractEntity {

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

	public static final String PINNED_MECHANISM_KEY = "pinned_mechanism";

	public static final int OPTION_USETLS = 0;
	public static final int OPTION_DISABLED = 1;
	public static final int OPTION_REGISTER = 2;
	public static final int OPTION_USECOMPRESSION = 3;
	public static final int OPTION_MAGIC_CREATE = 4;
	public static final int OPTION_REQUIRES_ACCESS_MODE_CHANGE = 5;
	public static final int OPTION_LOGGED_IN_SUCCESSFULLY = 6;
	public final HashSet<Pair<String, String>> inProgressDiscoFetches = new HashSet<>();

	public boolean httpUploadAvailable(long filesize) {
		return xmppConnection != null && xmppConnection.getFeatures().httpUpload(filesize);
	}

	public boolean httpUploadAvailable() {
		return httpUploadAvailable(0);
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public XmppConnection.Identity getServerIdentity() {
		if (xmppConnection == null) {
			return XmppConnection.Identity.UNKNOWN;
		} else {
			return xmppConnection.getServerIdentity();
		}
	}

	public Contact getSelfContact() {
		return getRoster().getContact(jid);
	}

	public boolean hasPendingPgpIntent(Conversation conversation) {
		return pgpDecryptionService != null && pgpDecryptionService.hasPendingIntent(conversation);
	}

	public boolean isPgpDecryptionServiceConnected() {
		return pgpDecryptionService != null && pgpDecryptionService.isConnected();
	}

	public boolean setShowErrorNotification(boolean newValue) {
		boolean oldValue = showErrorNotification();
		setKey("show_error",Boolean.toString(newValue));
		return newValue != oldValue;
	}

	public boolean showErrorNotification() {
		String key = getKey("show_error");
		return key == null || Boolean.parseBoolean(key);
	}

	public enum State {
		DISABLED,
		OFFLINE,
		CONNECTING,
		ONLINE,
		NO_INTERNET,
		UNAUTHORIZED(true),
		SERVER_NOT_FOUND(true),
		REGISTRATION_FAILED(true),
		REGISTRATION_WEB(true),
		REGISTRATION_CONFLICT(true),
		REGISTRATION_SUCCESSFUL,
		REGISTRATION_NOT_SUPPORTED(true),
		TLS_ERROR(true),
		INCOMPATIBLE_SERVER(true),
		TOR_NOT_AVAILABLE(true),
		DOWNGRADE_ATTACK(true),
		SESSION_FAILURE(true),
		BIND_FAILURE(true),
		HOST_UNKNOWN(true),
		REGISTRATION_PLEASE_WAIT(true),
		STREAM_ERROR(true),
		POLICY_VIOLATION(true),
		REGISTRATION_PASSWORD_TOO_WEAK(true),
		PAYMENT_REQUIRED(true),
		MISSING_INTERNET_PERMISSION(true),
		NETWORK_IS_UNREACHABLE(false);

		private final boolean isError;

		public boolean isError() {
			return this.isError;
		}

		State(final boolean isError) {
			this.isError = isError;
		}

		State() {
			this(false);
		}

		public int getReadableId() {
			switch (this) {
				case DISABLED:
					return R.string.account_status_disabled;
				case ONLINE:
					return R.string.account_status_online;
				case CONNECTING:
					return R.string.account_status_connecting;
				case OFFLINE:
					return R.string.account_status_offline;
				case UNAUTHORIZED:
					return R.string.account_status_unauthorized;
				case SERVER_NOT_FOUND:
					return R.string.account_status_not_found;
				case NO_INTERNET:
					return R.string.account_status_no_internet;
				case REGISTRATION_FAILED:
					return R.string.account_status_regis_fail;
				case REGISTRATION_WEB:
					return R.string.account_status_regis_web;
				case REGISTRATION_CONFLICT:
					return R.string.account_status_regis_conflict;
				case REGISTRATION_SUCCESSFUL:
					return R.string.account_status_regis_success;
				case REGISTRATION_NOT_SUPPORTED:
					return R.string.account_status_regis_not_sup;
				case TLS_ERROR:
					return R.string.account_status_tls_error;
				case INCOMPATIBLE_SERVER:
					return R.string.account_status_incompatible_server;
				case TOR_NOT_AVAILABLE:
					return R.string.account_status_tor_unavailable;
				case BIND_FAILURE:
					return R.string.account_status_bind_failure;
				case SESSION_FAILURE:
					return R.string.session_failure;
				case DOWNGRADE_ATTACK:
					return R.string.sasl_downgrade;
				case HOST_UNKNOWN:
					return R.string.account_status_host_unknown;
				case POLICY_VIOLATION:
					return R.string.account_status_policy_violation;
				case REGISTRATION_PLEASE_WAIT:
					return R.string.registration_please_wait;
				case REGISTRATION_PASSWORD_TOO_WEAK:
					return R.string.registration_password_too_weak;
				case STREAM_ERROR:
					return R.string.account_status_stream_error;
				case PAYMENT_REQUIRED:
					return R.string.payment_required;
				case MISSING_INTERNET_PERMISSION:
					return R.string.missing_internet_permission;
				case NETWORK_IS_UNREACHABLE:
					return R.string.network_is_unreachable;
				default:
					return R.string.account_status_unknown;
			}
		}
	}

	public List<Conversation> pendingConferenceJoins = new CopyOnWriteArrayList<>();
	public List<Conversation> pendingConferenceLeaves = new CopyOnWriteArrayList<>();

	private static final String KEY_PGP_SIGNATURE = "pgp_signature";
	private static final String KEY_PGP_ID = "pgp_id";

	protected Jid jid;
	protected String password;
	protected int options = 0;
	protected String rosterVersion;
	protected State status = State.OFFLINE;
	protected final JSONObject keys;
	protected String avatar;
	protected String displayName = null;
	protected String hostname = null;
	protected int port = 5222;
	protected boolean online = false;
	private OtrService mOtrService = null;
	private AxolotlService axolotlService = null;
	private PgpDecryptionService pgpDecryptionService = null;
	private XmppConnection xmppConnection = null;
	private long mEndGracePeriod = 0L;
	private String otrFingerprint;
	private final Roster roster = new Roster(this);
	private List<Bookmark> bookmarks = new CopyOnWriteArrayList<>();
	private final Collection<Jid> blocklist = new CopyOnWriteArraySet<>();
	private Presence.Status presenceStatus = Presence.Status.ONLINE;
	private String presenceStatusMessage = null;

	public Account(final Jid jid, final String password) {
		this(java.util.UUID.randomUUID().toString(), jid,
				password, 0, null, "", null, null, null, 5222, Presence.Status.ONLINE, null);
	}

	private Account(final String uuid, final Jid jid,
					final String password, final int options, final String rosterVersion, final String keys,
					final String avatar, String displayName, String hostname, int port,
					final Presence.Status status, String statusMessage) {
		this.uuid = uuid;
		this.jid = jid;
		if (jid.isBareJid()) {
			this.setResource("mobile");
		}
		this.password = password;
		this.options = options;
		this.rosterVersion = rosterVersion;
		JSONObject tmp;
		try {
			tmp = new JSONObject(keys);
		} catch(JSONException e) {
			tmp = new JSONObject();
		}
		this.keys = tmp;
		this.avatar = avatar;
		this.displayName = displayName;
		this.hostname = hostname;
		this.port = port;
		this.presenceStatus = status;
		this.presenceStatusMessage = statusMessage;
	}

	public static Account fromCursor(final Cursor cursor) {
		Jid jid = null;
		try {
			jid = Jid.fromParts(cursor.getString(cursor.getColumnIndex(USERNAME)),
					cursor.getString(cursor.getColumnIndex(SERVER)), "mobile");
		} catch (final InvalidJidException ignored) {
		}
		return new Account(cursor.getString(cursor.getColumnIndex(UUID)),
				jid,
				cursor.getString(cursor.getColumnIndex(PASSWORD)),
				cursor.getInt(cursor.getColumnIndex(OPTIONS)),
				cursor.getString(cursor.getColumnIndex(ROSTERVERSION)),
				cursor.getString(cursor.getColumnIndex(KEYS)),
				cursor.getString(cursor.getColumnIndex(AVATAR)),
				cursor.getString(cursor.getColumnIndex(DISPLAY_NAME)),
				cursor.getString(cursor.getColumnIndex(HOSTNAME)),
				cursor.getInt(cursor.getColumnIndex(PORT)),
				Presence.Status.fromShowString(cursor.getString(cursor.getColumnIndex(STATUS))),
				cursor.getString(cursor.getColumnIndex(STATUS_MESSAGE)));
	}

	public boolean isOptionSet(final int option) {
		return ((options & (1 << option)) != 0);
	}

	public boolean setOption(final int option, final boolean value) {
		final int before = this.options;
		if (value) {
			this.options |= 1 << option;
		} else {
			this.options &= ~(1 << option);
		}
		return before != this.options;
	}

	public String getUsername() {
		return jid.getLocalpart();
	}

	public boolean setJid(final Jid next) {
		final Jid prev = this.jid != null ? this.jid.toBareJid() : null;
		this.jid = next;
		return prev == null || (next != null && !prev.equals(next.toBareJid()));
	}

	public Jid getServer() {
		return jid.toDomainJid();
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(final String password) {
		this.password = password;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getHostname() {
		return this.hostname == null ? "" : this.hostname;
	}

	public boolean isOnion() {
		final Jid server = getServer();
		return server != null && server.toString().toLowerCase().endsWith(".onion");
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getPort() {
		return this.port;
	}

	public State getStatus() {
		if (isOptionSet(OPTION_DISABLED)) {
			return State.DISABLED;
		} else {
			return this.status;
		}
	}

	public State getTrueStatus() {
		return this.status;
	}

	public void setStatus(final State status) {
		this.status = status;
	}

	public boolean errorStatus() {
		return getStatus().isError();
	}

	public boolean hasErrorStatus() {
		return getXmppConnection() != null
				&& (getStatus().isError() || getStatus() == State.CONNECTING)
				&& getXmppConnection().getAttempt() >= 3;
	}

	public void setPresenceStatus(Presence.Status status) {
		this.presenceStatus = status;
	}

	public Presence.Status getPresenceStatus() {
		return this.presenceStatus;
	}

	public void setPresenceStatusMessage(String message) {
		this.presenceStatusMessage = message;
	}

	public String getPresenceStatusMessage() {
		return this.presenceStatusMessage;
	}

	public String getResource() {
		return jid.getResourcepart();
	}

	public boolean setResource(final String resource) {
		final String oldResource = jid.getResourcepart();
		if (oldResource == null || !oldResource.equals(resource)) {
			try {
				jid = Jid.fromParts(jid.getLocalpart(), jid.getDomainpart(), resource);
				return true;
			} catch (final InvalidJidException ignored) {
				return true;
			}
		}
		return false;
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

	public boolean setPrivateKeyAlias(String alias) {
		return setKey("private_key_alias", alias);
	}

	public String getPrivateKeyAlias() {
		return getKey("private_key_alias");
	}

	@Override
	public ContentValues getContentValues() {
		final ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(USERNAME, jid.getLocalpart());
		values.put(SERVER, jid.getDomainpart());
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
		return values;
	}

	public AxolotlService getAxolotlService() {
		return axolotlService;
	}

	public void initAccountServices(final XmppConnectionService context) {
		this.mOtrService = new OtrService(context, this);
		this.axolotlService = new AxolotlService(this, context);
		this.pgpDecryptionService = new PgpDecryptionService(context);
		if (xmppConnection != null) {
			xmppConnection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService);
		}
	}

	public OtrService getOtrService() {
		return this.mOtrService;
	}

	public PgpDecryptionService getPgpDecryptionService() {
		return this.pgpDecryptionService;
	}

	public XmppConnection getXmppConnection() {
		return this.xmppConnection;
	}

	public void setXmppConnection(final XmppConnection connection) {
		this.xmppConnection = connection;
	}

	public String getOtrFingerprint() {
		if (this.otrFingerprint == null) {
			try {
				if (this.mOtrService == null) {
					return null;
				}
				final PublicKey publicKey = this.mOtrService.getPublicKey();
				if (publicKey == null || !(publicKey instanceof DSAPublicKey)) {
					return null;
				}
				this.otrFingerprint = new OtrCryptoEngineImpl().getFingerprint(publicKey).toLowerCase(Locale.US);
				return this.otrFingerprint;
			} catch (final OtrCryptoException ignored) {
				return null;
			}
		} else {
			return this.otrFingerprint;
		}
	}

	public String getRosterVersion() {
		if (this.rosterVersion == null) {
			return "";
		} else {
			return this.rosterVersion;
		}
	}

	public void setRosterVersion(final String version) {
		this.rosterVersion = version;
	}

	public int countPresences() {
		return this.getSelfContact().getPresences().size();
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
		return this.roster;
	}

	public List<Bookmark> getBookmarks() {
		return this.bookmarks;
	}

	public void setBookmarks(final List<Bookmark> bookmarks) {
		this.bookmarks = bookmarks;
	}

	public boolean hasBookmarkFor(final Jid conferenceJid) {
		for (final Bookmark bookmark : this.bookmarks) {
			final Jid jid = bookmark.getJid();
			if (jid != null && jid.equals(conferenceJid.toBareJid())) {
				return true;
			}
		}
		return false;
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

	public void activateGracePeriod(long duration) {
		this.mEndGracePeriod = SystemClock.elapsedRealtime() + duration;
	}

	public void deactivateGracePeriod() {
		this.mEndGracePeriod = 0L;
	}

	public boolean inGracePeriod() {
		return SystemClock.elapsedRealtime() < this.mEndGracePeriod;
	}

	public String getShareableUri() {
		List<XmppUri.Fingerprint> fingerprints = this.getFingerprints();
		String uri = "xmpp:"+this.getJid().toBareJid().toString();
		if (fingerprints.size() > 0) {
			return XmppUri.getFingerprintUri(uri,fingerprints,';');
		} else {
			return uri;
		}
	}

	public String getShareableLink() {
		List<XmppUri.Fingerprint> fingerprints = this.getFingerprints();
		String uri = "https://conversations.im/i/"+this.getJid().toBareJid().toString();
		if (fingerprints.size() > 0) {
			return XmppUri.getFingerprintUri(uri,fingerprints,'&');
		} else {
			return uri;
		}
	}

	private List<XmppUri.Fingerprint> getFingerprints() {
		ArrayList<XmppUri.Fingerprint> fingerprints = new ArrayList<>();
		final String otr = this.getOtrFingerprint();
		if (otr != null) {
			fingerprints.add(new XmppUri.Fingerprint(XmppUri.FingerprintType.OTR,otr));
		}
		if (axolotlService == null) {
			return fingerprints;
		}
		fingerprints.add(new XmppUri.Fingerprint(XmppUri.FingerprintType.OMEMO,axolotlService.getOwnFingerprint().substring(2),axolotlService.getOwnDeviceId()));
		for(XmppAxolotlSession session : axolotlService.findOwnSessions()) {
			if (session.getTrust().isVerified() && session.getTrust().isActive()) {
				fingerprints.add(new XmppUri.Fingerprint(XmppUri.FingerprintType.OMEMO,session.getFingerprint().substring(2).replaceAll("\\s",""),session.getRemoteAddress().getDeviceId()));
			}
		}
		return fingerprints;
	}

	public boolean isBlocked(final ListItem contact) {
		final Jid jid = contact.getJid();
		return jid != null && (blocklist.contains(jid.toBareJid()) || blocklist.contains(jid.toDomainJid()));
	}

	public boolean isBlocked(final Jid jid) {
		return jid != null && blocklist.contains(jid.toBareJid());
	}

	public Collection<Jid> getBlocklist() {
		return this.blocklist;
	}

	public void clearBlocklist() {
		getBlocklist().clear();
	}

	public boolean isOnlineAndConnected() {
		return this.getStatus() == State.ONLINE && this.getXmppConnection() != null;
	}
}
