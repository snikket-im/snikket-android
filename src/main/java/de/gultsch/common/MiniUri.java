package de.gultsch.common;

import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMultimap;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MiniUri {

    private static final String EMPTY_STRING = "";

    private final String raw;
    private final String scheme;
    private final String authority;
    private final String path;
    private final Map<String, Collection<String>> parameter;

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

    private static Map<String, Collection<String>> parseParameters(
            final String query, final char separator) {
        final var builder = new ImmutableMultimap.Builder<String, String>();
        for (final String pair : Splitter.on(separator).split(query)) {
            final String[] parts = pair.split("=", 2);
            if (parts.length == 0) {
                continue;
            }
            final String key = parts[0].toLowerCase(Locale.US);
            if (parts.length == 2) {
                builder.put(key, urlDecodeOrEmpty(parts[1]));
            } else {
                builder.put(key, EMPTY_STRING);
            }
        }
        return builder.build().asMap();
    }

    public static String urlDecodeOrEmpty(final String input) {
        try {
            return URLDecoder.decode(input, "UTF-8");
        } catch (final UnsupportedEncodingException | IllegalArgumentException e) {
            return EMPTY_STRING;
        }
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

    public Map<String, Collection<String>> getParameter() {
        return this.parameter;
    }
}
