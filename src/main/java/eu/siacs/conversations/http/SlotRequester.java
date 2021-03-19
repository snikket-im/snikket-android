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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import okhttp3.Headers;
import okhttp3.HttpUrl;

public class SlotRequester {

    private final XmppConnectionService service;

    public SlotRequester(XmppConnectionService service) {
        this.service = service;
    }

    public void request(Method method, Account account, DownloadableFile file, String mime, OnSlotRequested callback) {
        if (method == Method.HTTP_UPLOAD_LEGACY) {
            final Jid host = account.getXmppConnection().findDiscoItemByFeature(Namespace.HTTP_UPLOAD_LEGACY);
            requestHttpUploadLegacy(account, host, file, mime, callback);
        } else {
            final Jid host = account.getXmppConnection().findDiscoItemByFeature(Namespace.HTTP_UPLOAD);
            requestHttpUpload(account, host, file, mime, callback);
        }
    }

    private void requestHttpUploadLegacy(Account account, Jid host, DownloadableFile file, String mime, OnSlotRequested callback) {
        IqPacket request = service.getIqGenerator().requestHttpUploadLegacySlot(host, file, mime);
        service.sendIqPacket(account, request, (a, packet) -> {
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                Element slotElement = packet.findChild("slot", Namespace.HTTP_UPLOAD_LEGACY);
                if (slotElement != null) {
                    try {
                        final String putUrl = slotElement.findChildContent("put");
                        final String getUrl = slotElement.findChildContent("get");
                        if (getUrl != null && putUrl != null) {
                            final Slot slot = new Slot(
                                    HttpUrl.get(putUrl),
                                    HttpUrl.get(getUrl),
                                    Headers.of("Content-Type", mime == null ? "application/octet-stream" : mime)
                            );
                            callback.success(slot);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        //fall through
                    }
                }
            }
            Log.d(Config.LOGTAG, account.getJid().toString() + ": invalid response to slot request " + packet);
            callback.failure(IqParser.extractErrorMessage(packet));
        });

    }

    private void requestHttpUpload(Account account, Jid host, DownloadableFile file, String mime, OnSlotRequested callback) {
        IqPacket request = service.getIqGenerator().requestHttpUploadSlot(host, file, mime);
        service.sendIqPacket(account, request, (a, packet) -> {
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                final Element slotElement = packet.findChild("slot", Namespace.HTTP_UPLOAD);
                if (slotElement != null) {
                    try {
                        final Element put = slotElement.findChild("put");
                        final Element get = slotElement.findChild("get");
                        final String putUrl = put == null ? null : put.getAttribute("url");
                        final String getUrl = get == null ? null : get.getAttribute("url");
                        if (getUrl != null && putUrl != null) {
                            final ImmutableMap.Builder<String, String> headers = new ImmutableMap.Builder<>();
                            for (Element child : put.getChildren()) {
                                if ("header".equals(child.getName())) {
                                    final String name = child.getAttribute("name");
                                    final String value = child.getContent();
                                    if (HttpUploadConnection.WHITE_LISTED_HEADERS.contains(name) && value != null && !value.trim().contains("\n")) {
                                        headers.put(name, value.trim());
                                    }
                                }
                            }
                            headers.put("Content-Type", mime == null ? "application/octet-stream" : mime);
                            final Slot slot = new Slot(HttpUrl.get(putUrl), HttpUrl.get(getUrl), headers.build());
                            callback.success(slot);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        //fall through
                    }
                }
            }
            Log.d(Config.LOGTAG, account.getJid().toString() + ": invalid response to slot request " + packet);
            callback.failure(IqParser.extractErrorMessage(packet));
        });

    }

    public interface OnSlotRequested {
        void success(Slot slot);
        void failure(String message);
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
