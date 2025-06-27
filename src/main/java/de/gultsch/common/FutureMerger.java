package de.gultsch.common;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.List;

public class FutureMerger {

    public static <T> ListenableFuture<List<T>> successfulAsList(
            final Collection<ListenableFuture<List<T>>> futures) {
        return Futures.transform(
                Futures.successfulAsList(futures),
                lists -> {
                    final var builder = new ImmutableList.Builder<T>();
                    for (final Collection<T> list : lists) {
                        if (list == null) {
                            continue;
                        }
                        builder.addAll(list);
                    }
                    return builder.build();
                },
                MoreExecutors.directExecutor());
    }

    public static <T> ListenableFuture<List<T>> allAsList(
            final Collection<ListenableFuture<Collection<T>>> futures) {
        return Futures.transform(
                Futures.allAsList(futures),
                lists -> {
                    final var builder = new ImmutableList.Builder<T>();
                    for (final Collection<T> list : lists) {
                        builder.addAll(list);
                    }
                    return builder.build();
                },
                MoreExecutors.directExecutor());
    }
}
