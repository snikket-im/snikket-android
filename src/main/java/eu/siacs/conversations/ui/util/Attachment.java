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

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class Attachment implements Parcelable {

    Attachment(Parcel in) {
        uri = in.readParcelable(Uri.class.getClassLoader());
        mime = in.readString();
        uuid = UUID.fromString(in.readString());
        type = Type.valueOf(in.readString());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, flags);
        dest.writeString(mime);
        dest.writeString(uuid.toString());
        dest.writeString(type.toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<Attachment> CREATOR = new Creator<Attachment>() {
        @Override
        public Attachment createFromParcel(Parcel in) {
            return new Attachment(in);
        }

        @Override
        public Attachment[] newArray(int size) {
            return new Attachment[size];
        }
    };

    public String getMime() {
        return mime;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        FILE, IMAGE, LOCATION, RECORDING
    }

    private final Uri uri;
    private final Type type;
    private final UUID uuid;
    private final String mime;

    private Attachment(Uri uri, Type type, String mime) {
        this.uri = uri;
        this.type = type;
        this.mime = mime;
        this.uuid = UUID.randomUUID();
    }

    public static List<Attachment> of(final Context context, Uri uri, Type type) {
        final String mime = type == Type.LOCATION ?null :MimeUtils.guessMimeTypeFromUri(context, uri);
        return Collections.singletonList(new Attachment(uri, type, mime));
    }

    public static List<Attachment> of(final Context context, List<Uri> uris) {
        List<Attachment> attachments = new ArrayList<>();
        for(Uri uri : uris) {
            final String mime = MimeUtils.guessMimeTypeFromUri(context, uri);
            attachments.add(new Attachment(uri, mime != null && mime.startsWith("image/") ? Type.IMAGE : Type.FILE,mime));
        }
        return attachments;
    }

    public static List<Attachment> extractAttachments(final Context context, final Intent intent, Type type) {
        List<Attachment> uris = new ArrayList<>();
        if (intent == null) {
            return uris;
        }
        final String contentType = intent.getType();
        final Uri data = intent.getData();
        if (data == null) {
            final ClipData clipData = intent.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); ++i) {
                    final Uri uri = clipData.getItemAt(i).getUri();
                    Log.d(Config.LOGTAG,"uri="+uri+" contentType="+contentType);
                    final String mime = contentType != null ? contentType : MimeUtils.guessMimeTypeFromUri(context, uri);
                    Log.d(Config.LOGTAG,"mime="+mime);
                    uris.add(new Attachment(uri, type, mime));
                }
            }
        } else {
            final String mime = contentType != null ? contentType : MimeUtils.guessMimeTypeFromUri(context, data);
            uris.add(new Attachment(data, type, mime));
        }
        return uris;
    }

    public boolean renderThumbnail() {
        return type == Type.IMAGE || (type == Type.FILE && mime != null && (mime.startsWith("video/") || mime.startsWith("image/")));
    }

    public Uri getUri() {
        return uri;
    }

    public UUID getUuid() {
        return uuid;
    }
}
