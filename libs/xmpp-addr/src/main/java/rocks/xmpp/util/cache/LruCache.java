/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2016 Christian Schudt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package rocks.xmpp.util.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A simple concurrent implementation of a least-recently-used cache.
 * <p>
 * This cache is keeps a maximal number of items in memory and removes the least-recently-used item, when new items are added.
 *
 * @param <K> The key.
 * @param <V> The value.
 * @author Christian Schudt
 * @see <a href="http://javadecodedquestions.blogspot.de/2013/02/java-cache-static-data-loading.html">http://javadecodedquestions.blogspot.de/2013/02/java-cache-static-data-loading.html</a>
 * @see <a href="http://stackoverflow.com/a/22891780">http://stackoverflow.com/a/22891780</a>
 */
public final class LruCache<K, V> implements Map<K, V> {
    private final int maxEntries;

    private final Map<K, V> map;

    final Queue<K> queue;

    public LruCache(final int maxEntries) {
        this.maxEntries = maxEntries;
        this.map = new ConcurrentHashMap<>(maxEntries);
        // Don't use a ConcurrentLinkedQueue here.
        // There's a JDK bug, leading to OutOfMemoryError and high CPU usage:
        // https://bugs.openjdk.java.net/browse/JDK-8054446
        this.queue = new ConcurrentLinkedDeque<>();
    }

    @Override
    public final int size() {
        return map.size();
    }

    @Override
    public final boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public final boolean containsKey(final Object key) {
        return map.containsKey(key);
    }

    @Override
    public final boolean containsValue(final Object value) {
        return map.containsValue(value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public final V get(final Object key) {
        final V v = map.get(key);
        if (v != null) {
            // Remove the key from the queue and re-add it to the tail. It is now the most recently used key.
            keyUsed((K) key);
        }
        return v;
    }


    @Override
    public final V put(final K key, final V value) {
        V v = map.put(key, value);
        keyUsed(key);
        limit();
        return v;
    }

    @Override
    public final V remove(final Object key) {
        queue.remove(key);
        return map.remove(key);
    }


    @Override
    public final void putAll(final Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public final void clear() {
        queue.clear();
        map.clear();
    }

    @Override
    public final Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public final Collection<V> values() {
        return map.values();
    }

    @Override
    public final Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }


    // Default methods

    @Override
    public final V putIfAbsent(final K key, final V value) {
        final V v = map.putIfAbsent(key, value);
        if (v == null) {
            keyUsed(key);
        }
        limit();
        return v;
    }

    @Override
    public final boolean remove(final Object key, final Object value) {
        final boolean removed = map.remove(key, value);
        if (removed) {
            queue.remove(key);
        }
        return removed;
    }

    @Override
    public final boolean replace(final K key, final V oldValue, final V newValue) {
        final boolean replaced = map.replace(key, oldValue, newValue);
        if (replaced) {
            keyUsed(key);
        }
        return replaced;
    }

    @Override
    public final V replace(final K key, final V value) {
        final V v = map.replace(key, value);
        if (v != null) {
            keyUsed(key);
        }
        return v;
    }

    @Override
    public final V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        return map.computeIfAbsent(key, mappingFunction.<V>andThen(v -> {
            keyUsed(key);
            limit();
            return v;
        }));
    }

    @Override
    public final V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return map.computeIfPresent(key, remappingFunction.<V>andThen(v -> {
            keyUsed(key);
            limit();
            return v;
        }));
    }

    @Override
    public final V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        return map.compute(key, remappingFunction.<V>andThen(v -> {
            keyUsed(key);
            limit();
            return v;
        }));
    }

    @Override
    public final V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        return map.merge(key, value, remappingFunction.<V>andThen(v -> {
            keyUsed(key);
            limit();
            return v;
        }));
    }

    private void limit() {
        while (queue.size() > maxEntries) {
            final K oldestKey = queue.poll();
            if (oldestKey != null) {
                map.remove(oldestKey);
            }
        }
    }

    private void keyUsed(final K key) {
        // remove it from the queue and re-add it, to make it the most recently used key.
        queue.remove(key);
        queue.offer(key);
    }
}