package eu.siacs.conversations.crypto.sasl;

import java.security.SecureRandom;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public abstract class SaslMechanism {

	final protected TagWriter tagWriter;
	final protected Account account;
	final protected SecureRandom rng;

	protected static enum State {
		INITIAL,
		AUTH_TEXT_SENT,
		RESPONSE_SENT,
		VALID_SERVER_RESPONSE,
	}

	public static class AuthenticationException extends Exception {
		public AuthenticationException(final String message) {
			super(message);
		}

		public AuthenticationException(final Exception inner) {
			super(inner);
		}
	}

	public static class InvalidStateException extends AuthenticationException {
		public InvalidStateException(final String message) {
			super(message);
		}

		public InvalidStateException(final State state) {
			this("Invalid state: " + state.toString());
		}
	}

	public SaslMechanism(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
		this.tagWriter = tagWriter;
		this.account = account;
		this.rng = rng;
	}

	public String getClientFirstMessage() {
		return "";
	}
	public String getResponse(final String challenge) throws AuthenticationException {
		return "";
	}
}
