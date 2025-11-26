package im.conversations.android.xmpp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class NodeConfiguration implements Map<String, Object> {

    private static final String PERSIST_ITEMS = "pubsub#persist_items";
    private static final String ACCESS_MODEL = "pubsub#access_model";
    private static final String SEND_LAST_PUBLISHED_ITEM = "pubsub#send_last_published_item";
    private static final String MAX_ITEMS = "pubsub#max_items";
    private static final String NOTIFY_DELETE = "pubsub#notify_delete";
    private static final String NOTIFY_RETRACT = "pubsub#notify_retract";

    public static final NodeConfiguration OPEN =
            new NodeConfiguration(
                    new ImmutableMap.Builder<String, Object>()
                            .put(PERSIST_ITEMS, Boolean.TRUE)
                            .put(ACCESS_MODEL, "open")
                            .build());
    public static final NodeConfiguration PRESENCE =
            new NodeConfiguration(
                    new ImmutableMap.Builder<String, Object>()
                            .put(PERSIST_ITEMS, Boolean.TRUE)
                            .put(ACCESS_MODEL, "presence")
                            .build());
    public static final NodeConfiguration WHITELIST_MAX_ITEMS =
            new NodeConfiguration(
                    new ImmutableMap.Builder<String, Object>()
                            .put(PERSIST_ITEMS, Boolean.TRUE)
                            .put(ACCESS_MODEL, "whitelist")
                            .put(SEND_LAST_PUBLISHED_ITEM, "never")
                            .put(MAX_ITEMS, "max")
                            .put(NOTIFY_DELETE, Boolean.TRUE)
                            .put(NOTIFY_RETRACT, Boolean.TRUE)
                            .build());
    private final Map<String, Object> delegate;

    private NodeConfiguration(Map<String, Object> map) {
        this.delegate = map;
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public boolean containsKey(@Nullable Object o) {
        return this.delegate.containsKey(o);
    }

    @Override
    public boolean containsValue(@Nullable Object o) {
        return this.delegate.containsValue(o);
    }

    @Nullable
    @Override
    public Object get(@Nullable Object o) {
        return this.delegate.get(o);
    }

    @Nullable
    @Override
    public Object put(String s, Object o) {
        return this.delegate.put(s, o);
    }

    @Nullable
    @Override
    public Object remove(@Nullable Object o) {
        return this.delegate.remove(o);
    }

    @Override
    public void putAll(@NonNull Map<? extends String, ?> map) {
        this.delegate.putAll(map);
    }

    @Override
    public void clear() {
        this.delegate.clear();
    }

    @NonNull
    @Override
    public Set<String> keySet() {
        return this.delegate.keySet();
    }

    @NonNull
    @Override
    public Collection<Object> values() {
        return this.delegate.values();
    }

    @NonNull
    @Override
    public Set<Entry<String, Object>> entrySet() {
        return this.delegate.entrySet();
    }
}
