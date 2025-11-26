package im.conversations.android.xmpp.model;

import java.util.Collection;

public abstract class AuthenticationStreamFeature extends StreamFeature{

    public AuthenticationStreamFeature(final Class<? extends AuthenticationStreamFeature> clazz) {
        super(clazz);
    }

    public abstract Collection<String> getMechanismNames();
}
