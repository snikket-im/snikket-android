package eu.siacs.conversations.crypto.sasl;

import java.security.SecureRandom;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public class Anonymous extends SaslMechanism {

	public Anonymous(TagWriter tagWriter, Account account, SecureRandom rng) {
		super(tagWriter, account, rng);
	}

	@Override
	public int getPriority() {
		return 0;
	}

	@Override
	public String getMechanism() {
		return "ANONYMOUS";
	}

	@Override
	public String getClientFirstMessage() {
		return "";
	}
}
