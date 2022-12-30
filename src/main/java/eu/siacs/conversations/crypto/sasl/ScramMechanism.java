package eu.siacs.conversations.crypto.sasl;

import android.util.Base64;

import com.google.common.base.CaseFormat;
import com.google.common.base.Objects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashFunction;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.util.concurrent.ExecutionException;

import javax.crypto.SecretKey;
import javax.net.ssl.SSLSocket;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;

abstract class ScramMechanism extends SaslMechanism {

    public static final SecretKey EMPTY_KEY =
            new SecretKey() {
                @Override
                public String getAlgorithm() {
                    return "HMAC";
                }

                @Override
                public String getFormat() {
                    return "RAW";
                }

                @Override
                public byte[] getEncoded() {
                    return new byte[0];
                }
            };

    private static final byte[] CLIENT_KEY_BYTES = "Client Key".getBytes();
    private static final byte[] SERVER_KEY_BYTES = "Server Key".getBytes();
    private static final Cache<CacheKey, KeyPair> CACHE =
            CacheBuilder.newBuilder().maximumSize(10).build();
    protected final ChannelBinding channelBinding;
    private final String gs2Header;
    private final String clientNonce;
    protected State state = State.INITIAL;
    private String clientFirstMessageBare;
    private byte[] serverSignature = null;

    ScramMechanism(final Account account, final ChannelBinding channelBinding) {
        super(account);
        this.channelBinding = channelBinding;
        if (channelBinding == ChannelBinding.NONE) {
            // TODO this needs to be changed to "y,," for the scram internal down grade protection
            // but we might risk compatibility issues if the server supports a binding that we don’t
            // support
            this.gs2Header = "n,,";
        } else {
            this.gs2Header =
                    String.format(
                            "p=%s,,",
                            CaseFormat.UPPER_UNDERSCORE
                                    .converterTo(CaseFormat.LOWER_HYPHEN)
                                    .convert(channelBinding.toString()));
        }
        // This nonce should be different for each authentication attempt.
        this.clientNonce = CryptoHelper.random(100);
        clientFirstMessageBare = "";
    }

    protected abstract HashFunction getHMac(final byte[] key);

    protected abstract HashFunction getDigest();

    private KeyPair getKeyPair(final String password, final String salt, final int iterations)
            throws ExecutionException {
        return CACHE.get(
                new CacheKey(getMechanism(), password, salt, iterations),
                () -> {
                    final byte[] saltedPassword, serverKey, clientKey;
                    saltedPassword =
                            hi(
                                    password.getBytes(),
                                    Base64.decode(salt, Base64.DEFAULT),
                                    iterations);
                    serverKey = hmac(saltedPassword, SERVER_KEY_BYTES);
                    clientKey = hmac(saltedPassword, CLIENT_KEY_BYTES);
                    return new KeyPair(clientKey, serverKey);
                });
    }

    private byte[] hmac(final byte[] key, final byte[] input) throws InvalidKeyException {
        return getHMac(key).hashBytes(input).asBytes();
    }

    private byte[] digest(final byte[] bytes) {
        return getDigest().hashBytes(bytes).asBytes();
    }

    /*
     * Hi() is, essentially, PBKDF2 [RFC2898] with HMAC() as the
     * pseudorandom function (PRF) and with dkLen == output length of
     * HMAC() == output length of H().
     */
    private byte[] hi(final byte[] key, final byte[] salt, final int iterations)
            throws InvalidKeyException {
        byte[] u = hmac(key, CryptoHelper.concatenateByteArrays(salt, CryptoHelper.ONE));
        byte[] out = u.clone();
        for (int i = 1; i < iterations; i++) {
            u = hmac(key, u);
            for (int j = 0; j < u.length; j++) {
                out[j] ^= u[j];
            }
        }
        return out;
    }

    @Override
    public String getClientFirstMessage(final SSLSocket sslSocket) {
        if (clientFirstMessageBare.isEmpty() && state == State.INITIAL) {
            clientFirstMessageBare =
                    "n="
                            + CryptoHelper.saslEscape(CryptoHelper.saslPrep(account.getUsername()))
                            + ",r="
                            + this.clientNonce;
            state = State.AUTH_TEXT_SENT;
        }
        return Base64.encodeToString(
                (gs2Header + clientFirstMessageBare).getBytes(Charset.defaultCharset()),
                Base64.NO_WRAP);
    }

