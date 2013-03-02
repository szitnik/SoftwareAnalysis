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

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Streams
 *
 * @author Brian Goetz
 */
public class Streams {

    private Streams() {
        throw new Error("no instances");
    }

    public static final long MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private static final int MAX_ITERATOR_CHUNK_SIZE = 1024;

    // Stream construction

    public static<T> Stream<T> emptyStream() {
        return stream(emptySpliterator());
    }

    public static<T> Stream<T> stream(Supplier<? extends Spliterator<T>> source, int characteristics) {
        Objects.requireNonNull(source);
        return new ReferencePipeline<>(source,
                                       StreamOpFlag.fromCharacteristics(characteristics) & ~StreamOpFlag.IS_PARALLEL);
    }

    public static<T> Stream<T> parallelStream(Supplier<? extends Spliterator<T>> source, int characteristics) {
        Objects.requireNonNull(source);
        return new ReferencePipeline<>(source,
                                       StreamOpFlag.fromCharacteristics(characteristics) | StreamOpFlag.IS_PARALLEL);
    }

    public static<T> Stream<T> stream(Spliterator<T> source) {
        return stream(() -> source, source.characteristics());
    }

    public static<T> Stream<T> parallelStream(Spliterator<T> source) {
        return parallelStream(() -> source, source.characteristics());
    }

    // IntStream construction

    public static IntStream emptyIntStream() {
        return intStream(emptyIntSpliterator());
    }

    public static IntStream intStream(Supplier<? extends Spliterator.OfInt> source, int characteristics) {
        return new IntPipeline<>(source,
                                 StreamOpFlag.fromCharacteristics(characteristics) & ~StreamOpFlag.IS_PARALLEL);
    }

    public static IntStream intParallelStream(Supplier<? extends Spliterator.OfInt> source, int characteristics) {
        return new IntPipeline<>(source,
                                 StreamOpFlag.fromCharacteristics(characteristics) | StreamOpFlag.IS_PARALLEL);
    }

    public static IntStream intStream(Spliterator.OfInt source) {
        return intStream(() -> source, source.characteristics());
    }

    public static IntStream intParallelStream(Spliterator.OfInt source) {
        return intParallelStream(() -> source, source.characteristics());
    }

    // LongStream construction

    public static LongStream emptyLongStream() {
        return longStream(emptyLongSpliterator());
    }

    public static LongStream longStream(Supplier<? extends Spliterator.OfLong> source, int characteristics) {
        return new LongPipeline<>(source,
                                  StreamOpFlag.fromCharacteristics(characteristics) & ~StreamOpFlag.IS_PARALLEL);
    }

    public static LongStream longParallelStream(Supplier<? extends Spliterator.OfLong> source, int characteristics) {
        return new LongPipeline<>(source,
                                  StreamOpFlag.fromCharacteristics(characteristics) | StreamOpFlag.IS_PARALLEL);
    }

    public static LongStream longStream(Spliterator.OfLong source) {
        return longStream(() -> source, source.characteristics());
    }

    public static LongStream longParallelStream(Spliterator.OfLong source) {
        return longParallelStream(() -> source, source.characteristics());
    }

    // DoubleStream construction

    public static DoubleStream emptyDoubleStream() {
        return doubleStream(emptyDoubleSpliterator());
    }

    public static DoubleStream doubleStream(Supplier<? extends Spliterator.OfDouble> source, int characteristics) {
        return new DoublePipeline<>(source,
                                    StreamOpFlag.fromCharacteristics(characteristics) & ~StreamOpFlag.IS_PARALLEL);
    }

    public static DoubleStream doubleParallelStream(Supplier<? extends Spliterator.OfDouble> source, int characteristics) {
        return new DoublePipeline<>(source,
                                    StreamOpFlag.fromCharacteristics(characteristics) | StreamOpFlag.IS_PARALLEL);
    }

    public static DoubleStream doubleStream(Spliterator.OfDouble source) {
        return doubleStream(() -> source, source.characteristics());
    }

