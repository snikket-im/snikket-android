package eu.siacs.conversations.xmpp.jingle;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import android.util.Log;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleConnectionManager {

	private XmppConnectionService xmppConnectionService;

	private List<JingleConnection> connections = new ArrayList<JingleConnection>(); // make
																					// concurrent

	private ConcurrentHashMap<String, Element> primaryCanditates = new ConcurrentHashMap<String, Element>();

	private SecureRandom random = new SecureRandom();

	public JingleConnectionManager(XmppConnectionService service) {
		this.xmppConnectionService = service;
	}

	public void deliverPacket(Account account, JinglePacket packet) {
		for (JingleConnection connection : connections) {
			if (connection.getAccountJid().equals(account.getJid()) && connection
					.getSessionId().equals(packet.getSessionId()) && connection
					.getCounterPart().equals(packet.getFrom())) {
				connection.deliverPacket(packet);
				return;
			}
		}
		Log.d("xmppService","delivering packet failed "+packet.toString());
	}

	public JingleConnection createNewConnection(Message message) {
		Account account = message.getConversation().getAccount();
		JingleConnection connection = new JingleConnection(this);
		connection.init(message);
		connections.add(connection);
		return connection;
	}

	public JingleConnection createNewConnection(JinglePacket packet) {
		JingleConnection connection = new JingleConnection(this);
		connections.add(connection);
		return connection;
	}

	private String generateInternalId(String account, String counterpart,
			String sid) {
		return account + "#" + counterpart + "#" + sid;

	}

	public XmppConnectionService getXmppConnectionService() {
		return this.xmppConnectionService;
	}

	public void getPrimaryCanditate(Account account,
			final OnPrimaryCanditateFound listener) {
		if (!this.primaryCanditates.containsKey(account.getJid())) {
			String xmlns = "http://jabber.org/protocol/bytestreams";
			final String proxy = account.getXmppConnection()
					.findDiscoItemByFeature(xmlns);
			if (proxy != null) {
				IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
				iq.setTo(proxy);
				iq.query(xmlns);
				account.getXmppConnection().sendIqPacket(iq,
						new OnIqPacketReceived() {

							@Override
							public void onIqPacketReceived(Account account,
									IqPacket packet) {
								Element streamhost = packet
										.query()
										.findChild("streamhost",
												"http://jabber.org/protocol/bytestreams");
								if (streamhost != null) {
									Log.d("xmppService", "streamhost found "
											+ streamhost.toString());
									Element canditate = new Element("canditate");
									canditate.setAttribute("cid",
											nextRandomId());
									canditate.setAttribute("host",
											streamhost.getAttribute("host"));
									canditate.setAttribute("port",
											streamhost.getAttribute("port"));
									canditate.setAttribute("type", "proxy");
									canditate.setAttribute("jid", proxy);
									canditate
											.setAttribute("priority", "655360");
									primaryCanditates.put(account.getJid(),
											canditate);
									listener.onPrimaryCanditateFound(true,
											canditate);
								} else {
									listener.onPrimaryCanditateFound(false,
											null);
								}
							}
						});
			} else {
				listener.onPrimaryCanditateFound(false, null);
			}

		} else {
			listener.onPrimaryCanditateFound(true,
					this.primaryCanditates.get(account.getJid()));
		}
	}

	public String nextRandomId() {
		return new BigInteger(50, random).toString(32);
	}
}
