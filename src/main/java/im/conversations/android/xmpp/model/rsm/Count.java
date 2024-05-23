package im.conversations.android.xmpp.model.rsm;

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Count extends Extension {

    public Count() {
        super(Count.class);
    }

    public Integer getCount() {
        final var content = getContent();
        if (Strings.isNullOrEmpty(content)) {
            return null;
        } else {
            return Ints.tryParse(content);
        }
    }
}
