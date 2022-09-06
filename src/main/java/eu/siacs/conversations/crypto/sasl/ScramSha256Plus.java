package eu.siacs.conversations.crypto.sasl;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.macs.HMac;

import eu.siacs.conversations.entities.Account;

public class ScramSha256Plus extends ScramPlusMechanism {

    public static final String MECHANISM = "SCRAM-SHA-256-PLUS";

    public ScramSha256Plus(final Account account, final ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected HMac getHMAC() {
        return new HMac(new SHA256Digest());
    }

    @Override
    protected Digest getDigest() {
        return new SHA256Digest();
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }
}
