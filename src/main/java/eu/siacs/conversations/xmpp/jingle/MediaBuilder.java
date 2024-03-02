package eu.siacs.conversations.xmpp.jingle;

import com.google.common.base.Joiner;
import com.google.common.collect.Multimap;

import java.util.List;

public class MediaBuilder {
    private String media;
    private int port;
    private String protocol;
    private String format;
    private String connectionData;
    private Multimap<String, String> attributes;

    public MediaBuilder setMedia(String media) {
        this.media = media;
        return this;
    }

    public MediaBuilder setPort(int port) {
        this.port = port;
        return this;
    }

    public MediaBuilder setProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    public MediaBuilder setFormats(final List<Integer> formats) {
        this.format = Joiner.on(' ').join(formats);
        return this;
    }

    public MediaBuilder setFormat(final String format) {
        this.format = format;
        return this;
    }

    public MediaBuilder setConnectionData(String connectionData) {
        this.connectionData = connectionData;
        return this;
    }

    public MediaBuilder setAttributes(Multimap<String, String> attributes) {
        this.attributes = attributes;
        return this;
    }

    public SessionDescription.Media createMedia() {
        return new SessionDescription.Media(
                media, port, protocol, format, connectionData, attributes);
    }
}
