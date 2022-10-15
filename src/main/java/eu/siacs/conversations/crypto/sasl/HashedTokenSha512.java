package eu.siacs.conversations.crypto.sasl;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import eu.siacs.conversations.entities.Account;

public class HashedTokenSha512 extends HashedToken {

    public HashedTokenSha512(final Account account, final ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected HashFunction getHashFunction(final byte[] key) {
        return Hashing.hmacSha512(key);
    }

    @Override
    public String getMechanism() {
        final String cbShortName = ChannelBinding.SHORT_NAMES.get(this.channelBinding);
        return String.format("HT-SHA-512-%s", cbShortName);
    }
}
