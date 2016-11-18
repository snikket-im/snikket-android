package eu.siacs.conversations.utils;

import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class XmppUri {

	protected String jid;
	protected boolean muc;
	protected List<Fingerprint> fingerprints = new ArrayList<>();

	public static final String OMEMO_URI_PARAM = "omemo-sid-";
	public static final String OTR_URI_PARAM = "otr-fingerprint";

	public XmppUri(String uri) {
		try {
			parse(Uri.parse(uri));
		} catch (IllegalArgumentException e) {
			try {
				jid = Jid.fromString(uri).toBareJid().toString();
			} catch (InvalidJidException e2) {
				jid = null;
			}
		}
	}

	public XmppUri(Uri uri) {
		parse(uri);
	}

	protected void parse(Uri uri) {
		String scheme = uri.getScheme();
		String host = uri.getHost();
		List<String> segments = uri.getPathSegments();
		if ("https".equalsIgnoreCase(scheme) && "conversations.im".equalsIgnoreCase(host)) {
			if (segments.size() >= 2 && segments.get(1).contains("@")) {
				// sample : https://conversations.im/i/foo@bar.com
				try {
					jid = Jid.fromString(segments.get(1)).toString();
				} catch (Exception e) {
					jid = null;
				}
			} else if (segments.size() >= 3) {
				// sample : https://conversations.im/i/foo/bar.com
				jid = segments.get(1) + "@" + segments.get(2);
			}
			muc = segments.size() > 1 && "j".equalsIgnoreCase(segments.get(0));
		} else if ("xmpp".equalsIgnoreCase(scheme)) {
			// sample: xmpp:foo@bar.com
			muc = "join".equalsIgnoreCase(uri.getQuery());
			if (uri.getAuthority() != null) {
				jid = uri.getAuthority();
			} else {
				jid = uri.getSchemeSpecificPart().split("\\?")[0];
			}
			this.fingerprints = parseFingerprints(uri.getQuery());
		} else if ("imto".equalsIgnoreCase(scheme)) {
			// sample: imto://xmpp/foo@bar.com
			try {
				jid = URLDecoder.decode(uri.getEncodedPath(), "UTF-8").split("/")[1];
			} catch (final UnsupportedEncodingException ignored) {
				jid = null;
			}
		} else {
			try {
				jid = Jid.fromString(uri.toString()).toBareJid().toString();
			} catch (final InvalidJidException ignored) {
				jid = null;
			}
		}
	}

	protected List<Fingerprint> parseFingerprints(String query) {
		List<Fingerprint> fingerprints = new ArrayList<>();
		String[] pairs = query == null ? new String[0] : query.split(";");
		for(String pair : pairs) {
			String[] parts = pair.split("=",2);
			if (parts.length == 2) {
				String key = parts[0].toLowerCase(Locale.US);
				String value = parts[1].toLowerCase(Locale.US);
				if (OTR_URI_PARAM.equals(key)) {
					fingerprints.add(new Fingerprint(FingerprintType.OTR,value));
				}
				if (key.startsWith(OMEMO_URI_PARAM)) {
					try {
						int id = Integer.parseInt(key.substring(OMEMO_URI_PARAM.length()));
						fingerprints.add(new Fingerprint(FingerprintType.OMEMO,value,id));
					} catch (Exception e) {
						//ignoring invalid device id
					}
				}
			}
		}
		return fingerprints;
	}

	public Jid getJid() {
		try {
			return this.jid == null ? null :Jid.fromString(this.jid.toLowerCase());
		} catch (InvalidJidException e) {
			return null;
		}
	}

	public List<Fingerprint> getFingerprints() {
		return this.fingerprints;
	}

	public boolean hasFingerprints() {
		return fingerprints.size() > 0;
	}
	public enum FingerprintType {
		OMEMO,
		OTR
	}

	public static class Fingerprint {
		public final FingerprintType type;
		public final String fingerprint;
		public final int deviceId;

		public Fingerprint(FingerprintType type, String fingerprint) {
			this(type, fingerprint, 0);
		}

		public Fingerprint(FingerprintType type, String fingerprint, int deviceId) {
			this.type = type;
			this.fingerprint = fingerprint;
			this.deviceId = deviceId;
		}

		@Override
		public String toString() {
			return type.toString()+": "+fingerprint+(deviceId != 0 ? " "+String.valueOf(deviceId) : "");
		}
	}
}