    @Override
    public String getResponse(final String challenge, final SSLSocket socket)
            throws AuthenticationException {
        switch (state) {
            case AUTH_TEXT_SENT:
                if (challenge == null) {
                    throw new AuthenticationException("challenge can not be null");
                }
                byte[] serverFirstMessage;
                try {
                    serverFirstMessage = Base64.decode(challenge, Base64.DEFAULT);
                } catch (IllegalArgumentException e) {
                    throw new AuthenticationException("Unable to decode server challenge", e);
                }
                final Tokenizer tokenizer = new Tokenizer(serverFirstMessage);
                String nonce = "";
                int iterationCount = -1;
                String salt = "";
                for (final String token : tokenizer) {
                    if (token.charAt(1) == '=') {
                        switch (token.charAt(0)) {
                            case 'i':
                                try {
                                    iterationCount = Integer.parseInt(token.substring(2));
                                } catch (final NumberFormatException e) {
                                    throw new AuthenticationException(e);
                                }
                                break;
                            case 's':
                                salt = token.substring(2);
                                break;
                            case 'r':
                                nonce = token.substring(2);
                                break;
                            case 'm':
                                /*
                                 * RFC 5802:
                                 * m: This attribute is reserved for future extensibility.  In this
                                 * version of SCRAM, its presence in a client or a server message
                                 * MUST cause authentication failure when the attribute is parsed by
                                 * the other end.
                                 */
                                throw new AuthenticationException(
                                        "Server sent reserved token: `m'");
                        }
                    }
                }

                if (iterationCount < 0) {
                    throw new AuthenticationException("Server did not send iteration count");
                }
                if (nonce.isEmpty() || !nonce.startsWith(clientNonce)) {
                    throw new AuthenticationException(
                            "Server nonce does not contain client nonce: " + nonce);
                }
                if (salt.isEmpty()) {
                    throw new AuthenticationException("Server sent empty salt");
                }

                final byte[] channelBindingData = getChannelBindingData(socket);

                final int gs2Len = this.gs2Header.getBytes().length;
                final byte[] cMessage = new byte[gs2Len + channelBindingData.length];
                System.arraycopy(this.gs2Header.getBytes(), 0, cMessage, 0, gs2Len);
                System.arraycopy(
                        channelBindingData, 0, cMessage, gs2Len, channelBindingData.length);

                final String clientFinalMessageWithoutProof =
                        "c=" + Base64.encodeToString(cMessage, Base64.NO_WRAP) + ",r=" + nonce;

                final byte[] authMessage =
                        (clientFirstMessageBare
                                        + ','
                                        + new String(serverFirstMessage)
                                        + ','
                                        + clientFinalMessageWithoutProof)
                                .getBytes();

                final KeyPair keys;
                try {
                    keys =
                            getKeyPair(
                                    CryptoHelper.saslPrep(account.getPassword()),
                                    salt,
                                    iterationCount);
                } catch (ExecutionException e) {
                    throw new AuthenticationException("Invalid keys generated");
                }
                final byte[] clientSignature;
                try {
                    serverSignature = hmac(keys.serverKey, authMessage);
                    final byte[] storedKey = digest(keys.clientKey);

                    clientSignature = hmac(storedKey, authMessage);

                } catch (final InvalidKeyException e) {
                    throw new AuthenticationException(e);
                }

                final byte[] clientProof = new byte[keys.clientKey.length];

                if (clientSignature.length < keys.clientKey.length) {
                    throw new AuthenticationException(
                            "client signature was shorter than clientKey");
                }

                for (int i = 0; i < clientProof.length; i++) {
                    clientProof[i] = (byte) (keys.clientKey[i] ^ clientSignature[i]);
                }

                final String clientFinalMessage =
                        clientFinalMessageWithoutProof
                                + ",p="
                                + Base64.encodeToString(clientProof, Base64.NO_WRAP);
                state = State.RESPONSE_SENT;
                return Base64.encodeToString(clientFinalMessage.getBytes(), Base64.NO_WRAP);
            case RESPONSE_SENT:
                try {
                    final String clientCalculatedServerFinalMessage =
                            "v=" + Base64.encodeToString(serverSignature, Base64.NO_WRAP);
                    if (!clientCalculatedServerFinalMessage.equals(
                            new String(Base64.decode(challenge, Base64.DEFAULT)))) {
                        throw new Exception();
                    }
                    state = State.VALID_SERVER_RESPONSE;
                    return "";
                } catch (Exception e) {
                    throw new AuthenticationException(
                            "Server final message does not match calculated final message");
                }
            default:
                throw new InvalidStateException(state);
        }
    }

    protected byte[] getChannelBindingData(final SSLSocket sslSocket)
            throws AuthenticationException {
        if (this.channelBinding == ChannelBinding.NONE) {
            return new byte[0];
        }
        throw new AssertionError("getChannelBindingData needs to be overwritten");
    }

    private static class CacheKey {
        final String algorithm;
        final String password;
        final String salt;
        final int iterations;

        private CacheKey(String algorithm, String password, String salt, int iterations) {
            this.algorithm = algorithm;
            this.password = password;
            this.salt = salt;
            this.iterations = iterations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return iterations == cacheKey.iterations
                    && Objects.equal(algorithm, cacheKey.algorithm)
                    && Objects.equal(password, cacheKey.password)
                    && Objects.equal(salt, cacheKey.salt);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(algorithm, password, salt, iterations);
        }
    }

    private static class KeyPair {
        final byte[] clientKey;
        final byte[] serverKey;

        KeyPair(final byte[] clientKey, final byte[] serverKey) {
            this.clientKey = clientKey;
            this.serverKey = serverKey;
        }
    }
}
