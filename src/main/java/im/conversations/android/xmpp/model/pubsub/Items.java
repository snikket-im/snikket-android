package im.conversations.android.xmpp.model.pubsub;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.pubsub.event.Retract;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;

public interface Items {

    Collection<? extends Item> getItems();

    String getNode();

    Collection<Retract> getRetractions();

    default <T extends Extension> Map<String, T> getItemMap(final Class<T> clazz) {
        final ImmutableMap.Builder<String, T> builder = ImmutableMap.builder();
        for (final Item item : getItems()) {
            final var id = item.getId();
            final T extension = item.getExtension(clazz);
            if (extension == null || Strings.isNullOrEmpty(id)) {
                continue;
            }
            builder.put(id, extension);
        }
        return builder.buildKeepingLast();
    }

    default <T extends Extension> T getItemOrThrow(final String id, final Class<T> clazz) {
        final var map = getItemMap(clazz);
        final var item = map.get(id);
        if (item == null) {
            throw new NoSuchElementException(
                    String.format("An item with id %s does not exist", id));
        }
        return item;
    }

    default <T extends Extension> T getFirstItem(final Class<T> clazz) {
        final var map = getItemMap(clazz);
        return Iterables.getFirst(map.values(), null);
    }

    default <T extends Extension> T getOnlyItem(final Class<T> clazz) {
        final var map = getItemMap(clazz);
        return Iterables.getOnlyElement(map.values());
    }
}
