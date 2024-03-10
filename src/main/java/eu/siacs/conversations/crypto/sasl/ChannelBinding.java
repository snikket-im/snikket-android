package eu.siacs.conversations.crypto.sasl;

import android.util.Log;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableBiMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.SSLSockets;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public enum ChannelBinding {
    NONE,
    TLS_EXPORTER,
    TLS_SERVER_END_POINT,
    TLS_UNIQUE;

    public static final BiMap<ChannelBinding, String> SHORT_NAMES;

    static {
        final ImmutableBiMap.Builder<ChannelBinding, String> builder = ImmutableBiMap.builder();
        for (final ChannelBinding cb : values()) {
            builder.put(cb, shortName(cb));
        }
        SHORT_NAMES = builder.build();
    }

    public static Collection<ChannelBinding> of(final Element channelBinding) {
        Preconditions.checkArgument(
                channelBinding == null
                        || ("sasl-channel-binding".equals(channelBinding.getName())
                                && Namespace.CHANNEL_BINDING.equals(channelBinding.getNamespace())),
                "pass null or a valid channel binding stream feature");
        return Collections2.filter(
                Collections2.transform(
                        Collections2.filter(
                                channelBinding == null
                                        ? Collections.emptyList()
                                        : channelBinding.getChildren(),
                                c -> c != null && "channel-binding".equals(c.getName())),
                        c -> c == null ? null : ChannelBinding.of(c.getAttribute("type"))),
                Predicates.notNull());
    }

    private static ChannelBinding of(final String type) {
        if (type == null) {
            return null;
        }
        try {
            return valueOf(
                    CaseFormat.LOWER_HYPHEN.converterTo(CaseFormat.UPPER_UNDERSCORE).convert(type));
        } catch (final IllegalArgumentException e) {
            Log.d(Config.LOGTAG, type + " is not a known channel binding");
            return null;
        }
    }

    public static ChannelBinding get(final String name) {
        if (Strings.isNullOrEmpty(name)) {
            return NONE;
        }
        try {
            return valueOf(name);
        } catch (final IllegalArgumentException e) {
            return NONE;
        }
    }

    public static ChannelBinding best(
            final Collection<ChannelBinding> bindings, final SSLSockets.Version sslVersion) {
        if (sslVersion == SSLSockets.Version.NONE) {
            return NONE;
        }
        if (bindings.contains(TLS_EXPORTER) && sslVersion == SSLSockets.Version.TLS_1_3) {
            return TLS_EXPORTER;
        } else if (bindings.contains(TLS_UNIQUE)
                && Arrays.asList(
                                SSLSockets.Version.TLS_1_0,
                                SSLSockets.Version.TLS_1_1,
                                SSLSockets.Version.TLS_1_2)
                        .contains(sslVersion)) {
            return TLS_UNIQUE;
        } else if (bindings.contains(TLS_SERVER_END_POINT)) {
            return TLS_SERVER_END_POINT;
        } else {
            return NONE;
        }
    }

    public static boolean isAvailable(
            final ChannelBinding channelBinding, final SSLSockets.Version sslVersion) {
        return ChannelBinding.best(Collections.singleton(channelBinding), sslVersion)
                == channelBinding;
    }

    private static String shortName(final ChannelBinding channelBinding) {
        return switch (channelBinding) {
            case TLS_UNIQUE -> "UNIQ";
            case TLS_EXPORTER -> "EXPR";
            case TLS_SERVER_END_POINT -> "ENDP";
            case NONE -> "NONE";
            default -> throw new AssertionError("Missing short name for " + channelBinding);
        };
    }

    public static int priority(final ChannelBinding channelBinding) {
        if (Arrays.asList(TLS_EXPORTER, TLS_UNIQUE).contains(channelBinding)) {
            return 2;
        } else if (channelBinding == ChannelBinding.TLS_SERVER_END_POINT) {
            return 1;
        } else {
            return 0;
        }
    }
}
