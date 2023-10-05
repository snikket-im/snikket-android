package eu.siacs.conversations.xmpp.jingle;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.OmemoVerifiedIceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

public class RtpContentMap {

    public final Group group;
    public final Map<String, DescriptionTransport> contents;

    public RtpContentMap(Group group, Map<String, DescriptionTransport> contents) {
        this.group = group;
        this.contents = contents;
    }

    public static RtpContentMap of(final JinglePacket jinglePacket) {
        final Map<String, DescriptionTransport> contents =
                DescriptionTransport.of(jinglePacket.getJingleContents());
        if (isOmemoVerified(contents)) {
            return new OmemoVerifiedRtpContentMap(jinglePacket.getGroup(), contents);
        } else {
            return new RtpContentMap(jinglePacket.getGroup(), contents);
        }
    }

    private static boolean isOmemoVerified(Map<String, DescriptionTransport> contents) {
        final Collection<DescriptionTransport> values = contents.values();
        if (values.size() == 0) {
            return false;
        }
        for (final DescriptionTransport descriptionTransport : values) {
            if (descriptionTransport.transport instanceof OmemoVerifiedIceUdpTransportInfo) {
                continue;
            }
            return false;
        }
        return true;
    }

    public static RtpContentMap of(
            final SessionDescription sessionDescription, final boolean isInitiator) {
        final ImmutableMap.Builder<String, DescriptionTransport> contentMapBuilder =
                new ImmutableMap.Builder<>();
        for (SessionDescription.Media media : sessionDescription.media) {
            final String id = Iterables.getFirst(media.attributes.get("mid"), null);
            Preconditions.checkNotNull(id, "media has no mid");
            contentMapBuilder.put(
                    id, DescriptionTransport.of(sessionDescription, isInitiator, media));
        }
        final String groupAttribute =
                Iterables.getFirst(sessionDescription.attributes.get("group"), null);
        final Group group = groupAttribute == null ? null : Group.ofSdpString(groupAttribute);
        return new RtpContentMap(group, contentMapBuilder.build());
    }

    public Set<Media> getMedia() {
        return Sets.newHashSet(
                Collections2.transform(
                        contents.values(),
                        input -> {
                            final RtpDescription rtpDescription =
                                    input == null ? null : input.description;
                            return rtpDescription == null
                                    ? Media.UNKNOWN
                                    : input.description.getMedia();
                        }));
    }

    public Set<Content.Senders> getSenders() {
        return ImmutableSet.copyOf(Collections2.transform(contents.values(), dt -> dt.senders));
    }

    public List<String> getNames() {
        return ImmutableList.copyOf(contents.keySet());
    }

    void requireContentDescriptions() {
        if (this.contents.size() == 0) {
            throw new IllegalStateException("No contents available");
        }
        for (Map.Entry<String, DescriptionTransport> entry : this.contents.entrySet()) {
            if (entry.getValue().description == null) {
                throw new IllegalStateException(
                        String.format("%s is lacking content description", entry.getKey()));
            }
        }
    }

    void requireDTLSFingerprint() {
        requireDTLSFingerprint(false);
    }

    void requireDTLSFingerprint(final boolean requireActPass) {
        if (this.contents.size() == 0) {
            throw new IllegalStateException("No contents available");
        }
        for (Map.Entry<String, DescriptionTransport> entry : this.contents.entrySet()) {
            final IceUdpTransportInfo transport = entry.getValue().transport;
            final IceUdpTransportInfo.Fingerprint fingerprint = transport.getFingerprint();
            if (fingerprint == null
                    || Strings.isNullOrEmpty(fingerprint.getContent())
                    || Strings.isNullOrEmpty(fingerprint.getHash())) {
                throw new SecurityException(
                        String.format(
                                "Use of DTLS-SRTP (XEP-0320) is required for content %s",
                                entry.getKey()));
            }
            final IceUdpTransportInfo.Setup setup = fingerprint.getSetup();
            if (setup == null) {
                throw new SecurityException(
                        String.format(
                                "Use of DTLS-SRTP (XEP-0320) is required for content %s but missing setup attribute",
                                entry.getKey()));
            }
            if (requireActPass && setup != IceUdpTransportInfo.Setup.ACTPASS) {
                throw new SecurityException(
                        "Initiator needs to offer ACTPASS as setup for DTLS-SRTP (XEP-0320)");
            }
        }
    }

