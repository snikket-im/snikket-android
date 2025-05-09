package eu.siacs.conversations.entities;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.utils.LanguageUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;

public class Room implements AvatarService.Avatarable, Comparable<Room> {

    public final String address;
    public final String name;
    public final String description;
    public final String language;
    public final int numberOfUsers;

    public Room(
            final String address,
            final String name,
            final String description,
            final String language,
            final Integer numberOfUsers) {
        this.address = address;
        this.name = name;
        this.description = description;
        this.language = language;
        this.numberOfUsers = numberOfUsers == null ? 0 : numberOfUsers;
    }

    public String getName() {
        if (Strings.isNullOrEmpty(name)) {
            final var jid = Jid.ofOrInvalid(address);
            return jid.getLocal();
        } else {
            return name;
        }
    }

    public String getDescription() {
        return description;
    }

    public Jid getRoom() {
        try {
            return Jid.of(address);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getLanguage() {
        return LanguageUtils.convert(language);
    }

    @Override
    public int getAvatarBackgroundColor() {
        Jid room = getRoom();
        return UIHelper.getColorForName(room != null ? room.asBareJid().toString() : name);
    }

    @Override
    public String getAvatarName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Room room = (Room) o;
        return Objects.equal(address, room.address)
                && Objects.equal(name, room.name)
                && Objects.equal(description, room.description);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(address, name, description);
    }

    public boolean contains(String needle) {
        return Strings.nullToEmpty(name).contains(needle)
                || Strings.nullToEmpty(description).contains(needle)
                || Strings.nullToEmpty(address).contains(needle);
    }

    @Override
    public int compareTo(Room o) {
        return ComparisonChain.start()
                .compare(o.numberOfUsers, numberOfUsers)
                .compare(Strings.nullToEmpty(name), Strings.nullToEmpty(o.name))
                .compare(Strings.nullToEmpty(address), Strings.nullToEmpty(o.address))
                .result();
    }

    public static Room of(final Jid address, InfoQuery query) {
        final var identity = Iterables.getFirst(query.getIdentities(), null);
        final var ri =
                query.getServiceDiscoveryExtension("http://jabber.org/protocol/muc#roominfo");
        final String name = identity == null ? null : identity.getIdentityName();
        String roomName = ri == null ? null : ri.getValue("muc#roomconfig_roomname");
        String description = ri == null ? null : ri.getValue("muc#roominfo_description");
        String language = ri == null ? null : ri.getValue("muc#roominfo_lang");
        String occupants = ri == null ? null : ri.getValue("muc#roominfo_occupants");
        final Integer numberOfUsers = Ints.tryParse(Strings.nullToEmpty(occupants));

        return new Room(
                address.toString(),
                Strings.isNullOrEmpty(roomName) ? name : roomName,
                description,
                language,
                numberOfUsers);
    }
}
