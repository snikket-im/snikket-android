package eu.siacs.conversations.crypto.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import eu.siacs.conversations.entities.Account;

public class ScramSha512Plus extends ScramPlusMechanism {

    public static final String MECHANISM = "SCRAM-SHA-512-PLUS";

    public ScramSha512Plus(final Account account, final ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected HashFunction getHMac(final byte[] key) {
        return (key == null || key.length == 0)
                ? Hashing.hmacSha512(EMPTY_KEY)
                : Hashing.hmacSha512(key);
    }

    @Override
    protected HashFunction getDigest() {
        return Hashing.sha512();
    }

    @Override
    public int getPriority() {
        return 45;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }
}
