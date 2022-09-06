package eu.siacs.conversations.crypto.sasl;

import javax.net.ssl.SSLSocket;

import eu.siacs.conversations.entities.Account;

abstract class ScramPlusMechanism extends ScramMechanism {
    ScramPlusMechanism(Account account, ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected byte[] getChannelBindingData(final SSLSocket sslSocket) throws AuthenticationException {
        if (this.channelBinding == ChannelBinding.NONE) {
            throw new AuthenticationException(String.format("%s is not a valid channel binding", ChannelBinding.NONE));
        }
        if (sslSocket == null) {
            throw new AuthenticationException("Channel binding attempt on non secure socket");
        }
        throw new AssertionError("not yet implemented");
    }
}
