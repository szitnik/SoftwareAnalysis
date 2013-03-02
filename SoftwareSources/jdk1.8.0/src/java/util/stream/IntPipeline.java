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
import java.util.OptionalInt;
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

class IntPipeline<E_IN> extends AbstractPipeline<E_IN, Integer, IntStream> implements IntStream {

    public IntPipeline(Supplier<? extends Spliterator<Integer>> source, int sourceFlags) {
        super((Supplier) source, sourceFlags);
    }

    public IntPipeline(AbstractPipeline<?, E_IN, ?> upstream, IntermediateOp<E_IN, Integer> op) {
        super(upstream, op);
    }

    @Override
    protected StreamShape getOutputShape() {
        return StreamShape.INT_VALUE;
    }

    @Override
    protected <P_IN> Node<Integer> collect(PipelineHelper<P_IN, Integer> helper, boolean flattenTree) {
        return NodeUtils.intCollect(helper, flattenTree);
    }

    @Override
    protected <P_IN> Node<Integer> flatten(PipelineHelper<P_IN, Integer> helper, Node<Integer> node) {
        return NodeUtils.intFlatten((Node.OfInt) node);
    }

    @Override
    protected <P_IN> Spliterator<Integer> wrap(PipelineHelper<P_IN, Integer> ph, Spliterator<P_IN> spliterator, boolean isParallel) {
        return new Spliterators.IntWrappingSpliterator<>(ph, spliterator, isParallel);
    }

    @Override
    protected Spliterator.OfInt lazySpliterator(Supplier supplier) {
        return new Spliterators.DelegatingSpliterator.OfInt(supplier);
    }

    @Override
    protected <T, S> AbstractPipeline<?, T, ?> stream(Supplier<Spliterator<S>> supplier, int flags) {
        return new IntPipeline((Supplier) supplier, flags);
    }

    @Override
    protected void forEachWithCancel(Spliterator<Integer> spliterator, Sink<Integer> sink) {
        Spliterator.OfInt spl = adapt(spliterator);
        IntConsumer adaptedSink = adapt(sink);
        while (!sink.cancellationRequested() && spl.tryAdvance(adaptedSink)) { }
    }

    private static IntConsumer adapt(Sink<Integer> sink) {
        if (sink instanceof IntConsumer) {
            return (IntConsumer) sink;
        }
        else {
            if (Tripwire.enabled)
                Tripwire.trip(AbstractPipeline.class, "using IntStream.adapt(Sink<Integer> s)");
            return sink::accept;
        }
    }

    @Override
    protected NodeFactory<Integer> getNodeFactory() {
        return Nodes.getIntNodeFactory();
    }

    //

    @Override
    public Spliterator.OfInt spliterator() {
        return adapt(super.spliterator());
    }

    private static Spliterator.OfInt adapt(Spliterator<Integer> s) {
        if (s instanceof Spliterator.OfInt) {
            return (Spliterator.OfInt) s;
        }
        else {
            if (Tripwire.enabled)
                Tripwire.trip(AbstractPipeline.class, "using IntStream.adapt(Spliterator<Integer> s)");
            throw new UnsupportedOperationException("IntStream.adapt(Spliterator<Integer> s)");
        }
    }

    //


    @Override
    public LongStream longs() {
        return chainedToLong(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.INT_VALUE,
                             (flags, sink) -> new Sink.ChainedInt(sink) {
                                 @Override
                                 public void accept(int t) {
                                     downstream.accept((long) t);
                                 }
                             });
    }

    @Override
    public DoubleStream doubles() {
        return chainedToDouble(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.INT_VALUE,
                               (flags, sink) -> new Sink.ChainedInt(sink) {
                                   @Override
                                   public void accept(int t) {
                                       downstream.accept((double) t);
                                   }
                               });
    }

    @Override
    public IntStream map(IntUnaryOperator mapper) {
        Objects.requireNonNull(mapper);
        return chainedToInt(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.INT_VALUE,
                            (flags, sink) -> new Sink.ChainedInt(sink) {
                                @Override
                                public void accept(int t) {
                                    downstream.accept(mapper.applyAsInt(t));
                                }
                            });
    }

