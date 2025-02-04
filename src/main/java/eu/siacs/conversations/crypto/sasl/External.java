package eu.siacs.conversations.crypto.sasl;

import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import eu.siacs.conversations.entities.Account;
import javax.net.ssl.SSLSocket;

public class External extends SaslMechanism {

    public static final String MECHANISM = "EXTERNAL";

    public External(final Account account) {
        super(account);
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public String getMechanism() {
        return MECHANISM;
    }

    @Override
    public String getClientFirstMessage(final SSLSocket sslSocket) {
        Preconditions.checkState(
                this.state == State.INITIAL, "Calling getClientFirstMessage from invalid state");
        this.state = State.AUTH_TEXT_SENT;
        final String message = account.getJid().asBareJid().toString();
        return BaseEncoding.base64().encode(message.getBytes());
    }

    @Override
    public String getResponse(String challenge, SSLSocket sslSocket)
            throws AuthenticationException {
        // TODO check that state is in auth text sent and move to finished
        return "";
    }
}
