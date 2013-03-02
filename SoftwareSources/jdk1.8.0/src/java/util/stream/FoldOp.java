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

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.function.IntBinaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

/**
 * FoldOp
 *
 * @author Brian Goetz
 */
class FoldOp<T, R, S extends OpUtils.AccumulatingSink<T, R, S>> implements TerminalOp<T, R> {
    private final Supplier<S> sinkSupplier;
    private final StreamShape inputShape;

    public FoldOp(StreamShape shape, Supplier<S> supplier) {
        sinkSupplier = supplier;
        inputShape = shape;
    }

    public FoldOp(Supplier<S> supplier) {
        this(StreamShape.REFERENCE, supplier);
    }

    @Override
    public StreamShape inputShape() {
        return inputShape;
    }

    public <S> R evaluateSequential(PipelineHelper<S, T> helper) {
        return helper.into(sinkSupplier.get(), helper.sourceSpliterator()).getAndClearState();
    }

    @Override
    public <S> R evaluateParallel(PipelineHelper<S, T> helper) {
        return OpUtils.parallelReduce(helper, sinkSupplier);
    }


    private static abstract class Box<U> {
        protected U state;

        public void clearState() {
            state = null;
        }

        public U getAndClearState() {
            try { return state; }
            finally { state = null; }
        }
    }

    private static abstract class OptionalBox<U> {
        protected boolean empty;
        protected U state;

        public void begin(long size) {
            empty = true;
            state = null;
        }

        public void clearState() {
            empty = true;
            state = null;
        }

        public Optional<U> getAndClearState() {
            try { return empty ? Optional.empty() : Optional.of(state); }
            finally { clearState(); }
        }
    }

    private static abstract class IntBox {
        protected int state;

        public void begin(long size) {
            state = 0;
        }

        public void clearState() {
            state = 0;
        }

        public Integer getAndClearState() {
            try { return state; }
            finally { state = 0; }
        }
    }

    private static abstract class OptionalIntBox {
        protected boolean empty;
        protected int state;

        public void begin(long size) {
            empty = true;
            state = 0;
        }

        public void clearState() {
            state = 0;
        }

        public OptionalInt getAndClearState() {
            try { return empty ? OptionalInt.empty() : OptionalInt.of(state); }
            finally { state = 0; }
        }
    }

    private static abstract class LongBox {
        protected long state;

        public void begin(long size) {
            state = 0;
        }

        public void clearState() {
            state = 0;
        }

        public Long getAndClearState() {
            try { return state; }
            finally { state = 0; }
        }
    }

    private static abstract class OptionalLongBox {
        protected boolean empty;
        protected long state;

        public void begin(long size) {
            empty = true;
            state = 0;
        }

        public void clearState() {
            state = 0;
        }

        public OptionalLong getAndClearState() {
            try { return empty ? OptionalLong.empty() : OptionalLong.of(state); }
            finally { state = 0; }
        }
    }

    private static abstract class DoubleBox {
        protected double state;

        public void begin(long size) {
            state = 0;
        }

        public void clearState() {
            state = 0;
        }

        public Double getAndClearState() {
            try { return state; }
            finally { state = 0; }
        }
    }

    private static abstract class OptionalDoubleBox {
        protected boolean empty;
        protected double state;

        public void begin(long size) {
            empty = true;
            state = 0;
        }

        public void clearState() {
            state = 0;
        }

        public OptionalDouble getAndClearState() {
            try { return empty ? OptionalDouble.empty() : OptionalDouble.of(state); }
            finally { state = 0; }
        }
    }

    public static<T, U> TerminalOp<T, U>
    makeRef(U seed, BiFunction<U, ? super T, U> reducer, BinaryOperator<U> combiner) {
        class FoldingSink extends Box<U> implements OpUtils.AccumulatingSink<T, U, FoldingSink>, Sink<T> {
            @Override
            public void begin(long size) {
                state = seed;
            }

            @Override
            public void accept(T t) {
                state = reducer.apply(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                state = combiner.apply(state, other.state);
            }
        }
        // @@@ Replace inner class suppliers with ctor refs or lambdas pending fix of compiler bug(s)
        return new FoldOp<>(new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {return new FoldingSink();}
        });
    }

