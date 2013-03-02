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

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;

class DoublePipeline<E_IN> extends AbstractPipeline<E_IN, Double, DoubleStream> implements DoubleStream {

    public DoublePipeline(Supplier<? extends Spliterator<Double>> source, int sourceFlags) {
        super((Supplier) source, sourceFlags);
    }

    public DoublePipeline(AbstractPipeline<?, E_IN, ?> upstream, IntermediateOp<E_IN, Double> op) {
        super(upstream, op);
    }

    @Override
    protected StreamShape getOutputShape() {
        return StreamShape.DOUBLE_VALUE;
    }

    @Override
    protected <P_IN> Node<Double> collect(PipelineHelper<P_IN, Double> helper, boolean flattenTree) {
        return NodeUtils.doubleCollect(helper, flattenTree);
    }

    @Override
    protected <P_IN> Node<Double> flatten(PipelineHelper<P_IN, Double> helper, Node<Double> node) {
        return NodeUtils.doubleFlatten((Node.OfDouble) node);
    }

    @Override
    protected <P_IN> Spliterator<Double> wrap(PipelineHelper<P_IN, Double> ph, Spliterator<P_IN> spliterator, boolean isParallel) {
        return new Spliterators.DoubleWrappingSpliterator<>(ph, spliterator, isParallel);
    }

    @Override
    protected Spliterator.OfDouble lazySpliterator(Supplier supplier) {
        return new Spliterators.DelegatingSpliterator.OfDouble(supplier);
    }

    @Override
    protected <T, S> AbstractPipeline<?, T, ?> stream(Supplier<Spliterator<S>> supplier, int flags) {
        return new DoublePipeline((Supplier) supplier, flags);
    }

    @Override
    protected void forEachWithCancel(Spliterator<Double> spliterator, Sink<Double> sink) {
        Spliterator.OfDouble spl = adapt(spliterator);
        DoubleConsumer adaptedSink = adapt(sink);
        while (!sink.cancellationRequested() && spl.tryAdvance(adaptedSink)) { }
    }

    private static DoubleConsumer adapt(Sink<Double> sink) {
        if (sink instanceof DoubleConsumer) {
            return (DoubleConsumer) sink;
        }
        else {
            if (Tripwire.enabled)
                Tripwire.trip(AbstractPipeline.class, "using DoubleStream.adapt(Sink<Double> s)");
            return sink::accept;
        }
    }

    @Override
    protected NodeFactory<Double> getNodeFactory() {
        return Nodes.getDoubleNodeFactory();
    }

    //

    @Override
    public Spliterator.OfDouble spliterator() {
        return adapt(super.spliterator());
    }

    static Spliterator.OfDouble adapt(Spliterator<Double> s) {
        if (s instanceof Spliterator.OfDouble) {
            return (Spliterator.OfDouble) s;
        }
        else {
            if (Tripwire.enabled)
                Tripwire.trip(AbstractPipeline.class, "using DoubleStream.adapt(Spliterator<Double> s)");
            throw new UnsupportedOperationException("DoubleStream.adapt(Spliterator<Double> s)");
        }
    }

    //

    @Override
    public DoubleStream map(DoubleUnaryOperator mapper) {
        Objects.requireNonNull(mapper);
        return chainedToDouble(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.DOUBLE_VALUE,
                               (flags, sink) -> new Sink.ChainedDouble(sink) {
                                   @Override
                                   public void accept(double t) {
                                       downstream.accept(mapper.applyAsDouble(t));
                                   }
                               });
    }

    @Override
    public <U> Stream<U> map(DoubleFunction<U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToRef(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.DOUBLE_VALUE,
                            (flags, sink) -> new Sink.ChainedDouble(sink) {
                                @Override
                                public void accept(double t) {
                                    downstream.accept(mapper.apply(t));
                                }
                            });
    }

    @Override
    public DoubleStream flatMap(FlatMapper.OfDoubleToDouble mapper) {
        Objects.requireNonNull(mapper);
        return chainedToDouble(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED,
                               StreamShape.DOUBLE_VALUE,
                               (flags, sink) -> new Sink.ChainedDouble(sink) {
                                   public void accept(double t) {
                                       mapper.explodeInto(t, (Sink.OfDouble) downstream);
                                   }
                               });
    }

    @Override
    public DoubleStream filter(DoublePredicate predicate) {
        Objects.requireNonNull(predicate);
        return chainedToDouble(StreamOpFlag.NOT_SIZED, StreamShape.DOUBLE_VALUE,
                               (flags, sink) -> new Sink.ChainedDouble(sink) {
                                   @Override
                                   public void accept(double t) {
                                       if (predicate.test(t))
                                           downstream.accept(t);
                                   }
                               });
    }

    @Override
    public DoubleStream limit(long maxSize) {
        if (maxSize < 0)
            throw new IllegalArgumentException(Long.toString(maxSize));
        return super.slice(0, maxSize);
    }

    @Override
    public DoubleStream substream(long startOffset) {
        if (startOffset < 0)
            throw new IllegalArgumentException(Long.toString(startOffset));
        if (startOffset == 0)
            return this;
        else
            return super.slice(startOffset, -1);
    }

    @Override
    public DoubleStream substream(long startOffset, long endOffset) {
        if (startOffset < 0 || endOffset < startOffset)
            throw new IllegalArgumentException(String.format("substream(%d, %d)", startOffset, endOffset));
        return super.slice(startOffset, endOffset - startOffset);
    }

    @Override
    public DoubleStream peek(DoubleConsumer consumer) {
        Objects.requireNonNull(consumer);
        return chainedToDouble(0, StreamShape.DOUBLE_VALUE,
                               (flags, sink) -> new Sink.ChainedDouble(sink) {
                                   @Override
                                   public void accept(double t) {
                                       consumer.accept(t);
                                       downstream.accept(t);
                                   }
                               });
    }

    @Override
    public DoubleStream sorted() {
        return pipeline(new SortedOp.OfDouble());
    }

    @Override
    public void forEach(DoubleConsumer consumer) {
        pipeline(ForEachOp.make(consumer));
    }

    @Override
    public void forEachUntil(DoubleConsumer consumer, BooleanSupplier until) {
        pipeline(ForEachUntilOp.make(consumer, until));
    }

    @Override
    public double reduce(double identity, DoubleBinaryOperator op) {
        return pipeline(FoldOp.makeDouble(identity, op));
    }

    @Override
    public OptionalDouble reduce(DoubleBinaryOperator op) {
        return pipeline(FoldOp.makeDouble(op));
    }

    @Override
    public <R> R collect(Collector.OfDouble<R> collector) {
        return pipeline(FoldOp.makeDouble(collector));
    }

    @Override
    public <R> R collectUnordered(Collector.OfDouble<R> collector) {
        if (collector.isConcurrent()) {
            R container = collector.resultSupplier().get();
            ObjDoubleConsumer<R> accumulator = collector.doubleAccumulator();
            forEach(u -> accumulator.accept(container, u));
            return container;
        }
        else {
            return collect(collector);
        }
    }

    @Override
    public boolean anyMatch(DoublePredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.ANY));
    }

    @Override
    public boolean allMatch(DoublePredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.ALL));
    }

    @Override
    public boolean noneMatch(DoublePredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.NONE));
    }

    @Override
    public OptionalDouble findFirst() {
        return pipeline(FindOp.makeDouble(true));
    }

    @Override
    public OptionalDouble findAny() {
        return pipeline(FindOp.makeDouble(false));
    }

    @Override
    public double[] toArray() {
        return ((Node.OfDouble) collectOutput(true)).asDoubleArray();
    }
}
