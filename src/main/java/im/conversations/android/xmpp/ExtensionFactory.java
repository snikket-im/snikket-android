package im.conversations.android.xmpp;


import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import eu.siacs.conversations.xml.Element;

import im.conversations.android.xmpp.model.Extension;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class ExtensionFactory {

    public static Element create(final String name, final String namespace) {
        final Class<? extends Extension> clazz = of(name, namespace);
        if (clazz == null) {
            return new Element(name, namespace);
        }
        final Constructor<? extends Element> constructor;
        try {
            constructor = clazz.getDeclaredConstructor();
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException(
                    String.format("%s has no default constructor", clazz.getName()),e);
        }
        try {
            return constructor.newInstance();
        } catch (final IllegalAccessException
                | InstantiationException
                | InvocationTargetException e) {
            throw new IllegalStateException(
                    String.format("%s has inaccessible default constructor", clazz.getName()),e);
        }
    }

    private static Class<? extends Extension> of(final String name, final String namespace) {
        return Extensions.EXTENSION_CLASS_MAP.get(new Id(name, namespace));
    }

    public static Id id(final Class<? extends Extension> clazz) {
        return Extensions.EXTENSION_CLASS_MAP.inverse().get(clazz);
    }

    private ExtensionFactory() {}

    public static class Id {
        public final String name;
        public final String namespace;

        public Id(String name, String namespace) {
            this.name = name;
            this.namespace = namespace;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Id id = (Id) o;
            return Objects.equal(name, id.name) && Objects.equal(namespace, id.namespace);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name, namespace);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("namespace", namespace)
                    .toString();
        }
    }
}
