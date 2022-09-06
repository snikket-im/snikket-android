package eu.siacs.conversations.crypto.sasl;

import org.conscrypt.Conscrypt;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;

import eu.siacs.conversations.entities.Account;

abstract class ScramPlusMechanism extends ScramMechanism {

    private static final String EXPORTER_LABEL = "EXPORTER-Channel-Binding";

    ScramPlusMechanism(Account account, ChannelBinding channelBinding) {
        super(account, channelBinding);
    }

    @Override
    protected byte[] getChannelBindingData(final SSLSocket sslSocket)
            throws AuthenticationException {
        if (sslSocket == null) {
            throw new AuthenticationException("Channel binding attempt on non secure socket");
        }
        if (this.channelBinding == ChannelBinding.TLS_EXPORTER) {
            try {
                return Conscrypt.exportKeyingMaterial(sslSocket, EXPORTER_LABEL, new byte[0], 32);
            } catch (final SSLException e) {
                throw new AuthenticationException("Could not export keying material");
            }
        } else {
            throw new AuthenticationException(
                    String.format("%s is not a valid channel binding", ChannelBinding.NONE));
        }
    }
}
