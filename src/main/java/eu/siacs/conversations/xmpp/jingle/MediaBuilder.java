package eu.siacs.conversations.xmpp.jingle;

import com.google.common.collect.ArrayListMultimap;

import java.util.List;

public class MediaBuilder {
    private String media;
    private int port;
    private String protocol;
    private List<Integer> formats;
    private String connectionData;
    private ArrayListMultimap<String,String> attributes;

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

    public MediaBuilder setFormats(List<Integer> formats) {
        this.formats = formats;
        return this;
    }

    public MediaBuilder setConnectionData(String connectionData) {
        this.connectionData = connectionData;
        return this;
    }

    public MediaBuilder setAttributes(ArrayListMultimap<String,String> attributes) {
        this.attributes = attributes;
        return this;
    }

    public SessionDescription.Media createMedia() {
        return new SessionDescription.Media(media, port, protocol, formats, connectionData, attributes);
    }
}