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


import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.xmpp.InvalidJid;
import rocks.xmpp.addr.Jid;

public class JidHelper {

	private static List<String> LOCALPART_BLACKLIST = Arrays.asList("xmpp","jabber","me");

	public static String localPartOrFallback(Jid jid) {
		if (LOCALPART_BLACKLIST.contains(jid.getLocal().toLowerCase(Locale.ENGLISH))) {
			final String domain = jid.getDomain();
			final int index = domain.lastIndexOf('.');
			return index > 1 ? domain.substring(0,index) : domain;
		} else {
			return jid.getLocal();
		}
	}

	public static Jid parseOrFallbackToInvalid(String jid) {
		try {
			return Jid.of(jid);
		} catch (IllegalArgumentException e) {
			return InvalidJid.of(jid, true);
		}
	}

}
