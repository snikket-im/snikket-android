package eu.siacs.conversations.crypto.sasl;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import java.nio.charset.Charset;
import java.util.Map;
import javax.net.ssl.SSLSocket;

public class DigestMd5 extends SaslMechanism {

    public static final String MECHANISM = "DIGEST-MD5";
    private State state = State.INITIAL;
    private String precalculatedRSPAuth;

    public DigestMd5(final Account account) {
        super(account);
    }

    @Override
    public int getPriority() {
        return 10;
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
    public String getResponse(final String challenge, final SSLSocket socket)
            throws AuthenticationException {
        return switch (state) {
            case AUTH_TEXT_SENT -> processChallenge(challenge, socket);
            case RESPONSE_SENT -> validateServerResponse(challenge);
            case VALID_SERVER_RESPONSE -> validateUnnecessarySuccessMessage(challenge);
            default -> throw new InvalidStateException(state);
        };
    }

    // ejabberd sends the RSPAuth response as a challenge and then an empty success
    // technically this is allowed as per https://datatracker.ietf.org/doc/html/rfc2222#section-5.2
    // although it says to do that only if the profile of the protocol does not allow data to be put
    // into success. which xmpp does allow. obviously
    private String validateUnnecessarySuccessMessage(final String challenge)
            throws AuthenticationException {
        if (Strings.isNullOrEmpty(challenge)) {
            return "";
        }
        throw new AuthenticationException("Success message must be empty");
    }

    private String validateServerResponse(final String challenge) throws AuthenticationException {
        Log.d(Config.LOGTAG, "DigestMd5.validateServerResponse(" + challenge + ")");
        final var attributes = messageToAttributes(challenge);
        Log.d(Config.LOGTAG, "attributes: " + attributes);
        final var rspauth = attributes.get("rspauth");
        if (Strings.isNullOrEmpty(rspauth)) {
            throw new AuthenticationException("no rspauth in server finish message");
        }
        final var expected = this.precalculatedRSPAuth;
        if (Strings.isNullOrEmpty(expected) || !this.precalculatedRSPAuth.equals(rspauth)) {
            throw new AuthenticationException("RSPAuth mismatch");
        }
        this.state = State.VALID_SERVER_RESPONSE;
        return "";
    }

    private String processChallenge(final String challenge, final SSLSocket socket)
            throws AuthenticationException {
        Log.d(Config.LOGTAG, "DigestMd5.processChallenge()");
        this.state = State.RESPONSE_SENT;
        final var attributes = messageToAttributes(challenge);

        final var nonce = attributes.get("nonce");

        if (Strings.isNullOrEmpty(nonce)) {
            throw new AuthenticationException("Server nonce missing");
        }
        final String digestUri = "xmpp/" + account.getServer();
        final String nonceCount = "00000001";
        final String x =
                account.getUsername() + ":" + account.getServer() + ":" + account.getPassword();
        final byte[] y = Hashing.md5().hashBytes(x.getBytes(Charset.defaultCharset())).asBytes();
        final String cNonce = CryptoHelper.random(100);
        final byte[] a1 =
                CryptoHelper.concatenateByteArrays(
                        y, (":" + nonce + ":" + cNonce).getBytes(Charset.defaultCharset()));
        final String a2 = "AUTHENTICATE:" + digestUri;
        final String ha1 = CryptoHelper.bytesToHex(Hashing.md5().hashBytes(a1).asBytes());
        final String ha2 =
                CryptoHelper.bytesToHex(
                        Hashing.md5().hashBytes(a2.getBytes(Charset.defaultCharset())).asBytes());
        final String kd = ha1 + ":" + nonce + ":" + nonceCount + ":" + cNonce + ":auth:" + ha2;

        final String a2ForResponse = ":" + digestUri;
        final String ha2ForResponse =
                CryptoHelper.bytesToHex(
                        Hashing.md5()
                                .hashBytes(a2ForResponse.getBytes(Charset.defaultCharset()))
                                .asBytes());
        final String kdForResponseInput =
                ha1 + ":" + nonce + ":" + nonceCount + ":" + cNonce + ":auth:" + ha2ForResponse;

        this.precalculatedRSPAuth =
                CryptoHelper.bytesToHex(
                        Hashing.md5()
                                .hashBytes(kdForResponseInput.getBytes(Charset.defaultCharset()))
                                .asBytes());

        final String response =
                CryptoHelper.bytesToHex(
                        Hashing.md5().hashBytes(kd.getBytes(Charset.defaultCharset())).asBytes());

        final String saslString =
                "username=\""
                        + account.getUsername()
                        + "\",realm=\""
                        + account.getServer()
                        + "\",nonce=\""
                        + nonce
                        + "\",cnonce=\""
                        + cNonce
                        + "\",nc="
                        + nonceCount
                        + ",qop=auth,digest-uri=\""
                        + digestUri
                        + "\",response="
                        + response
                        + ",charset=utf-8";
        return BaseEncoding.base64().encode(saslString.getBytes());
    }

    private static Map<String, String> messageToAttributes(final String message)
            throws AuthenticationException {
        byte[] asBytes;
        try {
            asBytes = BaseEncoding.base64().decode(message);
        } catch (final IllegalArgumentException e) {
            throw new AuthenticationException("Unable to decode server challenge", e);
        }
        try {
            return splitToAttributes(new String(asBytes));
        } catch (final IllegalArgumentException e) {
            throw new AuthenticationException("Duplicate attributes");
        }
    }

    private static Map<String, String> splitToAttributes(final String message) {
        final ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        for (final String token : Splitter.on(',').split(message)) {
            final var tuple = Splitter.on('=').limit(2).splitToList(token);
            if (tuple.size() == 2) {
                final var value = tuple.get(1);
                builder.put(tuple.get(0), trimQuotes(value));
            }
        }
        return builder.buildOrThrow();
    }

    public static String trimQuotes(@NonNull final String input) {
        if (input.length() >= 2
                && input.charAt(0) == '"'
                && input.charAt(input.length() - 1) == '"') {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }
}
