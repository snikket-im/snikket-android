package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class Contact implements ListItem, Blockable {
	public static final String TABLENAME = "contacts";

	public static final String SYSTEMNAME = "systemname";
	public static final String SERVERNAME = "servername";
	public static final String JID = "jid";
	public static final String OPTIONS = "options";
	public static final String SYSTEMACCOUNT = "systemaccount";
	public static final String PHOTOURI = "photouri";
	public static final String KEYS = "pgpkey";
	public static final String ACCOUNT = "accountUuid";
	public static final String AVATAR = "avatar";
	public static final String LAST_PRESENCE = "last_presence";
	public static final String LAST_TIME = "last_time";
	public static final String GROUPS = "groups";
	public Lastseen lastseen = new Lastseen();
	protected String accountUuid;
	protected String systemName;
	protected String serverName;
	protected String presenceName;
	protected Jid jid;
	protected int subscription = 0;
	protected String systemAccount;
	protected String photoUri;
	protected String avatar;
	protected JSONObject keys = new JSONObject();
	protected JSONArray groups = new JSONArray();
	protected Presences presences = new Presences();
	protected Account account;

	public Contact(final String account, final String systemName, final String serverName,
			final Jid jid, final int subscription, final String photoUri,
			final String systemAccount, final String keys, final String avatar, final Lastseen lastseen, final String groups) {
		this.accountUuid = account;
		this.systemName = systemName;
		this.serverName = serverName;
		this.jid = jid;
		this.subscription = subscription;
		this.photoUri = photoUri;
		this.systemAccount = systemAccount;
		try {
			this.keys = (keys == null ? new JSONObject("") : new JSONObject(keys));
		} catch (JSONException e) {
			this.keys = new JSONObject();
		}
		this.avatar = avatar;
		try {
			this.groups = (groups == null ? new JSONArray() : new JSONArray(groups));
		} catch (JSONException e) {
			this.groups = new JSONArray();
		}
		this.lastseen = lastseen;
	}

	public Contact(final Jid jid) {
		this.jid = jid;
	}

	public static Contact fromCursor(final Cursor cursor) {
		final Lastseen lastseen = new Lastseen(
				cursor.getString(cursor.getColumnIndex(LAST_PRESENCE)),
				cursor.getLong(cursor.getColumnIndex(LAST_TIME)));
		final Jid jid;
		try {
			jid = Jid.fromString(cursor.getString(cursor.getColumnIndex(JID)));
		} catch (final InvalidJidException e) {
			// TODO: Borked DB... handle this somehow?
			return null;
		}
		return new Contact(cursor.getString(cursor.getColumnIndex(ACCOUNT)),
				cursor.getString(cursor.getColumnIndex(SYSTEMNAME)),
				cursor.getString(cursor.getColumnIndex(SERVERNAME)),
				jid,
				cursor.getInt(cursor.getColumnIndex(OPTIONS)),
				cursor.getString(cursor.getColumnIndex(PHOTOURI)),
				cursor.getString(cursor.getColumnIndex(SYSTEMACCOUNT)),
				cursor.getString(cursor.getColumnIndex(KEYS)),
				cursor.getString(cursor.getColumnIndex(AVATAR)),
				lastseen,
				cursor.getString(cursor.getColumnIndex(GROUPS)));
	}

	public String getDisplayName() {
		if (this.systemName != null) {
			return this.systemName;
		} else if (this.serverName != null) {
			return this.serverName;
		} else if (this.presenceName != null) {
			return this.presenceName;
		} else if (jid.hasLocalpart()) {
			return jid.getLocalpart();
		} else {
			return jid.getDomainpart();
		}
	}

	public String getProfilePhoto() {
		return this.photoUri;
	}

	public Jid getJid() {
		return jid;
	}

	@Override
	public List<Tag> getTags() {
		final ArrayList<Tag> tags = new ArrayList<>();
		for (final String group : getGroups()) {
			tags.add(new Tag(group, UIHelper.getColorForName(group)));
		}
		switch (getMostAvailableStatus()) {
			case Presences.CHAT:
			case Presences.ONLINE:
				tags.add(new Tag("online", 0xff259b24));
				break;
			case Presences.AWAY:
				tags.add(new Tag("away", 0xffff9800));
				break;
			case Presences.XA:
				tags.add(new Tag("not available", 0xffe51c23));
				break;
			case Presences.DND:
				tags.add(new Tag("dnd", 0xffe51c23));
				break;
		}
		if (isBlocked()) {
			tags.add(new Tag("blocked", 0xff2e2f3b));
		}
		return tags;
	}

	public boolean match(String needle) {
		if (needle == null || needle.isEmpty()) {
			return true;
		}
		needle = needle.toLowerCase(Locale.US).trim();
		String[] parts = needle.split("\\s+");
		if (parts.length > 1) {
			for(int i = 0; i < parts.length; ++i) {
				if (!match(parts[i])) {
					return false;
				}
			}
			return true;
		} else {
			return jid.toString().contains(needle) ||
				getDisplayName().toLowerCase(Locale.US).contains(needle) ||
				matchInTag(needle);
		}
	}

	private boolean matchInTag(String needle) {
		needle = needle.toLowerCase(Locale.US);
		for (Tag tag : getTags()) {
			if (tag.getName().toLowerCase(Locale.US).contains(needle)) {
				return true;
			}
		}
		return false;
	}

	public ContentValues getContentValues() {
		final ContentValues values = new ContentValues();
		values.put(ACCOUNT, accountUuid);
		values.put(SYSTEMNAME, systemName);
		values.put(SERVERNAME, serverName);
		values.put(JID, jid.toString());
		values.put(OPTIONS, subscription);
		values.put(SYSTEMACCOUNT, systemAccount);
		values.put(PHOTOURI, photoUri);
		values.put(KEYS, keys.toString());
		values.put(AVATAR, avatar);
		values.put(LAST_PRESENCE, lastseen.presence);
		values.put(LAST_TIME, lastseen.time);
		values.put(GROUPS, groups.toString());
		return values;
	}

	public int getSubscription() {
		return this.subscription;
	}

	public Account getAccount() {
		return this.account;
	}

	public void setAccount(Account account) {
		this.account = account;
		this.accountUuid = account.getUuid();
	}

	public Presences getPresences() {
		return this.presences;
	}

	public void setPresences(Presences pres) {
		this.presences = pres;
	}

	public void updatePresence(final String resource, final int status) {
		this.presences.updatePresence(resource, status);
	}

	public void removePresence(final String resource) {
		this.presences.removePresence(resource);
	}

	public void clearPresences() {
		this.presences.clearPresences();
		this.resetOption(Options.PENDING_SUBSCRIPTION_REQUEST);
	}

	public int getMostAvailableStatus() {
		return this.presences.getMostAvailableStatus();
	}

	public void setPhotoUri(String uri) {
		this.photoUri = uri;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public void setSystemName(String systemName) {
		this.systemName = systemName;
	}

	public void setPresenceName(String presenceName) {
		this.presenceName = presenceName;
	}

	public String getSystemAccount() {
		return systemAccount;
	}

	public void setSystemAccount(String account) {
		this.systemAccount = account;
	}

	public List<String> getGroups() {
		ArrayList<String> groups = new ArrayList<String>();
		for (int i = 0; i < this.groups.length(); ++i) {
			try {
				groups.add(this.groups.getString(i));
			} catch (final JSONException ignored) {
			}
		}
		return groups;
	}

	public ArrayList<String> getOtrFingerprints() {
		final ArrayList<String> fingerprints = new ArrayList<String>();
		try {
			if (this.keys.has("otr_fingerprints")) {
				final JSONArray prints = this.keys.getJSONArray("otr_fingerprints");
				for (int i = 0; i < prints.length(); ++i) {
					final String print = prints.isNull(i) ? null : prints.getString(i);
					if (print != null && !print.isEmpty()) {
						fingerprints.add(prints.getString(i));
					}
				}
			}
		} catch (final JSONException ignored) {

		}
		return fingerprints;
	}

	public boolean addOtrFingerprint(String print) {
		if (getOtrFingerprints().contains(print)) {
			return false;
		}
		try {
			JSONArray fingerprints;
			if (!this.keys.has("otr_fingerprints")) {
				fingerprints = new JSONArray();

			} else {
				fingerprints = this.keys.getJSONArray("otr_fingerprints");
			}
			fingerprints.put(print);
			this.keys.put("otr_fingerprints", fingerprints);
			return true;
		} catch (final JSONException ignored) {
			return false;
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

	public void setPgpKeyId(long keyId) {
		try {
			this.keys.put("pgp_keyid", keyId);
		} catch (final JSONException ignored) {

		}
	}

	public void setOption(int option) {
		this.subscription |= 1 << option;
	}

	public void resetOption(int option) {
		this.subscription &= ~(1 << option);
	}

	public boolean getOption(int option) {
		return ((this.subscription & (1 << option)) != 0);
	}

	public boolean showInRoster() {
		return (this.getOption(Contact.Options.IN_ROSTER) && (!this
					.getOption(Contact.Options.DIRTY_DELETE)))
			|| (this.getOption(Contact.Options.DIRTY_PUSH));
	}

	public void parseSubscriptionFromElement(Element item) {
		String ask = item.getAttribute("ask");
		String subscription = item.getAttribute("subscription");

		if (subscription != null) {
			switch (subscription) {
				case "to":
					this.resetOption(Options.FROM);
					this.setOption(Options.TO);
					break;
				case "from":
					this.resetOption(Options.TO);
					this.setOption(Options.FROM);
					this.resetOption(Options.PREEMPTIVE_GRANT);
					break;
				case "both":
					this.setOption(Options.TO);
					this.setOption(Options.FROM);
					this.resetOption(Options.PREEMPTIVE_GRANT);
					break;
				case "none":
					this.resetOption(Options.FROM);
					this.resetOption(Options.TO);
					break;
			}
		}

		// do NOT override asking if pending push request
		if (!this.getOption(Contact.Options.DIRTY_PUSH)) {
			if ((ask != null) && (ask.equals("subscribe"))) {
				this.setOption(Contact.Options.ASKING);
			} else {
				this.resetOption(Contact.Options.ASKING);
			}
		}
	}

	public void parseGroupsFromElement(Element item) {
		this.groups = new JSONArray();
		for (Element element : item.getChildren()) {
			if (element.getName().equals("group") && element.getContent() != null) {
				this.groups.put(element.getContent());
			}
		}
	}

	public Element asElement() {
		final Element item = new Element("item");
		item.setAttribute("jid", this.jid.toString());
		if (this.serverName != null) {
			item.setAttribute("name", this.serverName);
		}
		for (String group : getGroups()) {
			item.addChild("group").setContent(group);
		}
		return item;
	}

	@Override
	public int compareTo(final ListItem another) {
		return this.getDisplayName().compareToIgnoreCase(
				another.getDisplayName());
	}

	public Jid getServer() {
		return getJid().toDomainJid();
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

	public boolean deleteOtrFingerprint(String fingerprint) {
		boolean success = false;
		try {
			if (this.keys.has("otr_fingerprints")) {
				JSONArray newPrints = new JSONArray();
				JSONArray oldPrints = this.keys
					.getJSONArray("otr_fingerprints");
				for (int i = 0; i < oldPrints.length(); ++i) {
					if (!oldPrints.getString(i).equals(fingerprint)) {
						newPrints.put(oldPrints.getString(i));
					} else {
						success = true;
					}
				}
				this.keys.put("otr_fingerprints", newPrints);
			}
			return success;
		} catch (JSONException e) {
			return false;
		}
	}

	public boolean trusted() {
		return getOption(Options.FROM) && getOption(Options.TO);
	}

	public String getShareableUri() {
		if (getOtrFingerprints().size() >= 1) {
			String otr = getOtrFingerprints().get(0);
			return "xmpp:" + getJid().toBareJid().toString() + "?otr-fingerprint=" + otr.replace(" ", "");
		} else {
			return "xmpp:" + getJid().toBareJid().toString();
		}
	}

	@Override
	public boolean isBlocked() {
		return getAccount().isBlocked(this);
	}

	@Override
	public boolean isDomainBlocked() {
		return getAccount().isBlocked(this.getJid().toDomainJid());
	}

	@Override
	public Jid getBlockedJid() {
		if (isDomainBlocked()) {
			return getJid().toDomainJid();
		} else {
			return getJid();
		}
	}

	public static class Lastseen {
		public long time;
		public String presence;

		public Lastseen() {
			this(null, 0);
		}

		public Lastseen(final String presence, final long time) {
			this.presence = presence;
			this.time = time;
		}
	}

	public final class Options {
		public static final int TO = 0;
		public static final int FROM = 1;
		public static final int ASKING = 2;
		public static final int PREEMPTIVE_GRANT = 3;
		public static final int IN_ROSTER = 4;
		public static final int PENDING_SUBSCRIPTION_REQUEST = 5;
		public static final int DIRTY_PUSH = 6;
		public static final int DIRTY_DELETE = 7;
	}
}
