package eu.siacs.conversations.xmpp.bind;

import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class Bind2 {

    public static final Collection<String> QUICKSTART_FEATURES = Arrays.asList(
            Namespace.CARBONS,
            Namespace.STREAM_MANAGEMENT
    );

    public static Collection<String> features(final Element inline) {
        final Element inlineBind2 =
                inline != null ? inline.findChild("bind", Namespace.BIND2) : null;
        final Element inlineBind2Inline =
                inlineBind2 != null ? inlineBind2.findChild("inline", Namespace.BIND2) : null;
        if (inlineBind2 == null) {
            return null;
        }
        if (inlineBind2Inline == null) {
            return Collections.emptyList();
        }
        return Collections2.filter(
                Collections2.transform(
                        Collections2.filter(
                                inlineBind2Inline.getChildren(),
                                c -> "feature".equals(c.getName())),
                        c -> c.getAttribute("var")),
                Predicates.notNull());
    }
}