    JinglePacket toJinglePacket(final JinglePacket.Action action, final String sessionId) {
        final JinglePacket jinglePacket = new JinglePacket(action, sessionId);
        if (this.group != null) {
            jinglePacket.addGroup(this.group);
        }
        for (Map.Entry<String, DescriptionTransport> entry : this.contents.entrySet()) {
            final DescriptionTransport descriptionTransport = entry.getValue();
            final Content content =
                    new Content(
                            Content.Creator.INITIATOR,
                            descriptionTransport.senders,
                            entry.getKey());
            if (descriptionTransport.description != null) {
                content.addChild(descriptionTransport.description);
            }
            content.addChild(descriptionTransport.transport);
            jinglePacket.addJingleContent(content);
        }
        return jinglePacket;
    }

    RtpContentMap transportInfo(
            final String contentName, final IceUdpTransportInfo.Candidate candidate) {
        final RtpContentMap.DescriptionTransport descriptionTransport = contents.get(contentName);
        final IceUdpTransportInfo transportInfo =
                descriptionTransport == null ? null : descriptionTransport.transport;
        if (transportInfo == null) {
            throw new IllegalArgumentException(
                    "Unable to find transport info for content name " + contentName);
        }
        final IceUdpTransportInfo newTransportInfo = transportInfo.cloneWrapper();
        newTransportInfo.addChild(candidate);
        return new RtpContentMap(
                null,
                ImmutableMap.of(
                        contentName,
                        new DescriptionTransport(
                                descriptionTransport.senders, null, newTransportInfo)));
    }

    RtpContentMap transportInfo() {
        return new RtpContentMap(
                null,
                Maps.transformValues(
                        contents,
                        dt ->
                                new DescriptionTransport(
                                        dt.senders, null, dt.transport.cloneWrapper())));
    }

    public IceUdpTransportInfo.Credentials getDistinctCredentials() {
        final Set<IceUdpTransportInfo.Credentials> allCredentials = getCredentials();
        final IceUdpTransportInfo.Credentials credentials =
                Iterables.getFirst(allCredentials, null);
        if (allCredentials.size() == 1 && credentials != null) {
            if (Strings.isNullOrEmpty(credentials.password)
                    || Strings.isNullOrEmpty(credentials.ufrag)) {
                throw new IllegalStateException("Credentials are missing password or ufrag");
            }
            return credentials;
        }
        throw new IllegalStateException("Content map does not have distinct credentials");
    }

    private Set<String> getCombinedIceOptions() {
        final Collection<List<String>> combinedIceOptions =
                Collections2.transform(contents.values(), dt -> dt.transport.getIceOptions());
        return ImmutableSet.copyOf(Iterables.concat(combinedIceOptions));
    }

    public Set<IceUdpTransportInfo.Credentials> getCredentials() {
        final Set<IceUdpTransportInfo.Credentials> credentials =
                ImmutableSet.copyOf(
                        Collections2.transform(
                                contents.values(), dt -> dt.transport.getCredentials()));
        if (credentials.isEmpty()) {
            throw new IllegalStateException("Content map does not have any credentials");
        }
        return credentials;
    }

