/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.http;

import android.util.Log;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.upload.Header;
import im.conversations.android.xmpp.model.upload.Slot;
import java.util.Map;
import okhttp3.Headers;
import okhttp3.HttpUrl;

public class SlotRequester {

    private final XmppConnectionService service;

    public SlotRequester(XmppConnectionService service) {
        this.service = service;
    }

    public ListenableFuture<Slot> request(
            final Account account, final DownloadableFile file, final String mime) {
        final var result =
                account.getXmppConnection()
                        .getServiceDiscoveryResultByFeature(Namespace.HTTP_UPLOAD);
        if (result == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No HTTP upload host found"));
        }
        return requestHttpUpload(account, result.getKey(), file, mime);
    }

    private ListenableFuture<Slot> requestHttpUpload(
            final Account account, final Jid host, final DownloadableFile file, final String mime) {
        final Iq request = service.getIqGenerator().requestHttpUploadSlot(host, file, mime);
        final var iqFuture = service.sendIqPacket(account, request);
        return Futures.transform(
                iqFuture,
                response -> {
                    final var slot =
                            response.getExtension(
                                    im.conversations.android.xmpp.model.upload.Slot.class);
                    if (slot == null) {
                        Log.d(Config.LOGTAG, "-->" + response.toString());
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
                    final var headers = new ImmutableMap.Builder<String, String>();
                    for (final Header header : put.getHeaders()) {
                        final String name = header.getHeaderName();
                        final String value = header.getContent();
                        if (Strings.isNullOrEmpty(value) || value.contains("\n")) {
                            continue;
                        }
                        headers.put(name, value.trim());
                    }
                    headers.put("Content-Type", mime == null ? "application/octet-stream" : mime);
                    return new Slot(putUrl, getUrl, headers.buildKeepingLast());
                },
                MoreExecutors.directExecutor());
    }

    public static class Slot {
        public final HttpUrl put;
        public final HttpUrl get;
        public final Headers headers;

        private Slot(HttpUrl put, HttpUrl get, Headers headers) {
            this.put = put;
            this.get = get;
            this.headers = headers;
        }

        private Slot(HttpUrl put, HttpUrl getUrl, Map<String, String> headers) {
            this.put = put;
            this.get = getUrl;
            this.headers = Headers.of(headers);
        }
    }
}
