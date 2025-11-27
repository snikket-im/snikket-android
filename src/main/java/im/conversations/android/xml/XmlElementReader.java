package im.conversations.android.xml;

import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.XmlReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class XmlElementReader {

    public static Element read(final String input) throws IOException {
        return read(CharSource.wrap(input).asByteSource(StandardCharsets.UTF_8).openStream());
    }

    public static Element read(byte[] bytes) throws IOException {
        return read(ByteSource.wrap(bytes).openStream());
    }

    public static Element read(final InputStream inputStream) throws IOException {
        try (final XmlReader xmlReader = new XmlReader()) {
            xmlReader.setInputStream(inputStream);
            return xmlReader.readElement(xmlReader.readTag());
        }
    }
}
