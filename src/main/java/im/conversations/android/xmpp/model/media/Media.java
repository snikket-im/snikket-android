package im.conversations.android.xmpp.model.media;

import com.google.common.collect.Collections2;
import de.gultsch.common.MiniUri;
import eu.siacs.conversations.xml.Element;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;

@XmlElement
public class Media extends Extension {

    public Media() {
        super(Media.class);
    }

    public Collection<MiniUri> getUris() {
        final var uris =
                Collections2.filter(
                        Collections2.transform(this.getExtensions(Uri.class), Element::getContent),
                        Objects::nonNull);
        return Collections2.transform(uris, MiniUri::new);
    }
}
