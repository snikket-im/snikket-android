package eu.siacs.conversations.xmpp.jingle;

import com.google.common.collect.ArrayListMultimap;

import java.util.List;

public class SessionDescriptionBuilder {
    private int version;
    private String name;
    private String connectionData;
    private ArrayListMultimap<String,String> attributes;
    private List<SessionDescription.Media> media;

    public SessionDescriptionBuilder setVersion(int version) {
        this.version = version;
        return this;
    }

    public SessionDescriptionBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public SessionDescriptionBuilder setConnectionData(String connectionData) {
        this.connectionData = connectionData;
        return this;
    }

    public SessionDescriptionBuilder setAttributes(ArrayListMultimap<String,String> attributes) {
        this.attributes = attributes;
        return this;
    }

    public SessionDescriptionBuilder setMedia(List<SessionDescription.Media> media) {
        this.media = media;
        return this;
    }

    public SessionDescription createSessionDescription() {
        return new SessionDescription(version, name, connectionData, attributes, media);
    }
}