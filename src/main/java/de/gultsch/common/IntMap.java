package de.gultsch.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class IntMap<E> implements Map<E, Integer> {

    private final ImmutableMap<E, Integer> inner;

    public IntMap(ImmutableMap<E, Integer> inner) {
        this.inner = inner;
    }

    @Override
    public int size() {
        return this.inner.size();
    }

    @Override
    public boolean isEmpty() {
        return this.inner.isEmpty();
    }

    @Override
    public boolean containsKey(@Nullable Object key) {
        return this.inner.containsKey(key);
    }

    @Override
    public boolean containsValue(@Nullable Object value) {
        return this.inner.containsValue(value);
    }

    @Nullable
    @Override
    public Integer get(@Nullable Object key) {
        return this.inner.get(key);
    }

    public int getInt(@Nullable E key) {
        final var value = this.inner.get(key);
        return value == null ? Integer.MIN_VALUE : value;
    }

    @Nullable
    @Override
    public Integer put(E key, Integer value) {
        return this.inner.put(key, value);
    }

    @Nullable
    @Override
    public Integer remove(@Nullable Object key) {
        return this.inner.remove(key);
    }

    @Override
    public void putAll(@NonNull Map<? extends E, ? extends Integer> m) {
        this.inner.putAll(m);
    }

    @Override
    public void clear() {
        this.inner.clear();
    }

    @NonNull
    @Override
    public Set<E> keySet() {
        return this.inner.keySet();
    }

    @NonNull
    @Override
    public Collection<Integer> values() {
        return this.inner.values();
    }

    @NonNull
    @Override
    public Set<Entry<E, Integer>> entrySet() {
        return this.inner.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IntMap<?> intMap)) return false;
        return Objects.equal(inner, intMap.inner);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(inner);
    }
}
