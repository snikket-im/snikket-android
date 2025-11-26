package im.conversations.android.xmpp.model.sasl;

import com.google.common.collect.Collections2;

import eu.siacs.conversations.xml.Element;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.AuthenticationStreamFeature;
import im.conversations.android.xmpp.model.StreamFeature;

import java.util.Collection;
import java.util.Objects;

@XmlElement
public class Mechanisms extends AuthenticationStreamFeature {


    public Mechanisms() {
        super(Mechanisms.class);
    }

    public Collection<Mechanism> getMechanisms() {
        return getExtensions(Mechanism.class);
    }

    public Collection<String> getMechanismNames() {
        return Collections2.filter(Collections2.transform(getMechanisms(), Element::getContent), Objects::nonNull);
    }
}
