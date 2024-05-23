package im.conversations.android.xmpp.model.muc.user;

import com.google.common.collect.Collections2;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;

@XmlElement(name = "x")
public class MucUser extends Extension {

    public static final int STATUS_CODE_SELF_PRESENCE = 110;

    public MucUser() {
        super(MucUser.class);
    }

    public Item getItem() {
        return this.getExtension(Item.class);
    }

    public Collection<Integer> getStatus() {
        return Collections2.filter(
                Collections2.transform(getExtensions(Status.class), Status::getCode),
                Objects::nonNull);
    }
}
