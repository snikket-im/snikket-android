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
    public Mechanism getTokenMechanism() {
        return new Mechanism("SHA-512", this.channelBinding);
    }
}
