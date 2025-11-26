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

package eu.siacs.conversations.ui.text;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.Toast;
import com.google.common.base.Joiner;
import de.gultsch.common.MiniUri;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.ShowLocationActivity;
import java.util.Arrays;

@SuppressLint("ParcelCreator")
public class FixedURLSpan extends URLSpan {

    private FixedURLSpan(final String url) {
        super(url);
    }

    public static void fix(final Spannable spannable) {
        for (final URLSpan urlspan : spannable.getSpans(0, spannable.length() - 1, URLSpan.class)) {
            final int start = spannable.getSpanStart(urlspan);
            final int end = spannable.getSpanEnd(urlspan);
            spannable.removeSpan(urlspan);
            spannable.setSpan(
                    new FixedURLSpan(urlspan.getURL()),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    public void onClick(final View widget) {
        final var uri = new MiniUri(getURL());
        final Context context = widget.getContext();
        final boolean candidateToProcessDirectly =
                "xmpp".equals(uri.getScheme())
                        || ("https".equals(uri.getScheme())
                                && "conversations.im".equals(uri.getAuthority())
                                && uri.getPathSegments().size() > 1
                                && Arrays.asList("j", "i").contains(uri.getPathSegments().get(0)));
        if (candidateToProcessDirectly
                && context instanceof ConversationsActivity conversationsActivity) {
            if (conversationsActivity.onXmppUriClicked(uri.asUri())) {
                Log.d(Config.LOGTAG, "handled xmpp uri internally");
                widget.playSoundEffect(SoundEffectConstants.CLICK);
                return;
            }
        }
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri.asUri());
        if ("web+ap".equals(uri.getScheme())) {
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                Log.d(Config.LOGTAG, "no app found to handle web+ap");
                final var webApAsHttps =
                        Uri.parse(
                                String.format(
                                        "https://%s/%s",
                                        uri.getAuthority(),
                                        Joiner.on('/').join(uri.getPathSegments())));
                final var viewHttpsIntent = new Intent(Intent.ACTION_VIEW, webApAsHttps);
                startActivity(widget, viewHttpsIntent);
                return;
            }
        }
        if ("geo".equals(uri.getScheme())) {
            intent.setClass(context, ShowLocationActivity.class);
        } else {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }
        startActivity(widget, intent);
    }

    private void startActivity(final View widget, final Intent intent) {
        final var context = widget.getContext();
        try {
            context.startActivity(intent);
            widget.playSoundEffect(SoundEffectConstants.CLICK);
        } catch (final ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
