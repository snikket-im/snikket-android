package im.conversations.android.xmpp.model.error;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

public abstract class Condition extends Extension {

    private Condition(Class<? extends Condition> clazz) {
        super(clazz);
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class BadRequest extends Condition {

        public BadRequest() {
            super(BadRequest.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class Conflict extends Condition {

        public Conflict() {
            super(Conflict.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class FeatureNotImplemented extends Condition {

        public FeatureNotImplemented() {
            super(FeatureNotImplemented.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class Forbidden extends Condition {

        public Forbidden() {
            super(Forbidden.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class Gone extends Condition {

        public Gone() {
            super(Gone.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class InternalServerError extends Condition {

        public InternalServerError() {
            super(InternalServerError.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class ItemNotFound extends Condition {

        public ItemNotFound() {
            super(ItemNotFound.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class JidMalformed extends Condition {

        public JidMalformed() {
            super(JidMalformed.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class NotAcceptable extends Condition {

        public NotAcceptable() {
            super(NotAcceptable.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class NotAllowed extends Condition {

        public NotAllowed() {
            super(NotAllowed.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class NotAuthorized extends Condition {

        public NotAuthorized() {
            super(NotAuthorized.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class PaymentRequired extends Condition {

        public PaymentRequired() {
            super(PaymentRequired.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class RecipientUnavailable extends Condition {

        public RecipientUnavailable() {
            super(RecipientUnavailable.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class Redirect extends Condition {

        public Redirect() {
            super(Redirect.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class RegistrationRequired extends Condition {

        public RegistrationRequired() {
            super(RegistrationRequired.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class RemoteServerNotFound extends Condition {

        public RemoteServerNotFound() {
            super(RemoteServerNotFound.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class RemoteServerTimeout extends Condition {

        public RemoteServerTimeout() {
            super(RemoteServerTimeout.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class ResourceConstraint extends Condition {

        public ResourceConstraint() {
            super(ResourceConstraint.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class ServiceUnavailable extends Condition {

        public ServiceUnavailable() {
            super(ServiceUnavailable.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class SubscriptionRequired extends Condition {

        public SubscriptionRequired() {
            super(SubscriptionRequired.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class UndefinedCondition extends Condition {

        public UndefinedCondition() {
            super(UndefinedCondition.class);
        }
    }

    @XmlElement(namespace = Namespace.STANZAS)
    public static class UnexpectedRequest extends Condition {

        public UnexpectedRequest() {
            super(UnexpectedRequest.class);
        }
    }
}
