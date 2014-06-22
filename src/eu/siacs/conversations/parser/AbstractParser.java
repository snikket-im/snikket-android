package eu.siacs.conversations.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;

public abstract class AbstractParser {
	
	protected XmppConnectionService mXmppConnectionService;

	protected AbstractParser(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}
	
	protected long getTimestamp(Element packet) {
		long now = System.currentTimeMillis();
		if (packet.hasChild("delay")) {
			try {
				String stamp = packet.findChild("delay").getAttribute(
						"stamp");
				stamp = stamp.replace("Z", "+0000");
				if (stamp.contains(".")) {
					Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US)
					.parse(stamp);
					if (now<date.getTime()) {
						return now;
					} else {
						return date.getTime();
					}
				} else {
					Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US)
							.parse(stamp);
					if (now<date.getTime()) {
						return now;
					} else {
						return date.getTime();
					}
				}
			} catch (ParseException e) {
				return now;
			}
		} else {
			return now;
		}
	}
	
	protected void updateLastseen(Element packet, Account account, boolean presenceOverwrite) {
		String[] fromParts = packet.getAttribute("from").split("/");
		String from = fromParts[0];
		String presence = null;
		if (fromParts.length >= 2) {
			presence = fromParts[1];
		}
		Contact contact = account.getRoster().getContact(from);
		long timestamp = getTimestamp(packet);
		if (timestamp >= contact.lastseen.time) {
			contact.lastseen.time = timestamp;
			if ((presence!=null)&&(presenceOverwrite)) {
				contact.lastseen.presence = presence;
			}
		}
	}
}
