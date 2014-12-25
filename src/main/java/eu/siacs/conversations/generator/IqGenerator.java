package eu.siacs.conversations.generator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class IqGenerator extends AbstractGenerator {

	public IqGenerator(final XmppConnectionService service) {
		super(service);
	}

	public IqPacket discoResponse(final IqPacket request) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE_RESULT);
		packet.setId(request.getId());
		packet.setTo(request.getFrom());
		final Element query = packet.addChild("query",
				"http://jabber.org/protocol/disco#info");
		query.setAttribute("node", request.query().getAttribute("node"));
		final Element identity = query.addChild("identity");
		identity.setAttribute("category", "client");
		identity.setAttribute("type", this.IDENTITY_TYPE);
		identity.setAttribute("name", IDENTITY_NAME);
		final List<String> features = Arrays.asList(FEATURES);
		Collections.sort(features);
		for (final String feature : features) {
			query.addChild("feature").setAttribute("var", feature);
		}
		return packet;
	}

	protected IqPacket publish(final String node, final Element item) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		final Element pubsub = packet.addChild("pubsub",
				"http://jabber.org/protocol/pubsub");
		final Element publish = pubsub.addChild("publish");
		publish.setAttribute("node", node);
		publish.addChild(item);
		return packet;
	}

	protected IqPacket retrieve(String node, Element item) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
		final Element pubsub = packet.addChild("pubsub",
				"http://jabber.org/protocol/pubsub");
		final Element items = pubsub.addChild("items");
		items.setAttribute("node", node);
		if (item != null) {
			items.addChild(item);
		}
		return packet;
	}

	public IqPacket publishAvatar(Avatar avatar) {
		final Element item = new Element("item");
		item.setAttribute("id", avatar.sha1sum);
		final Element data = item.addChild("data", "urn:xmpp:avatar:data");
		data.setContent(avatar.image);
		return publish("urn:xmpp:avatar:data", item);
	}

	public IqPacket publishAvatarMetadata(final Avatar avatar) {
		final Element item = new Element("item");
		item.setAttribute("id", avatar.sha1sum);
		final Element metadata = item
			.addChild("metadata", "urn:xmpp:avatar:metadata");
		final Element info = metadata.addChild("info");
		info.setAttribute("bytes", avatar.size);
		info.setAttribute("id", avatar.sha1sum);
		info.setAttribute("height", avatar.height);
		info.setAttribute("width", avatar.height);
		info.setAttribute("type", avatar.type);
		return publish("urn:xmpp:avatar:metadata", item);
	}

	public IqPacket retrieveAvatar(final Avatar avatar) {
		final Element item = new Element("item");
		item.setAttribute("id", avatar.sha1sum);
		final IqPacket packet = retrieve("urn:xmpp:avatar:data", item);
		packet.setTo(avatar.owner);
		return packet;
	}

	public IqPacket retrieveAvatarMetaData(final Jid to) {
		final IqPacket packet = retrieve("urn:xmpp:avatar:metadata", null);
		if (to != null) {
			packet.setTo(to);
		}
		return packet;
	}

	public IqPacket queryMessageArchiveManagement(final MessageArchiveService.Query mam) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		final Element query = packet.query("urn:xmpp:mam:0");
		query.setAttribute("queryid",mam.getQueryId());
		final Data data = new Data();
		data.setFormType("urn:xmpp:mam:0");
		if (mam.getWith()!=null) {
			data.put("with", mam.getWith().toString());
		}
		data.put("start",getTimestamp(mam.getStart()));
		data.put("end",getTimestamp(mam.getEnd()));
		query.addChild(data);
		if (mam.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
			query.addChild("set", "http://jabber.org/protocol/rsm").addChild("before").setContent(mam.getReference());
		} else if (mam.getReference() != null) {
			query.addChild("set", "http://jabber.org/protocol/rsm").addChild("after").setContent(mam.getReference());
		}
		return packet;
	}
	public IqPacket generateGetBlockList() {
		final IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.addChild("blocklist", Xmlns.BLOCKING);

		return iq;
	}

	public IqPacket generateSetBlockRequest(final Jid jid) {
		final IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		final Element block = iq.addChild("block", Xmlns.BLOCKING);
		block.addChild("item").setAttribute("jid", jid.toBareJid().toString());
		return iq;
	}

	public IqPacket generateSetUnblockRequest(final Jid jid) {
		final IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		final Element block = iq.addChild("unblock", Xmlns.BLOCKING);
		block.addChild("item").setAttribute("jid", jid.toBareJid().toString());
		return iq;
	}

	public IqPacket generateSetPassword(final Account account, final String newPassword) {
		final IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
		packet.setTo(account.getServer());
		final Element query = packet.addChild("query", Xmlns.REGISTER);
		final Jid jid = account.getJid();
		query.addChild("username").setContent(jid.getLocalpart());
		query.addChild("password").setContent(newPassword);
		return packet;
	}
}
