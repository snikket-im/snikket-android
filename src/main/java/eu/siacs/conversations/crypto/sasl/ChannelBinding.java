package eu.siacs.conversations.crypto.sasl;

import android.util.Log;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;

import java.util.Collection;

import eu.siacs.conversations.Config;

public enum ChannelBinding {
    NONE,
    TLS_EXPORTER,
    TLS_SERVER_END_POINT,
    TLS_UNIQUE;

    public static ChannelBinding of(final String type) {
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

    public static ChannelBinding best(final Collection<ChannelBinding> bindings) {
        if (bindings.contains(TLS_EXPORTER)) {
            return TLS_EXPORTER;
        } else if (bindings.contains(TLS_UNIQUE)) {
            return TLS_UNIQUE;
        } else if (bindings.contains(TLS_SERVER_END_POINT)) {
            return TLS_SERVER_END_POINT;
        } else {
            return null;
        }
    }
}
