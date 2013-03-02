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
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * An extension of {@link Consumer} used to conduct values through the stages of a stream pipeline, with additional
 * methods to manage size information, control flow, etc.
 *
 * For each stage of a stream pipeline, there is a {@code Sink} that passes values to the input of the next stage.
 * Because the "shape" of a pipeline (reference, integer, long, double) can change from stage to stage, some stages
 * are better represented by a specialization of {@code Consumer}, such as {@code IntConsumer}.  Rather than
 * creating adapters for pairs of shapes, {@code Sink} implements all of the forms of the {@code accept()} method,
 * and pipelines are expected to wire up stages so that the output shape of one stage matches the input shape
 * of the next.  So in addition to the {@code accept(T)} form, {@code Sink} also has primitive forms (e.g.,
 * {@code accept(int)}).
 *
 * @apinote
 * Most implementations will use the chaining wrappers which provide sensible defaults for all methods other
 * than the appropriate {@code accept()}, such as this implementation which maps a stream of references to a
 * stream of integers:
 *
 * <pre>
 *     IntSink is = new Sink.ChainedReference&lt;U>(sink) {
 *         public void accept(U u) {
 *             downstream.accept(mapper.applyAsInt(u));
 *         }
 *     };
 * </pre>
 *
 * @param <T> Type of elements for value streams
 */
@FunctionalInterface
interface Sink<T> extends Consumer<T> {
    /**
     * Reset the sink state to receive a fresh data set. This is used when a
     * {@code Sink} is being reused by multiple calculations.
     * @param size The exact size of the data to be pushed downstream, if
     * known or {@code Long.MAX_VALUE} if unknown or infinite.
     */
    default void begin(long size) {}

    /**
     * Indicate that all elements have been pushed.  If the {@code Sink} buffers any
     * results from previous values, they should dump their contents downstream and
     * clear any stored state.
     */
    default void end() {}

    /**
     * Used to communicate to upstream sources that this {@code Sink} does not wish to receive
     * any more data
     * @return
     */
    default boolean cancellationRequested() {
        return false;
    }

    /**
     * Accept an int value
     * @implspec The default implementation throws IllegalStateException
     *
     * @throws IllegalStateException If this sink does not accept int values
     */
    default void accept(int value) {
        throw new IllegalStateException("called wrong accept method");
    }

    /**
     * Accept a long value
     * @implspec The default implementation throws IllegalStateException
     *
     * @throws IllegalStateException If this sink does not accept long values
     */
    default void accept(long value) {
        throw new IllegalStateException("called wrong accept method");
    }

    /**
     * Accept a double value
     * @implspec The default implementation throws IllegalStateException
     *
     * @throws IllegalStateException If this sink does not accept double values
     */
    default void accept(double value) {
        throw new IllegalStateException("called wrong accept method");
    }

    /**
     * {@code Sink} that implements {@code Sink&lt;Integer>}, reabstracts {@code accept(int)},
     * and wires {@code accept(Integer)} to bridge to {@code accept(int)}.
     */
    @FunctionalInterface
    interface OfInt extends Sink<Integer>, IntConsumer {
        @Override
        void accept(int value);

        @Override
        default void accept(Integer i) {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} calling Sink.OfInt.accept(Integer)");
            accept(i.intValue());
        }
    }

    /**
     * {@code Sink} that implements {@code Sink&lt;Long>}, reabstracts {@code accept(long)},
     * and wires {@code accept(Long)} to bridge to {@code accept(long)}.
     */
    @FunctionalInterface
    interface OfLong extends Sink<Long>, LongConsumer {
        @Override
        void accept(long value);

        @Override
        default void accept(Long i) {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} calling Sink.OfLong.accept(Long)");
            accept(i.longValue());
        }
    }

    /**
     * {@code Sink} that implements {@code Sink&lt;Double>}, reabstracts {@code accept(double)},
     * and wires {@code accept(Double)} to bridge to {@code accept(double)}.
     */
    @FunctionalInterface
    interface OfDouble extends Sink<Double>, DoubleConsumer {
        @Override
        void accept(double value);

        @Override
        default void accept(Double i) {
            if (Tripwire.enabled)
                Tripwire.trip(getClass(), "{0} calling Sink.OfDouble.accept(Double)");
            accept(i.doubleValue());
        }
    }

    /**
     * {@code Sink} implementation designed for creating chains of sinks.  The {@code begin} and
     * {@code end}, and {@code cancellationRequested} methods are wired to chain to the downstream
     * {@code Sink}.  This implementation takes a downstream {@code Sink} of unknown input shape
     * and produces a {@code Sink&lt;T>}.  The implementation of the {@code accept()} method must
     * call the correct {@code accept()} method on the downstream {@code Sink}.
     */
    static abstract class ChainedReference<T> implements Sink<T> {
        protected final Sink downstream;

        public ChainedReference(Sink downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        @Override
        public void begin(long size) {
            downstream.begin(size);
        }

        @Override
        public void end() {
            downstream.end();
        }

        @Override
        public boolean cancellationRequested() {
            return downstream.cancellationRequested();
        }
    }

    /**
     * {@code Sink} implementation designed for creating chains of sinks.  The {@code begin} and
     * {@code end}, and {@code cancellationRequested} methods are wired to chain to the downstream
     * {@code Sink}.  This implementation takes a downstream {@code Sink} of unknown input shape
     * and produces a {@code Sink.OfInt}.  The implementation of the {@code accept()} method must
     * call the correct {@code accept()} method on the downstream {@code Sink}.
     */
    static abstract class ChainedInt implements Sink.OfInt {
        protected final Sink downstream;

        public ChainedInt(Sink downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        @Override
        public void begin(long size) {
            downstream.begin(size);
        }

        @Override
        public void end() {
            downstream.end();
        }

        @Override
        public boolean cancellationRequested() {
            return downstream.cancellationRequested();
        }
    }

    /**
     * {@code Sink} implementation designed for creating chains of sinks.  The {@code begin} and
     * {@code end}, and {@code cancellationRequested} methods are wired to chain to the downstream
     * {@code Sink}.  This implementation takes a downstream {@code Sink} of unknown input shape
     * and produces a {@code Sink.OfLong}.  The implementation of the {@code accept()} method must
     * call the correct {@code accept()} method on the downstream {@code Sink}.
     */
    static abstract class ChainedLong implements Sink.OfLong {
        protected final Sink downstream;

        public ChainedLong(Sink downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        @Override
        public void begin(long size) {
            downstream.begin(size);
        }

        @Override
        public void end() {
            downstream.end();
        }

        @Override
        public boolean cancellationRequested() {
            return downstream.cancellationRequested();
        }
    }

    /**
     * {@code Sink} implementation designed for creating chains of sinks.  The {@code begin} and
     * {@code end}, and {@code cancellationRequested} methods are wired to chain to the downstream
     * {@code Sink}.  This implementation takes a downstream {@code Sink} of unknown input shape
     * and produces a {@code Sink.OfDouble}.  The implementation of the {@code accept()} method must
     * call the correct {@code accept()} method on the downstream {@code Sink}.
     */
    static abstract class ChainedDouble implements Sink.OfDouble {
        protected final Sink downstream;

        public ChainedDouble(Sink downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        @Override
        public void begin(long size) {
            downstream.begin(size);
        }

        @Override
        public void end() {
            downstream.end();
        }

        @Override
        public boolean cancellationRequested() {
            return downstream.cancellationRequested();
        }
    }
}