    public static DoubleStream doubleParallelStream(Spliterator.OfDouble source) {
        return doubleParallelStream(() -> source, source.characteristics());
    }

    // Empty iterators and spliterators

    public static<T> Spliterator<T> emptySpliterator() {
        return new EmptySpliterator<>();
    }

    public static <E> Spliterator.OfInt emptyIntSpliterator() {
        return new EmptySpliterator.OfInt();
    }

    public static <E> Spliterator.OfLong emptyLongSpliterator() {
        return new EmptySpliterator.OfLong();
    }

    public static <E> Spliterator.OfDouble emptyDoubleSpliterator() {
        return new EmptySpliterator.OfDouble();
    }

    // Iterators from spliterators

    public static<T> Iterator<T> iteratorFrom(Spliterator<? extends T> spliterator) {
        class Adapter implements Iterator<T>, Consumer<T> {
            boolean valueReady = false;
            T nextElement;

            @Override
            public void accept(T t) {
                valueReady = true;
                nextElement = t;
            }

            @Override
            public boolean hasNext() {
                Consumer<T> b = this;
                if (!valueReady)
                    spliterator.tryAdvance(b);
                return valueReady;
            }

            @Override
            public T next() {
                if (!valueReady && !hasNext())
                    throw new NoSuchElementException();
                else {
                    valueReady = false;
                    return nextElement;
                }
            }
        }

        return new Adapter();
    }

    public static IntStream.IntIterator intIteratorFrom(Spliterator.OfInt spliterator) {
        class Adapter implements IntStream.IntIterator, IntConsumer {
            boolean valueReady = false;
            int nextElement;

            @Override
            public void accept(int t) {
                valueReady = true;
                nextElement = t;
            }

            @Override
            public boolean hasNext() {
                if (!valueReady)
                    spliterator.tryAdvance(this);
                return valueReady;
            }

            @Override
            public int nextInt() {
                if (!valueReady && !hasNext())
                    throw new NoSuchElementException();
                else {
                    valueReady = false;
                    return nextElement;
                }
            }
        }

        return new Adapter();
    }

    public static LongStream.LongIterator longIteratorFrom(Spliterator.OfLong spliterator) {
        class Adapter implements LongStream.LongIterator, LongConsumer {
            boolean valueReady = false;
            long nextElement;

            @Override
            public void accept(long t) {
                valueReady = true;
                nextElement = t;
            }

            @Override
            public boolean hasNext() {
                if (!valueReady)
                    spliterator.tryAdvance(this);
                return valueReady;
            }

            @Override
            public long nextLong() {
                if (!valueReady && !hasNext())
                    throw new NoSuchElementException();
                else {
                    valueReady = false;
                    return nextElement;
                }
            }
        }

        return new Adapter();
    }

    public static DoubleStream.DoubleIterator doubleIteratorFrom(Spliterator.OfDouble spliterator) {
        class Adapter implements DoubleStream.DoubleIterator, DoubleConsumer {
            boolean valueReady = false;
            double nextElement;

            @Override
            public void accept(double t) {
                valueReady = true;
                nextElement = t;
            }

            @Override
            public boolean hasNext() {
                if (!valueReady)
                    spliterator.tryAdvance(this);
                return valueReady;
            }

            @Override
            public double nextDouble() {
                if (!valueReady && !hasNext())
                    throw new NoSuchElementException();
                else {
                    valueReady = false;
                    return nextElement;
                }
            }
        }

        return new Adapter();
    }

    // Spliterators from iterators

    public static<T> Spliterator<T> spliteratorUnknownSize(Iterator<T> iterator, int characteristics) {
        return spliterator(iterator, -1, characteristics);
    }

