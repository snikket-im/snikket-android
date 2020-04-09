package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.support.annotation.NonNull;

import com.google.common.base.CaseFormat;

public enum Reason {
	SUCCESS, DECLINE, BUSY, CANCEL, CONNECTIVITY_ERROR, FAILED_TRANSPORT, FAILED_APPLICATION, TIMEOUT, UNKNOWN;

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