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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.Functions;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Collectors
 */
public class Collectors {
    public static<T> BinaryOperator<T> throwingMerger() {
        return (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); };
    }

    static class AbstractCollectorImpl<R> {
        private final Supplier<R> resultSupplier;
        private final BinaryOperator<R> combiner;
        private final boolean isConcurrent;

        AbstractCollectorImpl(Supplier<R> resultSupplier,
                              BinaryOperator<R> combiner,
                              boolean isConcurrent) {
            this.resultSupplier = resultSupplier;
            this.combiner = combiner;
            this.isConcurrent = isConcurrent;
        }

        AbstractCollectorImpl(Supplier<R> resultSupplier, BinaryOperator<R> combiner) {
            this(resultSupplier, combiner, false);
        }

        public Supplier<R> resultSupplier() {
            return resultSupplier;
        }

        public BinaryOperator<R> combiner() {
            return combiner;
        }

        public boolean isConcurrent() {
            return isConcurrent;
        }
    }

    static class CollectorImpl<T, R> extends AbstractCollectorImpl<R> implements Collector<T, R> {
        private final BiConsumer<R, T> accumulator;

        CollectorImpl(Supplier<R> resultSupplier,
                      BiConsumer<R, T> accumulator,
                      BinaryOperator<R> combiner,
                      boolean isConcurrent) {
            super(resultSupplier, combiner, isConcurrent);
            this.accumulator = accumulator;
        }

        CollectorImpl(Supplier<R> resultSupplier, BiConsumer<R, T> accumulator, BinaryOperator<R> combiner) {
            this(resultSupplier, accumulator, combiner, false);
        }

        @Override
        public BiConsumer<R, T> accumulator() {
            return accumulator;
        }
    }

    static class IntCollectorImpl<R> extends AbstractCollectorImpl<R> implements Collector.OfInt<R> {
        private final ObjIntConsumer<R> intAccumulator;

        IntCollectorImpl(Supplier<R> resultSupplier,
                         ObjIntConsumer<R> accumulator,
                         BinaryOperator<R> combiner,
                         boolean isConcurrent) {
            super(resultSupplier, combiner, isConcurrent);
            this.intAccumulator = accumulator;
        }

        IntCollectorImpl(Supplier<R> resultSupplier, ObjIntConsumer<R> accumulator, BinaryOperator<R> combiner) {
            this(resultSupplier, accumulator, combiner, false);
        }

        @Override
        public ObjIntConsumer<R> intAccumulator() {
            return intAccumulator;
        }
    }

    static class DoubleCollectorImpl<R> extends AbstractCollectorImpl<R> implements Collector.OfDouble<R> {
        private final ObjDoubleConsumer<R> doubleAccumulator;

        DoubleCollectorImpl(Supplier<R> resultSupplier,
                            ObjDoubleConsumer<R> accumulator,
                            BinaryOperator<R> combiner,
                            boolean isConcurrent) {
            super(resultSupplier, combiner, isConcurrent);
            this.doubleAccumulator = accumulator;
        }

        DoubleCollectorImpl(Supplier<R> resultSupplier, ObjDoubleConsumer<R> accumulator, BinaryOperator<R> combiner) {
            this(resultSupplier, accumulator, combiner, false);
        }

        @Override
        public ObjDoubleConsumer<R> doubleAccumulator() {
            return doubleAccumulator;
        }
    }

    static class LongCollectorImpl<R> extends AbstractCollectorImpl<R> implements Collector.OfLong<R> {
        private final ObjLongConsumer<R> longAccumulator;

        LongCollectorImpl(Supplier<R> resultSupplier,
                          ObjLongConsumer<R> accumulator,
                          BinaryOperator<R> combiner,
                          boolean isConcurrent) {
            super(resultSupplier, combiner, isConcurrent);
            this.longAccumulator = accumulator;
        }

        LongCollectorImpl(Supplier<R> resultSupplier, ObjLongConsumer<R> accumulator, BinaryOperator<R> combiner) {
            this(resultSupplier, accumulator, combiner, false);
        }

        @Override
        public ObjLongConsumer<R> longAccumulator() {
            return longAccumulator;
        }
    }


    static<T, R> Collector<T, R> leftCombining(Supplier<R> supplier,
                                               BiConsumer<R, T> accumulator,
                                               BiConsumer<R, R> merger) {
        return new CollectorImpl<>(supplier, accumulator, (r1, r2) -> { merger.accept(r1, r2); return r1; });
    }

    public static<T, C extends Collection<T>>
    Collector<T,C> toCollection(Supplier<C> collectionFactory) {
        return leftCombining(collectionFactory, Collection::add, Collection::addAll);
    }

    public static<T>
    Collector<T,List<T>> toList() {
        // Consider a tree-based List?
        return toCollection(ArrayList::new);
    }

    public static<T>
    Collector<T,Set<T>> toSet() {
        // @@@ Declare that the collector is NOT_ORDERED so the fold op can declare NOT_ORDERED in
        //     the terminal op flags
        return toCollection(HashSet::new);
    }

    public static Collector<String, StringBuilder> toStringBuilder() {
        return leftCombining(StringBuilder::new, StringBuilder::append, (sb, other) -> sb.append(other));
    }

    public static Collector<CharSequence, StringJoiner> toStringJoiner(String separator) {
        return leftCombining(() -> new StringJoiner(separator),
                             StringJoiner::add,
                             (sb, other) -> { if (other.length() > 0) sb.add(other.toString()); });
    }

    static<K, V, M extends Map<K,V>> BinaryOperator<M> leftMapMerger(BinaryOperator<V> mergeFunction) {
        return (m1, m2) -> {
            for (Map.Entry<K,V> e : m2.entrySet())
                m1.merge(e.getKey(), e.getValue(), mergeFunction);
            return m1;
        };
    }

    public static <T, U, R> Collector<T, R>
    mapping(Function<? super T, ? extends U> mapper, Collector<U, R> downstream) {
        BiConsumer<R, U> downstreamAccumulator = downstream.accumulator();
        return new CollectorImpl<>(downstream.resultSupplier(),
                                   (r, t) -> downstreamAccumulator.accept(r, mapper.apply(t)),
                                   downstream.combiner(), downstream.isConcurrent());
    }

    public static <T, R> Collector<T, R>
    mapping(ToIntFunction<? super T> mapper, Collector.OfInt<R> downstream) {
        ObjIntConsumer<R> downstreamAccumulator = downstream.intAccumulator();
        return new CollectorImpl<>(downstream.resultSupplier(),
                                   (r, t) -> downstreamAccumulator.accept(r, mapper.applyAsInt(t)),
                                   downstream.combiner(), downstream.isConcurrent());
    }

    public static <T, R> Collector<T, R>
    mapping(ToDoubleFunction<? super T> mapper, Collector.OfDouble<R> downstream) {
        ObjDoubleConsumer<R> downstreamAccumulator = downstream.doubleAccumulator();
        return new CollectorImpl<>(downstream.resultSupplier(),
                                   (r, t) -> downstreamAccumulator.accept(r, mapper.applyAsDouble(t)),
                                   downstream.combiner(), downstream.isConcurrent());
    }

    public static <T, R> Collector<T, R>
    mapping(ToLongFunction<? super T> mapper, Collector.OfLong<R> downstream) {
        ObjLongConsumer<R> downstreamAccumulator = downstream.longAccumulator();
        return new CollectorImpl<>(downstream.resultSupplier(),
                                   (r, t) -> downstreamAccumulator.accept(r, mapper.applyAsLong(t)),
                                   downstream.combiner(), downstream.isConcurrent());
    }

    public static <T, K>
    Collector<T, Map<K, Collection<T>>> groupingBy(Function<? super T, ? extends K> classifier) {
        return Collectors.groupingBy(classifier, HashMap::new, ArrayList::new);
    }

    public static <T, K, C extends Collection<T>, M extends Map<K, C>>
    Collector<T, M> groupingBy(Function<? super T, ? extends K> classifier,
                               Supplier<M> mapFactory,
                               Supplier<C> rowFactory) {
        return groupingBy(classifier, mapFactory, toCollection(rowFactory));
    }

    public static <T, K, D> Collector<T, Map<K, D>> groupingBy(Function<? super T, ? extends K> classifier,
                                                               Collector<T, D> downstream) {
        return groupingBy(classifier, HashMap::new, downstream);
    }

    public static <T, K, D, M extends Map<K, D>> Collector<T, M> groupingBy(Function<? super T, ? extends K> classifier,
                                                                            Supplier<M> mapFactory,
                                                                            Collector<T, D> downstream) {
        Supplier<D> downstreamSupplier = downstream.resultSupplier();
        BiConsumer<D, T> downstreamAccumulator = downstream.accumulator();
        BinaryOperator<D> downstreamCombiner = downstream.combiner();
        BiConsumer<M, T> accumulator = (m, t) -> {
            K key = Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key");
            downstreamAccumulator.accept(m.computeIfAbsent(key, k -> downstreamSupplier.get()), t);
        };
        return new CollectorImpl<>(mapFactory, accumulator, Collectors.<K, D, M>leftMapMerger(downstreamCombiner));
    }

    public static <T, K> Collector<T, Map<K,T>> groupingReduce(Function<? super T, ? extends K> classifier,
                                                               BinaryOperator<T> reducer) {
        return groupingReduce(classifier, HashMap::new, Functions.identity(), reducer);
    }

    public static <T, K, M extends Map<K, T>> Collector<T, M>
    groupingReduce(Function<? super T, ? extends K> classifier,
                   Supplier<M> mapFactory,
                   BinaryOperator<T> reducer) {
        return groupingReduce(classifier, mapFactory, Functions.identity(), reducer);
    }

    public static <T, K, D> Collector<T, Map<K,D>>
    groupingReduce(Function<? super T, ? extends K> classifier,
                   Function<? super T, ? extends D> mapper,
                   BinaryOperator<D> reducer) {
        return groupingReduce(classifier, HashMap::new, mapper, reducer);
    }

    public static <T, K, D, M extends Map<K, D>> Collector<T, M>
    groupingReduce(Function<? super T, ? extends K> classifier,
                   Supplier<M> mapFactory,
                   Function<? super T, ? extends D> mapper,
                   BinaryOperator<D> reducer) {
        BiConsumer<M, T> accumulator = (map, value) -> {
            map.merge(Objects.requireNonNull(classifier.apply(value), "element cannot be mapped to a null key"),
                      mapper.apply(value), reducer);
        };
        return new CollectorImpl<>(mapFactory, accumulator, Collectors.<K, D, M>leftMapMerger(reducer));
    }

    public static <T, U> Collector<T, Map<T,U>> joiningWith(Function<? super T, ? extends U> mapper) {
        return joiningWith(mapper, throwingMerger());
    }

    public static <T, U> Collector<T, Map<T,U>> joiningWith(Function<? super T, ? extends U> mapper,
                                                            BinaryOperator<U> mergeFunction) {
        return joiningWith(mapper, mergeFunction, HashMap::new);
    }

    public static <T, U, M extends Map<T, U>> Collector<T, M> joiningWith(Function<? super T, ? extends U> mapper,
                                                                          Supplier<M> mapSupplier) {
        return joiningWith(mapper, throwingMerger(), mapSupplier);
    }
    public static <T, U, M extends Map<T, U>> Collector<T, M> joiningWith(Function<? super T, ? extends U> mapper,
                                                                          BinaryOperator<U> mergeFunction,
                                                                          Supplier<M> mapSupplier) {
        BiConsumer<M, T> accumulator = (map, value) -> map.merge(value, mapper.apply(value), mergeFunction);
        return new CollectorImpl<>(mapSupplier, accumulator, leftMapMerger(mergeFunction));
    }

    static class Partition<T> extends AbstractMap<Boolean, T> implements Map<Boolean, T> {
        T forTrue;
        T forFalse;

        Partition(T forTrue, T forFalse) {
            this.forTrue = forTrue;
            this.forFalse = forFalse;
        }

        class Entry implements Map.Entry<Boolean, T> {
            private final int state;

            Entry(int state) {
                this.state = state;
            }

            @Override
            public Boolean getKey() {
                return state == 1;
            }

            @Override
            public T getValue() {
                return state == 0 ? forFalse : forTrue;
            }

            @Override
            public T setValue(T value) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public Set<Map.Entry<Boolean, T>> entrySet() {
            return new AbstractSet<Map.Entry<Boolean, T>>() {
                @Override
                public Iterator<Map.Entry<Boolean, T>> iterator() {
                    return new Iterator<Map.Entry<Boolean, T>>() {
                        int state = 0;

                        @Override
                        public boolean hasNext() {
                            return state < 2;
                        }

                        @Override
                        public Map.Entry<Boolean, T> next() {
                            if (state < 2)
                                return new Entry(++state);
                            else
                                throw new NoSuchElementException();
                        }
                    };
                }

                @Override
                public int size() {
                    return 2;
                }
            };
        }
    }

    public static<T> Collector<T, Map<Boolean, Collection<T>>> partitioningBy(Predicate<T> predicate) {
        return partitioningBy(predicate, ArrayList::new);
    }

    public static<T, C extends Collection<T>> Collector<T, Map<Boolean, C>>
    partitioningBy(Predicate<T> predicate,
                   Supplier<C> rowFactory) {
        return partitioningBy(predicate, toCollection(rowFactory));
    }

    static<D> BinaryOperator<Map<Boolean, D>> leftPartitionMerger(BinaryOperator<D> op) {
        return (m1, m2) -> {
            Partition<D> left = (Partition<D>) m1;
            Partition<D> right = (Partition<D>) m2;
            left.forFalse = op.apply(left.forFalse, right.forFalse);
            left.forTrue = op.apply(left.forTrue, right.forTrue);
            return left;
        };
    }

    public static<T, D> Collector<T, Map<Boolean, D>>
    partitioningBy(Predicate<T> predicate,
                   Collector<T, D> downstream) {
        BiConsumer<D, T> downstreamAccumulator = downstream.accumulator();
        BiConsumer<Map<Boolean, D>, T> accumulator = (result, t) -> {
            Partition<D> asPartition = ((Partition<D>) result);
            downstreamAccumulator.accept(predicate.test(t) ? asPartition.forTrue : asPartition.forFalse, t);
        };
        return new CollectorImpl<>(() -> new Partition<>(downstream.resultSupplier().get(),
                                                         downstream.resultSupplier().get()),
                                   accumulator, leftPartitionMerger(downstream.combiner()));
    }

    public static<T> Collector<T, Map<Boolean, T>>
    partitioningReduce(Predicate<T> predicate,
                       T identity,
                       BinaryOperator<T> reducer) {
        return partitioningReduce(predicate, identity, Functions.identity(), reducer);
    }

    public static<T, U> Collector<T, Map<Boolean, U>>
    partitioningReduce(Predicate<T> predicate,
                       U identity,
                       Function<T, U> mapper,
                       BinaryOperator<U> reducer) {
        BiConsumer<Map<Boolean, U>, T> accumulator = (result, t) -> {
            Partition<U> asPartition = ((Partition<U>) result);
            if (predicate.test(t))
                asPartition.forTrue = reducer.apply(asPartition.forTrue, mapper.apply(t));
            else
                asPartition.forFalse = reducer.apply(asPartition.forFalse, mapper.apply(t));
        };
        return new CollectorImpl<>(() -> new Partition<>(identity, identity),
                                   accumulator, leftPartitionMerger(reducer));
    }

    public static final class LongStatistics implements LongConsumer, IntConsumer {
        private long count;
        private long sum;
        private long min;
        private long max;

        @Override
        public void accept(int value) {
            accept((long) value);
        }

        @Override
        public void accept(long value) {
            ++count;
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        private void combine(LongStatistics other) {
            count += other.count;
            sum += other.sum;
            min = Math.min(min, other.min);
            max = Math.min(max, other.max);
        }

        public long getCount() {
            return count;
        }

        public long getSum() {
            return sum;
        }

        public long getMin() {
            return min;
        }

        public long getMax() {
            return max;
        }

        public OptionalDouble getMean() {
            return count > 0 ? OptionalDouble.of((double) sum / count) : OptionalDouble.empty();
        }
    }

    public static final class DoubleStatistics implements DoubleConsumer {
        private long count;
        private double sum;
        private double min;
        private double max;

        @Override
        public void accept(double value) {
            ++count;
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        private void combine(DoubleStatistics other) {
            count += other.count;
            sum += other.sum;
            min = Math.min(min, other.min);
            max = Math.min(max, other.max);
        }

        public long getCount() {
            return count;
        }

        public double getSum() {
            return sum;
        }

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public OptionalDouble getMean() {
            return count > 0 ? OptionalDouble.of(sum / count) : OptionalDouble.empty();
        }
    }

    public static Collector.OfLong<LongStatistics> toLongStatistics() {
        return new LongCollectorImpl<>(LongStatistics::new, LongStatistics::accept,
                                      (l, r) -> { l.combine(r); return l; });
    }

    public static Collector.OfDouble<DoubleStatistics> toDoubleStatistics() {
        return new DoubleCollectorImpl<>(DoubleStatistics::new, DoubleStatistics::accept,
                                         (l, r) -> { l.combine(r); return l; });
    }
}
