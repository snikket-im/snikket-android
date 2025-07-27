package im.conversations.android.xmpp.model.pubsub;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.data.Data;

@XmlElement
public class PublishOptions extends Extension {

    public PublishOptions() {
        super(PublishOptions.class);
    }

    public static PublishOptions of(final NodeConfiguration nodeConfiguration) {
        final var publishOptions = new PublishOptions();
        publishOptions.addExtension(Data.of(nodeConfiguration, Namespace.PUB_SUB_PUBLISH_OPTIONS));
        return publishOptions;
    }
}
