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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A simple directory based cache for caching of persistent items like avatars or entity capabilities.
 *
 * @author Christian Schudt
 */
public final class DirectoryCache implements Map<String, byte[]> {

    private final Path cacheDirectory;

    public DirectoryCache(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    @Override
    public final int size() {
        try (final Stream<Path> files = cacheContent()) {
            return (int) Math.min(files.count(), Integer.MAX_VALUE);
        }
    }

    @Override
    public final boolean isEmpty() {
        try (final Stream<Path> files = cacheContent()) {
            return files.findAny().map(file -> Boolean.FALSE).orElse(Boolean.TRUE);
        }
    }

    @Override
    public final boolean containsKey(Object key) {
        return Files.exists(cacheDirectory.resolve(key.toString()));
    }

    @Override
    public final boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final byte[] get(final Object key) {
        return Optional.ofNullable(key).map(Object::toString).filter(((Predicate<String>) String::isEmpty).negate()).map(cacheDirectory::resolve).filter(Files::isReadable).map(file -> {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).orElse(null);
    }

    @Override
    public final byte[] put(String key, byte[] value) {
        // Make sure the directory exists.
        byte[] data = get(key);
        if (!Arrays.equals(data, value))
            try {
                if (Files.notExists(cacheDirectory)) {
                    Files.createDirectories(cacheDirectory);
                }
                Path file = cacheDirectory.resolve(key);
                Files.write(file, value);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        return data;
    }

    @Override
    public final byte[] remove(Object key) {
        byte[] data = get(key);
        try {
            Files.deleteIfExists(cacheDirectory.resolve(key.toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return data;
    }

    @Override
    public final void putAll(Map<? extends String, ? extends byte[]> m) {
        m.forEach(this::put);
    }

    @Override
    public final void clear() {
        try {
            Files.walkFileTree(cacheDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    // Don't delete the cache directory itself.
                    if (!Files.isSameFile(dir, cacheDirectory)) {
                        Files.deleteIfExists(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public final Set<String> keySet() {
        try (final Stream<Path> files = Files.list(cacheDirectory)) {
            return Collections.unmodifiableSet(files.map(Path::getFileName).map(Path::toString).collect(Collectors.toSet()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public final Collection<byte[]> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final Set<Entry<String, byte[]>> entrySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void forEach(final BiConsumer<? super String, ? super byte[]> action) {
        if (Files.exists(cacheDirectory))
            try (final Stream<Path> files = cacheContent().filter(Files::isReadable)) {
                files.forEach(file -> {
                    try {
                        action.accept(file.getFileName().toString(), Files.readAllBytes(file));
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
    }

    @SuppressWarnings("StreamResourceLeak")
    private final Stream<Path> cacheContent() {
        try {
            return Files.walk(cacheDirectory).filter(Files::isRegularFile);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
