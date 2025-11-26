package im.conversations.android.xmpp.model.axolotl;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

@XmlElement(name = "list")
public class DeviceList extends Extension {

    public DeviceList() {
        super(DeviceList.class);
    }

    public Collection<Device> getDevices() {
        return this.getExtensions(Device.class);
    }

    public Set<Integer> getDeviceIds() {
        return ImmutableSet.copyOf(
                Collections2.filter(
                        Collections2.transform(getDevices(), Device::getDeviceId),
                        Objects::nonNull));
    }

    public void setDeviceIds(Collection<Integer> deviceIds) {
        for (final Integer deviceId : deviceIds) {
            final var device = this.addExtension(new Device());
            device.setDeviceId(deviceId);
        }
    }
}
