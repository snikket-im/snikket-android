package eu.siacs.conversations.utils;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import rocks.xmpp.addr.Jid;

public class XmppUri {

	protected Uri uri;
	protected String jid;
	private List<Fingerprint> fingerprints = new ArrayList<>();
	private Map<String,String> parameters = Collections.emptyMap();
	private boolean safeSource = true;

	private static final String OMEMO_URI_PARAM = "omemo-sid-";

	public static final String ACTION_JOIN = "join";
	public static final String ACTION_MESSAGE = "message";
	public static final String ACTION_REGISTER = "register";
	public static final String ACTION_ROSTER = "roster";

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
					jid = null;
				}
			} else if (segments.size() >= 3) {
				// sample : https://conversations.im/i/foo/bar.com
				jid = segments.get(1) + "@" + segments.get(2);
			}
			if (segments.size() > 1 && "j".equalsIgnoreCase(segments.get(0))) {
				this.parameters = ImmutableMap.of(ACTION_JOIN, "");
			}
			final Map<String,String> parameters = parseParameters(uri.getQuery(), '&');
			this.fingerprints = parseFingerprints(parameters);
		} else if ("xmpp".equalsIgnoreCase(scheme)) {
			// sample: xmpp:foo@bar.com
			this.parameters = parseParameters(uri.getQuery(), ';');
			if (uri.getAuthority() != null) {
				jid = uri.getAuthority();
			} else {
				final String[] parts = uri.getSchemeSpecificPart().split("\\?");
				if (parts.length > 0) {
					jid = parts[0];
				} else {
					return;
				}
			}
			this.fingerprints = parseFingerprints(parameters);
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


	private static Map<String,String> parseParameters(final String query, final char seperator) {
		final ImmutableMap.Builder<String,String> builder = new ImmutableMap.Builder<>();
		final String[] pairs = query == null ? new String[0] : query.split(String.valueOf(seperator));
		for (String pair : pairs) {
			final String[] parts = pair.split("=", 2);
			if (parts.length == 0) {
				continue;
			}
			final String key = parts[0].toLowerCase(Locale.US);
			final String value;
			if (parts.length == 2) {
				String decoded;
				try {
					decoded = URLDecoder.decode(parts[1],"UTF-8");
				} catch (UnsupportedEncodingException e) {
					decoded = "";
				}
				value = decoded;
			} else {
				value = "";
			}
			builder.put(key, value);
		}
		return builder.build();
	}

	@Override
	@NonNull
	public String toString() {
		if (uri != null) {
			return uri.toString();
		}
		return "";
	}

	private static List<Fingerprint> parseFingerprints(Map<String,String> parameters) {
		ImmutableList.Builder<Fingerprint> builder = new ImmutableList.Builder<>();
		for (Map.Entry<String, String> parameter : parameters.entrySet()) {
			final String key = parameter.getKey();
			final String value = parameter.getValue().toLowerCase(Locale.US);
			if (key.startsWith(OMEMO_URI_PARAM)) {
				try {
					final int id = Integer.parseInt(key.substring(OMEMO_URI_PARAM.length()));
					builder.add(new Fingerprint(FingerprintType.OMEMO, value, id));
				} catch (Exception e) {
					//ignoring invalid device id
				}
			}
		}
		return builder.build();
	}

	public boolean isAction(final String action) {
		return parameters.containsKey(action);
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
		return parameters.get("body");
	}

	public String getName() {
		return parameters.get("name");
	}

	public String getParamater(String key) {
		return this.parameters.get(key);
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

	public static String getFingerprintUri(String base, List<XmppUri.Fingerprint> fingerprints, char separator) {
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
				builder.append(separator);
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

		@NonNull
		@Override
		public String toString() {
			return type.toString() + ": " + fingerprint + (deviceId != 0 ? " " + deviceId : "");
		}
	}

	private static String lameUrlDecode(String url) {
		return url.replace("%23", "#").replace("%25", "%");
	}

	public static String lameUrlEncode(String url) {
		return url.replace("%", "%25").replace("#", "%23");
	}
}
