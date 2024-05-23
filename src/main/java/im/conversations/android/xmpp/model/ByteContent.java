package im.conversations.android.xmpp.model;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

import eu.siacs.conversations.xml.Element;

public interface ByteContent {

    String getContent();

    default byte[] asBytes() {
        final var content = this.getContent();
        if (Strings.isNullOrEmpty(content)) {
            throw new IllegalStateException(
                    String.format("%s element is lacking content", getClass().getName()));
        }
        final var contentCleaned = CharMatcher.whitespace().removeFrom(content);
        if (BaseEncoding.base64().canDecode(contentCleaned)) {
            return BaseEncoding.base64().decode(contentCleaned);
        } else {
            throw new IllegalStateException(
                    String.format("%s element contains invalid base64", getClass().getName()));
        }
    }

    default void setContent(final byte[] bytes) {
        setContent(BaseEncoding.base64().encode(bytes));
    }

    Element setContent(final String content);
}