    public static<T> Spliterator<T> spliterator(Iterator<T> iterator, long exactSizeIfKnown, int characteristics) {
        Objects.requireNonNull(iterator);
        return new Spliterator<T>() {
            // The characteristics of this spliterator
            final int c = exactSizeIfKnown >= 0
                           ? characteristics | Spliterator.SIZED | Spliterator.UNIFORM
                           : characteristics & ~(Spliterator.SIZED | Spliterator.UNIFORM);
            // The exact size of this spliterator, otherwise Long.MAX_VALUE
            long size = exactSizeIfKnown >= 0 ? exactSizeIfKnown : Long.MAX_VALUE;
            // The exact size of the spliterator returned from the next call to trySplit
            int nextSize = 1;

            @Override
            public Spliterator<T> trySplit() {
                if (!iterator.hasNext())
                    return null;
                else {
                    @SuppressWarnings("unchecked")
                    T[] array = (T[]) new Object[nextSize];
                    int i = 0;
                    while (i < array.length && iterator.hasNext())
                        array[i++] = iterator.next();
                    if (size < Long.MAX_VALUE) {
                        size -= i;
                    }
                    nextSize = Math.min(nextSize * 2, MAX_ITERATOR_CHUNK_SIZE);
                    // @@@ Need to inherit characteristics
                    return Arrays.spliterator(array, 0, i);
                }
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> consumer) {
                boolean hasNext = iterator.hasNext();
                if (hasNext)
                    consumer.accept(iterator.next());
                return hasNext;
            }

            @Override
            public void forEach(Consumer<? super T> consumer) {
                iterator.forEach(consumer);
            }

            @Override
            public int characteristics() {
                return c;
            }

            @Override
            public long estimateSize() {
                return size;
            }
        };
    }

    public static Spliterator.OfInt intSpliteratorUnknownSize(IntStream.IntIterator iterator, int characteristics) {
        return intSpliterator(iterator, -1, characteristics);
    }

    public static Spliterator.OfInt intSpliterator(IntStream.IntIterator iterator, long exactSizeIfKnown, int characteristics) {
        Objects.requireNonNull(iterator);
        return new Spliterator.OfInt() {
            // The characteristics of this spliterator
            final int c = exactSizeIfKnown >= 0
                          ? characteristics | Spliterator.SIZED | Spliterator.UNIFORM
                          : characteristics & ~(Spliterator.SIZED | Spliterator.UNIFORM);
            // The exact size of this spliterator, otherwise Long.MAX_VALUE
            long size = exactSizeIfKnown >= 0 ? exactSizeIfKnown : Long.MAX_VALUE;
            // The exact size of the spliterator returned from the next call to trySplit
            int nextSize = 1;

            @Override
            public Spliterator.OfInt trySplit() {
                if (!iterator.hasNext())
                    return null;
                else {
                    int[] array = new int[nextSize];
                    int i = 0;
                    while (i < array.length && iterator.hasNext())
                        array[i++] = iterator.nextInt();
                    if (size >= 0) {
                        size -= i;
                    }
                    nextSize = Math.min(nextSize * 2, MAX_ITERATOR_CHUNK_SIZE);
                    return Arrays.spliterator(array, 0, i);
                }
            }

            @Override
            public void forEach(IntConsumer consumer) {
                iterator.forEachInt(consumer);
            }

            @Override
            public boolean tryAdvance(IntConsumer consumer) {
                boolean hasNext = iterator.hasNext();
                if (hasNext)
                    consumer.accept(iterator.nextInt());
                return hasNext;
            }

            @Override
            public int characteristics() {
                return c;
            }

            @Override
            public long estimateSize() {
                return size;
            }
        };
    }

    public static Spliterator.OfLong longSpliteratorUnknownSize(LongStream.LongIterator iterator, int characteristics) {
        return longSpliterator(iterator, -1, characteristics);
    }

