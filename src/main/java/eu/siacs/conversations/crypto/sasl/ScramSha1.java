package eu.siacs.conversations.crypto.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import eu.siacs.conversations.entities.Account;

public class ScramSha1 extends ScramMechanism {

    public static final String MECHANISM = "SCRAM-SHA-1";

    public ScramSha1(final Account account) {
        super(account, ChannelBinding.NONE);
    }

    @Override
    protected HashFunction getHMac(final byte[] key) {
        return (key == null || key.length == 0)
                ? Hashing.hmacSha1(EMPTY_KEY)
                : Hashing.hmacSha1(key);
    }

    @Override
    protected HashFunction getDigest() {
        return Hashing.sha1();
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }
}
