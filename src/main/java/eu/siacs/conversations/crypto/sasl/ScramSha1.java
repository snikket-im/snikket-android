package eu.siacs.conversations.crypto.sasl;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.macs.HMac;

import eu.siacs.conversations.entities.Account;

public class ScramSha1 extends ScramMechanism {

    public static final String MECHANISM = "SCRAM-SHA-1";

    public ScramSha1(final Account account) {
        super(account, ChannelBinding.NONE);
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
        return 20;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }
}
