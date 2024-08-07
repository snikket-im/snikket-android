package im.conversations.android.xmpp.model;

import im.conversations.android.xmpp.model.sasl.SaslError;

public abstract class AuthenticationFailure extends StreamElement {

    protected AuthenticationFailure(Class<? extends AuthenticationFailure> clazz) {
        super(clazz);
    }

    public SaslError getErrorCondition() {
        return this.getExtension(SaslError.class);
    }

    public String getText() {
        return this.findChildContent("text");
    }
}
