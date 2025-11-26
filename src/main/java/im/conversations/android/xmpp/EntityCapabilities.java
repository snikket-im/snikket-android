package im.conversations.android.xmpp;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.data.Field;
import im.conversations.android.xmpp.model.disco.info.Feature;
import im.conversations.android.xmpp.model.disco.info.Identity;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class EntityCapabilities {
    public static EntityCapsHash hash(final InfoQuery info) {
        final StringBuilder s = new StringBuilder();
        final List<Identity> orderedIdentities =
                Ordering.from(
                                (Comparator<Identity>)
                                        (a, b) ->
                                                ComparisonChain.start()
                                                        .compare(
                                                                blankNull(a.getCategory()),
                                                                blankNull(b.getCategory()))
                                                        .compare(
                                                                blankNull(a.getType()),
                                                                blankNull(b.getType()))
                                                        .compare(
                                                                blankNull(a.getLang()),
                                                                blankNull(b.getLang()))
                                                        .compare(
                                                                blankNull(a.getIdentityName()),
                                                                blankNull(b.getIdentityName()))
                                                        .result())
                        .sortedCopy(info.getIdentities());

        for (final Identity id : orderedIdentities) {
            s.append(blankNull(id.getCategory()))
                    .append("/")
                    .append(blankNull(id.getType()))
                    .append("/")
                    .append(blankNull(id.getLang()))
                    .append("/")
                    .append(blankNull(id.getIdentityName()))
                    .append("<");
        }

        final List<String> features =
                Ordering.natural()
                        .sortedCopy(Collections2.transform(info.getFeatures(), Feature::getVar));
        for (final String feature : features) {
            s.append(clean(feature)).append("<");
        }

        final List<Data> extensions =
                Ordering.from(Comparator.comparing(Data::getFormType))
                        .sortedCopy(info.getExtensions(Data.class));

        for (final Data extension : extensions) {
            s.append(clean(extension.getFormType())).append("<");
            final List<Field> fields =
                    Ordering.from(
                                    Comparator.comparing(
                                            (Field lhs) -> Strings.nullToEmpty(lhs.getFieldName())))
                            .sortedCopy(extension.getFields());
            for (final Field field : fields) {
                s.append(Strings.nullToEmpty(field.getFieldName())).append("<");
                final List<String> values = Ordering.natural().sortedCopy(field.getValues());
                for (final String value : values) {
                    s.append(blankNull(value)).append("<");
                }
            }
        }
        return new EntityCapsHash(
                Hashing.sha1().hashString(s.toString(), StandardCharsets.UTF_8).asBytes());
    }

    private static String clean(String s) {
        return s.replace("<", "&lt;");
    }

    private static String blankNull(String s) {
        return s == null ? "" : clean(s);
    }

    public abstract static class Hash {
        public final byte[] hash;

        protected Hash(byte[] hash) {
            this.hash = hash;
        }

        public String encoded() {
            return BaseEncoding.base64().encode(hash);
        }

        public abstract String capabilityNode(final String node);

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Hash hash1 = (Hash) o;
            return Arrays.equals(hash, hash1.hash);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(hash);
        }
    }

    public static class EntityCapsHash extends Hash {

        protected EntityCapsHash(byte[] hash) {
            super(hash);
        }

        @Override
        public String capabilityNode(String node) {
            return String.format("%s#%s", node, encoded());
        }

        public static EntityCapsHash of(final String encoded) {
            return new EntityCapsHash(BaseEncoding.base64().decode(encoded));
        }
    }
}
