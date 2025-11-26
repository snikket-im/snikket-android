package im.conversations.android.xmpp.model;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

import eu.siacs.conversations.xml.Element;

import im.conversations.android.xmpp.ExtensionFactory;

import java.util.Collection;

public class Extension extends Element {

    private Extension(final ExtensionFactory.Id id) {
        super(id.name, id.namespace);
    }

    public Extension(final Class<? extends Extension> clazz) {
        this(
                Preconditions.checkNotNull(
                        ExtensionFactory.id(clazz),
                        String.format(
                                "%s does not seem to be annotated with @XmlElement",
                                clazz.getName())));
        Preconditions.checkArgument(
                getClass().equals(clazz), "clazz passed in constructor must match class");
    }

    public <E extends Extension> boolean hasExtension(final Class<E> clazz) {
        return Iterables.any(this.children, clazz::isInstance);
    }

    public <E extends Extension> E getExtension(final Class<E> clazz) {
        final var extension = Iterables.find(this.children, clazz::isInstance, null);
        if (extension == null) {
            return null;
        }
        return clazz.cast(extension);
    }

    public <E extends Extension> Collection<E> getExtensions(final Class<E> clazz) {
        return Collections2.transform(
                Collections2.filter(this.children, clazz::isInstance), clazz::cast);
    }

    public Collection<ExtensionFactory.Id> getExtensionIds() {
        return Collections2.transform(
                this.children, c -> new ExtensionFactory.Id(c.getName(), c.getNamespace()));
    }

    public <T extends Extension> T addExtension(T child) {
        this.addChild(child);
        return child;
    }

    public void addExtensions(final Collection<? extends Extension> extensions) {
        for (final Extension extension : extensions) {
            addExtension(extension);
        }
    }
}
