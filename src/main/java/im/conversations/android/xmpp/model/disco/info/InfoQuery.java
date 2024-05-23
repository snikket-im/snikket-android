package im.conversations.android.xmpp.model.disco.info;

import com.google.common.collect.Iterables;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;

@XmlElement(name = "query")
public class InfoQuery extends Extension {

    public InfoQuery() {
        super(InfoQuery.class);
    }

    public void setNode(final String node) {
        this.setAttribute("node", node);
    }

    public String getNode() {
        return this.getAttribute("node");
    }

    public Collection<Feature> getFeatures() {
        return this.getExtensions(Feature.class);
    }

    public boolean hasFeature(final String feature) {
        return Iterables.any(getFeatures(), f -> feature.equals(f.getVar()));
    }

    public Collection<Identity> getIdentities() {
        return this.getExtensions(Identity.class);
    }

    public boolean hasIdentityWithCategory(final String category) {
        return Iterables.any(getIdentities(), i -> category.equals(i.getCategory()));
    }
}
