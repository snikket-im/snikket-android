package im.conversations.android.xmpp.model.sasl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

public class SaslError extends Extension {

    private SaslError(final Class<? extends SaslError> clazz) {
        super(clazz);
    }

    @XmlElement
    public static class Aborted extends SaslError {
        public Aborted() {
            super(Aborted.class);
        }
    }

    @XmlElement
    public static class AccountDisabled extends SaslError {
        public AccountDisabled() {
            super(AccountDisabled.class);
        }
    }

    @XmlElement
    public static class CredentialsExpired extends SaslError {
        public CredentialsExpired() {
            super(CredentialsExpired.class);
        }
    }

    @XmlElement
    public static class EncryptionRequired extends SaslError {
        public EncryptionRequired() {
            super(EncryptionRequired.class);
        }
    }

    @XmlElement
    public static class IncorrectEncoding extends SaslError {
        public IncorrectEncoding() {
            super(IncorrectEncoding.class);
        }
    }

    @XmlElement
    public static class InvalidAuthzid extends SaslError {
        public InvalidAuthzid() {
            super(InvalidAuthzid.class);
        }
    }

    @XmlElement
    public static class InvalidMechanism extends SaslError {
        public InvalidMechanism() {
            super(InvalidMechanism.class);
        }
    }

    @XmlElement
    public static class MalformedRequest extends SaslError {
        public MalformedRequest() {
            super(MalformedRequest.class);
        }
    }

    @XmlElement
    public static class MechanismTooWeak extends SaslError {
        public MechanismTooWeak() {
            super(MechanismTooWeak.class);
        }
    }

    @XmlElement
    public static class NotAuthorized extends SaslError {

        public NotAuthorized() {
            super(NotAuthorized.class);
        }
    }

    @XmlElement
    public static class TemporaryAuthFailure extends SaslError {
        public TemporaryAuthFailure() {
            super(TemporaryAuthFailure.class);
        }
    }
}
