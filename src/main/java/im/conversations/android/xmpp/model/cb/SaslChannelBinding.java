package im.conversations.android.xmpp.model.cb;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
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

    public Collection<String> getChannelBindingTypes() {
        return Collections2.filter(
                Collections2.transform(getChannelBindings(), ChannelBinding::getType),
                Predicates.notNull());
    }
}
