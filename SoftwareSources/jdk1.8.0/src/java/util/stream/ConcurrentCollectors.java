/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.util.stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Functions;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * ConcurrentReducers
 *
 * @author Brian Goetz
 */
public class ConcurrentCollectors {
    public static <T, K>
    Collector<T, ConcurrentMap<K, Collection<T>>> groupingBy(Function<? super T, ? extends K> classifier) {
        return groupingBy(classifier, ConcurrentHashMap::new, ArrayList::new);
    }

    public static <T, K, C extends Collection<T>, M extends ConcurrentMap<K, C>>
    Collector<T, M> groupingBy(Function<? super T, ? extends K> classifier,
                               Supplier<M> mapFactory,
                               Supplier<C> rowFactory) {
        return groupingBy(classifier, mapFactory, Collectors.toCollection(rowFactory));
    }

    public static <T, K, D> Collector<T, ConcurrentMap<K, D>> groupingBy(Function<? super T, ? extends K> classifier,
                                                                         Collector<T, D> downstream) {
        return groupingBy(classifier, (Supplier<ConcurrentMap<K, D>>) ConcurrentHashMap::new, downstream);
    }

    static <T, K, D, M extends ConcurrentMap<K, D>> Collector<T, M> groupingBy(Function<? super T, ? extends K> classifier,
                                                                               Supplier<M> mapFactory,
                                                                               Collector<T, D> downstream) {
        Supplier<D> downstreamSupplier = downstream.resultSupplier();
        BiConsumer<D, T> downstreamAccumulator = downstream.accumulator();
        BiConsumer<D, T> wrappedAccumulator
                = downstream.isConcurrent()
                  ? downstreamAccumulator
                  : (d, t) -> { synchronized(d) { downstreamAccumulator.accept(d, t); } };
        BiConsumer<M, T> accumulator = (m, t) -> {
            K key = Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key");
            wrappedAccumulator.accept(m.computeIfAbsent(key, k -> downstreamSupplier.get()), t);
        };
        return new Collectors.CollectorImpl<>(mapFactory, accumulator,
                                              Collectors.leftMapMerger(downstream.combiner()), true);
    }

    public static <T, K> Collector<T, ConcurrentMap<K,T>> groupingReduce(Function<? super T, ? extends K> classifier,
                                                                         BinaryOperator<T> reducer) {
        return groupingReduce(classifier, ConcurrentHashMap::new, Functions.identity(), reducer);
    }

    public static <T, K, M extends ConcurrentMap<K, T>>
    Collector<T, M> groupingReduce(Function<? super T, ? extends K> classifier,
                                   Supplier<M> mapFactory,
                                   BinaryOperator<T> reducer) {
        return groupingReduce(classifier, mapFactory, Functions.identity(), reducer);
    }

    public static <T, K, D> Collector<T, ConcurrentMap<K,D>> groupingReduce(Function<? super T, ? extends K> classifier,
                                                                            Function<? super T, ? extends D> mapper,
                                                                            BinaryOperator<D> reducer) {
        return groupingReduce(classifier, (Supplier<ConcurrentMap<K, D>>) ConcurrentHashMap::new, mapper, reducer);
    }

    public static <T, K, D, M extends ConcurrentMap<K, D>>
    Collector<T, M> groupingReduce(Function<? super T, ? extends K> classifier,
                                   Supplier<M> mapFactory,
                                   Function<? super T, ? extends D> mapper,
                                   BinaryOperator<D> reducer) {
        BiConsumer<M, T> accumulator = (map, value) -> map.merge(classifier.apply(value), mapper.apply(value), reducer);
        return new Collectors.CollectorImpl<>(mapFactory, accumulator, Collectors.leftMapMerger(reducer), true);
    }


    public static <T, U> Collector<T, ConcurrentMap<T,U>> joiningWith(Function<? super T, ? extends U> mapper) {
        return joiningWith(mapper, Collectors.throwingMerger());
    }

