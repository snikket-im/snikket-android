package im.conversations.android.xmpp.model.sasl2;

import com.google.common.collect.Collections2;

import eu.siacs.conversations.xml.Element;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.fast.Fast;
import im.conversations.android.xmpp.model.fast.Mechanism;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@XmlElement
public class Inline extends Extension {

    public Inline() {
        super(Inline.class);
    }

    public Fast getFast() {
        return this.getExtension(Fast.class);
    }

    public Collection<String> getFastMechanisms() {
        final var fast = getFast();
        final Collection<Mechanism> mechanisms =
                fast == null ? Collections.emptyList() : fast.getExtensions(Mechanism.class);
        return Collections2.filter(
                Collections2.transform(mechanisms, Element::getContent), Objects::nonNull);
    }
}
