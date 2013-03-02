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

import java.util.*;
import java.util.concurrent.CountedCompleter;
import java.util.function.Predicate;
import java.util.function.Supplier;

class FindOp<T, O> implements TerminalOp<T, O> {

    public static<T> FindOp<T, Optional<T>> makeRef(boolean mustFindFirst) {
        return new FindOp<>(mustFindFirst, StreamShape.REFERENCE, Optional.empty(), Optional::isPresent, FindSink.OfRef::new);
    }

    public static FindOp<Integer, OptionalInt> makeInt(boolean mustFindFirst) {
        return new FindOp<>(mustFindFirst, StreamShape.INT_VALUE, OptionalInt.empty(), OptionalInt::isPresent, FindSink.OfInt::new);
    }

    public static FindOp<Long, OptionalLong> makeLong(boolean mustFindFirst) {
        return new FindOp<>(mustFindFirst, StreamShape.LONG_VALUE, OptionalLong.empty(), OptionalLong::isPresent, FindSink.OfLong::new);
    }

    public static FindOp<Double, OptionalDouble> makeDouble(boolean mustFindFirst) {
        return new FindOp<>(mustFindFirst, StreamShape.DOUBLE_VALUE, OptionalDouble.empty(), OptionalDouble::isPresent, FindSink.OfDouble::new);
    }

    static abstract class FindSink<T, O> implements TerminalSink<T, O> {
        boolean hasValue;
        T value;

        @Override
        public void accept(T value) {
            if (!hasValue) {
                hasValue = true;
                this.value = value;
            }
        }

        @Override
        public boolean cancellationRequested() {
            return hasValue;
        }

        static class OfRef<T> extends FindSink<T, Optional<T>> {
            @Override
            public Optional<T> getAndClearState() {
                return hasValue ? Optional.of(value) : null;
            }
        }

        static class OfInt extends FindSink<Integer, OptionalInt> implements Sink.OfInt {
            @Override
            public void accept(int value) {
                accept((Integer) value);
            }

            @Override
            public OptionalInt getAndClearState() {
                return hasValue ? OptionalInt.of(value) : null;
            }
        }

        static class OfLong extends FindSink<Long, OptionalLong> implements Sink.OfLong {
            @Override
            public void accept(long value) {
                accept((Long) value);
            }

            @Override
            public OptionalLong getAndClearState() {
                return hasValue ? OptionalLong.of(value) : null;
            }
        }

        static class OfDouble extends FindSink<Double, OptionalDouble> implements Sink.OfDouble {
            @Override
            public void accept(double value) {
                accept((Double) value);
            }

            @Override
            public OptionalDouble getAndClearState() {
                return hasValue ? OptionalDouble.of(value) : null;
            }
        }
    }

    private final boolean mustFindFirst;
    private final StreamShape shape;
    private final O emptyValue;
    private final Predicate<O> presentPredicate;
    private final Supplier<TerminalSink<T, O>> sinkSupplier;

    private FindOp(boolean mustFindFirst,
                   StreamShape shape,
                   O emptyValue,
                   Predicate<O> presentPredicate,
                   Supplier<TerminalSink<T, O>> sinkSupplier) {
        this.mustFindFirst = mustFindFirst;
        this.shape = shape;
        this.emptyValue = emptyValue;
        this.presentPredicate = presentPredicate;
        this.sinkSupplier = sinkSupplier;
    }

    @Override
    public int getOpFlags() {
        return StreamOpFlag.IS_SHORT_CIRCUIT | (mustFindFirst ? 0 : StreamOpFlag.NOT_ORDERED);
    }

    @Override
    public StreamShape inputShape() {
        return shape;
    }

    @Override
    public <S> O evaluateSequential(PipelineHelper<S, T> helper) {
        O result = helper.into(sinkSupplier.get(), helper.sourceSpliterator()).getAndClearState();
        return result != null ? result : emptyValue;
    }

    @Override
    public <P_IN> O evaluateParallel(PipelineHelper<P_IN, T> helper) {
        return new FindTask<>(helper, this).invoke();
    }

    private static class FindTask<S, T, O> extends AbstractShortCircuitTask<S, T, O, FindTask<S, T, O>> {
        private final FindOp<T, O> op;

        private FindTask(PipelineHelper<S, T> helper, FindOp<T, O> op) {
            super(helper);
            this.op = op;
        }

        private FindTask(FindTask<S, T, O> parent, Spliterator<S> spliterator) {
            super(parent, spliterator);
            this.op = parent.op;
        }

        @Override
        protected FindTask<S, T, O> makeChild(Spliterator<S> spliterator) {
            return new FindTask<>(this, spliterator);
        }

        @Override
        protected O getEmptyResult() {
            return op.emptyValue;
        }

        private void foundResult(O answer) {
            if (isLeftSpine())
                shortCircuit(answer);
            else
                cancelLaterNodes();
        }

        @Override
        protected O doLeaf() {
            O result = helper.into(op.sinkSupplier.get(), spliterator).getAndClearState();
            if (!op.mustFindFirst) {
                if (result != null)
                    shortCircuit(result);
                return null;
            }
            else {
                if (result != null) {
                    foundResult(result);
                    return result;
                }
                else
                    return null;
            }
        }

        @Override
        public void onCompletion(CountedCompleter<?> caller) {
            if (op.mustFindFirst) {
                for (FindTask<S, T, O> child = children; child != null; child = child.nextSibling) {
                    O result = child.getLocalResult();
                    if (result != null && op.presentPredicate.test(result)) {
                        setLocalResult(result);
                        foundResult(result);
                        break;
                    }
                }
            }
        }
    }
}
