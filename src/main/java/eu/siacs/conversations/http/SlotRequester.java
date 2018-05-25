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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class SlotRequester {

	private XmppConnectionService service;

	public SlotRequester(XmppConnectionService service) {
		this.service = service;
	}

	public void request(Method method, Account account, DownloadableFile file, String mime, String md5, OnSlotRequested callback) {
		if (method == Method.HTTP_UPLOAD) {
			Jid host = account.getXmppConnection().findDiscoItemByFeature(Namespace.HTTP_UPLOAD);
			requestHttpUpload(account, host, file, mime, callback);
		} else {
			requestP1S3(account, Jid.of(account.getServer()), file.getName(), md5, callback);
		}
	}

	private void requestHttpUpload(Account account, Jid host, DownloadableFile file, String mime, OnSlotRequested callback) {
		IqPacket request = service.getIqGenerator().requestHttpUploadSlot(host, file, mime);
		service.sendIqPacket(account, request, (a, packet) -> {
			if (packet.getType() == IqPacket.TYPE.RESULT) {
				Element slotElement = packet.findChild("slot", Namespace.HTTP_UPLOAD);
				if (slotElement != null) {
					try {
						final Element put = slotElement.findChild("put");
						final Element get = slotElement.findChild("get");
						final String putUrl = put == null ? null : put.getAttribute("url");
						final String getUrl = get == null ? null : get.getAttribute("url");
						if (getUrl != null && putUrl != null) {
							Slot slot = new Slot(new URL(putUrl));
							slot.getUrl = new URL(getUrl);
							slot.headers = new HashMap<>();
							for (Element child : put.getChildren()) {
								if ("header".equals(child.getName())) {
									final String name = child.getAttribute("name");
									final String value = child.getContent();
									if (HttpUploadConnection.WHITE_LISTED_HEADERS.contains(name) && value != null && !value.trim().contains("\n")) {
										slot.headers.put(name, value.trim());
									}
									slot.headers.put("Content-Type", mime == null ? "application/octet-stream" : mime);
								}
							}
							callback.success(slot);
							return;
						}
					} catch (MalformedURLException e) {
						//fall through
					}
				}
			}
			Log.d(Config.LOGTAG, account.getJid().toString() + ": invalid response to slot request " + packet);
			callback.failure(IqParser.extractErrorMessage(packet));
		});

	}

	private void requestP1S3(final Account account, Jid host, String filename, String md5, OnSlotRequested callback) {
		IqPacket request = service.getIqGenerator().requestP1S3Slot(host, md5);
		service.sendIqPacket(account, request, (a, packet) -> {
			if (packet.getType() == IqPacket.TYPE.RESULT) {
				String putUrl = packet.query(Namespace.P1_S3_FILE_TRANSFER).getAttribute("upload");
				String id = packet.query().getAttribute("fileid");
				try {
					if (putUrl != null && id != null) {
						Slot slot = new Slot(new URL(putUrl));
						slot.getUrl = P1S3UrlStreamHandler.of(id, filename);
						slot.headers = new HashMap<>();
						slot.headers.put("Content-MD5", md5);
						slot.headers.put("Content-Type", " "); //required to force it to empty. otherwise library will set something
						callback.success(slot);
						return;
					}
				} catch (MalformedURLException e) {
					//fall through;
				}
			}
			callback.failure("unable to request slot");
		});
		Log.d(Config.LOGTAG, "requesting slot with p1. md5=" + md5);
	}


	public interface OnSlotRequested {

		void success(Slot slot);

		void failure(String message);

	}

	public static class Slot {
		private final URL putUrl;
		private URL getUrl;
		private HashMap<String, String> headers;

		private Slot(URL putUrl) {
			this.putUrl = putUrl;
		}

		public URL getPutUrl() {
			return putUrl;
		}

		public URL getGetUrl() {
			return getUrl;
		}

		public HashMap<String, String> getHeaders() {
			return headers;
		}
	}
}