    @Override
    public <U> Stream<U> map(IntFunction<U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToRef(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.INT_VALUE,
                            (flags, sink) -> new Sink.ChainedInt(sink) {
                                @Override
                                public void accept(int t) {
                                    downstream.accept(mapper.apply(t));
                                }
                            });
    }

    @Override
    public IntStream flatMap(FlatMapper.OfIntToInt mapper) {
        Objects.requireNonNull(mapper);
        return chainedToInt(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED,
                            StreamShape.INT_VALUE,
                            (flags, sink) -> new Sink.ChainedInt(sink) {
                                public void accept(int t) {
                                    mapper.explodeInto(t, (Sink.OfInt) downstream);
                                }
                            });
    }

    @Override
    public IntStream filter(IntPredicate predicate) {
        Objects.requireNonNull(predicate);
        return chainedToInt(StreamOpFlag.NOT_SIZED, StreamShape.INT_VALUE,
                            (flags, sink) -> new Sink.ChainedInt(sink) {
                                @Override
                                public void accept(int t) {
                                    if (predicate.test(t))
                                        downstream.accept(t);
                                }
                            });
    }

    @Override
    public IntStream limit(long maxSize) {
        if (maxSize < 0)
            throw new IllegalArgumentException(Long.toString(maxSize));
        return super.slice(0, maxSize);
    }

    @Override
    public IntStream substream(long startOffset) {
        if (startOffset < 0)
            throw new IllegalArgumentException(Long.toString(startOffset));
        if (startOffset == 0)
            return this;
        else
            return super.slice(startOffset, -1);
    }

    @Override
    public IntStream substream(long startOffset, long endOffset) {
        if (startOffset < 0 || endOffset < startOffset)
            throw new IllegalArgumentException(String.format("substream(%d, %d)", startOffset, endOffset));
        return super.slice(startOffset, endOffset - startOffset);
    }

    @Override
    public IntStream peek(IntConsumer consumer) {
        Objects.requireNonNull(consumer);
        return chainedToInt(0, StreamShape.INT_VALUE,
                            (flags, sink) -> new Sink.ChainedInt(sink) {
                                @Override
                                public void accept(int t) {
                                    consumer.accept(t);
                                    downstream.accept(t);
                                }
                            });
    }

    @Override
    public IntStream sorted() {
        return pipeline(new SortedOp.OfInt());
    }

    @Override
    public void forEach(IntConsumer consumer) {
        pipeline(ForEachOp.make(consumer));
    }

    @Override
    public void forEachUntil(IntConsumer consumer, BooleanSupplier until) {
        pipeline(ForEachUntilOp.make(consumer, until));
    }

    @Override
    public int reduce(int identity, IntBinaryOperator op) {
        return pipeline(FoldOp.makeInt(identity, op));
    }

    @Override
    public OptionalInt reduce(IntBinaryOperator op) {
        return pipeline(FoldOp.makeInt(op));
    }

    @Override
    public <R> R collect(Collector.OfInt<R> collector) {
        return pipeline(FoldOp.makeInt(collector));
    }

    @Override
    public <R> R collectUnordered(Collector.OfInt<R> collector) {
        if (collector.isConcurrent()) {
            R container = collector.resultSupplier().get();
            ObjIntConsumer<R> accumulator = collector.intAccumulator();
            forEach(u -> accumulator.accept(container, u));
            return container;
        }
        else {
            return collect(collector);
        }
    }

    @Override
    public boolean anyMatch(IntPredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.ANY));
    }

    @Override
    public boolean allMatch(IntPredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.ALL));
    }

    @Override
    public boolean noneMatch(IntPredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.NONE));
    }

    @Override
    public OptionalInt findFirst() {
        return pipeline(FindOp.makeInt(true));
    }

    @Override
    public OptionalInt findAny() {
        return pipeline(FindOp.makeInt(false));
    }

    @Override
    public int[] toArray() {
        return ((Node.OfInt) collectOutput(true)).asIntArray();
    }
}
