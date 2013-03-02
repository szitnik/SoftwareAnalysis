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

import java.util.Comparator;
import java.util.Comparators;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * A potentially infinite sequence of elements. A stream is a consumable data
 * structure. The elements of the stream are available for consumption by either
 * iteration or an operation. Once consumed the elements are no longer available
 * from the stream.
 *
 * @param <T> Type of elements.
 *
 * @author Brian Goetz
 */
public interface Stream<T> extends BaseStream<T, Stream<T>> {

    /**
     * Produce a {@code Stream} containing the elements of this stream that match
     * the given {@link Predicate}.
     */
    Stream<T> filter(Predicate<? super T> predicate);

    /**
     * Produce a {@code Stream} that is the result of applying the given {@link Function} to the
     * elements of this stream.
     */
    <R> Stream<R> map(Function<? super T, ? extends R> mapper);

    /**
     * Produce an {@link IntStream} that is the result of applying the given {@link ToIntFunction} to the
     * elements of this stream.
     */
    IntStream map(ToIntFunction<? super T> mapper);

    /**
     * Produce an {@link LongStream} that is the result of applying the given {@link ToLongFunction} to the
     * elements of this stream.
     */
    LongStream map(ToLongFunction<? super T> mapper);

    /**
     * Produce an {@link DoubleStream} that is the result of applying the given {@link ToDoubleFunction} to the
     * elements of this stream.
     */
    DoubleStream map(ToDoubleFunction<? super T> mapper);

    /**
     * Produce a {@code Stream} that is the result of mapping each element of this {@code Stream} to
     * zero or more elements.  The mapping is accomplished by passing the element, along with a
     * {@link Consumer}, to the provided {@link FlatMapper}; the {@code FlatMapper} should pass the emitted
     * elements to the {@code Consumer}.
     */
    default <R> Stream<R> flatMap(Function<T, Stream<? extends R>> mapper) {
        // We can do better than this, by polling cancellationRequested when stream is infinite
        return flatMap((T t, Consumer<R> sink) -> mapper.apply(t).sequential().forEach(sink));
    }

    <R> Stream<R> flatMap(FlatMapper<? super T, R> mapper);

    IntStream flatMap(FlatMapper.ToInt<? super T> mapper);

    LongStream flatMap(FlatMapper.ToLong<? super T> mapper);

    DoubleStream flatMap(FlatMapper.ToDouble<? super T> mapper);

    /**
     * Produce a {@code Stream} containing the elements of this stream that are distinct according to
     * {@code Object.equals}.
     * @return
     */
    Stream<T> distinct();

    /**
     * Produce a {@code Stream} containing the contents of this stream sorted in natural order.  If the
     * elements of this stream are not {@code Comparable}, a {@code java.lang.ClassCastException} may be
     * thrown when the stream is processed
     */
    Stream<T> sorted();

    /**
     * Produce a {@code Stream} containing the contents of this stream sorted by the provide {@link Comparator}.
     */
    Stream<T> sorted(Comparator<? super T> comparator);

    /**
     * Each element of this stream is processed by the provided consumer.
     *
     * @param consumer the Consumer via which all elements will be processed.
     */
    void forEach(Consumer<? super T> consumer);

    /**
     * Each element of this stream is processed by the provided consumer, until the
     * termination criteria occurs.
     *
     * @param consumer the Consumer via which all elements will be processed.
     */
    void forEachUntil(Consumer<? super T> consumer, BooleanSupplier until);

    /**
     * Produce a {@code Stream} containing the elements of this stream, and also provide elements
     * to the specified {@link Consumer} as elements are passed through.
     */
    Stream<T> peek(Consumer<? super T> consumer);

    /**
     * Limit this stream to at most {@code maxSize} elements. The stream will not be affected
     * if it contains less than or equal to {@code maxSize} elements.
     *
     * @param maxSize the number elements the stream should be limited to.
     * @return the truncated stream
     */
    Stream<T> limit(long maxSize);

    /**
     * Skip at most {@code startingOffset} elements.
     *
     * @param startingOffset the number of elements to be skipped.
     * @return the truncated stream
     */
    Stream<T> substream(long startingOffset);

    /**
     * Compute a subsequence of this stream
     *
     * @param startingOffset the starting position of the substream, inclusive
     * @param endingOffset the ending position of the substream, exclusive
     * @return the truncated stream
     */
    Stream<T> substream(long startingOffset, long endingOffset);

    /**
     * Returns an array containing all of the elements in this stream.
     *
     * @return an array containing all of the elements in this stream.
     */
    default Object[] toArray() {
        // Avoid declaring the annotation on the default method
        @SuppressWarnings("unchecked")
        IntFunction<T[]> generator = s -> (T[]) new Object[s];
        return toArray(generator);

        /* @@@ We'd like to say:
                return toArray(Object[]::new);
           but the compiler crashes.
         */
    }

    /**
     * Returns an array containing all of the elements in this stream;
     * the runtime type of the returned array is that of the runtime
     * type returned from the array generator.
     *
     * @param generator the array generator.
     * @param <A> the type of elements in the array.
     * @return an array containing all of the elements in this stream.
     * @throws ArrayStoreException if the runtime type of an array returned from the array generator
     * is not a supertype of the runtime type of every element in this stream.
     */
    <A> A[] toArray(IntFunction<A[]> generator);

    T reduce(T identity, BinaryOperator<T> reducer);

    Optional<T> reduce(BinaryOperator<T> reducer);

        <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> reducer);

    <R> R collect(Supplier<R> resultFactory,
                  BiConsumer<R, ? super T> accumulator,
                  BiConsumer<R, R> reducer);

    <R> R collect(Collector<? super T, R> collector);

    <R> R collectUnordered(Collector<? super T, R> collector);

    default Optional<T> max(Comparator<? super T> comparator) {
        return reduce(Comparators.greaterOf(comparator));
    }

    default Optional<T> min(Comparator<? super T> comparator) {
        return reduce(Comparators.lesserOf(comparator));
    }

    boolean anyMatch(Predicate<? super T> predicate);

    boolean allMatch(Predicate<? super T> predicate);

    boolean noneMatch(Predicate<? super T> predicate);

    /**
     * Return an {@link Optional} describing the first element of the stream, or an
     * empty {@code Optional} if the stream is empty
     *
     * @return
     */
    Optional<T> findFirst();

    /**
     * Return an {@link Optional} describing some element of the stream, or an
     * empty {@code Optional} if the stream is empty
     *
     * @return
     */
    Optional<T> findAny();

    /**
     * Convert this stream, if a parallel stream, to a sequential stream.
     *
     * @return a sequential stream.
     */
    Stream<T> sequential();

    /**
     * Convert this stream, if a sequential stream, to a parallel stream.
     *
     * @return a sequential stream.
     */
    Stream<T> parallel();
}
