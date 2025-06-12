package im.conversations.android.xmpp.model.register;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;

@XmlElement(name = "register", namespace = Namespace.REGISTER_STREAM_FEATURE)
public class RegisterStreamFeature extends StreamFeature {

    public RegisterStreamFeature() {
        super(RegisterStreamFeature.class);
    }
}
