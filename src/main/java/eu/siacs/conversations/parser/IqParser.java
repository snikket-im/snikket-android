package eu.siacs.conversations.parser;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class IqParser extends AbstractParser implements OnIqPacketReceived {

	public IqParser(final XmppConnectionService service) {
		super(service);
	}

	private void rosterItems(final Account account, final Element query) {
		final String version = query.getAttribute("ver");
		if (version != null) {
			account.getRoster().setVersion(version);
		}
		for (final Element item : query.getChildren()) {
			if (item.getName().equals("item")) {
				final Jid jid = item.getAttributeAsJid("jid");
				if (jid == null) {
					continue;
				}
				final String name = item.getAttribute("name");
				final String subscription = item.getAttribute("subscription");
				final Contact contact = account.getRoster().getContact(jid);
				if (!contact.getOption(Contact.Options.DIRTY_PUSH)) {
					contact.setServerName(name);
					contact.parseGroupsFromElement(item);
				}
				if (subscription != null) {
					if (subscription.equals("remove")) {
						contact.resetOption(Contact.Options.IN_ROSTER);
						contact.resetOption(Contact.Options.DIRTY_DELETE);
						contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
					} else {
						contact.setOption(Contact.Options.IN_ROSTER);
						contact.resetOption(Contact.Options.DIRTY_PUSH);
						contact.parseSubscriptionFromElement(item);
					}
				}
				mXmppConnectionService.getAvatarService().clear(contact);
			}
		}
		mXmppConnectionService.updateConversationUi();
		mXmppConnectionService.updateRosterUi();
	}

	public String avatarData(final IqPacket packet) {
		final Element pubsub = packet.findChild("pubsub",
				"http://jabber.org/protocol/pubsub");
		if (pubsub == null) {
			return null;
		}
		final Element items = pubsub.findChild("items");
		if (items == null) {
			return null;
		}
		return super.avatarData(items);
	}

	public static boolean fromServer(final Account account, final IqPacket packet) {
		return packet.getFrom() == null || packet.getFrom().equals(account.getServer()) || packet.getFrom().equals(account.getJid().toBareJid());
	}

	@Override
	public void onIqPacketReceived(final Account account, final IqPacket packet) {
		if (packet.hasChild("query", Xmlns.ROSTER) && fromServer(account, packet)) {
			final Element query = packet.findChild("query");
			// If this is in response to a query for the whole roster:
			if (packet.getType() == IqPacket.TYPE_RESULT) {
				account.getRoster().markAllAsNotInRoster();
			}
			this.rosterItems(account, query);
		} else if ((packet.hasChild("block", Xmlns.BLOCKING) || packet.hasChild("blocklist", Xmlns.BLOCKING)) &&
				fromServer(account, packet)) {
			// Block list or block push.
			Log.d(Config.LOGTAG, "Received blocklist update from server");
			final Element blocklist = packet.findChild("blocklist", Xmlns.BLOCKING);
			final Element block = packet.findChild("block", Xmlns.BLOCKING);
			final Collection<Element> items = blocklist != null ? blocklist.getChildren() :
				(block != null ? block.getChildren() : null);
			// If this is a response to a blocklist query, clear the block list and replace with the new one.
			// Otherwise, just update the existing blocklist.
			if (packet.getType() == IqPacket.TYPE_RESULT) {
				account.clearBlocklist();
			}
			if (items != null) {
				final Collection<Jid> jids = new ArrayList<>(items.size());
				// Create a collection of Jids from the packet
				for (final Element item : items) {
					if (item.getName().equals("item")) {
						final Jid jid = item.getAttributeAsJid("jid");
						if (jid != null) {
							jids.add(jid);
						}
					}
				}
				account.getBlocklist().addAll(jids);
			}
			// Update the UI
			mXmppConnectionService.updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
		} else if (packet.hasChild("unblock", Xmlns.BLOCKING) &&
				fromServer(account, packet) && packet.getType() == IqPacket.TYPE_SET) {
			Log.d(Config.LOGTAG, "Received unblock update from server");
			final Collection<Element> items = packet.findChild("unblock", Xmlns.BLOCKING).getChildren();
			if (items.size() == 0) {
				// No children to unblock == unblock all
				account.getBlocklist().clear();
			} else {
				final Collection<Jid> jids = new ArrayList<>(items.size());
				for (final Element item : items) {
					if (item.getName().equals("item")) {
						final Jid jid = item.getAttributeAsJid("jid");
						if (jid != null) {
							jids.add(jid);
						}
					}
				}
				account.getBlocklist().removeAll(jids);
			}
			mXmppConnectionService.updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
		} else if (packet.hasChild("open", "http://jabber.org/protocol/ibb")
				|| packet.hasChild("data", "http://jabber.org/protocol/ibb")) {
			mXmppConnectionService.getJingleConnectionManager()
				.deliverIbbPacket(account, packet);
		} else if (packet.hasChild("query", "http://jabber.org/protocol/disco#info")) {
			final IqPacket response = mXmppConnectionService.getIqGenerator()
				.discoResponse(packet);
			account.getXmppConnection().sendIqPacket(response, null);
		} else if (packet.hasChild("ping", "urn:xmpp:ping")) {
			final IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
			mXmppConnectionService.sendIqPacket(account, response, null);
		} else {
			if ((packet.getType() == IqPacket.TYPE_GET)
					|| (packet.getType() == IqPacket.TYPE_SET)) {
				final IqPacket response = packet.generateRespone(IqPacket.TYPE_ERROR);
				final Element error = response.addChild("error");
				error.setAttribute("type", "cancel");
				error.addChild("feature-not-implemented",
						"urn:ietf:params:xml:ns:xmpp-stanzas");
				account.getXmppConnection().sendIqPacket(response, null);
					}
		}
	}

}
