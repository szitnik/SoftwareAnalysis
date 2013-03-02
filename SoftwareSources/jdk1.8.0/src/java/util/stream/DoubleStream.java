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
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;

public interface DoubleStream extends BaseStream<Double, DoubleStream> {

    // BaseStream

    @Override
    default DoubleIterator iterator() {
        return Streams.doubleIteratorFrom(spliterator());
    }

    @Override
    Spliterator.OfDouble spliterator();

    default Stream<Double> boxed() {
        return map((DoubleFunction<Double>) i -> Double.valueOf(i));
    }

    DoubleStream map(DoubleUnaryOperator mapper);

    <U> Stream<U> map(DoubleFunction<U> mapper);

    default DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper) {
        return flatMap((double i, DoubleConsumer sink) -> mapper.apply(i).sequential().forEach(sink));
    }

    DoubleStream flatMap(FlatMapper.OfDoubleToDouble mapper);

    DoubleStream filter(DoublePredicate predicate);

    DoubleStream peek(DoubleConsumer consumer);

    DoubleStream sorted();

    default DoubleStream distinct() {
        // @@@ While functional and quick to implement this approach is not very efficient.
        //     An efficient version requires an double-specific map/set implementation.
        return boxed().distinct().map(i -> (double) i);
    }

    DoubleStream limit(long maxSize);

    DoubleStream substream(long startOffset);

    DoubleStream substream(long startOffset, long endOffset);

    DoubleStream sequential();

    DoubleStream parallel();

    double reduce(double identity, DoubleBinaryOperator op);

    OptionalDouble reduce(DoubleBinaryOperator op);

    <R> R collect(Collector.OfDouble<R> collector);

    <R> R collectUnordered(Collector.OfDouble<R> collector);

    boolean anyMatch(DoublePredicate predicate);

    boolean allMatch(DoublePredicate predicate);

    boolean noneMatch(DoublePredicate predicate);

    OptionalDouble findFirst();

    OptionalDouble findAny();

    void forEach(DoubleConsumer consumer);

    void forEachUntil(DoubleConsumer consumer, BooleanSupplier until);

    default double sum() {
        // better algorithm to compensate for errors ?
        return reduce(0.0, Double::sum);
    }

    default OptionalDouble min() {
        return reduce(Math::min);
    }

    default OptionalDouble max() {
        return reduce(Math::max);
    }

    default OptionalDouble average() {
        return collect(Collectors.toDoubleStatistics()).getMean();
    }

    double[] toArray();

    interface DoubleIterator extends Iterator<Double> {

        @Override
        default Double next() {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} using boxed double in next()");
            return nextDouble();
        }

        double nextDouble();

        @Override
        default void forEach(Consumer<? super Double> sink) {
            if (sink instanceof DoubleConsumer) {
                forEachDouble((DoubleConsumer) sink);
            }
            else {
                if (Tripwire.enabled)
                    Tripwire.trip(getClass(), "{0} using boxed double in forEach()");
                while (hasNext()) {
                    sink.accept(nextDouble());
                }
            }
        }

        default void forEachDouble(DoubleConsumer block) {
            while (hasNext())
                block.accept(nextDouble());
        }
    }
}
