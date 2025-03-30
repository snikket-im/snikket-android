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

package eu.siacs.conversations.ui.util;

import android.net.Uri;
import android.text.Editable;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import eu.siacs.conversations.ui.text.FixedURLSpan;
import eu.siacs.conversations.utils.Patterns;
import eu.siacs.conversations.utils.XmppUri;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MyLinkify {

    private static final Linkify.MatchFilter MATCH_FILTER =
            (s, start, end) -> {
                final var match = s.subSequence(start, end);
                final var scheme =
                        Iterables.getFirst(Splitter.on(':').limit(2).splitToList(match), null);
                if (scheme == null) {
                    return false;
                }
                return switch (scheme) {
                    case "tel" -> Patterns.URI_TEL.matcher(match).matches();
                    case "http", "https" -> Patterns.URI_HTTP.matcher(match).matches();
                    case "geo" -> Patterns.URI_GEO.matcher(match).matches();
                    case "xmpp" -> new XmppUri(Uri.parse(match.toString())).isValidJid();
                    case "web+ap" -> Patterns.URI_WEB_AP.matcher(match).matches();
                    default -> true;
                };
            };

    public static void addLinks(final Editable body) {
        Linkify.addLinks(body, Patterns.URI_GENERIC, null, MATCH_FILTER, null);
        FixedURLSpan.fix(body);
    }

    public static List<String> extractLinks(final Editable body) {
        MyLinkify.addLinks(body);
        final Collection<URLSpan> spans =
                Arrays.asList(body.getSpans(0, body.length() - 1, URLSpan.class));
        final Collection<UrlWrapper> urlWrappers =
                Collections2.filter(
                        Collections2.transform(
                                spans,
                                s ->
                                        s == null
                                                ? null
                                                : new UrlWrapper(body.getSpanStart(s), s.getURL())),
                        Objects::nonNull);
        List<UrlWrapper> sorted =
                ImmutableList.sortedCopyOf(Comparator.comparingInt(a -> a.position), urlWrappers);
        return Lists.transform(sorted, uw -> uw.url);
    }

    private static class UrlWrapper {
        private final int position;
        private final String url;

        private UrlWrapper(int position, String url) {
            this.position = position;
            this.url = url;
        }
    }
}
