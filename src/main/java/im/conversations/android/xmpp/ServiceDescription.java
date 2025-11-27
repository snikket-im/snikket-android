package im.conversations.android.xmpp;

import com.google.common.collect.Collections2;
import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import java.util.Collection;
import java.util.List;

public class ServiceDescription {
    public final List<String> features;
    public final Identity identity;

    public ServiceDescription(List<String> features, Identity identity) {
        this.features = features;
        this.identity = identity;
    }

    public InfoQuery asInfoQuery() {
        final var infoQuery = new InfoQuery();
        final Collection<Feature> features =
                Collections2.transform(
                        this.features,
                        sf -> {
                            final var feature = new Feature();
                            feature.setVar(sf);
                            return feature;
                        });
        infoQuery.addExtensions(features);
        final var identity =
                infoQuery.addExtension(
                        new im.conversations.android.xmpp.model.disco.info.Identity());
        identity.setIdentityName(this.identity.name);
        identity.setCategory(this.identity.category);
        identity.setType(this.identity.type);
        return infoQuery;
    }

    public static class Identity {
        public final String name;
        public final String category;
        public final String type;

        public Identity(String name, String category, String type) {
            this.name = name;
            this.category = category;
            this.type = type;
        }
    }
}
