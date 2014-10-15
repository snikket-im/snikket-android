package eu.siacs.conversations.xmpp.jingle;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import android.annotation.SuppressLint;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleConnectionManager extends AbstractConnectionManager {
	private List<JingleConnection> connections = new CopyOnWriteArrayList<JingleConnection>();

	private HashMap<String, JingleCandidate> primaryCandidates = new HashMap<String, JingleCandidate>();

	@SuppressLint("TrulyRandom")
	private SecureRandom random = new SecureRandom();

	public JingleConnectionManager(XmppConnectionService service) {
		super(service);
	}

	public void deliverPacket(Account account, JinglePacket packet) {
		if (packet.isAction("session-initiate")) {
			JingleConnection connection = new JingleConnection(this);
			connection.init(account, packet);
			connections.add(connection);
		} else {
			for (JingleConnection connection : connections) {
				if (connection.getAccountJid().equals(account.getFullJid())
						&& connection.getSessionId().equals(
								packet.getSessionId())
						&& connection.getCounterPart().equals(packet.getFrom())) {
					connection.deliverPacket(packet);
					return;
				}
			}
			account.getXmppConnection().sendIqPacket(
					packet.generateRespone(IqPacket.TYPE_ERROR), null);
		}
	}

	public JingleConnection createNewConnection(Message message) {
		JingleConnection connection = new JingleConnection(this);
		connection.init(message);
		this.connections.add(connection);
		return connection;
	}

	public JingleConnection createNewConnection(JinglePacket packet) {
		JingleConnection connection = new JingleConnection(this);
		this.connections.add(connection);
		return connection;
	}

	public void finishConnection(JingleConnection connection) {
		this.connections.remove(connection);
	}

	public void getPrimaryCandidate(Account account,
			final OnPrimaryCandidateFound listener) {
		if (!this.primaryCandidates.containsKey(account.getJid())) {
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
									JingleCandidate candidate = new JingleCandidate(
											nextRandomId(), true);
									candidate.setHost(streamhost
											.getAttribute("host"));
									candidate.setPort(Integer
											.parseInt(streamhost
													.getAttribute("port")));
									candidate
											.setType(JingleCandidate.TYPE_PROXY);
									candidate.setJid(proxy);
									candidate.setPriority(655360 + 65535);
									primaryCandidates.put(account.getJid(),
											candidate);
									listener.onPrimaryCandidateFound(true,
											candidate);
								} else {
									listener.onPrimaryCandidateFound(false,
											null);
								}
							}
						});
			} else {
				listener.onPrimaryCandidateFound(false, null);
			}

		} else {
			listener.onPrimaryCandidateFound(true,
					this.primaryCandidates.get(account.getJid()));
		}
	}

	public String nextRandomId() {
		return new BigInteger(50, random).toString(32);
	}

	public void deliverIbbPacket(Account account, IqPacket packet) {
		String sid = null;
		Element payload = null;
		if (packet.hasChild("open", "http://jabber.org/protocol/ibb")) {
			payload = packet
					.findChild("open", "http://jabber.org/protocol/ibb");
			sid = payload.getAttribute("sid");
		} else if (packet.hasChild("data", "http://jabber.org/protocol/ibb")) {
			payload = packet
					.findChild("data", "http://jabber.org/protocol/ibb");
			sid = payload.getAttribute("sid");
		}
		if (sid != null) {
			for (JingleConnection connection : connections) {
				if (connection.hasTransportId(sid)) {
					JingleTransport transport = connection.getTransport();
					if (transport instanceof JingleInbandTransport) {
						JingleInbandTransport inbandTransport = (JingleInbandTransport) transport;
						inbandTransport.deliverPayload(packet, payload);
						return;
					}
				}
			}
			Log.d(Config.LOGTAG,
					"couldnt deliver payload: " + payload.toString());
		} else {
			Log.d(Config.LOGTAG, "no sid found in incomming ibb packet");
		}
	}

	public void cancelInTransmission() {
		for (JingleConnection connection : this.connections) {
			if (connection.getJingleStatus() == JingleConnection.JINGLE_STATUS_TRANSMITTING) {
				connection.cancel();
			}
		}
	}
}
