package eu.siacs.conversations.crypto.sasl;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashFunction;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLSocket;

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
    private final String clientFirstMessageBare;
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
        this.clientFirstMessageBare =
                String.format(
                        "n=%s,r=%s",
                        CryptoHelper.saslEscape(CryptoHelper.saslPrep(account.getUsername())),
                        this.clientNonce);
    }

    protected abstract HashFunction getHMac(final byte[] key);

    protected abstract HashFunction getDigest();

    private KeyPair getKeyPair(final String password, final byte[] salt, final int iterations)
            throws ExecutionException {
        final var key = new CacheKey(getMechanism(), password, salt, iterations);
        return CACHE.get(key, () -> calculateKeyPair(password, salt, iterations));
    }

    private KeyPair calculateKeyPair(final String password, final byte[] salt, final int iterations)
            throws InvalidKeyException {
        final byte[] saltedPassword, serverKey, clientKey;
        saltedPassword = hi(password.getBytes(), salt, iterations);
        serverKey = hmac(saltedPassword, SERVER_KEY_BYTES);
        clientKey = hmac(saltedPassword, CLIENT_KEY_BYTES);
        return new KeyPair(clientKey, serverKey);
    }

    @Override
    public String getMechanism() {
        return "";
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
        if (this.state != State.INITIAL) {
            throw new IllegalArgumentException("Calling getClientFirstMessage from invalid state");
        }
        this.state = State.AUTH_TEXT_SENT;
        final byte[] message = (gs2Header + clientFirstMessageBare).getBytes();
        return BaseEncoding.base64().encode(message);
    }

    @Override
    public String getResponse(final String challenge, final SSLSocket socket)
            throws AuthenticationException {
        return switch (state) {
            case AUTH_TEXT_SENT -> processServerFirstMessage(challenge, socket);
            case RESPONSE_SENT -> processServerFinalMessage(challenge);
            default -> throw new InvalidStateException(state);
        };
    }

    private String processServerFirstMessage(final String challenge, final SSLSocket socket)
            throws AuthenticationException {
        if (Strings.isNullOrEmpty(challenge)) {
            throw new AuthenticationException("challenge can not be null");
        }
        byte[] serverFirstMessage;
        try {
            serverFirstMessage = BaseEncoding.base64().decode(challenge);
        } catch (final IllegalArgumentException e) {
            throw new AuthenticationException("Unable to decode server challenge", e);
        }
        final Map<String, String> attributes;
        try {
            attributes = splitToAttributes(new String(serverFirstMessage));
        } catch (final IllegalArgumentException e) {
            throw new AuthenticationException("Duplicate attributes");
        }
        if (attributes.containsKey("m")) {
            /*
             * RFC 5802:
             * m: This attribute is reserved for future extensibility.  In this
             * version of SCRAM, its presence in a client or a server message
             * MUST cause authentication failure when the attribute is parsed by
             * the other end.
             */
            throw new AuthenticationException("Server sent reserved token: 'm'");
        }
        final String i = attributes.get("i");
        final String s = attributes.get("s");
        final String nonce = attributes.get("r");
        final String d = attributes.get("d");
        if (Strings.isNullOrEmpty(s) || Strings.isNullOrEmpty(nonce) || Strings.isNullOrEmpty(i)) {
            throw new AuthenticationException("Missing attributes from server first message");
        }
        final Integer iterationCount = Ints.tryParse(i);

        if (iterationCount == null || iterationCount < 0) {
            throw new AuthenticationException("Server did not send iteration count");
        }
        if (!nonce.startsWith(clientNonce)) {
            throw new AuthenticationException(
                    "Server nonce does not contain client nonce: " + nonce);
        }

        final byte[] salt;

        try {
            salt = BaseEncoding.base64().decode(s);
        } catch (final IllegalArgumentException e) {
            throw new AuthenticationException("Invalid salt in server first message");
        }

        final byte[] channelBindingData = getChannelBindingData(socket);

        final int gs2Len = this.gs2Header.getBytes().length;
        final byte[] cMessage = new byte[gs2Len + channelBindingData.length];
        System.arraycopy(this.gs2Header.getBytes(), 0, cMessage, 0, gs2Len);
        System.arraycopy(channelBindingData, 0, cMessage, gs2Len, channelBindingData.length);

        final String clientFinalMessageWithoutProof =
                String.format("c=%s,r=%s", BaseEncoding.base64().encode(cMessage), nonce);

        final var authMessage =
                Joiner.on(',')
                        .join(
                                clientFirstMessageBare,
                                new String(serverFirstMessage),
                                clientFinalMessageWithoutProof);

        final KeyPair keys;
        try {
            keys = getKeyPair(CryptoHelper.saslPrep(account.getPassword()), salt, iterationCount);
        } catch (final ExecutionException e) {
            throw new AuthenticationException("Invalid keys generated");
        }
        final byte[] clientSignature;
        try {
            serverSignature = hmac(keys.serverKey, authMessage.getBytes());
            final byte[] storedKey = digest(keys.clientKey);

            clientSignature = hmac(storedKey, authMessage.getBytes());

        } catch (final InvalidKeyException e) {
            throw new AuthenticationException(e);
        }

        final byte[] clientProof = new byte[keys.clientKey.length];

        if (clientSignature.length < keys.clientKey.length) {
            throw new AuthenticationException("client signature was shorter than clientKey");
        }

        for (int j = 0; j < clientProof.length; j++) {
            clientProof[j] = (byte) (keys.clientKey[j] ^ clientSignature[j]);
        }

        final var clientFinalMessage =
                String.format(
                        "%s,p=%s",
                        clientFinalMessageWithoutProof, BaseEncoding.base64().encode(clientProof));
        this.state = State.RESPONSE_SENT;
        return BaseEncoding.base64().encode(clientFinalMessage.getBytes());
    }

    private Map<String, String> splitToAttributes(final String message) {
        final ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        for (final String token : Splitter.on(',').split(message)) {
            final var tuple = Splitter.on('=').limit(2).splitToList(token);
            if (tuple.size() == 2) {
                builder.put(tuple.get(0), tuple.get(1));
            }
        }
        return builder.buildOrThrow();
    }

    private String processServerFinalMessage(final String challenge)
            throws AuthenticationException {
        final String serverFinalMessage;
        try {
            serverFinalMessage = new String(BaseEncoding.base64().decode(challenge));
        } catch (final IllegalArgumentException e) {
            throw new AuthenticationException("Invalid base64 in server final message", e);
        }
        final var clientCalculatedServerFinalMessage =
                String.format("v=%s", BaseEncoding.base64().encode(serverSignature));
        if (clientCalculatedServerFinalMessage.equals(serverFinalMessage)) {
            this.state = State.VALID_SERVER_RESPONSE;
            return "";
        }
        throw new AuthenticationException(
                "Server final message does not match calculated final message");
    }

    protected byte[] getChannelBindingData(final SSLSocket sslSocket)
            throws AuthenticationException {
        if (this.channelBinding == ChannelBinding.NONE) {
            return new byte[0];
        }
        throw new AssertionError("getChannelBindingData needs to be overwritten");
    }

    private static class CacheKey {
        private final String algorithm;
        private final String password;
        private final byte[] salt;
        private final int iterations;

        private CacheKey(
                final String algorithm,
                final String password,
                final byte[] salt,
                final int iterations) {
            this.algorithm = algorithm;
            this.password = password;
            this.salt = salt;
            this.iterations = iterations;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return iterations == cacheKey.iterations
                    && Objects.equal(algorithm, cacheKey.algorithm)
                    && Objects.equal(password, cacheKey.password)
                    && Arrays.equals(salt, cacheKey.salt);
        }

        @Override
        public int hashCode() {
            final int result = Objects.hashCode(algorithm, password, iterations);
            return 31 * result + Arrays.hashCode(salt);
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
