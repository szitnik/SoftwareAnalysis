/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
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
 * A {@code Stream} implementation that delegate operations to another {@code
 * Stream}.
 *
 * @since 1.8
 */
public class DelegatingStream<T> implements Stream<T> {
    final private Stream<T> delegate;

    /**
     * Construct a {@code Stream} that delegates operations to another {@code
     * Stream}.
     *
     * @param delegate The {@link Stream} that actually performs the operation
     * @throws NullPointerException if the delegate is null
     */
    public DelegatingStream(Stream<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    // -- BaseStream methods --

    public Spliterator<T> spliterator() {
        return delegate.spliterator();
    }

    public boolean isParallel() {
        return delegate.isParallel();
    }

    public int getStreamFlags() {
        return delegate.getStreamFlags();
    }

    // -- Stream methods --

    public Stream<T> filter(Predicate<? super T> predicate) {
        return delegate.filter(predicate);
    }

    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return delegate.map(mapper);
    }

    public IntStream map(ToIntFunction<? super T> mapper) {
        return delegate.map(mapper);
    }

    public LongStream map(ToLongFunction<? super T> mapper) {
        return delegate.map(mapper);
    }

    public DoubleStream map(ToDoubleFunction<? super T> mapper) {
        return delegate.map(mapper);
    }

    public <R> Stream<R> flatMap(Function<T, Stream<? extends R>> mapper) {
        return delegate.flatMap(mapper);
    }

    public <R> Stream<R> flatMap(FlatMapper<? super T, R> mapper) {
        return delegate.flatMap(mapper);
    }

    public IntStream flatMap(FlatMapper.ToInt<? super T> mapper) {
        return delegate.flatMap(mapper);
    }

    public LongStream flatMap(FlatMapper.ToLong<? super T> mapper) {
        return delegate.flatMap(mapper);
    }

    public DoubleStream flatMap(FlatMapper.ToDouble<? super T> mapper) {
        return delegate.flatMap(mapper);
    }

    public Stream<T> distinct() {
        return delegate.distinct();
    }

    public Stream<T> sorted() {
        return delegate.sorted();
    }

    public Stream<T> sorted(Comparator<? super T> comparator) {
        return delegate.sorted(comparator);
    }

    public void forEach(Consumer<? super T> consumer) {
        delegate.forEach(consumer);
    }

    public void forEachUntil(Consumer<? super T> consumer, BooleanSupplier until) {
        delegate.forEachUntil(consumer, until);
    }

    public Stream<T> peek(Consumer<? super T> consumer) {
        return delegate.peek(consumer);
    }

    public Stream<T> limit(long maxSize) {
        return delegate.limit(maxSize);
    }

    public Stream<T> substream(long startingOffset) {
        return delegate.substream(startingOffset);
    }

    public Stream<T> substream(long startingOffset, long endingOffset) {
        return delegate.substream(startingOffset, endingOffset);
    }

    public <A> A[] toArray(IntFunction<A[]> generator) {
        return delegate.toArray(generator);
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    public T reduce(T identity, BinaryOperator<T> reducer) {
        return delegate.reduce(identity, reducer);
    }

    public Optional<T> reduce(BinaryOperator<T> reducer) {
        return delegate.reduce(reducer);
    }

    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator,
                        BinaryOperator<U> reducer) {
        return delegate.reduce(identity, accumulator, reducer);
    }

    public <R> R collect(Supplier<R> resultFactory,
                         BiConsumer<R, ? super T> accumulator,
                         BiConsumer<R, R> reducer) {
        return delegate.collect(resultFactory, accumulator, reducer);
    }

    public <R> R collect(Collector<? super T, R> collector) {
        return delegate.collect(collector);
    }

    public <R> R collectUnordered(Collector<? super T, R> collector) {
        return delegate.collectUnordered(collector);
    }

    public Optional<T> max(Comparator<? super T> comparator) {
        return delegate.max(comparator);
    }

    public Optional<T> min(Comparator<? super T> comparator) {
        return delegate.min(comparator);
    }

    public boolean anyMatch(Predicate<? super T> predicate) {
        return delegate.anyMatch(predicate);
    }

    public boolean allMatch(Predicate<? super T> predicate) {
        return delegate.allMatch(predicate);
    }

    public boolean noneMatch(Predicate<? super T> predicate) {
        return delegate.noneMatch(predicate);
    }

    public Optional<T> findFirst() {
        return delegate.findFirst();
    }

    public Optional<T> findAny() {
        return delegate.findAny();
    }

    public Stream<T> sequential() {
        return delegate.sequential();
    }

    public Stream<T> parallel() {
        return delegate.parallel();
    }
}