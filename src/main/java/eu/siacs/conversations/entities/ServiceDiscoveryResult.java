package eu.siacs.conversations.entities;

import java.util.List;
import java.util.ArrayList;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class ServiceDiscoveryResult {
	public static class Identity {
		protected final String category;
		protected final String type;
		protected final String name;

		public Identity(final String category, final String type, final String name) {
			this.category = category;
			this.type = type;
			this.name = name;
		}

		public Identity(final Element el) {
			this.category = el.getAttribute("category");
			this.type = el.getAttribute("type");
			this.name = el.getAttribute("name");
		}

		public String getCategory() {
			return this.category;
		}

		public String getType() {
			return this.type;
		}

		public String getName() {
			return this.name;
		}
	}

	protected final List<Identity> identities;
	protected final List<String> features;

	public ServiceDiscoveryResult(final List<Identity> identities, final List<String> features) {
		this.identities = identities;
		this.features = features;
	}

	public ServiceDiscoveryResult(final IqPacket packet) {
		this.identities = new ArrayList<>();
		this.features = new ArrayList<>();

		final List<Element> elements = packet.query().getChildren();

		for (final Element element : elements) {
			if (element.getName().equals("identity")) {
				Identity id = new Identity(element);
				if (id.getType() != null && id.getCategory() != null) {
					identities.add(id);
				}
			} else if (element.getName().equals("feature")) {
				features.add(element.getAttribute("var"));
			}
		}
	}

	public List<Identity> getIdentities() {
		return this.identities;
	}

	public List<String> getFeatures() {
		return this.features;
	}

	public boolean hasIdentity(String category, String type) {
		for(Identity id : this.getIdentities()) {
			if((category == null || id.getCategory().equals(category)) &&
			   (type == null || id.getType().equals(type))) {
				return true;
			}
		}

		return false;
	}
}
