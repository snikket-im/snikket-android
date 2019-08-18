package eu.siacs.conversations.generator;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;

public abstract class AbstractGenerator {
	private final String[] FEATURES = {
			"urn:xmpp:jingle:1",
			Content.Version.FT_3.getNamespace(),
			Content.Version.FT_4.getNamespace(),
			Content.Version.FT_5.getNamespace(),
			Namespace.JINGLE_TRANSPORTS_S5B,
			Namespace.JINGLE_TRANSPORTS_IBB,
			"http://jabber.org/protocol/muc",
			"jabber:x:conference",
			Namespace.OOB,
			"http://jabber.org/protocol/caps",
			"http://jabber.org/protocol/disco#info",
			"urn:xmpp:avatar:metadata+notify",
			Namespace.NICK+"+notify",
			Namespace.BOOKMARKS+"+notify",
			"urn:xmpp:ping",
			"jabber:iq:version",
			"http://jabber.org/protocol/chatstates"
	};
	private final String[] MESSAGE_CONFIRMATION_FEATURES = {
			"urn:xmpp:chat-markers:0",
			"urn:xmpp:receipts"
	};
	private final String[] MESSAGE_CORRECTION_FEATURES = {
			"urn:xmpp:message-correct:0"
	};
	private final String[] PRIVACY_SENSITIVE = {
			"urn:xmpp:time" //XEP-0202: Entity Time leaks time zone
	};
	private String mVersion = null;

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

	protected XmppConnectionService mXmppConnectionService;

	AbstractGenerator(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	String getIdentityVersion() {
		if (mVersion == null) {
			this.mVersion = PhoneHelper.getVersionName(mXmppConnectionService);
		}
		return this.mVersion;
	}

	String getIdentityName() {
		return mXmppConnectionService.getString(R.string.app_name);
	}

	public String getUserAgent() {
		return mXmppConnectionService.getString(R.string.app_name) + '/' + getIdentityVersion();
	}

	String getIdentityType() {
		if ("chromium".equals(android.os.Build.BRAND)) {
			return "pc";
		} else {
			return mXmppConnectionService.getString(R.string.default_resource).toLowerCase();
		}
	}

	String getCapHash(final Account account) {
		StringBuilder s = new StringBuilder();
		s.append("client/").append(getIdentityType()).append("//").append(getIdentityName()).append('<');
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}

		for (String feature : getFeatures(account)) {
			s.append(feature).append('<');
		}
		byte[] sha1 = md.digest(s.toString().getBytes());
		return new String(Base64.encode(sha1, Base64.DEFAULT)).trim();
	}

	public static String getTimestamp(long time) {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
		return DATE_FORMAT.format(time);
	}

	public List<String> getFeatures(Account account) {
		ArrayList<String> features = new ArrayList<>(Arrays.asList(FEATURES));
		if (mXmppConnectionService.confirmMessages()) {
			features.addAll(Arrays.asList(MESSAGE_CONFIRMATION_FEATURES));
		}
		if (mXmppConnectionService.allowMessageCorrection()) {
			features.addAll(Arrays.asList(MESSAGE_CORRECTION_FEATURES));
		}
		if (Config.supportOmemo()) {
			features.add(AxolotlService.PEP_DEVICE_LIST_NOTIFY);
		}
		if (!mXmppConnectionService.useTorToConnect() && !account.isOnion()) {
			features.addAll(Arrays.asList(PRIVACY_SENSITIVE));
		}
		if (mXmppConnectionService.broadcastLastActivity()) {
			features.add(Namespace.IDLE);
		}
		Collections.sort(features);
		return features;
	}
}
