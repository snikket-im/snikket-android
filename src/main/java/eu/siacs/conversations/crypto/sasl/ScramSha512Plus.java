package eu.siacs.conversations.crypto.sasl;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;

import eu.siacs.conversations.entities.Account;

public class ScramSha512Plus extends ScramPlusMechanism {

    public static final String MECHANISM = "SCRAM-SHA-512-PLUS";

    public ScramSha512Plus(final Account account, final ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected HMac getHMAC() {
        return new HMac(new SHA512Digest());
    }

    @Override
    protected Digest getDigest() {
        return new SHA512Digest();
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
