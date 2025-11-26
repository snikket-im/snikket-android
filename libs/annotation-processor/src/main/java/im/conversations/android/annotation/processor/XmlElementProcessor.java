package im.conversations.android.annotation.processor;

import com.google.auto.service.AutoService;
import com.google.common.base.CaseFormat;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.annotation.XmlPackage;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("im.conversations.android.annotation.XmlElement")
public class XmlElementProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        final Set<? extends Element> elements =
                roundEnvironment.getElementsAnnotatedWith(XmlElement.class);
        final ImmutableMap.Builder<Id, String> builder = ImmutableMap.builder();
        for (final Element element : elements) {
            if (element instanceof final TypeElement typeElement) {
                final Id id = of(typeElement);
                builder.put(id, typeElement.getQualifiedName().toString());
            }
        }
        final ImmutableMap<Id, String> maps = builder.build();
        if (maps.isEmpty()) {
            return false;
        }
        final JavaFileObject extensionFile;
        try {
            extensionFile =
                    processingEnv
                            .getFiler()
                            .createSourceFile("im.conversations.android.xmpp.Extensions");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        try (final PrintWriter out = new PrintWriter(extensionFile.openWriter())) {
            out.println("package im.conversations.android.xmpp;");
            out.println("import com.google.common.collect.BiMap;");
            out.println("import com.google.common.collect.ImmutableBiMap;");
            out.println("import im.conversations.android.xmpp.ExtensionFactory;");
            out.println("import im.conversations.android.xmpp.model.Extension;");
            out.print("\n");
            out.println("public final class Extensions {");
            out.println(
                    "public static final BiMap<ExtensionFactory.Id, Class<? extends Extension>>"
                            + " EXTENSION_CLASS_MAP;");
            out.println("static {");
            out.println(
                    "final var builder = new ImmutableBiMap.Builder<ExtensionFactory.Id, Class<?"
                            + " extends Extension>>();");
            for (final Map.Entry<Id, String> entry : maps.entrySet()) {
                Id id = entry.getKey();
                String clazz = entry.getValue();
                out.format(
                        "builder.put(new ExtensionFactory.Id(\"%s\",\"%s\"),%s.class);",
                        id.name, id.namespace, clazz);
                out.print("\n");
            }
            out.println("EXTENSION_CLASS_MAP = builder.build();");
            out.println("}");
            out.println(" private Extensions() {}");
            out.println("}");
            // writing generated file to out …
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static Id of(final TypeElement typeElement) {
        final XmlElement xmlElement = typeElement.getAnnotation(XmlElement.class);
        final PackageElement packageElement = getPackageElement(typeElement);
        final XmlPackage xmlPackage =
                packageElement == null ? null : packageElement.getAnnotation(XmlPackage.class);
        if (xmlElement == null) {
            throw new IllegalStateException(
                    String.format(
                            "%s is not annotated as @XmlElement",
                            typeElement.getQualifiedName().toString()));
        }
        final String packageNamespace = xmlPackage == null ? null : xmlPackage.namespace();
        final String elementName = xmlElement.name();
        final String elementNamespace = xmlElement.namespace();
        final String namespace;
        if (!Strings.isNullOrEmpty(elementNamespace)) {
            namespace = elementNamespace;
        } else if (!Strings.isNullOrEmpty(packageNamespace)) {
            namespace = packageNamespace;
        } else {
            throw new IllegalStateException(
                    String.format(
                            "%s does not declare a namespace",
                            typeElement.getQualifiedName().toString()));
        }
        if (!hasEmptyDefaultConstructor(typeElement)) {
            throw new IllegalStateException(
                    String.format(
                            "%s does not have an empty default constructor",
                            typeElement.getQualifiedName().toString()));
        }
        final String name;
        if (Strings.isNullOrEmpty(elementName)) {
            name =
                    CaseFormat.UPPER_CAMEL.to(
                            CaseFormat.LOWER_HYPHEN, typeElement.getSimpleName().toString());
        } else {
            name = elementName;
        }
        return new Id(name, namespace);
    }

    private static PackageElement getPackageElement(final TypeElement typeElement) {
        final Element parent = typeElement.getEnclosingElement();
        if (parent instanceof PackageElement) {
            return (PackageElement) parent;
        } else {
            final Element nextParent = parent.getEnclosingElement();
            if (nextParent instanceof PackageElement) {
                return (PackageElement) nextParent;
            } else {
                return null;
            }
        }
    }

    private static boolean hasEmptyDefaultConstructor(final TypeElement typeElement) {
        final List<ExecutableElement> constructors =
                ElementFilter.constructorsIn(typeElement.getEnclosedElements());
        for (final ExecutableElement constructor : constructors) {
            if (constructor.getParameters().isEmpty()
                    && constructor.getModifiers().contains(Modifier.PUBLIC)) {
                return true;
            }
        }
        return false;
    }

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
    }
}
