package eu.siacs.conversations.crypto.sasl;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;

import eu.siacs.conversations.entities.Account;

public class ScramSha1Plus extends ScramPlusMechanism {

    public static final String MECHANISM = "SCRAM-SHA-1-PLUS";

    public ScramSha1Plus(final Account account, final ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected HMac getHMAC() {
        return new HMac(new SHA1Digest());
    }

    @Override
    protected Digest getDigest() {
        return new SHA1Digest();
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
