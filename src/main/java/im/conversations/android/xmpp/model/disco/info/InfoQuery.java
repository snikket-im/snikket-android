package im.conversations.android.xmpp.model.disco.info;

import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.data.Data;
import java.util.Collection;
import java.util.Objects;

@XmlElement(name = "query")
public class InfoQuery extends Extension {

    public InfoQuery() {
        super(InfoQuery.class);
    }

    public InfoQuery(
            final Collection<Identity> identities,
            final Collection<String> features,
            final Data... extensions) {
        this();
        this.addExtensions(identities);
        this.addExtensions(Feature.of(features));
        for (final var extension : extensions) {
            this.addExtension(extension);
        }
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

    public boolean hasIdentityWithCategoryAndType(final String category, final String type) {
        return Iterables.any(
                getIdentities(), i -> category.equals(i.getCategory()) && type.equals(i.getType()));
    }

    public Collection<String> getFeatureStrings() {
        return Collections2.filter(
                Collections2.transform(getFeatures(), Feature::getVar), Objects::nonNull);
    }

    public Collection<Data> getServiceDiscoveryExtensions() {
        return getExtensions(Data.class);
    }

    public Data getServiceDiscoveryExtension(final String formType) {
        return Iterables.find(
                getServiceDiscoveryExtensions(), e -> formType.equals(e.getFormType()), null);
    }

    public String getServiceDiscoveryExtension(final String formType, final String fieldName) {
        final var extension =
                Iterables.find(
                        getServiceDiscoveryExtensions(),
                        e -> formType.equals(e.getFormType()),
                        null);
        if (extension == null) {
            return null;
        }
        final var field = extension.getFieldByName(fieldName);
        if (field == null) {
            return null;
        }
        return field.getValue();
    }
}
