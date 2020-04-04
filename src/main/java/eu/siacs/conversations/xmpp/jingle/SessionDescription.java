package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;
import android.util.Pair;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;

public class SessionDescription {

    public final int version;
    public final String name;
    public final String connectionData;
    public final ArrayListMultimap<String, String> attributes;
    public final List<Media> media;


    public SessionDescription(int version, String name, String connectionData, ArrayListMultimap<String, String> attributes, List<Media> media) {
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
        ArrayListMultimap<String, String> attributeMap = ArrayListMultimap.create();
        ImmutableList.Builder<Media> mediaBuilder = new ImmutableList.Builder<>();
        for (final String line : input.split("\n")) {
            final String[] pair = line.trim().split("=", 2);
            if (pair.length < 2 || pair[0].length() != 1) {
                Log.d(Config.LOGTAG, "skipping sdp parsing on line " + line);
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
                    final Pair<String, String> attribute = parseAttribute(value);
                    attributeMap.put(attribute.first, attribute.second);
                    break;
                case 'm':
                    if (currentMediaBuilder == null) {
                        sessionDescriptionBuilder.setAttributes(attributeMap);
                        ;
                    } else {
                        currentMediaBuilder.setAttributes(attributeMap);
                        mediaBuilder.add(currentMediaBuilder.createMedia());
                    }
                    attributeMap = ArrayListMultimap.create();
                    currentMediaBuilder = new MediaBuilder();
                    final String[] parts = value.split(" ");
                    if (parts.length >= 3) {
                        currentMediaBuilder.setMedia(parts[0]);
                        currentMediaBuilder.setPort(ignorantIntParser(parts[1]));
                        currentMediaBuilder.setProtocol(parts[2]);
                        ImmutableList.Builder<Integer> formats = new ImmutableList.Builder<>();
                        for (int i = 3; i < parts.length; ++i) {
                            formats.add(ignorantIntParser(parts[i]));
                        }
                        currentMediaBuilder.setFormats(formats.build());
                    } else {
                        Log.d(Config.LOGTAG, "skipping media line " + line);
                    }
                    break;
            }

        }
        if (currentMediaBuilder != null) {
            currentMediaBuilder.setAttributes(attributeMap);
            mediaBuilder.add(currentMediaBuilder.createMedia());
        }
        sessionDescriptionBuilder.setMedia(mediaBuilder.build());
        return sessionDescriptionBuilder.createSessionDescription();
    }

    public static int ignorantIntParser(final String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Pair<String, String> parseAttribute(final String input) {
        final String[] pair = input.split(":", 2);
        if (pair.length == 2) {
            return new Pair<>(pair[0], pair[1]);
        } else {
            return new Pair<>(pair[0], "");
        }
    }

    public static class Media {
        public final String media;
        public final int port;
        public final String protocol;
        public final List<Integer> formats;
        public final String connectionData;
        public final ArrayListMultimap<String, String> attributes;

        public Media(String media, int port, String protocol, List<Integer> formats, String connectionData, ArrayListMultimap<String, String> attributes) {
            this.media = media;
            this.port = port;
            this.protocol = protocol;
            this.formats = formats;
            this.connectionData = connectionData;
            this.attributes = attributes;
        }
    }

}
