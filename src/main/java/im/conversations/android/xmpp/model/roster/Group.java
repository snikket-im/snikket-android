package im.conversations.android.xmpp.model.roster;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.xml.Element;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

@XmlElement
public class Group extends Extension {

    public Group() {
        super(Group.class);
    }

    public Group(final String group) {
        this();
        this.setContent(group);
    }

    public static Set<String> getGroups(final Collection<Group> groups) {
        return ImmutableSet.copyOf(
                Collections2.filter(
                        Collections2.transform(groups, Element::getContent), Objects::nonNull));
    }
}
