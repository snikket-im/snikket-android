package im.conversations.android.xmpp.model.jmi;

import com.google.common.primitives.Ints;

import eu.siacs.conversations.xml.Element;
import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Proceed extends JingleMessage {

    public Proceed() {
        super(Proceed.class);
    }

    public Integer getDeviceId() {
        // TODO use proper namespace and create extension
        final Element device = this.findChild("device");
        final String id = device == null ? null : device.getAttribute("id");
        if (id == null) {
            return null;
        }
        return Ints.tryParse(id);
    }
}
