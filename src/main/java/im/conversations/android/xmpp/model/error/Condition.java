package im.conversations.android.xmpp.model.error;

import com.google.common.collect.ImmutableMap;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Map;

public abstract sealed class Condition extends Extension {

    public static final Map<Class<? extends Condition>, ErrorTypeCode> ERROR_CONDITION_MAPPING =
            new ImmutableMap.Builder<Class<? extends Condition>, ErrorTypeCode>()
                    .put(BadRequest.class, new ErrorTypeCode(Error.Type.MODIFY, 400))
                    .put(Conflict.class, new ErrorTypeCode(Error.Type.CANCEL, 409))
                    .put(FeatureNotImplemented.class, new ErrorTypeCode(Error.Type.CANCEL, 501))
                    .put(Forbidden.class, new ErrorTypeCode(Error.Type.AUTH, 403))
                    .put(Gone.class, new ErrorTypeCode(Error.Type.MODIFY, 302))
                    .put(InternalServerError.class, new ErrorTypeCode(Error.Type.WAIT, 500))
                    .put(ItemNotFound.class, new ErrorTypeCode(Error.Type.CANCEL, 404))
                    .put(JidMalformed.class, new ErrorTypeCode(Error.Type.MODIFY, 400))
                    .put(NotAcceptable.class, new ErrorTypeCode(Error.Type.MODIFY, 406))
                    .put(NotAllowed.class, new ErrorTypeCode(Error.Type.CANCEL, 405))
                    .put(NotAuthorized.class, new ErrorTypeCode(Error.Type.AUTH, 401))
                    .put(PaymentRequired.class, new ErrorTypeCode(Error.Type.AUTH, 402))
                    .put(RecipientUnavailable.class, new ErrorTypeCode(Error.Type.WAIT, 404))
                    .put(Redirect.class, new ErrorTypeCode(Error.Type.MODIFY, 302))
                    .put(RegistrationRequired.class, new ErrorTypeCode(Error.Type.AUTH, 407))
                    .put(RemoteServerNotFound.class, new ErrorTypeCode(Error.Type.CANCEL, 404))
                    .put(RemoteServerTimeout.class, new ErrorTypeCode(Error.Type.WAIT, 504))
                    .put(ResourceConstraint.class, new ErrorTypeCode(Error.Type.WAIT, 500))
                    .put(ServiceUnavailable.class, new ErrorTypeCode(Error.Type.CANCEL, 503))
                    .put(SubscriptionRequired.class, new ErrorTypeCode(Error.Type.AUTH, 407))
                    .put(UndefinedCondition.class, new ErrorTypeCode(Error.Type.WAIT, 500))
                    .put(UnexpectedRequest.class, new ErrorTypeCode(Error.Type.WAIT, 400))
                    .buildOrThrow();

    private Condition(Class<? extends Condition> clazz) {
        super(clazz);
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class BadRequest extends Condition {

        public BadRequest() {
            super(BadRequest.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class Conflict extends Condition {

        public Conflict() {
            super(Conflict.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class FeatureNotImplemented extends Condition {

        public FeatureNotImplemented() {
            super(FeatureNotImplemented.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class Forbidden extends Condition {

        public Forbidden() {
            super(Forbidden.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class Gone extends Condition {

        public Gone() {
            super(Gone.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class InternalServerError extends Condition {

        public InternalServerError() {
            super(InternalServerError.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class ItemNotFound extends Condition {

        public ItemNotFound() {
            super(ItemNotFound.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class JidMalformed extends Condition {

        public JidMalformed() {
            super(JidMalformed.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class NotAcceptable extends Condition {

        public NotAcceptable() {
            super(NotAcceptable.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class NotAllowed extends Condition {

        public NotAllowed() {
            super(NotAllowed.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class NotAuthorized extends Condition {

        public NotAuthorized() {
            super(NotAuthorized.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class PaymentRequired extends Condition {

        public PaymentRequired() {
            super(PaymentRequired.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class RecipientUnavailable extends Condition {

        public RecipientUnavailable() {
            super(RecipientUnavailable.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class Redirect extends Condition {

        public Redirect() {
            super(Redirect.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class RegistrationRequired extends Condition {

        public RegistrationRequired() {
            super(RegistrationRequired.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class RemoteServerNotFound extends Condition {

        public RemoteServerNotFound() {
            super(RemoteServerNotFound.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class RemoteServerTimeout extends Condition {

        public RemoteServerTimeout() {
            super(RemoteServerTimeout.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class ResourceConstraint extends Condition {

        public ResourceConstraint() {
            super(ResourceConstraint.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class ServiceUnavailable extends Condition {

        public ServiceUnavailable() {
            super(ServiceUnavailable.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class SubscriptionRequired extends Condition {

        public SubscriptionRequired() {
            super(SubscriptionRequired.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class UndefinedCondition extends Condition {

        public UndefinedCondition() {
            super(UndefinedCondition.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static final class UnexpectedRequest extends Condition {

        public UnexpectedRequest() {
            super(UnexpectedRequest.class);
        }
    }

    public record ErrorTypeCode(Error.Type errorType, int legacyErrorCode) {}
}