    public static Spliterator.OfLong longSpliterator(LongStream.LongIterator iterator, long exactSizeIfKnown, int characteristics) {
        Objects.requireNonNull(iterator);
        return new Spliterator.OfLong() {
            // The characteristics of this spliterator
            final int c = exactSizeIfKnown >= 0
                          ? characteristics | Spliterator.SIZED | Spliterator.UNIFORM
                          : characteristics & ~(Spliterator.SIZED | Spliterator.UNIFORM);
            // The exact size of this spliterator, otherwise Long.MAX_VALUE
            long size = exactSizeIfKnown >= 0 ? exactSizeIfKnown : Long.MAX_VALUE;
            // The exact size of the spliterator returned from the next call to trySplit
            int nextSize = 1;

            @Override
            public Spliterator.OfLong trySplit() {
                if (!iterator.hasNext())
                    return null;
                else {
                    long[] array = new long[nextSize];
                    int i = 0;
                    while (i < array.length && iterator.hasNext())
                        array[i++] = iterator.nextLong();
                    if (size >= 0) {
                        size -= i;
                    }
                    nextSize = Math.min(nextSize * 2, MAX_ITERATOR_CHUNK_SIZE);
                    return Arrays.spliterator(array, 0, i);
                }
            }

            @Override
            public void forEach(LongConsumer consumer) {
                iterator.forEachLong(consumer);
            }

            @Override
            public boolean tryAdvance(LongConsumer consumer) {
                boolean hasNext = iterator.hasNext();
                if (hasNext)
                    consumer.accept(iterator.nextLong());
                return hasNext;
            }

            @Override
            public int characteristics() {
                return c;
            }

            @Override
            public long estimateSize() {
                return size;
            }
        };
    }

    public static Spliterator.OfDouble doubleSpliteratorUnknownSize(DoubleStream.DoubleIterator iterator, int characteristics) {
        return doubleSpliterator(iterator, -1, characteristics);
    }

    public static Spliterator.OfDouble doubleSpliterator(DoubleStream.DoubleIterator iterator, long exactSizeIfKnown, int characteristics) {
        Objects.requireNonNull(iterator);
        return new Spliterator.OfDouble() {
            // The characteristics of this spliterator
            final int c = exactSizeIfKnown >= 0
                          ? characteristics | Spliterator.SIZED | Spliterator.UNIFORM
                          : characteristics & ~(Spliterator.SIZED | Spliterator.UNIFORM);
            // The exact size of this spliterator, otherwise Long.MAX_VALUE
            long size = exactSizeIfKnown >= 0 ? exactSizeIfKnown : Long.MAX_VALUE;
            // The exact size of the spliterator returned from the next call to trySplit
            int nextSize = 1;

            @Override
            public Spliterator.OfDouble trySplit() {
                if (!iterator.hasNext())
                    return null;
                else {
                    double[] array = new double[nextSize];
                    int i = 0;
                    while (i < array.length && iterator.hasNext())
                        array[i++] = iterator.nextDouble();
                    if (size >= 0) {
                        size -= i;
                    }
                    nextSize = Math.min(nextSize * 2, MAX_ITERATOR_CHUNK_SIZE);
                    return Arrays.spliterator(array, 0, i);
                }
            }

            @Override
            public void forEach(DoubleConsumer consumer) {
                iterator.forEachDouble(consumer);
            }

            @Override
            public boolean tryAdvance(DoubleConsumer consumer) {
                boolean hasNext = iterator.hasNext();
                if (hasNext)
                    consumer.accept(iterator.nextDouble());
                return hasNext;
            }

            @Override
            public int characteristics() {
                return c;
            }

            @Override
            public long estimateSize() {
                return size;
            }
        };
    }

    // Infinite stream generators

    public static<T> Stream<T> iterate(final T seed, final UnaryOperator<T> f) {
        Objects.requireNonNull(f);
        final InfiniteIterator<T> iterator = new InfiniteIterator<T>() {
            T t = null;

            @Override
            public T next() {
                return t = (t == null) ? seed : f.apply(t);
            }
        };
        return stream(spliteratorUnknownSize(iterator, Spliterator.ORDERED));
    }

    public static<T> Stream<T> generate(Supplier<T> f) {
        InfiniteIterator<T> iterator = f::get;
        return stream(spliteratorUnknownSize(iterator, Spliterator.ORDERED));
    }

