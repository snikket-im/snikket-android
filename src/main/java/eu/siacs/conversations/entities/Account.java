package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;

import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.interfaces.DSAPublicKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrEngine;
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

	public static final String PINNED_MECHANISM_KEY = "pinned_mechanism";

	public static final int OPTION_USETLS = 0;
	public static final int OPTION_DISABLED = 1;
	public static final int OPTION_REGISTER = 2;
	public static final int OPTION_USECOMPRESSION = 3;

	public static enum State {
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
		INCOMPATIBLE_SERVER(true);

		private boolean isError;

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
				default:
					return R.string.account_status_unknown;
			}
		}
	}

	public List<Conversation> pendingConferenceJoins = new CopyOnWriteArrayList<>();
	public List<Conversation> pendingConferenceLeaves = new CopyOnWriteArrayList<>();
	protected Jid jid;
	protected String password;
	protected int options = 0;
	protected String rosterVersion;
	protected State status = State.OFFLINE;
	protected JSONObject keys = new JSONObject();
	protected String avatar;
	protected boolean online = false;
	private OtrEngine otrEngine = null;
	private XmppConnection xmppConnection = null;
	private Presences presences = new Presences();
	private long mEndGracePeriod = 0L;
	private String otrFingerprint;
	private Roster roster = null;
	private List<Bookmark> bookmarks = new CopyOnWriteArrayList<>();

	public Account() {
		this.uuid = "0";
	}

	public Account(final Jid jid, final String password) {
		this(java.util.UUID.randomUUID().toString(), jid,
				password, 0, null, "", null);
	}

	public Account(final String uuid, final Jid jid,
			final String password, final int options, final String rosterVersion, final String keys,
			final String avatar) {
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

		}
		this.avatar = avatar;
	}

	public static Account fromCursor(Cursor cursor) {
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
				cursor.getString(cursor.getColumnIndex(AVATAR)));
	}

	public boolean isOptionSet(int option) {
		return ((options & (1 << option)) != 0);
	}

	public void setOption(int option, boolean value) {
		if (value) {
			this.options |= 1 << option;
		} else {
			this.options &= ~(1 << option);
		}
	}

	public String getUsername() {
		return jid.getLocalpart();
	}

	public void setUsername(final String username) throws InvalidJidException {
		jid = Jid.fromParts(username, jid.getDomainpart(), jid.getResourcepart());
	}

	public Jid getServer() {
		return jid.toDomainJid();
	}

	public void setServer(final String server) throws InvalidJidException {
		jid = Jid.fromParts(jid.getLocalpart(), server, jid.getResourcepart());
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(final String password) {
		this.password = password;
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
		return getXmppConnection() != null && getStatus().isError() && getXmppConnection().getAttempt() >= 2;
	}

	public String getResource() {
		return jid.getResourcepart();
	}

	public void setResource(final String resource) {
		try {
			jid = Jid.fromParts(jid.getLocalpart(), jid.getDomainpart(), resource);
		} catch (final InvalidJidException ignored) {
		}
	}

	public Jid getJid() {
		return jid;
	}

	public JSONObject getKeys() {
		return keys;
	}

	public String getSSLFingerprint() {
		if (keys.has("ssl_cert")) {
			try {
				return keys.getString("ssl_cert");
			} catch (JSONException e) {
				return null;
			}
		} else {
			return null;
		}
	}

	public void setSSLCertFingerprint(String fingerprint) {
		this.setKey("ssl_cert", fingerprint);
	}

	public boolean setKey(String keyName, String keyValue) {
		try {
			this.keys.put(keyName, keyValue);
			return true;
		} catch (JSONException e) {
			return false;
		}
	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(USERNAME, jid.getLocalpart());
		values.put(SERVER, jid.getDomainpart());
		values.put(PASSWORD, password);
		values.put(OPTIONS, options);
		values.put(KEYS, this.keys.toString());
		values.put(ROSTERVERSION, rosterVersion);
		values.put(AVATAR, avatar);
		return values;
	}

	public void initOtrEngine(XmppConnectionService context) {
		this.otrEngine = new OtrEngine(context, this);
	}

	public OtrEngine getOtrEngine() {
		return this.otrEngine;
	}

	public XmppConnection getXmppConnection() {
		return this.xmppConnection;
	}

	public void setXmppConnection(XmppConnection connection) {
		this.xmppConnection = connection;
	}

	public String getOtrFingerprint() {
		if (this.otrFingerprint == null) {
			try {
				if (this.otrEngine == null) {
					return null;
				}
				DSAPublicKey publicKey = (DSAPublicKey) this.otrEngine.getPublicKey();
				if (publicKey == null) {
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

	public void setRosterVersion(String version) {
		this.rosterVersion = version;
	}

	public void updatePresence(String resource, int status) {
		this.presences.updatePresence(resource, status);
	}

	public void removePresence(String resource) {
		this.presences.removePresence(resource);
	}

	public void clearPresences() {
		this.presences = new Presences();
	}

	public int countPresences() {
		return this.presences.size();
	}

	public String getPgpSignature() {
		if (keys.has("pgp_signature")) {
			try {
				return keys.getString("pgp_signature");
			} catch (JSONException e) {
				return null;
			}
		} else {
			return null;
		}
	}

	public Roster getRoster() {
		if (this.roster == null) {
			this.roster = new Roster(this);
		}
		return this.roster;
	}

	public List<Bookmark> getBookmarks() {
		return this.bookmarks;
	}

	public void setBookmarks(List<Bookmark> bookmarks) {
		this.bookmarks = bookmarks;
	}

	public boolean hasBookmarkFor(final Jid conferenceJid) {
		for (Bookmark bmark : this.bookmarks) {
			if (bmark.getJid().equals(conferenceJid.toBareJid())) {
				return true;
			}
		}
		return false;
	}

	public boolean setAvatar(String filename) {
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

	public void activateGracePeriod() {
		this.mEndGracePeriod = SystemClock.elapsedRealtime()
			+ (Config.CARBON_GRACE_PERIOD * 1000);
	}

	public void deactivateGracePeriod() {
		this.mEndGracePeriod = 0L;
	}

	public boolean inGracePeriod() {
		return SystemClock.elapsedRealtime() < this.mEndGracePeriod;
	}

	public String getShareableUri() {
		String fingerprint = this.getOtrFingerprint();
		if (fingerprint != null) {
			return "xmpp:" + this.getJid().toBareJid().toString() + "?otr-fingerprint="+fingerprint;
		} else {
			return "xmpp:" + this.getJid().toBareJid().toString();
		}
	}
}
