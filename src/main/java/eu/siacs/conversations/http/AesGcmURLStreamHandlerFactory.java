package eu.siacs.conversations.http;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class AesGcmURLStreamHandlerFactory implements URLStreamHandlerFactory {
    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (AesGcmURLStreamHandler.PROTOCOL_NAME.equals(protocol)) {
            return new AesGcmURLStreamHandler();
        } else {
            return null;
        }
    }
}
