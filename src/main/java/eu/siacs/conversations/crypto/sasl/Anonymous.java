package eu.siacs.conversations.crypto.sasl;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import eu.siacs.conversations.entities.Account;
import javax.net.ssl.SSLSocket;

public class Anonymous extends SaslMechanism {

    public static final String MECHANISM = "ANONYMOUS";

    public Anonymous(final Account account) {
        super(account);
    }

    @Override
    public int getPriority() {
        return 0;
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
        return "";
    }

    @Override
    public String getResponse(final String challenge, final SSLSocket sslSocket)
            throws AuthenticationException {
        checkState(State.AUTH_TEXT_SENT);
        if (Strings.isNullOrEmpty(challenge)) {
            this.state = State.VALID_SERVER_RESPONSE;
            return null;
        }
        throw new AuthenticationException("Unexpected server response");
    }
}
