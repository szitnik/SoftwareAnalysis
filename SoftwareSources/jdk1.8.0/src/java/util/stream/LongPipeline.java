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
import java.util.OptionalLong;
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

class LongPipeline<E_IN> extends AbstractPipeline<E_IN, Long, LongStream> implements LongStream {

    public LongPipeline(Supplier<? extends Spliterator<Long>> source, int sourceFlags) {
        super((Supplier) source, sourceFlags);
    }

    public LongPipeline(AbstractPipeline<?, E_IN, ?> upstream, IntermediateOp<E_IN, Long> op) {
        super(upstream, op);
    }

    @Override
    protected StreamShape getOutputShape() {
        return StreamShape.LONG_VALUE;
    }

    @Override
    protected <P_IN> Node<Long> collect(PipelineHelper<P_IN, Long> helper, boolean flattenTree) {
        return NodeUtils.longCollect(helper, flattenTree);
    }

    @Override
    protected <P_IN> Node<Long> flatten(PipelineHelper<P_IN, Long> helper, Node<Long> node) {
        return NodeUtils.longFlatten((Node.OfLong) node);
    }

    @Override
    protected <P_IN> Spliterator<Long> wrap(PipelineHelper<P_IN, Long> ph, Spliterator<P_IN> spliterator, boolean isParallel) {
        return new Spliterators.LongWrappingSpliterator<>(ph, spliterator, isParallel);
    }

    @Override
    protected Spliterator.OfLong lazySpliterator(Supplier supplier) {
        return new Spliterators.DelegatingSpliterator.OfLong(supplier);
    }

    @Override
    protected <T, S> AbstractPipeline<?, T, ?> stream(Supplier<Spliterator<S>> supplier, int flags) {
        return new LongPipeline((Supplier) supplier, flags);
    }

    @Override
    protected void forEachWithCancel(Spliterator<Long> spliterator, Sink<Long> sink) {
        Spliterator.OfLong spl = adapt(spliterator);
        LongConsumer adaptedSink =  adapt(sink);
        while (!sink.cancellationRequested() && spl.tryAdvance(adaptedSink)) { }
    }

    private static LongConsumer adapt(Sink<Long> sink) {
        if (sink instanceof LongConsumer) {
            return (LongConsumer) sink;
        }
        else {
            if (Tripwire.enabled)
                Tripwire.trip(AbstractPipeline.class, "using LongStream.adapt(Sink<Long> s)");
            return sink::accept;
        }
    }

    @Override
    protected NodeFactory<Long> getNodeFactory() {
        return Nodes.getLongNodeFactory();
    }

    //

    @Override
    public Spliterator.OfLong spliterator() {
        return adapt(super.spliterator());
    }

    static Spliterator.OfLong adapt(Spliterator<Long> s) {
        if (s instanceof Spliterator.OfLong) {
            return (Spliterator.OfLong) s;
        }
        else {
            if (Tripwire.enabled)
                Tripwire.trip(AbstractPipeline.class, "using LongStream.adapt(Spliterator<Long> s)");
            throw new UnsupportedOperationException("LongStream.adapt(Spliterator<Long> s)");
        }
    }

    //


    @Override
    public DoubleStream doubles() {
        return chainedToDouble(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.LONG_VALUE,
                               (flags, sink) -> new Sink.ChainedLong(sink) {
                                   @Override
                                   public void accept(long t) {
                                       downstream.accept((double) t);
                                   }
                               });
    }

    @Override
    public LongStream map(LongUnaryOperator mapper) {
        Objects.requireNonNull(mapper);
        return chainedToLong(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.LONG_VALUE,
                             (flags, sink) -> new Sink.ChainedLong(sink) {
                                 @Override
                                 public void accept(long t) {
                                     downstream.accept(mapper.applyAsLong(t));
                                 }
                             });
    }

