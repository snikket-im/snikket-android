package eu.siacs.conversations.crypto.sasl;

import java.security.SecureRandom;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public abstract class SaslMechanism {

    final protected TagWriter tagWriter;
    final protected Account account;
    final protected SecureRandom rng;

    public SaslMechanism(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
        this.tagWriter = tagWriter;
        this.account = account;
        this.rng = rng;
    }

    public abstract String getMechanism();
    public String getStartAuth() {
        return "";
    }
    public String getResponse(final String challenge) {
        return "";
    }
}
