package eu.siacs.conversations.crypto.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import eu.siacs.conversations.entities.Account;

public class ScramSha1Plus extends ScramPlusMechanism {

    public static final String MECHANISM = "SCRAM-SHA-1-PLUS";

    public ScramSha1Plus(final Account account, final ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected HashFunction getHMac(final byte[] key) {
        return Hashing.hmacSha1(key);
    }

    @Override
    protected HashFunction getDigest() {
        return Hashing.sha1();
    }

    @Override
    public int getPriority() {
        return 35; // higher than SCRAM-SHA512 (30)
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }
}
