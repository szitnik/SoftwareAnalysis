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

import java.util.Iterator;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;

public interface LongStream extends BaseStream<Long, LongStream> {

    // BaseStream

    @Override
    default LongIterator iterator() {
        return Streams.longIteratorFrom(spliterator());
    }

    @Override
    Spliterator.OfLong spliterator();

    DoubleStream doubles();

    default Stream<Long> boxed() {
        return map((LongFunction<Long>) i -> Long.valueOf(i));
    }

    LongStream map(LongUnaryOperator mapper);

    <U> Stream<U> map(LongFunction<U> mapper);

    default LongStream flatMap(LongFunction<? extends LongStream> mapper) {
        return flatMap((long i, LongConsumer sink) -> mapper.apply(i).sequential().forEach(sink));
    }

    LongStream flatMap(FlatMapper.OfLongToLong mapper);

    LongStream filter(LongPredicate predicate);

    LongStream peek(LongConsumer consumer);

    LongStream sorted();

    default LongStream distinct() {
        // @@@ While functional and quick to implement this approach is not very efficient.
        //     An efficient version requires an long-specific map/set implementation.
        return boxed().distinct().map(i -> (long) i);
    }

    LongStream limit(long maxSize);

    LongStream substream(long startOffset);

    LongStream substream(long startOffset, long endOffset);

    LongStream sequential();

    LongStream parallel();

    long reduce(long identity, LongBinaryOperator op);

    OptionalLong reduce(LongBinaryOperator op);

    <R> R collect(Collector.OfLong<R> collector);

    <R> R collectUnordered(Collector.OfLong<R> collector);

    boolean anyMatch(LongPredicate predicate);

    boolean allMatch(LongPredicate predicate);

    boolean noneMatch(LongPredicate predicate);

    OptionalLong findFirst();

    OptionalLong findAny();

    void forEach(LongConsumer consumer);

    void forEachUntil(LongConsumer consumer, BooleanSupplier until);

    default long sum() {
        // @@@ better algorithm to compensate for intermediate overflow?
        return reduce(0, Long::sum);
    }

    default OptionalLong min() {
        return reduce(Math::min);
    }

    default OptionalLong max() {
        return reduce(Math::max);
    }

    default OptionalDouble average() {
        return collect(Collectors.toLongStatistics()).getMean();
    }

    long[] toArray();

    interface LongIterator extends Iterator<Long> {

        @Override
        default Long next() {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} using boxed long in next()");
            return nextLong();
        }

        long nextLong();

        @Override
        default void forEach(Consumer<? super Long> sink) {
            if (sink instanceof LongConsumer) {
                forEachLong((LongConsumer) sink);
            }
            else {
                if (Tripwire.enabled)
                    Tripwire.trip(getClass(), "{0} using boxed long in forEach()");
                while (hasNext()) {
                    sink.accept(nextLong());
                }
            }
        }

        default void forEachLong(LongConsumer block) {
            while (hasNext())
                block.accept(nextLong());
        }
    }
}