    public static IntStream iterateInt(final int seed, final IntUnaryOperator f) {
        Objects.requireNonNull(f);
        final InfiniteIterator.OfInt iterator = new InfiniteIterator.OfInt() {
            int t = seed;

            @Override
            public int nextInt() {
                int v = t;
                t = f.applyAsInt(t);
                return v;
            }
        };
        return intStream(intSpliteratorUnknownSize(iterator, Spliterator.ORDERED));
    }

    public static IntStream generateInt(IntSupplier f) {
        InfiniteIterator.OfInt iterator = f::getAsInt;
        return intStream(intSpliteratorUnknownSize(iterator, Spliterator.ORDERED));
    }

    public static IntStream intRange(final int from, final int upTo) {
        return intRange(from, upTo, 1);
    }

    public static IntStream intRange(final int from, final int upTo, final int step) {
        return intStream(new RangeIntSpliterator(from, upTo, step));
    }

    public static LongStream iterateLong(final long seed, final LongUnaryOperator f) {
        Objects.requireNonNull(f);
        final InfiniteIterator.OfLong iterator = new InfiniteIterator.OfLong() {
            long t = seed;

            @Override
            public long nextLong() {
                long v = t;
                t = f.applyAsLong(t);
                return v;
            }
        };
        return longStream(longSpliteratorUnknownSize(iterator, Spliterator.ORDERED));
    }

    public static LongStream generateLong(LongSupplier f) {
        InfiniteIterator.OfLong iterator = f::getAsLong;
        return longStream(longSpliteratorUnknownSize(iterator, Spliterator.ORDERED));
    }

    public static LongStream longRange(final long from, final long upTo) {
        return longRange(from, upTo, 1);
    }

    public static LongStream longRange(final long from, final long upTo, final long step) {
        return longStream(new RangeLongSpliterator(from, upTo, step));
    }

    public static DoubleStream iterateDouble(final double seed, final DoubleUnaryOperator f) {
        Objects.requireNonNull(f);
        final InfiniteIterator.OfDouble iterator = new InfiniteIterator.OfDouble() {
            double t = seed;

            @Override
            public double nextDouble() {
                double v = t;
                t = f.applyAsDouble(t);
                return v;
            }
        };
        return doubleStream(doubleSpliteratorUnknownSize(iterator, Spliterator.ORDERED));
    }

    public static DoubleStream generateDouble(DoubleSupplier f) {
        InfiniteIterator.OfDouble iterator = f::getAsDouble;
        return doubleStream(doubleSpliteratorUnknownSize(iterator, Spliterator.ORDERED));
    }

    // Concat

    public static<T> Stream<T> concat(Stream<? extends T> a, Stream<? extends T> b) {
        @SuppressWarnings("unchecked")
        Spliterator<T> aSpliterator = (Spliterator<T>) Objects.requireNonNull(a).spliterator();
        @SuppressWarnings("unchecked")
        Spliterator<T> bSpliterator = (Spliterator<T>) Objects.requireNonNull(b).spliterator();

        Spliterator<T> split = new Spliterator<T>() {
            // True when no split has occurred, otherwise false
            boolean beforeSplit = true;

            @Override
            public Spliterator<T> trySplit() {
                Spliterator<T> ret = beforeSplit ? aSpliterator : bSpliterator.trySplit();
                beforeSplit = false;
                return ret;
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> consumer) {
                boolean hasNext;
                if (beforeSplit) {
                    hasNext = aSpliterator.tryAdvance(consumer);
                    if (!hasNext) {
                        beforeSplit = false;
                        hasNext = bSpliterator.tryAdvance(consumer);
                    }
                }
                else
                    hasNext = bSpliterator.tryAdvance(consumer);
                return hasNext;
            }

            @Override
            public void forEach(Consumer<? super T> consumer) {
                if (beforeSplit) {
                    aSpliterator.forEach(consumer);
                    bSpliterator.forEach(consumer);
                }
                else
                    bSpliterator.forEach(consumer);
            }

            @Override
            public long estimateSize() {
                if (beforeSplit) {
                    // If one or both estimates are Long.MAX_VALUE then the sum
                    // will either be Long.MAX_VALUE or overflow to a negative value
                    long size = aSpliterator.estimateSize() + bSpliterator.estimateSize();
                    return (size >= 0) ? size : Long.MAX_VALUE;
                }
                else {
                    return bSpliterator.estimateSize();
                }
            }

            @Override
            public int characteristics() {
                if (beforeSplit) {
                    // Concatenation looses DISTINCT and SORTED characteristics
                    int both = aSpliterator.characteristics() & bSpliterator.characteristics() &
                           ~(Spliterator.DISTINCT | Spliterator.SORTED);

                    // Concatenation is INFINITE if either stream is INFINITE
                    int either = (aSpliterator.characteristics() | aSpliterator.characteristics()) &
                            Spliterator.INFINITE;

                    return both | either;
                }
                else {
                    return bSpliterator.characteristics();
                }
            }
        };

        return (a.isParallel() || b.isParallel())
               ? parallelStream(split)
               : stream(split);
    }

