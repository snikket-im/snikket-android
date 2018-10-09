package eu.siacs.conversations.utils;

import android.net.Uri;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import rocks.xmpp.addr.Jid;

public class XmppUri {

	protected Uri uri;
	protected String jid;
	private List<Fingerprint> fingerprints = new ArrayList<>();
	private String body;
	private String name;
	private String action;
	private boolean safeSource = true;

	private static final String OMEMO_URI_PARAM = "omemo-sid-";

	public static final String ACTION_JOIN = "join";
	public static final String ACTION_MESSAGE = "message";

	public XmppUri(String uri) {
		try {
			parse(Uri.parse(uri));
		} catch (IllegalArgumentException e) {
			try {
				jid = Jid.of(uri).asBareJid().toString();
			} catch (IllegalArgumentException e2) {
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

	protected void parse(final Uri uri) {
		if (uri == null) {
			return;
		}
		this.uri = uri;
		String scheme = uri.getScheme();
		String host = uri.getHost();
		List<String> segments = uri.getPathSegments();
		if ("https".equalsIgnoreCase(scheme) && "conversations.im".equalsIgnoreCase(host)) {
			if (segments.size() >= 2 && segments.get(1).contains("@")) {
				// sample : https://conversations.im/i/foo@bar.com
				try {
					jid = Jid.of(lameUrlDecode(segments.get(1))).toString();
				} catch (Exception e) {
					Log.d(Config.LOGTAG, "parsing failed ", e);
					jid = null;
				}
			} else if (segments.size() >= 3) {
				// sample : https://conversations.im/i/foo/bar.com
				jid = segments.get(1) + "@" + segments.get(2);
			}
			if (segments.size() > 1 && "j".equalsIgnoreCase(segments.get(0))) {
				action = ACTION_JOIN;
			}
			fingerprints = parseFingerprints(uri.getQuery(), '&');
		} else if ("xmpp".equalsIgnoreCase(scheme)) {
			// sample: xmpp:foo@bar.com

			final String query = uri.getQuery();

			if (hasAction(query, ACTION_JOIN)) {
				this.action = ACTION_JOIN;
			} else if (hasAction(query, ACTION_MESSAGE)) {
				this.action = ACTION_MESSAGE;
			}

			if (uri.getAuthority() != null) {
				jid = uri.getAuthority();
			} else {
				String[] parts = uri.getSchemeSpecificPart().split("\\?");
				if (parts.length > 0) {
					jid = parts[0];
				} else {
					return;
				}
			}
			this.fingerprints = parseFingerprints(uri.getQuery());
			this.body = parseParameter("body", uri.getQuery());
			this.name = parseParameter("name", uri.getQuery());
		} else if ("imto".equalsIgnoreCase(scheme)) {
			// sample: imto://xmpp/foo@bar.com
			try {
				jid = URLDecoder.decode(uri.getEncodedPath(), "UTF-8").split("/")[1].trim();
			} catch (final UnsupportedEncodingException ignored) {
				jid = null;
			}
		} else {
			try {
				jid = Jid.of(uri.toString()).asBareJid().toString();
			} catch (final IllegalArgumentException ignored) {
				jid = null;
			}
		}
	}

	public String toString() {
		if (uri != null) {
			return uri.toString();
		}
		return "";
	}

	private List<Fingerprint> parseFingerprints(String query) {
		return parseFingerprints(query, ';');
	}

	private List<Fingerprint> parseFingerprints(String query, char seperator) {
		List<Fingerprint> fingerprints = new ArrayList<>();
		String[] pairs = query == null ? new String[0] : query.split(String.valueOf(seperator));
		for (String pair : pairs) {
			String[] parts = pair.split("=", 2);
			if (parts.length == 2) {
				String key = parts[0].toLowerCase(Locale.US);
				String value = parts[1].toLowerCase(Locale.US);
				if (key.startsWith(OMEMO_URI_PARAM)) {
					try {
						int id = Integer.parseInt(key.substring(OMEMO_URI_PARAM.length()));
						fingerprints.add(new Fingerprint(FingerprintType.OMEMO, value, id));
					} catch (Exception e) {
						//ignoring invalid device id
					}
				}
			}
		}
		return fingerprints;
	}

	private String parseParameter(String key, String query) {
		for (String pair : query == null ? new String[0] : query.split(";")) {
			final String[] parts = pair.split("=", 2);
			if (parts.length == 2 && key.equals(parts[0].toLowerCase(Locale.US))) {
				try {
					return URLDecoder.decode(parts[1], "UTF-8");
				} catch (UnsupportedEncodingException e) {
					return null;
				}
			}
		}
		return null;
	}

	private boolean hasAction(String query, String action) {
		for (String pair : query == null ? new String[0] : query.split(";")) {
			final String[] parts = pair.split("=", 2);
			if (parts.length == 1 && parts[0].equals(action)) {
				return true;
			}
		}
		return false;
	}

	public boolean isAction(final String action) {
		return this.action != null && this.action.equals(action);

	}

	public Jid getJid() {
		try {
			return this.jid == null ? null : Jid.of(this.jid);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public boolean isJidValid() {
		if (jid == null) {
			return false;
		}
		try {
			Jid.of(jid);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public String getBody() {
		return body;
	}

	public String getName() {
		return name;
	}

	public List<Fingerprint> getFingerprints() {
		return this.fingerprints;
	}

	public boolean hasFingerprints() {
		return fingerprints.size() > 0;
	}

	public enum FingerprintType {
		OMEMO
	}

	public static String getFingerprintUri(String base, List<XmppUri.Fingerprint> fingerprints, char seperator) {
		StringBuilder builder = new StringBuilder(base);
		builder.append('?');
		for (int i = 0; i < fingerprints.size(); ++i) {
			XmppUri.FingerprintType type = fingerprints.get(i).type;
			if (type == XmppUri.FingerprintType.OMEMO) {
				builder.append(XmppUri.OMEMO_URI_PARAM);
				builder.append(fingerprints.get(i).deviceId);
			}
			builder.append('=');
			builder.append(fingerprints.get(i).fingerprint);
			if (i != fingerprints.size() - 1) {
				builder.append(seperator);
			}
		}
		return builder.toString();
	}

	public static class Fingerprint {
		public final FingerprintType type;
		public final String fingerprint;
		final int deviceId;

		public Fingerprint(FingerprintType type, String fingerprint, int deviceId) {
			this.type = type;
			this.fingerprint = fingerprint;
			this.deviceId = deviceId;
		}

		@Override
		public String toString() {
			return type.toString() + ": " + fingerprint + (deviceId != 0 ? " " + String.valueOf(deviceId) : "");
		}
	}

	private static String lameUrlDecode(String url) {
		return url.replace("%23", "#").replace("%25", "%");
	}

	public static String lameUrlEncode(String url) {
		return url.replace("%", "%25").replace("#", "%23");
	}
}
