package eu.siacs.conversations.xml;

import com.google.common.io.ByteSource;

import java.io.IOException;
import java.io.InputStream;

public class XmlElementReader {

    public static Element read(byte[] bytes) throws IOException {
        return read(ByteSource.wrap(bytes).openStream());
    }

    public static Element read(InputStream inputStream) throws IOException {
        final XmlReader xmlReader = new XmlReader();
        xmlReader.setInputStream(inputStream);
        return xmlReader.readElement(xmlReader.readTag());
    }

}
