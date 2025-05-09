package im.conversations.android.xml;

import com.google.common.io.ByteSource;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.XmlReader;
import java.io.IOException;
import java.io.InputStream;

public class XmlElementReader {

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
