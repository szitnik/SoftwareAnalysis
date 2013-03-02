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

import java.util.Spliterator;
import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * MatchOp
 *
 * @author Brian Goetz
 */
class MatchOp<T> implements TerminalOp<T, Boolean> {
    private final StreamShape inputShape;
    protected final MatchKind matchKind;
    protected final Supplier<BooleanTerminalSink<T>> sinkSupplier;

    private MatchOp(StreamShape shape, MatchKind matchKind, Supplier<BooleanTerminalSink<T>> sinkSupplier) {
        this.inputShape = shape;
        this.matchKind = matchKind;
        this.sinkSupplier = sinkSupplier;
    }

    // Boolean specific terminal sink to avoid the boxing costs when returning results
    private static abstract class BooleanTerminalSink<T> implements Sink<T> {
        protected boolean stop;
        protected boolean value;

        protected BooleanTerminalSink(MatchKind matchKind) {
            value = !matchKind.shortCircuitResult;
        }

        public boolean getAndClearState() {
            return value;
        }

        @Override
        public boolean cancellationRequested() {
            return stop;
        }
    }

    public static <T> MatchOp<T> match(Predicate<? super T> predicate, MatchKind matchKind) {
        class MatchSink extends BooleanTerminalSink<T> {

            private MatchSink() {
                super(matchKind);
            }

            @Override
            public void accept(T t) {
                // @@@ assert !stop when SortedOp supports short-circuit on Sink.end
                //     for sequential operations
                if (!stop && predicate.test(t) == matchKind.stopOnPredicateMatches) {
                    stop = true;
                    value = matchKind.shortCircuitResult;
                }
            }
        }

        // @@@ Change to return MatchSink::new when compiler and runtime bugs are fixed
        Supplier<BooleanTerminalSink<T>> s = new Supplier<BooleanTerminalSink<T>>() {
            @Override
            public BooleanTerminalSink<T> get() {return new MatchSink();}
        };
        return new MatchOp<>(StreamShape.REFERENCE, matchKind, s);
    }

    public static MatchOp<Integer> match(IntPredicate predicate, MatchKind matchKind) {
        class MatchSink extends BooleanTerminalSink<Integer> implements Sink.OfInt {
            private MatchSink() {
                super(matchKind);
            }

            @Override
            public void accept(int t) {
                if (!stop && predicate.test(t) == matchKind.stopOnPredicateMatches) {
                    stop = true;
                    value = matchKind.shortCircuitResult;
                }
            }
        }

        Supplier<BooleanTerminalSink<Integer>> s = new Supplier<BooleanTerminalSink<Integer>>() {
            @Override
            public BooleanTerminalSink<Integer> get() {return new MatchSink();}
        };
        return new MatchOp<>(StreamShape.INT_VALUE, matchKind, s);
    }

    public static MatchOp<Long> match(LongPredicate predicate, MatchKind matchKind) {
        class MatchSink extends BooleanTerminalSink<Long> implements Sink.OfLong {

            private MatchSink() {
                super(matchKind);
            }

            @Override
            public void accept(long t) {
                if (!stop && predicate.test(t) == matchKind.stopOnPredicateMatches) {
                    stop = true;
                    value = matchKind.shortCircuitResult;
                }
            }
        }

        Supplier<BooleanTerminalSink<Long>> s = new Supplier<BooleanTerminalSink<Long>>() {
            @Override
            public BooleanTerminalSink<Long> get() {return new MatchSink();}
        };
        return new MatchOp<>(StreamShape.LONG_VALUE, matchKind, s);
    }

    public static MatchOp<Double> match(DoublePredicate predicate, MatchKind matchKind) {
        class MatchSink extends BooleanTerminalSink<Double> implements Sink.OfDouble {

            private MatchSink() {
                super(matchKind);
            }

            @Override
            public void accept(double t) {
                if (!stop && predicate.test(t) == matchKind.stopOnPredicateMatches) {
                    stop = true;
                    value = matchKind.shortCircuitResult;
                }
            }
        }

        Supplier<BooleanTerminalSink<Double>> s = new Supplier<BooleanTerminalSink<Double>>() {
            @Override
            public BooleanTerminalSink<Double> get() {return new MatchSink();}
        };
        return new MatchOp<>(StreamShape.DOUBLE_VALUE, matchKind, s);
    }

    @Override
    public int getOpFlags() {
        return StreamOpFlag.IS_SHORT_CIRCUIT | StreamOpFlag.NOT_ORDERED;
    }

    @Override
    public StreamShape inputShape() {
        return inputShape;
    }

    @Override
    public <S> Boolean evaluateSequential(PipelineHelper<S, T> helper) {
        return helper.into(sinkSupplier.get(), helper.sourceSpliterator()).getAndClearState();
    }

    @Override
    public <S> Boolean evaluateParallel(PipelineHelper<S, T> helper) {
        // Approach for parallel implementation:
        // - Decompose as per usual
        // - run match on leaf chunks, call result "b"
        // - if b == matchKind.shortCircuitOn, complete early and return b
        // - else if we complete normally, return !shortCircuitOn

        return new MatchTask<>(this, helper).invoke();
    }

    private static class MatchTask<S, T> extends AbstractShortCircuitTask<S, T, Boolean, MatchTask<S, T>> {
        private final MatchOp<T> op;

        private MatchTask(MatchOp<T> op, PipelineHelper<S, T> helper) {
            super(helper);
            this.op = op;
        }

        private MatchTask(MatchTask<S, T> parent, Spliterator<S> spliterator) {
            super(parent, spliterator);
            this.op = parent.op;
        }

        @Override
        protected MatchTask<S, T> makeChild(Spliterator<S> spliterator) {
            return new MatchTask<>(this, spliterator);
        }

        @Override
        protected Boolean doLeaf() {
            boolean b = helper.into(op.sinkSupplier.get(), spliterator).getAndClearState();
            if (b == op.matchKind.shortCircuitResult)
                shortCircuit(b);
            return null;
        }

        @Override
        protected Boolean getEmptyResult() {
            return !op.matchKind.shortCircuitResult;
        }
    }

    public enum MatchKind {
        ANY(true, true),
        ALL(false, false),
        NONE(true, false);

        private final boolean stopOnPredicateMatches;
        private final boolean shortCircuitResult;

        private MatchKind(boolean stopOnPredicateMatches, boolean shortCircuitResult) {
            this.stopOnPredicateMatches = stopOnPredicateMatches;
            this.shortCircuitResult = shortCircuitResult;
        }
    }
}
