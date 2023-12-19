package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.util.Log;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.transports.SocksByteStreamsTransport;

import java.util.Collection;
import java.util.List;

public class SocksByteStreamsTransportInfo extends GenericTransportInfo {

    private SocksByteStreamsTransportInfo() {
        super("transport", Namespace.JINGLE_TRANSPORTS_S5B);
    }

    public String getTransportId() {
        return this.getAttribute("sid");
    }

    public SocksByteStreamsTransportInfo(
            final String transportId,
            final Collection<SocksByteStreamsTransport.Candidate> candidates) {
        super("transport", Namespace.JINGLE_TRANSPORTS_S5B);
        Preconditions.checkNotNull(transportId, "transport id must not be null");
        for (SocksByteStreamsTransport.Candidate candidate : candidates) {
            this.addChild(candidate.asElement());
        }
        this.setAttribute("sid", transportId);
    }

    public TransportInfo getTransportInfo() {
        if (hasChild("proxy-error")) {
            return new ProxyError();
        } else if (hasChild("candidate-error")) {
            return new CandidateError();
        } else if (hasChild("candidate-used")) {
            final Element candidateUsed = findChild("candidate-used");
            final String cid = candidateUsed == null ? null : candidateUsed.getAttribute("cid");
            if (Strings.isNullOrEmpty(cid)) {
                return null;
            } else {
                return new CandidateUsed(cid);
            }
        } else if (hasChild("activated")) {
            final Element activated = findChild("activated");
            final String cid = activated == null ? null : activated.getAttribute("cid");
            if (Strings.isNullOrEmpty(cid)) {
                return null;
            } else {
                return new Activated(cid);
            }
        } else {
            return null;
        }
    }

    public List<SocksByteStreamsTransport.Candidate> getCandidates() {
        final ImmutableList.Builder<SocksByteStreamsTransport.Candidate> candidateBuilder =
                new ImmutableList.Builder<>();
        for (final Element child : this.children) {
            if ("candidate".equals(child.getName())
                    && Namespace.JINGLE_TRANSPORTS_S5B.equals(child.getNamespace())) {
                try {
                    candidateBuilder.add(SocksByteStreamsTransport.Candidate.of(child));
                } catch (final Exception e) {
                    Log.d(Config.LOGTAG, "skip over broken candidate", e);
                }
            }
        }
        return candidateBuilder.build();
    }

    public static SocksByteStreamsTransportInfo upgrade(final Element element) {
        Preconditions.checkArgument(
                "transport".equals(element.getName()), "Name of provided element is not transport");
        Preconditions.checkArgument(
                Namespace.JINGLE_TRANSPORTS_S5B.equals(element.getNamespace()),
                "Element does not match s5b transport namespace");
        final SocksByteStreamsTransportInfo transportInfo = new SocksByteStreamsTransportInfo();
        transportInfo.setAttributes(element.getAttributes());
        transportInfo.setChildren(element.getChildren());
        return transportInfo;
    }

    public String getDestinationAddress() {
        return this.getAttribute("dstaddr");
    }

    public abstract static class TransportInfo {}

    public static class CandidateUsed extends TransportInfo {
        public final String cid;

        public CandidateUsed(String cid) {
            this.cid = cid;
        }
    }

    public static class Activated extends TransportInfo {
        public final String cid;

        public Activated(final String cid) {
            this.cid = cid;
        }
    }

    public static class CandidateError extends TransportInfo {}

    public static class ProxyError extends TransportInfo {}
}
