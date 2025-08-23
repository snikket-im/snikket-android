package im.conversations.android.xmpp.model.disco.info;

import com.google.common.collect.Collections2;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;

@XmlElement
public class Feature extends Extension {
    public Feature() {
        super(Feature.class);
    }

    public String getVar() {
        return this.getAttribute("var");
    }

    public void setVar(final String feature) {
        this.setAttribute("var", feature);
    }

    public static Collection<Feature> of(final Collection<String> features) {
        return Collections2.transform(
                features,
                sf -> {
                    final var feature = new Feature();
                    feature.setVar(sf);
                    return feature;
                });
    }
}