    @Override
    public <U> Stream<U> map(LongFunction<U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToRef(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.LONG_VALUE,
                            (flags, sink) -> new Sink.ChainedLong(sink) {
                                @Override
                                public void accept(long t) {
                                    downstream.accept(mapper.apply(t));
                                }
                            });
    }

    @Override
    public LongStream flatMap(FlatMapper.OfLongToLong mapper) {
        Objects.requireNonNull(mapper);
        return chainedToLong(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED,
                             StreamShape.LONG_VALUE,
                             (flags, sink) -> new Sink.ChainedLong(sink) {
                                 public void accept(long t) {
                                     mapper.explodeInto(t, (Sink.OfLong) downstream);
                                 }
                             });
    }

    @Override
    public LongStream filter(LongPredicate predicate) {
        Objects.requireNonNull(predicate);
        return chainedToLong(StreamOpFlag.NOT_SIZED, StreamShape.LONG_VALUE,
                             (flags, sink) -> new Sink.ChainedLong(sink) {
                                 @Override
                                 public void accept(long t) {
                                     if (predicate.test(t))
                                         downstream.accept(t);
                                 }
                             });
    }

    @Override
    public LongStream limit(long maxSize) {
        if (maxSize < 0)
            throw new IllegalArgumentException(Long.toString(maxSize));
        return super.slice(0, maxSize);
    }

    @Override
    public LongStream substream(long startOffset) {
        if (startOffset < 0)
            throw new IllegalArgumentException(Long.toString(startOffset));
        if (startOffset == 0)
            return this;
        else
            return super.slice(startOffset, -1);
    }

    @Override
    public LongStream substream(long startOffset, long endOffset) {
        if (startOffset < 0 || endOffset < startOffset)
            throw new IllegalArgumentException(String.format("substream(%d, %d)", startOffset, endOffset));
        return super.slice(startOffset, endOffset - startOffset);
    }

    @Override
    public LongStream peek(LongConsumer consumer) {
        Objects.requireNonNull(consumer);
        return chainedToLong(0, StreamShape.LONG_VALUE,
                             (flags, sink) -> new Sink.ChainedLong(sink) {
                                 @Override
                                 public void accept(long t) {
                                     consumer.accept(t);
                                     downstream.accept(t);
                                 }
                             });
    }

    @Override
    public LongStream sorted() {
        return pipeline(new SortedOp.OfLong());
    }

    @Override
    public void forEach(LongConsumer consumer) {
        pipeline(ForEachOp.make(consumer));
    }

    @Override
    public void forEachUntil(LongConsumer consumer, BooleanSupplier until) {
        pipeline(ForEachUntilOp.make(consumer, until));
    }

    @Override
    public long reduce(long identity, LongBinaryOperator op) {
        return pipeline(FoldOp.makeLong(identity, op));
    }

    @Override
    public OptionalLong reduce(LongBinaryOperator op) {
        return pipeline(FoldOp.makeLong(op));
    }

    @Override
    public <R> R collect(Collector.OfLong<R> collector) {
        return pipeline(FoldOp.makeLong(collector));
    }

    @Override
    public <R> R collectUnordered(Collector.OfLong<R> collector) {
        if (collector.isConcurrent()) {
            R container = collector.resultSupplier().get();
            ObjLongConsumer<R> accumulator = collector.longAccumulator();
            forEach(u -> accumulator.accept(container, u));
            return container;
        }
        else {
            return collect(collector);
        }
    }

    @Override
    public boolean anyMatch(LongPredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.ANY));
    }

    @Override
    public boolean allMatch(LongPredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.ALL));
    }

    @Override
    public boolean noneMatch(LongPredicate predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.NONE));
    }

    @Override
    public OptionalLong findFirst() {
        return pipeline(FindOp.makeLong(true));
    }

    @Override
    public OptionalLong findAny() {
        return pipeline(FindOp.makeLong(false));
    }

    @Override
    public long[] toArray() {
        return ((Node.OfLong) collectOutput(true)).asLongArray();
    }
}
