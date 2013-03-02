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

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.Objects;

/**
 * ForEachOp
 *
 * @author Brian Goetz
 */
class ForEachOp<T> implements TerminalOp<T, Void> {
    protected final TerminalSink<T, Void> sink;
    protected final StreamShape shape;

    protected ForEachOp(TerminalSink<T, Void> sink, StreamShape shape) {
        this.shape = Objects.requireNonNull(shape);
        this.sink = Objects.requireNonNull(sink);
    }

    protected interface VoidTerminalSink<T> extends TerminalSink<T, Void> {
        @Override
        default public Void getAndClearState() {
            return null;
        }

        public interface OfInt extends VoidTerminalSink<Integer>, Sink.OfInt { }

        public interface OfLong extends VoidTerminalSink<Long>, Sink.OfLong { }

        public interface OfDouble extends VoidTerminalSink<Double>, Sink.OfDouble { }
    }

    public static <T> ForEachOp<T> make(final Consumer<? super T> consumer) {
        return new ForEachOp<>((VoidTerminalSink<T>) consumer::accept, StreamShape.REFERENCE);
    }

    public static ForEachOp<Integer> make(final IntConsumer consumer) {
        return new ForEachOp<>((VoidTerminalSink.OfInt) consumer::accept, StreamShape.INT_VALUE);
    }

    public static ForEachOp<Long> make(final LongConsumer consumer) {
        return new ForEachOp<>((VoidTerminalSink.OfLong) consumer::accept, StreamShape.LONG_VALUE);
    }

    public static ForEachOp<Double> make(final DoubleConsumer consumer) {
        return new ForEachOp<>((VoidTerminalSink.OfDouble) consumer::accept, StreamShape.DOUBLE_VALUE);
    }

    @Override
    public int getOpFlags() {
        return StreamOpFlag.NOT_ORDERED;
    }

    @Override
    public StreamShape inputShape() {
        return shape;
    }

    @Override
    public <S> Void evaluateSequential(PipelineHelper<S, T> helper) {
        return helper.into(sink, helper.sourceSpliterator()).getAndClearState();
    }

    @Override
    public <S> Void evaluateParallel(PipelineHelper<S, T> helper) {
        OpUtils.parallelForEach(helper, helper.wrapSink(sink));
        return null;
    }
}
