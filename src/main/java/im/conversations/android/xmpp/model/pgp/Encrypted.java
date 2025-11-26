package im.conversations.android.xmpp.model.pgp;

import eu.siacs.conversations.xml.Namespace;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "x", namespace = Namespace.PGP_ENCRYPTED)
public class Encrypted extends Extension {

    public Encrypted() {
        super(Encrypted.class);
    }
}
