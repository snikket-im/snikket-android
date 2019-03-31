package eu.siacs.conversations.entities;

import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ChannelSearchResult implements AvatarService.Avatarable {

    private final String name;
    private final String description;
    private final Jid room;

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Jid getRoom() {
        return room;
    }

    public ChannelSearchResult(String name, String description, Jid room) {
        this.name = name;
        this.description = description;
        this.room = room;
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(room != null ? room.asBareJid().toEscapedString() : getName());
    }
}