    public static<A, B, C> Stream<C> zip(Stream<? extends A> a,
                                         Stream<? extends B> b,
                                         BiFunction<? super A, ? super B, ? extends C> zipper) {
        Objects.requireNonNull(zipper);
        @SuppressWarnings("unchecked")
        Spliterator<A> aSpliterator = (Spliterator<A>) Objects.requireNonNull(a).spliterator();
        @SuppressWarnings("unchecked")
        Spliterator<B> bSpliterator = (Spliterator<B>) Objects.requireNonNull(b).spliterator();

        // Zipping looses DISTINCT and SORTED characteristics
        int both = aSpliterator.characteristics() & bSpliterator.characteristics() &
                   ~(Spliterator.DISTINCT | Spliterator.SORTED);
        // Zipping is INFINITE if either stream is INFINITE
        int either = (aSpliterator.characteristics() | aSpliterator.characteristics()) &
                     Spliterator.INFINITE;
        int characteristics = both | either;

        long zipSize = ((characteristics & Spliterator.SIZED) != 0)
                     ? Math.min(aSpliterator.getExactSizeIfKnown(), bSpliterator.getExactSizeIfKnown())
                     : -1;

        Iterator<A> aIterator = Streams.iteratorFrom(aSpliterator);
        Iterator<B> bIterator = Streams.iteratorFrom(bSpliterator);
        Iterator<C> cIterator = new Iterator<C>() {
            @Override
            public boolean hasNext() {
                return aIterator.hasNext() && bIterator.hasNext();
            }

            @Override
            public C next() {
                return zipper.apply(aIterator.next(), bIterator.next());
            }
        };

        Spliterator<C> split = spliterator(cIterator, zipSize, characteristics);
        return (a.isParallel() || b.isParallel())
               ? parallelStream(split)
               : stream(split);
    }

    // Spliterator implementations

    private interface InfiniteIterator<T> extends Iterator<T> {
        /** Always returns true*/
        @Override
        public default boolean hasNext() {
            return true;
        }

        public interface OfInt extends InfiniteIterator<Integer>, IntStream.IntIterator { }

        public interface OfLong extends InfiniteIterator<Long>, LongStream.LongIterator { }

        public interface OfDouble extends InfiniteIterator<Double>, DoubleStream.DoubleIterator { }
    }

    private static class RangeIntSpliterator implements Spliterator.OfInt {
        private int from;
        private final int upTo;
        private final int step;

        RangeIntSpliterator(int from, int upTo, int step) {
            this.from = from;
            this.upTo = upTo;
            this.step = step;
        }

        @Override
        public boolean tryAdvance(IntConsumer consumer) {
            boolean hasNext = (step > 0) ? from < upTo : from > upTo;
            if (hasNext) {
                consumer.accept(from);
                from += step;
            }
            return hasNext;
        }

        @Override
        public void forEach(IntConsumer consumer) {
            int hUpTo = upTo;
            int hStep = step; // hoist accesses and checks from loop
            for (int i = from; i < hUpTo; i += hStep)
                consumer.accept(i);
            from = upTo;
        }

