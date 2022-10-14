package eu.siacs.conversations.crypto.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import eu.siacs.conversations.entities.Account;

public class HashedTokenSha256 extends HashedToken {

    public HashedTokenSha256(final Account account, final ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected HashFunction getHashFunction(final byte[] key) {
        return Hashing.hmacSha256(key);
    }

    @Override
    public String getMechanism() {
        final String cbShortName = ChannelBinding.SHORT_NAMES.get(this.channelBinding);
        return String.format("HT-SHA-256-%s", cbShortName);
    }
}
