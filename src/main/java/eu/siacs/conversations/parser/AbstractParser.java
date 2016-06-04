package eu.siacs.conversations.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public abstract class AbstractParser {

	protected XmppConnectionService mXmppConnectionService;

	protected AbstractParser(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public static Long parseTimestamp(Element element, Long d) {
		Element delay = element.findChild("delay","urn:xmpp:delay");
		if (delay != null) {
			String stamp = delay.getAttribute("stamp");
			if (stamp != null) {
				try {
					return AbstractParser.parseTimestamp(delay.getAttribute("stamp"));
				} catch (ParseException e) {
					return d;
				}
			}
		}
		return d;
	}

	public static long parseTimestamp(Element element) {
		return parseTimestamp(element, System.currentTimeMillis());
	}

	public static long parseTimestamp(String timestamp) throws ParseException {
		timestamp = timestamp.replace("Z", "+0000");
		SimpleDateFormat dateFormat;
		long ms;
		if (timestamp.charAt(19) == '.' && timestamp.length() >= 25) {
			String millis = timestamp.substring(19,timestamp.length() - 5);
			try {
				double fractions = Double.parseDouble("0" + millis);
				ms = Math.round(1000 * fractions);
			} catch (NumberFormatException e) {
				ms = 0;
			}
		} else {
			ms = 0;
		}
		timestamp = timestamp.substring(0,19)+timestamp.substring(timestamp.length() -5,timestamp.length());
		dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US);
		return Math.min(dateFormat.parse(timestamp).getTime()+ms, System.currentTimeMillis());
	}

	protected void updateLastseen(final Account account, final Jid from) {
		final Contact contact = account.getRoster().getContact(from);
		contact.setLastPresence(from.isBareJid() ? "" : from.getResourcepart());
	}

	protected String avatarData(Element items) {
		Element item = items.findChild("item");
		if (item == null) {
			return null;
		}
		return item.findChildContent("data", "urn:xmpp:avatar:data");
	}

	public static MucOptions.User parseItem(Conversation conference, Element item) {
		final String local = conference.getJid().getLocalpart();
		final String domain = conference.getJid().getDomainpart();
		String affiliation = item.getAttribute("affiliation");
		String role = item.getAttribute("role");
		String nick = item.getAttribute("nick");
		Jid fullJid;
		try {
			fullJid = nick != null ? Jid.fromParts(local, domain, nick) : null;
		} catch (InvalidJidException e) {
			fullJid = null;
		}
		Jid realJid = item.getAttributeAsJid("jid");
		MucOptions.User user = new MucOptions.User(conference.getMucOptions(), nick == null ? null : fullJid);
		user.setRealJid(realJid);
		user.setAffiliation(affiliation);
		user.setRole(role);
		return user;
	}
}
