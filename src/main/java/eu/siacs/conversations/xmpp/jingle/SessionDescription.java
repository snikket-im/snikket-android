package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;

public class SessionDescription {

    private final int version;
    private final String name;
    private final String connectionData;
    private final List<Attribute> attributes;
    private final List<Media> media;


    public SessionDescription(int version, String name, String connectionData, List<Attribute> attributes, List<Media> media) {
        this.version = version;
        this.name = name;
        this.connectionData = connectionData;
        this.attributes = attributes;
        this.media = media;
    }

    public static SessionDescription parse(final Map<String, JingleRtpConnection.DescriptionTransport> contents) {
        final SessionDescriptionBuilder sessionDescriptionBuilder = new SessionDescriptionBuilder();
        return sessionDescriptionBuilder.createSessionDescription();
    }

    public static SessionDescription parse(final String input) {
        final SessionDescriptionBuilder sessionDescriptionBuilder = new SessionDescriptionBuilder();
        MediaBuilder currentMediaBuilder = null;
        ImmutableList.Builder<Attribute> attributeBuilder = new ImmutableList.Builder<>();
        ImmutableList.Builder<Media> mediaBuilder = new ImmutableList.Builder<>();
        for(final String line : input.split("\n")) {
            final String[] pair = line.split("=",2);
            if (pair.length < 2 || pair[0].length() != 1) {
                Log.d(Config.LOGTAG,"skipping sdp parsing on line "+line);
                continue;
            }
            final char key = pair[0].charAt(0);
            final String value = pair[1];
            switch (key) {
                case 'v':
                    sessionDescriptionBuilder.setVersion(ignorantIntParser(value));
                    break;
                case 'c':
                    if (currentMediaBuilder != null) {
                        currentMediaBuilder.setConnectionData(value);
                    } else {
                        sessionDescriptionBuilder.setConnectionData(value);
                    }
                    break;
                case 's':
                    sessionDescriptionBuilder.setName(value);
                    break;
                case 'a':
                    attributeBuilder.add(Attribute.parse(value));
                    break;
                case 'm':
                    if (currentMediaBuilder == null) {
                        sessionDescriptionBuilder.setAttributes(attributeBuilder.build());;
                    } else {
                        currentMediaBuilder.setAttributes(attributeBuilder.build());
                        mediaBuilder.add(currentMediaBuilder.createMedia());
                    }
                    attributeBuilder = new ImmutableList.Builder<>();
                    currentMediaBuilder = new MediaBuilder();
                    final String[] parts = value.split(" ");
                    if (parts.length >= 3) {
                        currentMediaBuilder.setMedia(parts[0]);
                        currentMediaBuilder.setPort(ignorantIntParser(parts[1]));
                        currentMediaBuilder.setProtocol(parts[2]);
                        ImmutableList.Builder<Integer> formats = new ImmutableList.Builder<>();
                        for(int i = 3; i < parts.length; ++i) {
                            formats.add(ignorantIntParser(parts[i]));
                        }
                        currentMediaBuilder.setFormats(formats.build());
                    } else {
                        Log.d(Config.LOGTAG,"skipping media line "+line);
                    }
                    break;
            }

        }
        if (currentMediaBuilder != null) {
            currentMediaBuilder.setAttributes(attributeBuilder.build());
            mediaBuilder.add(currentMediaBuilder.createMedia());
        }
        sessionDescriptionBuilder.setMedia(mediaBuilder.build());
        return sessionDescriptionBuilder.createSessionDescription();
    }

    private static int ignorantIntParser(final String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static class Attribute {
        private final String key;
        private final String value;

        public Attribute(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public static Attribute parse(String input) {
            final String[] pair = input.split(":",2);
            if (pair.length == 2) {
                return new Attribute(pair[0],pair[1]);
            } else {
                return new Attribute(pair[0], null);
            }
        }


    }

    public static class Media {
        private final String media;
        private final int port;
        private final String protocol;
        private final List<Integer> formats;
        private final String connectionData;
        private final List<Attribute> attributes;

        public Media(String media, int port, String protocol, List<Integer> formats, String connectionData, List<Attribute> attributes) {
            this.media = media;
            this.port = port;
            this.protocol = protocol;
            this.formats = formats;
            this.connectionData = connectionData;
            this.attributes = attributes;
        }
    }

}
