package eu.siacs.conversations.http;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

public class CustomURLStreamHandlerFactory implements URLStreamHandlerFactory {

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (AesGcmURLStreamHandler.PROTOCOL_NAME.equals(protocol)) {
            return new AesGcmURLStreamHandler();
        } else if (P1S3UrlStreamHandler.PROTOCOL_NAME.equals(protocol)) {
            return new P1S3UrlStreamHandler();
        } else {
            return null;
        }
    }
}
