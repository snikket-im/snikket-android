package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.support.annotation.NonNull;

import com.google.common.base.CaseFormat;

public enum Reason {
    ALTERNATIVE_SESSION,
    BUSY,
    CANCEL,
    CONNECTIVITY_ERROR,
    DECLINE,
    EXPIRED,
    FAILED_APPLICATION,
    FAILED_TRANSPORT,
    GENERAL_ERROR,
    GONE,
    INCOMPATIBLE_PARAMETERS,
    MEDIA_ERROR,
    SECURITY_ERROR,
    SUCCESS,
    TIMEOUT,
    UNSUPPORTED_APPLICATIONS,
    UNSUPPORTED_TRANSPORTS,
    UNKNOWN;

    public static Reason of(final String value) {
        try {
            return Reason.valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, value));
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    @Override
    @NonNull
    public String toString() {
        return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, super.toString());
    }
}