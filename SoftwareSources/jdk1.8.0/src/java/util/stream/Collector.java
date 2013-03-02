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

import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

/**
 * Collector
 *
 * @author Brian Goetz
 */
public interface Collector<T, R> {
    Supplier<R> resultSupplier();
    BiConsumer<R, T> accumulator();
    BinaryOperator<R> combiner();

    default boolean isConcurrent() { return false; }

    interface OfInt<R> extends Collector<Integer, R> {
        ObjIntConsumer<R> intAccumulator();

        @Override
        default BiConsumer<R, Integer> accumulator() {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} calling Collector.OfInt.accumulator()");
            return (r, i) -> intAccumulator().accept(r, i);
        }
    }

    interface OfLong<R> extends Collector<Long, R> {
        ObjLongConsumer<R> longAccumulator();

        @Override
        default BiConsumer<R, Long> accumulator() {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} calling Collector.OfLong.accumulator()");
            return (r, l) -> longAccumulator().accept(r, l);
        }
    }

    interface OfDouble<R> extends Collector<Double, R> {
        ObjDoubleConsumer<R> doubleAccumulator();

        @Override
        default BiConsumer<R, Double> accumulator() {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} calling Collector.OfDouble.accumulator()");
            return (r, d) -> doubleAccumulator().accept(r, d);
        }
    }

}
