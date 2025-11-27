package eu.siacs.conversations.xmpp.pep;

import android.util.Base64;
import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import eu.siacs.conversations.xmpp.Jid;
import okhttp3.HttpUrl;

public class Avatar {

    @Override
    @NonNull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("type", type)
                .add("sha1sum", sha1sum)
                .add("url", url)
                .add("image", image)
                .add("height", height)
                .add("width", width)
                .add("size", size)
                .add("owner", owner)
                .add("origin", origin)
                .toString();
    }

    public enum Origin {
        PEP,
        VCARD
    }

    public String type;
    public String sha1sum;
    public HttpUrl url;
    public String image;
    public int height;
    public int width;
    public long size;
    public Jid owner;
    public Origin origin = Origin.PEP; // default to maintain compat

    public byte[] getImageAsBytes() {
        return Base64.decode(image, Base64.DEFAULT);
    }

    public String getFilename() {
        return sha1sum;
    }

    @Override
    public boolean equals(Object object) {
        if (object != null && object instanceof Avatar other) {
            return other.getFilename().equals(this.getFilename());
        } else {
            return false;
        }
    }
}
