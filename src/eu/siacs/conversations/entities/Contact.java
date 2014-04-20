package eu.siacs.conversations.entities;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.xml.Element;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

public class Contact extends AbstractEntity implements Serializable {
	private static final long serialVersionUID = -4570817093119419962L;

	public static final String TABLENAME = "contacts";

	public static final String DISPLAYNAME = "name";
	public static final String JID = "jid";
	public static final String SUBSCRIPTION = "subscription";
	public static final String SYSTEMACCOUNT = "systemaccount";
	public static final String PHOTOURI = "photouri";
	public static final String KEYS = "pgpkey";
	public static final String PRESENCES = "presences";
	public static final String ACCOUNT = "accountUuid";

	protected String accountUuid;
	protected String displayName;
	protected String jid;
	protected int subscription = 0;
	protected String systemAccount;
	protected String photoUri;
	protected JSONObject keys = new JSONObject();
	protected Presences presences = new Presences();

	protected Account account;

	protected boolean inRoster = true;

	public Contact(Account account, String displayName, String jid,
			String photoUri) {
		if (account == null) {
			this.accountUuid = null;
		} else {
			this.accountUuid = account.getUuid();
		}
		this.account = account;
		this.displayName = displayName;
		this.jid = jid;
		this.photoUri = photoUri;
		this.uuid = java.util.UUID.randomUUID().toString();
	}

	public Contact(String uuid, String account, String displayName, String jid,
			int subscription, String photoUri, String systemAccount,
			String keys, String presences) {
		this.uuid = uuid;
		this.accountUuid = account;
		this.displayName = displayName;
		this.jid = jid;
		this.subscription = subscription;
		this.photoUri = photoUri;
		this.systemAccount = systemAccount;
		if (keys == null) {
			keys = "";
		}
		try {
			this.keys = new JSONObject(keys);
		} catch (JSONException e) {
			this.keys = new JSONObject();
		}
		this.presences = Presences.fromJsonString(presences);
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getProfilePhoto() {
		return this.photoUri;
	}

	public String getJid() {
		return this.jid;
	}

	public boolean match(String needle) {
		return (jid.toLowerCase().contains(needle.toLowerCase()) || (displayName
				.toLowerCase().contains(needle.toLowerCase())));
	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(ACCOUNT, accountUuid);
		values.put(DISPLAYNAME, displayName);
		values.put(JID, jid);
		values.put(SUBSCRIPTION, subscription);
		values.put(SYSTEMACCOUNT, systemAccount);
		values.put(PHOTOURI, photoUri);
		values.put(KEYS, keys.toString());
		values.put(PRESENCES, presences.toJsonString());
		return values;
	}

	public static Contact fromCursor(Cursor cursor) {
		return new Contact(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(ACCOUNT)),
				cursor.getString(cursor.getColumnIndex(DISPLAYNAME)),
				cursor.getString(cursor.getColumnIndex(JID)),
				cursor.getInt(cursor.getColumnIndex(SUBSCRIPTION)),
				cursor.getString(cursor.getColumnIndex(PHOTOURI)),
				cursor.getString(cursor.getColumnIndex(SYSTEMACCOUNT)),
				cursor.getString(cursor.getColumnIndex(KEYS)),
				cursor.getString(cursor.getColumnIndex(PRESENCES)));
	}

	public int getSubscription() {
		return this.subscription;
	}

	public void setSystemAccount(String account) {
		this.systemAccount = account;
	}

	public void setAccount(Account account) {
		this.account = account;
		this.accountUuid = account.getUuid();
	}

	public Account getAccount() {
		return this.account;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public boolean couldBeMuc() {
		String[] split = this.getJid().split("@");
		if (split.length != 2) {
			return false;
		} else {
			String[] domainParts = split[1].split("\\.");
			if (domainParts.length < 3) {
				return false;
			} else {
				return (domainParts[0].equals("conf")
						|| domainParts[0].equals("conference")
						|| domainParts[0].equals("muc")
						|| domainParts[0].equals("sala")
						|| domainParts[0].equals("salas"));
			}
		}
	}

	public Hashtable<String, Integer> getPresences() {
		return this.presences.getPresences();
	}

	public void updatePresence(String resource, int status) {
		this.presences.updatePresence(resource, status);
	}

	public void removePresence(String resource) {
		this.presences.removePresence(resource);
	}

	public void clearPresences() {
		this.presences.clearPresences();
	}

	public int getMostAvailableStatus() {
		return this.presences.getMostAvailableStatus();
	}

	public void setPresences(Presences pres) {
		this.presences = pres;
	}

	public void setPhotoUri(String uri) {
		this.photoUri = uri;
	}

	public void setDisplayName(String name) {
		this.displayName = name;
	}

	public String getSystemAccount() {
		return systemAccount;
	}

	public Set<String> getOtrFingerprints() {
		Set<String> set = new HashSet<String>();
		try {
			if (this.keys.has("otr_fingerprints")) {
				JSONArray fingerprints = this.keys
						.getJSONArray("otr_fingerprints");
				for (int i = 0; i < fingerprints.length(); ++i) {
					set.add(fingerprints.getString(i));
				}
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return set;
	}

	public void addOtrFingerprint(String print) {
		try {
			JSONArray fingerprints;
			if (!this.keys.has("otr_fingerprints")) {
				fingerprints = new JSONArray();

			} else {
				fingerprints = this.keys.getJSONArray("otr_fingerprints");
			}
			fingerprints.put(print);
			this.keys.put("otr_fingerprints", fingerprints);
		} catch (JSONException e) {

		}
	}

	public void setPgpKeyId(long keyId) {
		try {
			this.keys.put("pgp_keyid", keyId);
		} catch (JSONException e) {

		}
	}

	public long getPgpKeyId() {
		if (this.keys.has("pgp_keyid")) {
			try {
				return this.keys.getLong("pgp_keyid");
			} catch (JSONException e) {
				return 0;
			}
		} else {
			return 0;
		}
	}

	public void setSubscriptionOption(int option) {
		this.subscription |= 1 << option;
	}

	public void resetSubscriptionOption(int option) {
		this.subscription &= ~(1 << option);
	}

	public boolean getSubscriptionOption(int option) {
		return ((this.subscription & (1 << option)) != 0);
	}

	public void parseSubscriptionFromElement(Element item) {
		String ask = item.getAttribute("ask");
		String subscription = item.getAttribute("subscription");

		if (subscription != null) {
			if (subscription.equals("to")) {
				this.resetSubscriptionOption(Contact.Subscription.FROM);
				this.setSubscriptionOption(Contact.Subscription.TO);
			} else if (subscription.equals("from")) {
				this.resetSubscriptionOption(Contact.Subscription.TO);
				this.setSubscriptionOption(Contact.Subscription.FROM);
			} else if (subscription.equals("both")) {
				this.setSubscriptionOption(Contact.Subscription.TO);
				this.setSubscriptionOption(Contact.Subscription.FROM);
			}
		}

		if ((ask != null) && (ask.equals("subscribe"))) {
			this.setSubscriptionOption(Contact.Subscription.ASKING);
		} else {
			this.resetSubscriptionOption(Contact.Subscription.ASKING);
		}
	}

	public class Subscription {
		public static final int TO = 0;
		public static final int FROM = 1;
		public static final int ASKING = 2;
		public static final int PREEMPTIVE_GRANT = 4;
	}

	public void flagAsNotInRoster() {
		this.inRoster = false;
	}

	public boolean isInRoster() {
		return this.inRoster;
	}

	public String getAccountUuid() {
		return this.accountUuid;
	}
}
