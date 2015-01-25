package eu.siacs.conversations.generator;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;

import eu.siacs.conversations.services.XmppConnectionService;

public abstract class AbstractGenerator {
	public final String[] FEATURES = {
			"urn:xmpp:jingle:1",
			"urn:xmpp:jingle:apps:file-transfer:3",
			"urn:xmpp:jingle:transports:s5b:1",
			"urn:xmpp:jingle:transports:ibb:1",
			"http://jabber.org/protocol/muc",
			"jabber:x:conference",
			"http://jabber.org/protocol/caps",
			"http://jabber.org/protocol/disco#info",
			"urn:xmpp:avatar:metadata+notify",
			"urn:xmpp:ping"};
	public final String[] MESSAGE_CONFIRMATION_FEATURES = {
			"urn:xmpp:chat-markers:0",
			"urn:xmpp:receipts"
	};
	public final String IDENTITY_NAME = "Conversations 1.0-beta2";
	public final String IDENTITY_TYPE = "phone";

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);

	protected XmppConnectionService mXmppConnectionService;

	protected AbstractGenerator(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public String getCapHash() {
		StringBuilder s = new StringBuilder();
		s.append("client/" + IDENTITY_TYPE + "//" + IDENTITY_NAME + "<");
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
		ArrayList<String> features = new ArrayList<>();
		features.addAll(Arrays.asList(FEATURES));
		if (mXmppConnectionService.confirmMessages()) {
			features.addAll(Arrays.asList(MESSAGE_CONFIRMATION_FEATURES));
		}
		Collections.sort(features);
		for (String feature : features) {
			s.append(feature + "<");
		}
		byte[] sha1 = md.digest(s.toString().getBytes());
		return new String(Base64.encode(sha1, Base64.DEFAULT)).trim();
	}

	public static String getTimestamp(long time) {
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
		return DATE_FORMAT.format(time);
	}
}
