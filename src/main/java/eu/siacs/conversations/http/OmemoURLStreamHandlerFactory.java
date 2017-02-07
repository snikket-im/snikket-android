package eu.siacs.conversations.http;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class OmemoURLStreamHandlerFactory implements URLStreamHandlerFactory {
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if ("omemo".equals(protocol)) {
            return new OmemoURLStreamHandler();
        } else {
            return null;
        }
    }
}