    public IceUdpTransportInfo.Credentials getCredentials(final String contentName) {
        final DescriptionTransport descriptionTransport = this.contents.get(contentName);
        if (descriptionTransport == null) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unable to find transport info for content name %s", contentName));
        }
        return descriptionTransport.transport.getCredentials();
    }

    public IceUdpTransportInfo.Setup getDtlsSetup() {
        final Set<IceUdpTransportInfo.Setup> setups =
                ImmutableSet.copyOf(
                        Collections2.transform(
                                contents.values(), dt -> dt.transport.getFingerprint().getSetup()));
        final IceUdpTransportInfo.Setup setup = Iterables.getFirst(setups, null);
        if (setups.size() == 1 && setup != null) {
            return setup;
        }
        throw new IllegalStateException("Content map doesn't have distinct DTLS setup");
    }

    private DTLS getDistinctDtls() {
        final Set<DTLS> dtlsSet =
                ImmutableSet.copyOf(
                        Collections2.transform(
                                contents.values(),
                                dt -> {
                                    final IceUdpTransportInfo.Fingerprint fp =
                                            dt.transport.getFingerprint();
                                    return new DTLS(fp.getHash(), fp.getSetup(), fp.getContent());
                                }));
        final DTLS dtls = Iterables.getFirst(dtlsSet, null);
        if (dtlsSet.size() == 1 && dtls != null) {
            return dtls;
        }
        throw new IllegalStateException("Content map doesn't have distinct DTLS setup");
    }

    public boolean emptyCandidates() {
        int count = 0;
        for (DescriptionTransport descriptionTransport : contents.values()) {
            count += descriptionTransport.transport.getCandidates().size();
        }
        return count == 0;
    }

    public RtpContentMap modifiedCredentials(
            IceUdpTransportInfo.Credentials credentials, final IceUdpTransportInfo.Setup setup) {
        final ImmutableMap.Builder<String, DescriptionTransport> contentMapBuilder =
                new ImmutableMap.Builder<>();
        for (final Map.Entry<String, DescriptionTransport> content : contents.entrySet()) {
            final DescriptionTransport descriptionTransport = content.getValue();
            final RtpDescription rtpDescription = descriptionTransport.description;
            final IceUdpTransportInfo transportInfo = descriptionTransport.transport;
            final IceUdpTransportInfo modifiedTransportInfo =
                    transportInfo.modifyCredentials(credentials, setup);
            contentMapBuilder.put(
                    content.getKey(),
                    new DescriptionTransport(
                            descriptionTransport.senders, rtpDescription, modifiedTransportInfo));
        }
        return new RtpContentMap(this.group, contentMapBuilder.build());
    }

    public RtpContentMap modifiedSenders(final Content.Senders senders) {
        return new RtpContentMap(
                this.group,
                Maps.transformValues(
                        contents,
                        dt -> new DescriptionTransport(senders, dt.description, dt.transport)));
    }

    public RtpContentMap modifiedSendersChecked(
            final boolean isInitiator, final Map<String, Content.Senders> modification) {
        final ImmutableMap.Builder<String, DescriptionTransport> contentMapBuilder =
                new ImmutableMap.Builder<>();
        for (final Map.Entry<String, DescriptionTransport> content : contents.entrySet()) {
            final String id = content.getKey();
            final DescriptionTransport descriptionTransport = content.getValue();
            final Content.Senders currentSenders = descriptionTransport.senders;
            final Content.Senders targetSenders = modification.get(id);
            if (targetSenders == null || currentSenders == targetSenders) {
                contentMapBuilder.put(id, descriptionTransport);
            } else {
                checkSenderModification(isInitiator, currentSenders, targetSenders);
                contentMapBuilder.put(
                        id,
                        new DescriptionTransport(
                                targetSenders,
                                descriptionTransport.description,
                                descriptionTransport.transport));
            }
        }
        return new RtpContentMap(this.group, contentMapBuilder.build());
    }

    private static void checkSenderModification(
            final boolean isInitiator,
            final Content.Senders current,
            final Content.Senders target) {
        if (isInitiator) {
            // we were both sending and now other party only wants to receive
            if (current == Content.Senders.BOTH && target == Content.Senders.INITIATOR) {
                return;
            }
            // only we were sending but now other party wants to send too
            if (current == Content.Senders.INITIATOR && target == Content.Senders.BOTH) {
                return;
            }
        } else {
            // we were both sending and now other party only wants to receive
            if (current == Content.Senders.BOTH && target == Content.Senders.RESPONDER) {
                return;
            }
            // only we were sending but now other party wants to send too
            if (current == Content.Senders.RESPONDER && target == Content.Senders.BOTH) {
                return;
            }
        }
        throw new IllegalArgumentException(
                String.format("Unsupported senders modification %s -> %s", current, target));
    }

    public RtpContentMap toContentModification(final Collection<String> modifications) {
        return new RtpContentMap(
                this.group,
                Maps.transformValues(
                        Maps.filterKeys(contents, Predicates.in(modifications)),
                        dt ->
                                new DescriptionTransport(
                                        dt.senders, dt.description, IceUdpTransportInfo.STUB)));
    }

    public RtpContentMap toStub() {
        return new RtpContentMap(
                null,
                Maps.transformValues(
                        this.contents,
                        dt ->
                                new DescriptionTransport(
                                        dt.senders,
                                        RtpDescription.stub(dt.description.getMedia()),
                                        IceUdpTransportInfo.STUB)));
    }

    public RtpContentMap activeContents() {
        return new RtpContentMap(
                group, Maps.filterValues(this.contents, dt -> dt.senders != Content.Senders.NONE));
    }

    public Diff diff(final RtpContentMap rtpContentMap) {
        final Set<String> existingContentIds = this.contents.keySet();
        final Set<String> newContentIds = rtpContentMap.contents.keySet();
        return new Diff(
                ImmutableSet.copyOf(Sets.difference(newContentIds, existingContentIds)),
                ImmutableSet.copyOf(Sets.difference(existingContentIds, newContentIds)));
    }

    public boolean iceRestart(final RtpContentMap rtpContentMap) {
        try {
            return !getDistinctCredentials().equals(rtpContentMap.getDistinctCredentials());
        } catch (final IllegalStateException e) {
            return false;
        }
    }

    public RtpContentMap addContent(
            final RtpContentMap modification, final IceUdpTransportInfo.Setup setup) {
        final IceUdpTransportInfo.Credentials credentials = getDistinctCredentials();
        final Collection<String> iceOptions = getCombinedIceOptions();
        final DTLS dtls = getDistinctDtls();
        final Map<String, DescriptionTransport> combined = merge(contents, modification.contents);
        final Map<String, DescriptionTransport> combinedFixedTransport =
                Maps.transformValues(
                        combined,
                        dt -> {
                            final IceUdpTransportInfo iceUdpTransportInfo;
                            if (dt.transport.emptyCredentials()) {
                                iceUdpTransportInfo =
                                        IceUdpTransportInfo.of(
                                                credentials,
                                                iceOptions,
                                                setup,
                                                dtls.hash,
                                                dtls.fingerprint);
                            } else {
                                iceUdpTransportInfo =
                                        IceUdpTransportInfo.of(
                                                dt.transport.getCredentials(),
                                                iceOptions,
                                                setup,
                                                dtls.hash,
                                                dtls.fingerprint);
                            }
                            return new DescriptionTransport(
                                    dt.senders, dt.description, iceUdpTransportInfo);
                        });
        return new RtpContentMap(modification.group, combinedFixedTransport);
    }

    private static Map<String, DescriptionTransport> merge(
            final Map<String, DescriptionTransport> a, final Map<String, DescriptionTransport> b) {
        final Map<String, DescriptionTransport> combined = new HashMap<>();
        combined.putAll(a);
        combined.putAll(b);
        return ImmutableMap.copyOf(combined);
    }

    public static class DescriptionTransport {
        public final Content.Senders senders;
        public final RtpDescription description;
        public final IceUdpTransportInfo transport;

        public DescriptionTransport(
                final Content.Senders senders,
                final RtpDescription description,
                final IceUdpTransportInfo transport) {
            this.senders = senders;
            this.description = description;
            this.transport = transport;
        }

        public static DescriptionTransport of(final Content content) {
            final GenericDescription description = content.getDescription();
            final GenericTransportInfo transportInfo = content.getTransport();
            final Content.Senders senders = content.getSenders();
            final RtpDescription rtpDescription;
            final IceUdpTransportInfo iceUdpTransportInfo;
            if (description == null) {
                rtpDescription = null;
            } else if (description instanceof RtpDescription) {
                rtpDescription = (RtpDescription) description;
            } else {
                throw new UnsupportedApplicationException(
                        "Content does not contain rtp description");
            }
            if (transportInfo instanceof IceUdpTransportInfo) {
                iceUdpTransportInfo = (IceUdpTransportInfo) transportInfo;
            } else {
                throw new UnsupportedTransportException(
                        "Content does not contain ICE-UDP transport");
            }
            return new DescriptionTransport(
                    senders,
                    rtpDescription,
                    OmemoVerifiedIceUdpTransportInfo.upgrade(iceUdpTransportInfo));
        }

        private static DescriptionTransport of(
                final SessionDescription sessionDescription,
                final boolean isInitiator,
                final SessionDescription.Media media) {
            final Content.Senders senders = Content.Senders.of(media, isInitiator);
            final RtpDescription rtpDescription = RtpDescription.of(sessionDescription, media);
            final IceUdpTransportInfo transportInfo =
                    IceUdpTransportInfo.of(sessionDescription, media);
            return new DescriptionTransport(senders, rtpDescription, transportInfo);
        }

        public static Map<String, DescriptionTransport> of(final Map<String, Content> contents) {
            return ImmutableMap.copyOf(
                    Maps.transformValues(
                            contents, content -> content == null ? null : of(content)));
        }
    }

    public static class UnsupportedApplicationException extends IllegalArgumentException {
        UnsupportedApplicationException(String message) {
            super(message);
        }
    }

    public static class UnsupportedTransportException extends IllegalArgumentException {
        UnsupportedTransportException(String message) {
            super(message);
        }
    }

    public static final class Diff {
        public final Set<String> added;
        public final Set<String> removed;

        private Diff(final Set<String> added, final Set<String> removed) {
            this.added = added;
            this.removed = removed;
        }

        public boolean hasModifications() {
            return !this.added.isEmpty() || !this.removed.isEmpty();
        }

        public boolean isEmpty() {
            return this.added.isEmpty() && this.removed.isEmpty();
        }

        @Override
        @Nonnull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("added", added)
                    .add("removed", removed)
                    .toString();
        }
    }

    public static final class DTLS {
        public final String hash;
        public final IceUdpTransportInfo.Setup setup;
        public final String fingerprint;

        private DTLS(String hash, IceUdpTransportInfo.Setup setup, String fingerprint) {
            this.hash = hash;
            this.setup = setup;
            this.fingerprint = fingerprint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DTLS dtls = (DTLS) o;
            return Objects.equal(hash, dtls.hash)
                    && setup == dtls.setup
                    && Objects.equal(fingerprint, dtls.fingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(hash, setup, fingerprint);
        }
    }
}