    public static <T, U> Collector<T, ConcurrentMap<T,U>> joiningWith(Function<? super T, ? extends U> mapper,
                                                                      BinaryOperator<U> mergeFunction) {
        return joiningWith(mapper, mergeFunction, ConcurrentHashMap::new);
    }

    public static <T, U, M extends ConcurrentMap<T, U>> Collector<T, M> joiningWith(Function<? super T, ? extends U> mapper,
                                                                                    Supplier<M> mapSupplier) {
        return joiningWith(mapper, Collectors.throwingMerger(), mapSupplier);
    }

    public static <T, U, M extends ConcurrentMap<T, U>> Collector<T, M> joiningWith(Function<? super T, ? extends U> mapper,
                                                                                    BinaryOperator<U> mergeFunction,
                                                                                    Supplier<M> mapSupplier) {
        BiConsumer<M, T> accumulator = (map, value) -> map.merge(value, mapper.apply(value), mergeFunction);
        return new Collectors.CollectorImpl<>(mapSupplier, accumulator, Collectors.leftMapMerger(mergeFunction), true);
    }


    public static<T> Collector<T, Map<Boolean, Collection<T>>> partitioningBy(Predicate<T> predicate) {
        return partitioningBy(predicate, ArrayList::new);
    }

    public static<T, C extends Collection<T>> Collector<T, Map<Boolean, C>>
    partitioningBy(Predicate<T> predicate,
                   Supplier<C> rowFactory) {
        return partitioningBy(predicate, Collectors.toCollection(rowFactory));
    }

    public static<T, D> Collector<T, Map<Boolean, D>>
    partitioningBy(Predicate<T> predicate,
                   Collector<T, D> downstream) {
        BiConsumer<D, T> downstreamAccumulator = downstream.accumulator();
        BiConsumer<D, T> wrappedAccumulator
                = downstream.isConcurrent()
                  ? downstreamAccumulator
                  : (d, t) -> { synchronized(d) { downstreamAccumulator.accept(d, t); } };
        BiConsumer<Map<Boolean, D>, T> accumulator = (result, t) -> {
            Collectors.Partition<D> asPartition = ((Collectors.Partition<D>) result);
            wrappedAccumulator.accept(predicate.test(t) ? asPartition.forTrue : asPartition.forFalse, t);
        };
        return new Collectors.CollectorImpl<>(() -> new Collectors.Partition<>(downstream.resultSupplier().get(),
                                                                               downstream.resultSupplier().get()),
                                              accumulator, Collectors.leftPartitionMerger(downstream.combiner()), true);
    }

    public static<T> Collector<T, Map<Boolean, T>> partitioningReduce(Predicate<T> predicate,
                                                                      T identity,
                                                                      BinaryOperator<T> reducer) {
        return partitioningReduce(predicate, identity, Functions.identity(), reducer);
    }

    public static<T, U> Collector<T, Map<Boolean, U>> partitioningReduce(Predicate<T> predicate,
                                                                         U identity,
                                                                         Function<T, U> mapper,
                                                                         BinaryOperator<U> reducer) {
        final Object trueLock = new Object();
        final Object falseLock = new Object();
        BiConsumer<Map<Boolean, U>, T> accumulator = (result, t) -> {
            Collectors.Partition<U> asPartition = ((Collectors.Partition<U>) result);
            if (predicate.test(t)) {
                synchronized (trueLock) {
                    asPartition.forTrue = reducer.apply(asPartition.forTrue, mapper.apply(t));
                }
            }
            else {
                synchronized (falseLock) {
                    asPartition.forFalse = reducer.apply(asPartition.forFalse, mapper.apply(t));
                }
            }
        };
        return new Collectors.CollectorImpl<>(() -> new Collectors.Partition<>(identity, identity),
                                              accumulator, Collectors.leftPartitionMerger(reducer), true);
    }
}
