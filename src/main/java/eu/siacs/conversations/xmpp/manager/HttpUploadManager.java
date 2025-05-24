package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Base64;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.upload.Request;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import okhttp3.Headers;
import okhttp3.HttpUrl;

public class HttpUploadManager extends AbstractManager {

    public HttpUploadManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Slot> request(final DownloadableFile file, final String mime) {
        final var result =
                getManager(DiscoManager.class).findDiscoItemByFeature(Namespace.HTTP_UPLOAD);
        if (result == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No HTTP upload host found"));
        }
        return requestHttpUpload(result.getKey(), file, mime);
    }

    private ListenableFuture<Slot> requestHttpUpload(
            final Jid host, final DownloadableFile file, final String mime) {
        final Iq iq = new Iq(Iq.Type.GET);
        iq.setTo(host);
        final var request = iq.addExtension(new Request());
        request.setFilename(convertFilename(file.getName()));
        request.setSize(file.getExpectedSize());
        request.setContentType(mime);
        final var iqFuture = this.connection.sendIqPacket(iq);
        return Futures.transform(
                iqFuture,
                response -> {
                    final var slot =
                            response.getExtension(
                                    im.conversations.android.xmpp.model.upload.Slot.class);
                    if (slot == null) {
                        throw new IllegalStateException("Slot not found in IQ response");
                    }
                    final var getUrl = slot.getGetUrl();
                    final var put = slot.getPut();
                    if (getUrl == null || put == null) {
                        throw new IllegalStateException("Missing get or put in slot response");
                    }
                    final var putUrl = put.getUrl();
                    if (putUrl == null) {
                        throw new IllegalStateException("Missing put url");
                    }
                    final var contentType = mime == null ? "application/octet-stream" : mime;
                    final var headers =
                            new ImmutableMap.Builder<String, String>()
                                    .putAll(put.getHeadersAllowList())
                                    .put("Content-Type", contentType)
                                    .buildKeepingLast();
                    return new Slot(putUrl, getUrl, headers);
                },
                MoreExecutors.directExecutor());
    }

    private static String convertFilename(final String name) {
        int pos = name.indexOf('.');
        if (pos < 0) {
            return name;
        }
        try {
            UUID uuid = UUID.fromString(name.substring(0, pos));
            ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
            bb.putLong(uuid.getMostSignificantBits());
            bb.putLong(uuid.getLeastSignificantBits());
            return Base64.encodeToString(
                            bb.array(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP)
                    + name.substring(pos);
        } catch (final Exception e) {
            return name;
        }
    }

    public static class Slot {
        public final HttpUrl put;
        public final HttpUrl get;
        public final Headers headers;

        private Slot(final HttpUrl put, final HttpUrl get, final Headers headers) {
            this.put = put;
            this.get = get;
            this.headers = headers;
        }

        private Slot(final HttpUrl put, final HttpUrl get, final Map<String, String> headers) {
            this(put, get, Headers.of(headers));
        }
    }
}
