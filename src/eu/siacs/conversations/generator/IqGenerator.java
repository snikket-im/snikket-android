package eu.siacs.conversations.generator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class IqGenerator extends AbstractGenerator {

	

	public IqPacket discoResponse(IqPacket request) {
		IqPacket packet = new IqPacket(IqPacket.TYPE_RESULT);
		packet.setId(request.getId());
		packet.setTo(request.getFrom());
		Element query = packet.addChild("query","http://jabber.org/protocol/disco#info");
		query.setAttribute("node", request.query().getAttribute("node"));
		Element identity = query.addChild("identity");
		identity.setAttribute("category","client");
		identity.setAttribute("type", this.IDENTITY_TYPE);
		identity.setAttribute("name", IDENTITY_NAME);
		List<String> features = Arrays.asList(FEATURES);
		Collections.sort(features);
		for(String feature : features) {
			query.addChild("feature").setAttribute("var",feature);
		}
		return packet;
	}
}