    public static<T> TerminalOp<T, Optional<T>>
    makeRef(BinaryOperator<T> operator) {
        class FoldingSink extends OptionalBox<T> implements OpUtils.AccumulatingSink<T, Optional<T>, FoldingSink> {

            @Override
            public void accept(T t) {
                if (empty) {
                    empty = false;
                    state = t;
                } else {
                    state = operator.apply(state, t);
                }
            }

            @Override
            public void combine(FoldingSink other) {
                if (!other.empty)
                    accept(other.state);
            }
        }
        return new FoldOp<>(new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {return new FoldingSink();}
        });
    }

    public static<T,R> TerminalOp<T, R>
    makeRef(Collector<? super T,R> collector) {
        Supplier<R> supplier = collector.resultSupplier();
        BiConsumer<R, ? super T> accumulator = collector.accumulator();
        BinaryOperator<R> combiner = collector.combiner();
        class FoldingSink extends Box<R> implements OpUtils.AccumulatingSink<T, R, FoldingSink> {
            @Override
            public void begin(long size) {
                state = supplier.get();
            }

            @Override
            public void accept(T t) {
                accumulator.accept(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                state = combiner.apply(state, other.state);
            }
        }
        return new FoldOp<>(new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {return new FoldingSink();}
        });
    }

    public static<T, R> TerminalOp<T, R>
    makeRef(Supplier<R> seedFactory, BiConsumer<R, ? super T> accumulator, BiConsumer<R,R> reducer) {
        class FoldingSink extends Box<R> implements OpUtils.AccumulatingSink<T, R, FoldingSink> {
            @Override
            public void begin(long size) {
                state = seedFactory.get();
            }

            @Override
            public void accept(T t) {
                accumulator.accept(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                reducer.accept(state, other.state);
            }
        }
        return new FoldOp<>(new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {return new FoldingSink();}
        });
    }

    public static TerminalOp<Integer, Integer>
    makeInt(int identity, IntBinaryOperator operator) {
        class FoldingSink extends IntBox implements OpUtils.AccumulatingSink<Integer, Integer, FoldingSink>, Sink.OfInt {
            @Override
            public void begin(long size) {
                state = identity;
            }

            @Override
            public void accept(int t) {
                state = operator.applyAsInt(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                accept(other.state);
            }
        }
        return new FoldOp<>(StreamShape.INT_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }

    public static TerminalOp<Integer, OptionalInt>
    makeInt(IntBinaryOperator operator) {
        class FoldingSink extends OptionalIntBox implements OpUtils.AccumulatingSink<Integer, OptionalInt, FoldingSink>, Sink.OfInt {
            @Override
            public void accept(int t) {
                if (empty) {
                    empty = false;
                    state = t;
                }
                else {
                    state = operator.applyAsInt(state, t);
                }
            }

            @Override
            public void combine(FoldingSink other) {
                if (!other.empty)
                    accept(other.state);
            }
        }

        return new FoldOp<>(StreamShape.INT_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }

    public static <R> TerminalOp<Integer, R>
    makeInt(Collector.OfInt<R> reducer) {
        Supplier<R> supplier = reducer.resultSupplier();
        ObjIntConsumer<R> accumulator = reducer.intAccumulator();
        BinaryOperator<R> combiner = reducer.combiner();
        class FoldingSink extends Box<R> implements OpUtils.AccumulatingSink<Integer, R, FoldingSink>, Sink.OfInt {
            @Override
            public void begin(long size) {
                state = supplier.get();
            }

            @Override
            public void accept(int t) {
                accumulator.accept(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                state = combiner.apply(state, other.state);
            }
        }

        return new FoldOp<>(StreamShape.INT_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }

    public static TerminalOp<Long, Long>
    makeLong(long identity, LongBinaryOperator operator) {
        class FoldingSink extends LongBox implements OpUtils.AccumulatingSink<Long, Long, FoldingSink>, Sink.OfLong {
            @Override
            public void begin(long size) {
                state = identity;
            }

            @Override
            public void accept(long t) {
                state = operator.applyAsLong(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                accept(other.state);
            }
        }
        return new FoldOp<>(StreamShape.LONG_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }

    public static TerminalOp<Long, OptionalLong>
    makeLong(LongBinaryOperator operator) {
        class FoldingSink extends OptionalLongBox implements OpUtils.AccumulatingSink<Long, OptionalLong, FoldingSink>, Sink.OfLong {
            @Override
            public void accept(long t) {
                if (empty) {
                    empty = false;
                    state = t;
                }
                else {
                    state = operator.applyAsLong(state, t);
                }
            }

            @Override
            public void combine(FoldingSink other) {
                if (!other.empty)
                    accept(other.state);
            }
        }

        return new FoldOp<>(StreamShape.LONG_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }

    public static <R> TerminalOp<Long, R>
    makeLong(Collector.OfLong<R> reducer) {
        Supplier<R> supplier = reducer.resultSupplier();
        ObjLongConsumer<R> accumulator = reducer.longAccumulator();
        BinaryOperator<R> combiner = reducer.combiner();
        class FoldingSink extends Box<R> implements OpUtils.AccumulatingSink<Long, R, FoldingSink>, Sink.OfLong {
            @Override
            public void begin(long size) {
                state = supplier.get();
            }

            @Override
            public void accept(long t) {
                accumulator.accept(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                state = combiner.apply(state, other.state);
            }
        }

        return new FoldOp<>(StreamShape.LONG_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }

    public static TerminalOp<Double, Double>
    makeDouble(double identity, DoubleBinaryOperator operator) {
        class FoldingSink extends DoubleBox implements OpUtils.AccumulatingSink<Double, Double, FoldingSink>, Sink.OfDouble {
            @Override
            public void begin(long size) {
                state = identity;
            }

            @Override
            public void accept(double t) {
                state = operator.applyAsDouble(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                accept(other.state);
            }
        }
        return new FoldOp<>(StreamShape.DOUBLE_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }

    public static TerminalOp<Double, OptionalDouble>
    makeDouble(DoubleBinaryOperator operator) {
        class FoldingSink extends OptionalDoubleBox implements OpUtils.AccumulatingSink<Double, OptionalDouble, FoldingSink>, Sink.OfDouble {
            @Override
            public void accept(double t) {
                if (empty) {
                    empty = false;
                    state = t;
                }
                else {
                    state = operator.applyAsDouble(state, t);
                }
            }

            @Override
            public void combine(FoldingSink other) {
                if (!other.empty)
                    accept(other.state);
            }
        }

        return new FoldOp<>(StreamShape.DOUBLE_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }

    public static <R> TerminalOp<Double, R> makeDouble(Collector.OfDouble<R> reducer) {
        Supplier<R> supplier = reducer.resultSupplier();
        ObjDoubleConsumer<R> accumulator = reducer.doubleAccumulator();
        BinaryOperator<R> combiner = reducer.combiner();

        class FoldingSink extends Box<R> implements OpUtils.AccumulatingSink<Double, R, FoldingSink>, Sink.OfDouble {
            @Override
            public void begin(long size) {
                state = supplier.get();
            }

            @Override
            public void accept(double t) {
                accumulator.accept(state, t);
            }

            @Override
            public void combine(FoldingSink other) {
                state = combiner.apply(state, other.state);
            }
        }

        return new FoldOp<>(StreamShape.DOUBLE_VALUE, new Supplier<FoldingSink>() {
            @Override
            public FoldingSink get() {
                return new FoldingSink();
            }
        });
    }
}
