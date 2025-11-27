package im.conversations.android.xmpp;

import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import java.util.List;

public record ServiceDescription(
        List<String> features, im.conversations.android.xmpp.ServiceDescription.Identity identity) {

    public InfoQuery asInfoQuery() {
        final var infoQuery = new InfoQuery();
        infoQuery.addExtensions(Feature.of(this.features));
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
