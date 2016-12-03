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
	private String body;
	protected boolean safeSource = true;

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

	public XmppUri(Uri uri, boolean safeSource) {
		this.safeSource = safeSource;
		parse(uri);
	}

	public boolean isSafeSource() {
		return safeSource;
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
			fingerprints = parseFingerprints(uri.getQuery(),'&');
		} else if ("xmpp".equalsIgnoreCase(scheme)) {
			// sample: xmpp:foo@bar.com
			muc = isMuc(uri.getQuery());
			if (uri.getAuthority() != null) {
				jid = uri.getAuthority();
			} else {
				jid = uri.getSchemeSpecificPart().split("\\?")[0];
			}
			this.fingerprints = parseFingerprints(uri.getQuery());
			this.body = parseBody(uri.getQuery());
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
		return parseFingerprints(query,';');
	}

	protected List<Fingerprint> parseFingerprints(String query, char seperator) {
		List<Fingerprint> fingerprints = new ArrayList<>();
		String[] pairs = query == null ? new String[0] : query.split(String.valueOf(seperator));
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

	protected String parseBody(String query) {
		for(String pair : query == null ? new String[0] : query.split(";")) {
			final String[] parts = pair.split("=",2);
			if (parts.length == 2 && "body".equals(parts[0].toLowerCase(Locale.US))) {
				try {
					return URLDecoder.decode(parts[1],"UTF-8");
				} catch (UnsupportedEncodingException e) {
					return null;
				}
			}
		}
		return null;
	}

	protected boolean isMuc(String query) {
		for(String pair : query == null ? new String[0] : query.split(";")) {
			final String[] parts = pair.split("=",2);
			if (parts.length == 1 && "join".equals(parts[0])) {
				return true;
			}
		}
		return false;
	}

	public Jid getJid() {
		try {
			return this.jid == null ? null :Jid.fromString(this.jid.toLowerCase());
		} catch (InvalidJidException e) {
			return null;
		}
	}

	public String getBody() {
		return body;
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

	public static String getFingerprintUri(String base, List<XmppUri.Fingerprint> fingerprints, char seperator) {
		StringBuilder builder = new StringBuilder(base);
		builder.append('?');
		for(int i = 0; i < fingerprints.size(); ++i) {
			XmppUri.FingerprintType type = fingerprints.get(i).type;
			if (type == XmppUri.FingerprintType.OMEMO) {
				builder.append(XmppUri.OMEMO_URI_PARAM);
				builder.append(fingerprints.get(i).deviceId);
			} else if (type == XmppUri.FingerprintType.OTR) {
				builder.append(XmppUri.OTR_URI_PARAM);
			}
			builder.append('=');
			builder.append(fingerprints.get(i).fingerprint);
			if (i != fingerprints.size() -1) {
				builder.append(seperator);
			}
		}
		return builder.toString();
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
