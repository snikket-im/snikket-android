package eu.siacs.conversations.utils;

import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;

import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class XmppUri {

	protected String jid;
	protected boolean muc;
	protected String fingerprint;

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
			fingerprint = parseFingerprint(uri.getQuery());
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

	protected  String parseFingerprint(String query) {
		if (query == null) {
			return null;
		} else {
			final String NEEDLE = "otr-fingerprint=";
			int index = query.indexOf(NEEDLE);
			if (index >= 0 && query.length() >= (NEEDLE.length() + index + 40)) {
				return query.substring(index + NEEDLE.length(), index + NEEDLE.length() + 40);
			} else {
				return null;
			}
		}
	}

	public Jid getJid() {
		try {
			return this.jid == null ? null :Jid.fromString(this.jid.toLowerCase());
		} catch (InvalidJidException e) {
			return null;
		}
	}

	public String getFingerprint() {
		return this.fingerprint;
	}
}
