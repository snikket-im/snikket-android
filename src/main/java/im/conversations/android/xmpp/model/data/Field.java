package im.conversations.android.xmpp.model.data;
import eu.siacs.conversations.xml.Element;
import com.google.common.collect.Collections2;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
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
        return Collections2.transform(getExtensions(Value.class), Element::getContent);
    }

    public void setFieldName(String name) {
        this.setAttribute("var", name);
    }

    public void setType(String type) {
        this.setAttribute("type", type);
    }
}