        @Override
        public long estimateSize() {
            int d = upTo - from;

            return (d / step) + ((d % step > 0) ? 1 : 0) ;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.UNIFORM;
        }

        @Override
        public Spliterator.OfInt trySplit() {
            if (estimateSize() <= 1)
                return null;
            else {
                int mid = midPoint();
                if (mid == 0)
                    return null;
                else {
                    RangeIntSpliterator ret = new RangeIntSpliterator(from, from + mid, step);
                    from += mid;
                    return ret;
                }
            }
        }

        private int midPoint() {
            if (step == 1) {
                return (upTo - from) / 2;
            } else {
                // Size is known to be >= 2
                int bisection = (upTo - from) / 2;
                int remainder = bisection % step;
                // Prefer to round down if within bounds, otherwise round up
                return (bisection > remainder) ? bisection - remainder :  bisection + (step - remainder);
            }
        }
    }

    private static class RangeLongSpliterator implements Spliterator.OfLong {
        private long from;
        private final long upTo;
        private final long step;

        RangeLongSpliterator(long from, long upTo, long step) {
            this.from = from;
            this.upTo = upTo;
            this.step = step;
        }

        @Override
        public boolean tryAdvance(LongConsumer consumer) {
            boolean hasNext = (step > 0) ? from < upTo : from > upTo;
            if (hasNext) {
                consumer.accept(from);
                from += step;
            }
            return hasNext;
        }

        @Override
        public void forEach(LongConsumer consumer) {
            long hUpTo = upTo;
            long hStep = step; // hoist accesses and checks from loop
            for (long i = from; i < hUpTo; i += hStep)
                consumer.accept(i);
            from = upTo;
        }

        @Override
        public long estimateSize() {
            long d = upTo - from;

            return (d / step) + ((d % step > 0) ? 1 : 0) ;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.UNIFORM;
        }

        @Override
        public Spliterator.OfLong trySplit() {
            if (estimateSize() <= 1)
                return null;
            else {
                long mid = midPoint();
                if (mid == 0)
                    return null;
                else {
                    RangeLongSpliterator ret = new RangeLongSpliterator(from, from + mid, step);
                    from += mid;
                    return ret;
                }
            }
        }

        private long midPoint() {
            if (step == 1) {
                return (upTo - from) / 2;
            } else {
                // Size is known to be >= 2
                long bisection = (upTo - from) / 2;
                long remainder = bisection % step;
                // Prefer to round down if within bounds, otherwise round up
                return (bisection > remainder) ? bisection - remainder :  bisection + (step - remainder);
            }
        }
    }

    private static class EmptySpliterator<T> implements Spliterator<T> {

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> consumer) {
            return false;
        }

        @Override
        public void forEach(Consumer<? super T> consumer) { }

        @Override
        public long estimateSize() {
            return 0;
        }

        @Override
        public int characteristics() {
            return Spliterator.SIZED | Spliterator.UNIFORM;
        }

        private static class OfInt extends EmptySpliterator<Integer> implements Spliterator.OfInt {
            @Override
            public Spliterator.OfInt trySplit() {
                return null;
            }

            @Override
            public boolean tryAdvance(IntConsumer consumer) {
                return false;
            }

            @Override
            public void forEach(IntConsumer consumer) {
            }
        }

        private static class OfLong extends EmptySpliterator<Long> implements Spliterator.OfLong {
            @Override
            public Spliterator.OfLong trySplit() {
                return null;
            }

            @Override
            public boolean tryAdvance(LongConsumer consumer) {
                return false;
            }

            @Override
            public void forEach(LongConsumer consumer) {
            }
        }

        private static class OfDouble extends EmptySpliterator<Double> implements Spliterator.OfDouble {
            @Override
            public Spliterator.OfDouble trySplit() {
                return null;
            }

            @Override
            public boolean tryAdvance(DoubleConsumer consumer) {
                return false;
            }

            @Override
            public void forEach(DoubleConsumer consumer) {
            }
        }
    }
}
