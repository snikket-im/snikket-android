package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.util.Base64;
import java.io.UnsupportedEncodingException;
import java.lang.Comparable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class ServiceDiscoveryResult {

	protected static String blankNull(String s) {
		return s == null ? "" : s;
	}

	public static class Identity implements Comparable {
		protected final String category;
		protected final String type;
		protected final String lang;
		protected final String name;

		public Identity(final String category, final String type, final String lang, final String name) {
			this.category = category;
			this.type = type;
			this.lang = lang;
			this.name = name;
		}

		public Identity(final Element el) {
			this.category = el.getAttribute("category");
			this.type = el.getAttribute("type");
			this.lang = el.getAttribute("xml:lang");
			this.name = el.getAttribute("name");
		}

		public String getCategory() {
			return this.category;
		}

		public String getType() {
			return this.type;
		}

		public String getLang() {
			return this.lang;
		}

		public String getName() {
			return this.name;
		}

		public int compareTo(Object other) {
			Identity o = (Identity)other;
			int r = blankNull(this.getCategory()).compareTo(blankNull(o.getCategory()));
			if(r == 0) {
				r = blankNull(this.getType()).compareTo(blankNull(o.getType()));
			}
			if(r == 0) {
				r = blankNull(this.getLang()).compareTo(blankNull(o.getLang()));
			}
			if(r == 0) {
				r = blankNull(this.getName()).compareTo(blankNull(o.getName()));
			}

			return r;
		}

		public JSONObject toJSON() {
			try {
				JSONObject o = new JSONObject();
				o.put("category", this.getCategory());
				o.put("type", this.getType());
				o.put("lang", this.getLang());
				o.put("name", this.getName());
				return o;
			} catch(JSONException e) {
				return null;
			}
		}
	}

	protected final List<Identity> identities;
	protected final List<String> features;

	public ServiceDiscoveryResult(final List<Identity> identities, final List<String> features) {
		this.identities = identities;
		this.features = features;
	}

	public ServiceDiscoveryResult(final IqPacket packet) {
		this.identities = new ArrayList<>();
		this.features = new ArrayList<>();

		final List<Element> elements = packet.query().getChildren();

		for (final Element element : elements) {
			if (element.getName().equals("identity")) {
				Identity id = new Identity(element);
				if (id.getType() != null && id.getCategory() != null) {
					identities.add(id);
				}
			} else if (element.getName().equals("feature")) {
				if (element.getAttribute("var") != null) {
					features.add(element.getAttribute("var"));
				}
			}
		}
	}

	public List<Identity> getIdentities() {
		return this.identities;
	}

	public List<String> getFeatures() {
		return this.features;
	}

	public boolean hasIdentity(String category, String type) {
		for(Identity id : this.getIdentities()) {
			if((category == null || id.getCategory().equals(category)) &&
			   (type == null || id.getType().equals(type))) {
				return true;
			}
		}

		return false;
	}

	public byte[] getCapHash() {
		StringBuilder s = new StringBuilder();

		List<Identity> identities = this.getIdentities();
		Collections.sort(identities);

		for(Identity id : identities) {
			s.append(
				blankNull(id.getCategory()) + "/" +
				blankNull(id.getType()) + "/" +
				blankNull(id.getLang()) + "/" +
				blankNull(id.getName()) + "<"
			);
		}

		List<String> features = this.getFeatures();
		Collections.sort(features);

		for (String feature : features) {
			s.append(feature + "<");
		}

		// TODO: data forms?

		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}

		try {
			return md.digest(s.toString().getBytes("UTF-8"));
		} catch(UnsupportedEncodingException e) {
			return null;
		}
	}

	public JSONObject toJSON() {
		try {
			JSONObject o = new JSONObject();

			JSONArray ids = new JSONArray();
			for(Identity id : this.getIdentities()) {
				ids.put(id.toJSON());
			}
			o.put("identites", ids);

			o.put("features", new JSONArray(this.getFeatures()));

			return o;
		} catch(JSONException e) {
			return null;
		}
	}

}
