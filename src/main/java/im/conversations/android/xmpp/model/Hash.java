package im.conversations.android.xmpp.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.CaseFormat;
import com.google.common.base.Strings;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;

@XmlElement(namespace = Namespace.HASHES)
public class Hash extends Extension {
    public Hash() {
        super(Hash.class);
    }

    public Algorithm getAlgorithm() {
        return Algorithm.tryParse(this.getAttribute("algo"));
    }

    public void setAlgorithm(final Algorithm algorithm) {
        this.setAttribute("algo", algorithm.toString());
    }

    public enum Algorithm {
        SHA_1,
        SHA_256,
        SHA_512;

        public static Algorithm tryParse(@Nullable final String name) {
            try {
                return valueOf(
                        CaseFormat.LOWER_HYPHEN.to(
                                CaseFormat.UPPER_UNDERSCORE, Strings.nullToEmpty(name)));
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }

        @NonNull
        @Override
        public String toString() {
            return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, super.toString());
        }
    }
}
