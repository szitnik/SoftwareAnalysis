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

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * ForEachWhileOp
 *
 * @author Brian Goetz
 */
class ForEachUntilOp<T> extends ForEachOp<T> implements TerminalOp<T, Void> {
    public ForEachUntilOp(TerminalSink<T, Void> sink, StreamShape shape) {
        super(sink, shape);
    }

    public static <T> ForEachUntilOp<T> make(final Consumer<? super T> consumer, BooleanSupplier until) {
        Objects.requireNonNull(consumer);
        return new ForEachUntilOp<>(new VoidTerminalSink<T>() {
            @Override
            public void accept(T t) {
                consumer.accept(t);
            }

            @Override
            public boolean cancellationRequested() {
                return until.getAsBoolean();
            }
        }, StreamShape.REFERENCE);
    }

    public static ForEachUntilOp<Integer> make(final IntConsumer consumer, BooleanSupplier until) {
        Objects.requireNonNull(consumer);
        return new ForEachUntilOp<>(new VoidTerminalSink.OfInt() {
            @Override
            public void accept(int i) {
                consumer.accept(i);
            }

            @Override
            public boolean cancellationRequested() {
                return until.getAsBoolean();
            }
        }, StreamShape.INT_VALUE);
    }

    public static ForEachUntilOp<Long> make(final LongConsumer consumer, BooleanSupplier until) {
        Objects.requireNonNull(consumer);
        return new ForEachUntilOp<>(new VoidTerminalSink.OfLong() {
            @Override
            public void accept(long i) {
                consumer.accept(i);
            }

            @Override
            public boolean cancellationRequested() {
                return until.getAsBoolean();
            }
        }, StreamShape.LONG_VALUE);
    }

    public static ForEachUntilOp<Double> make(final DoubleConsumer consumer, BooleanSupplier until) {
        Objects.requireNonNull(consumer);
        return new ForEachUntilOp<>(new VoidTerminalSink.OfDouble() {
            @Override
            public void accept(double i) {
                consumer.accept(i);
            }

            @Override
            public boolean cancellationRequested() {
                return until.getAsBoolean();
            }
        }, StreamShape.DOUBLE_VALUE);
    }

    @Override
    public int getOpFlags() {
        return StreamOpFlag.IS_SHORT_CIRCUIT | StreamOpFlag.NOT_ORDERED;
    }
}
