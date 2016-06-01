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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrService;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.services.XmppConnectionService;
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

	public enum State {
		DISABLED,
		OFFLINE,
		CONNECTING,
		ONLINE,
		NO_INTERNET,
		UNAUTHORIZED(true),
		SERVER_NOT_FOUND(true),
		REGISTRATION_FAILED(true),
		REGISTRATION_CONFLICT(true),
		REGISTRATION_SUCCESSFUL,
		REGISTRATION_NOT_SUPPORTED(true),
		SECURITY_ERROR(true),
		INCOMPATIBLE_SERVER(true),
		TOR_NOT_AVAILABLE(true),
		BIND_FAILURE(true),
		HOST_UNKNOWN(true),
		REGISTRATION_PLEASE_WAIT(true);

		private final boolean isError;

		public boolean isError() {
			return this.isError;
		}

		private State(final boolean isError) {
			this.isError = isError;
		}

		private State() {
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
				case REGISTRATION_CONFLICT:
					return R.string.account_status_regis_conflict;
				case REGISTRATION_SUCCESSFUL:
					return R.string.account_status_regis_success;
				case REGISTRATION_NOT_SUPPORTED:
					return R.string.account_status_regis_not_sup;
				case SECURITY_ERROR:
					return R.string.account_status_security_error;
				case INCOMPATIBLE_SERVER:
					return R.string.account_status_incompatible_server;
				case TOR_NOT_AVAILABLE:
					return R.string.account_status_tor_unavailable;
				case BIND_FAILURE:
					return R.string.account_status_bind_failure;
				case HOST_UNKNOWN:
					return R.string.account_status_host_unknown;
				case REGISTRATION_PLEASE_WAIT:
					return R.string.registration_please_wait;
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
	protected JSONObject keys = new JSONObject();
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
		try {
			this.keys = new JSONObject(keys);
		} catch (final JSONException ignored) {
			this.keys = new JSONObject();
		}
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

	public void setOption(final int option, final boolean value) {
		if (value) {
			this.options |= 1 << option;
		} else {
			this.options &= ~(1 << option);
		}
	}

	public String getUsername() {
		return jid.getLocalpart();
	}

	public void setJid(final Jid jid) {
		this.jid = jid;
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
		return getServer().toString().toLowerCase().endsWith(".onion");
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

	public void setStatus(final State status) {
		this.status = status;
	}

	public boolean errorStatus() {
		return getStatus().isError();
	}

	public boolean hasErrorStatus() {
		return getXmppConnection() != null && getStatus().isError() && getXmppConnection().getAttempt() >= 3;
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
		return this.keys.optString(name, null);
	}

	public boolean setKey(final String keyName, final String keyValue) {
		try {
			this.keys.put(keyName, keyValue);
			return true;
		} catch (final JSONException e) {
			return false;
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
		values.put(KEYS, this.keys.toString());
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
		if (xmppConnection != null) {
			xmppConnection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService);
		}
		this.pgpDecryptionService = new PgpDecryptionService(context);
	}

	public OtrService getOtrService() {
		return this.mOtrService;
	}

	public PgpDecryptionService getPgpDecryptionService() {
		return pgpDecryptionService;
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
				this.otrFingerprint = new OtrCryptoEngineImpl().getFingerprint(publicKey);
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
		try {
			if (keys.has(KEY_PGP_SIGNATURE) && !"null".equals(keys.getString(KEY_PGP_SIGNATURE))) {
				return keys.getString(KEY_PGP_SIGNATURE);
			} else {
				return null;
			}
		} catch (final JSONException e) {
			return null;
		}
	}

	public boolean setPgpSignature(String signature) {
		try {
			keys.put(KEY_PGP_SIGNATURE, signature);
		} catch (JSONException e) {
			return false;
		}
		return true;
	}

	public boolean unsetPgpSignature() {
		try {
			keys.put(KEY_PGP_SIGNATURE, JSONObject.NULL);
		} catch (JSONException e) {
			return false;
		}
		return true;
	}

	public long getPgpId() {
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

	public boolean setPgpSignId(long pgpID) {
		try {
			keys.put(KEY_PGP_ID, pgpID);
		} catch (JSONException e) {
			return false;
		}
		return true;
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
		final String fingerprint = this.getOtrFingerprint();
		if (fingerprint != null) {
			return "xmpp:" + this.getJid().toBareJid().toString() + "?otr-fingerprint="+fingerprint;
		} else {
			return "xmpp:" + this.getJid().toBareJid().toString();
		}
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
