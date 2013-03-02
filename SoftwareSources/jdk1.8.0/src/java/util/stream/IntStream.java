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

import java.util.Iterator;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public interface IntStream extends BaseStream<Integer, IntStream> {

    // BaseStream

    @Override
    default IntIterator iterator() {
        return Streams.intIteratorFrom(spliterator());
    }

    @Override
    Spliterator.OfInt spliterator();

    LongStream longs();

    DoubleStream doubles();

    default Stream<Integer> boxed() {
        return map((IntFunction<Integer>) i -> Integer.valueOf(i));
    }

    IntStream map(IntUnaryOperator mapper);

    <U> Stream<U> map(IntFunction<U> mapper);

    default IntStream flatMap(IntFunction<? extends IntStream> mapper) {
        return flatMap((int i, IntConsumer sink) -> mapper.apply(i).sequential().forEach(sink));
    }

    IntStream flatMap(FlatMapper.OfIntToInt mapper);

    IntStream filter(IntPredicate predicate);

    IntStream peek(IntConsumer consumer);

    IntStream sorted();

    default IntStream distinct() {
        // @@@ While functional and quick to implement this approach is not very efficient.
        //     An efficient version requires an int-specific map/set implementation.
        return boxed().distinct().map(i -> (int) i);
    }

    IntStream limit(long maxSize);

    IntStream substream(long startOffset);

    IntStream substream(long startOffset, long endOffset);

    IntStream sequential();

    IntStream parallel();

    int reduce(int identity, IntBinaryOperator op);

    OptionalInt reduce(IntBinaryOperator op);

    <R> R collect(Collector.OfInt<R> collector);

    <R> R collectUnordered(Collector.OfInt<R> collector);

    boolean anyMatch(IntPredicate predicate);

    boolean allMatch(IntPredicate predicate);

    boolean noneMatch(IntPredicate predicate);

    OptionalInt findFirst();

    OptionalInt findAny();

    void forEach(IntConsumer consumer);

    void forEachUntil(IntConsumer consumer, BooleanSupplier until);

    default int sum() {
        return reduce(0, Integer::sum);
    }

    default OptionalInt min() {
        return reduce(Math::min);
    }

    default OptionalInt max() {
        return reduce(Math::max);
    }

    default OptionalDouble average() {
        return longs().average();
    }

    int[] toArray();

    interface IntIterator extends Iterator<Integer> {

        @Override
        default Integer next() {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} using boxed int in next()");
            return nextInt();
        }

        int nextInt();

        @Override
        default void forEach(Consumer<? super Integer> sink) {
            if (sink instanceof IntConsumer) {
                forEachInt((IntConsumer) sink);
            }
            else {
                if (Tripwire.enabled)
                    Tripwire.trip(getClass(), "{0} using boxed int in forEach()");
                while (hasNext()) {
                    sink.accept(nextInt());
                }
            }
        }

        default void forEachInt(IntConsumer block) {
            while (hasNext())
                block.accept(nextInt());
        }
    }
}
