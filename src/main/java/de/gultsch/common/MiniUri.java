package de.gultsch.common;

import android.net.Uri;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;

public class MiniUri {

    private static final String EMPTY_STRING = "";

    private final String raw;
    private final String scheme;
    private final String authority;
    private final String path;
    private final Map<String, String> parameter;

    public MiniUri(final String uri) {
        this.raw = uri;
        final var schemeAndRest = Splitter.on(':').limit(2).splitToList(uri);
        if (schemeAndRest.size() < 2) {
            this.scheme = uri;
            this.authority = null;
            this.path = null;
            this.parameter = Collections.emptyMap();
            return;
        }
        this.scheme = schemeAndRest.get(0);
        final var rest = schemeAndRest.get(1);
        // TODO add fragment parser
        final var authorityPathAndQuery = Splitter.on('?').limit(2).splitToList(rest);
        final var authorityPath = authorityPathAndQuery.get(0);
        System.out.println("authorityPath " + authorityPath);
        if (authorityPath.length() >= 2 && authorityPath.startsWith("//")) {
            final var authorityPathParts =
                    Splitter.on('/').limit(2).splitToList(authorityPath.substring(2));
            this.authority = authorityPathParts.get(0);
            this.path = authorityPathParts.size() == 2 ? authorityPathParts.get(1) : null;
        } else {
            this.authority = null;
            // TODO path ; style path components from something like geo uri
            this.path = authorityPath;
        }
        if (authorityPathAndQuery.size() == 2) {
            this.parameter = parseParameters(authorityPathAndQuery.get(1), getDelimiter(scheme));
        } else {
            this.parameter = Collections.emptyMap();
        }
    }

    private static char getDelimiter(final String scheme) {
        return switch (scheme) {
            case "xmpp", "geo" -> ';';
            default -> '&';
        };
    }

    private static Map<String, String> parseParameters(final String query, final char separator) {
        final ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        for (final String pair : Splitter.on(separator).split(query)) {
            final String[] parts = pair.split("=", 2);
            if (parts.length == 0) {
                continue;
            }
            final String key = parts[0].toLowerCase(Locale.US);
            if (parts.length == 2) {
                try {
                    builder.put(key, URLDecoder.decode(parts[1], "UTF-8"));
                } catch (final UnsupportedEncodingException e) {
                    builder.put(key, EMPTY_STRING);
                }
            } else {
                builder.put(key, EMPTY_STRING);
            }
        }
        return builder.build();
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scheme", scheme)
                .add("authority", authority)
                .add("path", path)
                .add("parameter", parameter)
                .toString();
    }

    public String getScheme() {
        return this.scheme;
    }

    public String getAuthority() {
        return this.authority;
    }

    public String getPath() {
        return Strings.isNullOrEmpty(this.path) || this.authority == null
                ? this.path
                : '/' + this.path;
    }

    public List<String> getPathSegments() {
        return Strings.isNullOrEmpty(this.path)
                ? Collections.emptyList()
                : Splitter.on('/').splitToList(this.path);
    }

    public String getRaw() {
        return this.raw;
    }

    public Uri asUri() {
        return Uri.parse(this.raw);
    }

    public Map<String, String> getParameter() {
        return this.parameter;
    }
}
