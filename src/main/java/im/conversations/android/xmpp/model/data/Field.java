package im.conversations.android.xmpp.model.data;

import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.xml.Element;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.media.Media;
import java.util.Collection;

@XmlElement
public class Field extends Extension {
    public Field() {
        super(Field.class);
    }

    public String getFieldName() {
        return getAttribute("var");
    }

    public Collection<String> getValues() {
        // TODO filter null
        return Collections2.transform(getExtensions(Value.class), Element::getContent);
    }

    public String getValue() {
        return Iterables.getFirst(getValues(), null);
    }

    public void setFieldName(String name) {
        this.setAttribute("var", name);
    }

    public void setType(String type) {
        this.setAttribute("type", type);
    }

    public Media getMedia() {
        return getOnlyExtension(Media.class);
    }

    public Type getType() {
        final var type = this.getAttribute("type");
        if (Strings.isNullOrEmpty(type)) {
            return null;
        }
        try {
            return Type.valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, type));
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public enum Type {
        BOOLEAN,
        FIXED,
        HIDDEN,
        JID_MULTI,
        JID_SINGLE,
        LIST_MULTI,
        LIST_SINGLE,
        TEXT_MULTI,
        TEXT_PRIVATE,
        TEXT_SINGLE
    }
}
