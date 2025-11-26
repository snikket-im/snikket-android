package im.conversations.android.xmpp.model.axolotl;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;

@XmlElement
public class Header extends Extension {

    public Header() {
        super(Header.class);
    }

    public void addIv(byte[] iv) {
        this.addExtension(new IV()).setContent(iv);
    }

    public void setSourceDevice(long sourceDeviceId) {
        this.setAttribute("sid", sourceDeviceId);
    }

    public Optional<Integer> getSourceDevice() {
        return getOptionalIntAttribute("sid");
    }

    public Collection<Key> getKeys() {
        return this.getExtensions(Key.class);
    }

    public Key getKey(final int deviceId) {
        return Iterables.find(
                getKeys(), key -> Objects.equals(key.getRemoteDeviceId(), deviceId), null);
    }

    public byte[] getIv() {
        final IV iv = this.getExtension(IV.class);
        if (iv == null) {
            throw new IllegalStateException("No IV in header");
        }
        return iv.asBytes();
    }
}
