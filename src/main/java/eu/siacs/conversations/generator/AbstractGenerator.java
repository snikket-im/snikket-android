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
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.PhoneHelper;

public abstract class AbstractGenerator {
	private final String[] FEATURES = {
			"urn:xmpp:jingle:1",
			"urn:xmpp:jingle:apps:file-transfer:3",
			"urn:xmpp:jingle:transports:s5b:1",
			"urn:xmpp:jingle:transports:ibb:1",
			"http://jabber.org/protocol/muc",
			"jabber:x:conference",
			"http://jabber.org/protocol/caps",
			"http://jabber.org/protocol/disco#info",
			"urn:xmpp:avatar:metadata+notify",
			"http://jabber.org/protocol/nick+notify",
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
	private String mVersion = null;
	protected final String IDENTITY_NAME = "Conversations";
	protected final String IDENTITY_TYPE = "phone";

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

	protected XmppConnectionService mXmppConnectionService;

	protected AbstractGenerator(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	protected String getIdentityVersion() {
		if (mVersion == null) {
			this.mVersion = PhoneHelper.getVersionName(mXmppConnectionService);
		}
		return this.mVersion;
	}

	public String getIdentityName() {
		return IDENTITY_NAME + " " + getIdentityVersion();
	}

	public String getCapHash() {
		StringBuilder s = new StringBuilder();
		s.append("client/" + IDENTITY_TYPE + "//" + getIdentityName() + "<");
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}

		for (String feature : getFeatures()) {
			s.append(feature + "<");
		}
		byte[] sha1 = md.digest(s.toString().getBytes());
		return new String(Base64.encode(sha1, Base64.DEFAULT)).trim();
	}

	public static String getTimestamp(long time) {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
		return DATE_FORMAT.format(time);
	}

	public List<String> getFeatures() {
		ArrayList<String> features = new ArrayList<>();
		features.addAll(Arrays.asList(FEATURES));
		if (mXmppConnectionService.confirmMessages()) {
			features.addAll(Arrays.asList(MESSAGE_CONFIRMATION_FEATURES));
		}
		if (mXmppConnectionService.allowMessageCorrection()) {
			features.addAll(Arrays.asList(MESSAGE_CORRECTION_FEATURES));
		}
		if (Config.supportOmemo()) {
			features.add(AxolotlService.PEP_DEVICE_LIST_NOTIFY);
		}
		Collections.sort(features);
		return features;
	}
}
