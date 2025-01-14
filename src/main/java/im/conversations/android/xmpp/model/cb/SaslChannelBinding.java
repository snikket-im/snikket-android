package im.conversations.android.xmpp.model.cb;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;
import java.util.Collection;

@XmlElement
public class SaslChannelBinding extends StreamFeature {

    public SaslChannelBinding() {
        super(SaslChannelBinding.class);
    }

    public Collection<ChannelBinding> getChannelBindings() {
        return this.getExtensions(ChannelBinding.class);
    }
}
