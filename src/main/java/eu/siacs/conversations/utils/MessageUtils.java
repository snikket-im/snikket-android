/*
 * Copyright (c) 2017, Daniel Gultsch All rights reserved.
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

package eu.siacs.conversations.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.http.AesGcmURLStreamHandler;
import eu.siacs.conversations.http.P1S3UrlStreamHandler;

public class MessageUtils {

	private static final Pattern LTR_RTL = Pattern.compile("(\\u200E[^\\u200F]*\\u200F){3,}");

	private static final String EMPTY_STRING = "";

	public static String prepareQuote(Message message) {
		final StringBuilder builder = new StringBuilder();
		final String body = message.getMergedBody().toString();
		for (String line : body.split("\n")) {
			if (line.length() <= 0) {
				continue;
			}
			final char c = line.charAt(0);
			if (c == '>' && UIHelper.isPositionFollowedByQuoteableCharacter(line, 0)
					|| (c == '\u00bb' && !UIHelper.isPositionFollowedByQuote(line, 0))) {
				continue;
			}
			if (builder.length() != 0) {
				builder.append('\n');
			}
			builder.append(line.trim());
		}
		return builder.toString();
	}

	public static boolean treatAsDownloadable(final String body, final boolean oob) {
		try {
			final String[] lines = body.split("\n");
			if (lines.length == 0) {
				return false;
			}
			for (String line : lines) {
				if (line.contains("\\s+")) {
					return false;
				}
			}
			final URL url = new URL(lines[0]);
			final String ref = url.getRef();
			final String protocol = url.getProtocol();
			final boolean encrypted = ref != null && AesGcmURLStreamHandler.IV_KEY.matcher(ref).matches();
			final boolean followedByDataUri = lines.length == 2 && lines[1].startsWith("data:");
			final boolean validAesGcm = AesGcmURLStreamHandler.PROTOCOL_NAME.equalsIgnoreCase(protocol) && encrypted && (lines.length == 1 || followedByDataUri);
			final boolean validProtocol = "http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol) || P1S3UrlStreamHandler.PROTOCOL_NAME.equalsIgnoreCase(protocol);
			final boolean validOob = validProtocol && (oob || encrypted) && lines.length == 1;
			return validAesGcm || validOob;
		} catch (MalformedURLException e) {
			return false;
		}
	}

	public static String filterLtrRtl(String body) {
		return LTR_RTL.matcher(body).replaceFirst(EMPTY_STRING);
	}

	public static boolean unInitiatedButKnownSize(Message message) {
		return message.getType() == Message.TYPE_TEXT && message.getTransferable() == null && message.isOOb() && message.getFileParams().size > 0 && message.getFileParams().url != null;
	}
}
