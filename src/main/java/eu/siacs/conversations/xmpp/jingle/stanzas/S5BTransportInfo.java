package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import java.util.Collection;
import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.JingleCandidate;

public class S5BTransportInfo extends GenericTransportInfo {

    private S5BTransportInfo(final String name, final String xmlns) {
        super(name, xmlns);
    }

    public String getTransportId() {
        return this.getAttribute("sid");
    }

    public S5BTransportInfo(final String transportId, final Collection<JingleCandidate> candidates) {
        super("transport", Namespace.JINGLE_TRANSPORTS_S5B);
        Preconditions.checkNotNull(transportId,"transport id must not be null");
        for(JingleCandidate candidate : candidates) {
            this.addChild(candidate.toElement());
        }
        this.setAttribute("sid", transportId);
    }

    public S5BTransportInfo(final String transportId, final Element child) {
        super("transport", Namespace.JINGLE_TRANSPORTS_S5B);
        Preconditions.checkNotNull(transportId,"transport id must not be null");
        this.addChild(child);
        this.setAttribute("sid", transportId);
    }

    public List<JingleCandidate> getCandidates() {
        return JingleCandidate.parse(this.getChildren());
    }

    public static S5BTransportInfo upgrade(final Element element) {
        Preconditions.checkArgument("transport".equals(element.getName()), "Name of provided element is not transport");
        Preconditions.checkArgument(Namespace.JINGLE_TRANSPORTS_S5B.equals(element.getNamespace()), "Element does not match s5b transport namespace");
        final S5BTransportInfo transportInfo = new S5BTransportInfo("transport", Namespace.JINGLE_TRANSPORTS_S5B);
        transportInfo.setAttributes(element.getAttributes());
        transportInfo.setChildren(element.getChildren());
        return transportInfo;
    }
}
