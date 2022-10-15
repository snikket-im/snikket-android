package eu.siacs.conversations.crypto.sasl;

import android.util.Base64;

import javax.net.ssl.SSLSocket;

import eu.siacs.conversations.entities.Account;

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
        return Base64.encodeToString(
                account.getJid().asBareJid().toEscapedString().getBytes(), Base64.NO_WRAP);
    }
}
