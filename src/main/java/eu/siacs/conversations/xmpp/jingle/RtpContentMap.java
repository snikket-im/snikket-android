package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;

public class RtpContentMap {

    public final Group group;
    public final Map<String, DescriptionTransport> contents;

    private RtpContentMap(Group group, Map<String, DescriptionTransport> contents) {
        this.group = group;
        this.contents = contents;
    }

    public static RtpContentMap of(final JinglePacket jinglePacket) {
        return new RtpContentMap(jinglePacket.getGroup(), DescriptionTransport.of(jinglePacket.getJingleContents()));
    }

    public static RtpContentMap of(final SessionDescription sessionDescription) {
        final ImmutableMap.Builder<String, DescriptionTransport> contentMapBuilder = new ImmutableMap.Builder<>();
        for (SessionDescription.Media media : sessionDescription.media) {
            final String id = Iterables.getFirst(media.attributes.get("mid"), null);
            Preconditions.checkNotNull(id, "media has no mid");
            contentMapBuilder.put(id, DescriptionTransport.of(sessionDescription, media));
        }
        final String groupAttribute = Iterables.getFirst(sessionDescription.attributes.get("group"), null);
        final Group group = groupAttribute == null ? null : Group.ofSdpString(groupAttribute);
        return new RtpContentMap(group, contentMapBuilder.build());
    }

    public JinglePacket toJinglePacket(final JinglePacket.Action action, final String sessionId) {
        final JinglePacket jinglePacket = new JinglePacket(action, sessionId);
        if (this.group != null) {
            jinglePacket.addGroup(this.group);
        }
        for (Map.Entry<String, DescriptionTransport> entry : this.contents.entrySet()) {
            final Content content = new Content(Content.Creator.INITIATOR, entry.getKey());
            content.addChild(entry.getValue().description);
            content.addChild(entry.getValue().transport);
            jinglePacket.addJingleContent(content);
        }
        return jinglePacket;
    }

    public static class DescriptionTransport {
        public final RtpDescription description;
        public final IceUdpTransportInfo transport;

        public DescriptionTransport(final RtpDescription description, final IceUdpTransportInfo transport) {
            this.description = description;
            this.transport = transport;
        }

        public static DescriptionTransport of(final Content content) {
            final GenericDescription description = content.getDescription();
            final GenericTransportInfo transportInfo = content.getTransport();
            final RtpDescription rtpDescription;
            final IceUdpTransportInfo iceUdpTransportInfo;
            if (description instanceof RtpDescription) {
                rtpDescription = (RtpDescription) description;
            } else {
                Log.d(Config.LOGTAG, "description was " + description);
                throw new IllegalArgumentException("Content does not contain RtpDescription");
            }
            if (transportInfo instanceof IceUdpTransportInfo) {
                iceUdpTransportInfo = (IceUdpTransportInfo) transportInfo;
            } else {
                throw new IllegalArgumentException("Content does not contain ICE-UDP transport");
            }
            return new DescriptionTransport(rtpDescription, iceUdpTransportInfo);
        }

        public static DescriptionTransport of(final SessionDescription sessionDescription, final SessionDescription.Media media) {
            final RtpDescription rtpDescription = RtpDescription.of(media);
            final IceUdpTransportInfo transportInfo = IceUdpTransportInfo.of(sessionDescription, media);
            return new DescriptionTransport(rtpDescription, transportInfo);
        }

        public static Map<String, DescriptionTransport> of(final Map<String, Content> contents) {
            return ImmutableMap.copyOf(Maps.transformValues(contents, new Function<Content, DescriptionTransport>() {
                @NullableDecl
                @Override
                public DescriptionTransport apply(@NullableDecl Content content) {
                    return content == null ? null : of(content);
                }
            }));
        }
    }
}
