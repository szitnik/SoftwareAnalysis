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

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * A pipeline of elements that are references to objects of type <code>T</code>.
 *
 * @param <T> Type of elements in the upstream source.
 * @param <U> Type of elements in produced by this stage.
 *
 * @author Brian Goetz
 */
class ReferencePipeline<T, U> extends AbstractPipeline<T, U, Stream<U>> implements Stream<U>  {

    public<S> ReferencePipeline(Supplier<? extends Spliterator<S>> supplier, int sourceFlags) {
        super((Supplier) supplier, sourceFlags);
    }

    public ReferencePipeline(AbstractPipeline<?, T, ?> upstream, IntermediateOp<T, U> op) {
        super(upstream, op);
    }

    @Override
    protected StreamShape getOutputShape() {
        return StreamShape.REFERENCE;
    }

    @Override
    protected <P_IN> Node<U> collect(PipelineHelper<P_IN, U> helper, boolean flattenTree) {
        return NodeUtils.collect(helper, flattenTree);
    }

    @Override
    protected <P_IN> Node<U> flatten(PipelineHelper<P_IN, U> helper, Node<U> node) {
        return NodeUtils.flatten(node, helper.arrayGenerator());
    }

    @Override
    protected <P_IN> Spliterator<U> wrap(PipelineHelper<P_IN, U> ph, Spliterator<P_IN> spliterator, boolean isParallel) {
        return new Spliterators.WrappingSpliterator<>(ph, spliterator, isParallel);
    }

    @Override
    protected Spliterator<U> lazySpliterator(Supplier<Spliterator<U>> supplier) {
        return new Spliterators.DelegatingSpliterator<>(supplier);
    }

    @Override
    protected <E_OUT, S> AbstractPipeline<?, E_OUT, ?> stream(Supplier<Spliterator<S>> supplier, int flags) {
        return new ReferencePipeline<>(supplier, flags);
    }

    @Override
    protected void forEachWithCancel(Spliterator<U> spliterator, Sink<U> sink) {
        while (!sink.cancellationRequested() && spliterator.tryAdvance(sink)) { }
    }

    @Override
    protected NodeFactory<U> getNodeFactory() {
        return Nodes.getNodeFactory();
    }

    //

    @Override
    public Stream<U> filter(Predicate<? super U> predicate) {
        Objects.requireNonNull(predicate);
        return chainedToRef(StreamOpFlag.NOT_SIZED, StreamShape.REFERENCE,
                            (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                @Override
                                public void accept(U u) {
                                    if (predicate.test(u))
                                        downstream.accept(u);
                                }
                            });
    }

    @Override
    public <R> Stream<R> map(Function<? super U, ? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToRef(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.REFERENCE,
                            (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                @Override
                                public void accept(U u) {
                                    downstream.accept(mapper.apply(u));
                                }
                            });
    }

    @Override
    public IntStream map(ToIntFunction<? super U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToInt(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.REFERENCE,
                            (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                @Override
                                public void accept(U u) {
                                    downstream.accept(mapper.applyAsInt(u));
                                }
                            });
    }

    @Override
    public LongStream map(ToLongFunction<? super U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToLong(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.REFERENCE,
                             (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                 @Override
                                 public void accept(U u) {
                                     downstream.accept(mapper.applyAsLong(u));
                                 }
                             });
    }

    @Override
    public DoubleStream map(ToDoubleFunction<? super U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToDouble(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT, StreamShape.REFERENCE,
                               (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                   @Override
                                   public void accept(U u) {
                                       downstream.accept(mapper.applyAsDouble(u));
                                   }
                               });
    }

    @Override
    public <R> Stream<R> flatMap(FlatMapper<? super U, R> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToRef(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED,
                            StreamShape.REFERENCE,
                            (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                public void accept(U u) {
                                    mapper.explodeInto(u, downstream);
                                }
                            });
    }

    @Override
    public IntStream flatMap(FlatMapper.ToInt<? super U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToInt(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED,
                            StreamShape.REFERENCE,
                            (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                public void accept(U u) {
                                    mapper.explodeInto(u, (Sink.OfInt) downstream);
                                }
                            });
    }

    @Override
    public DoubleStream flatMap(FlatMapper.ToDouble<? super U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToDouble(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED,
                               StreamShape.REFERENCE,
                               (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                   public void accept(U u) {
                                       mapper.explodeInto(u, (Sink.OfDouble) downstream);
                                   }
                               });
    }

    @Override
    public LongStream flatMap(FlatMapper.ToLong<? super U> mapper) {
        Objects.requireNonNull(mapper);
        return chainedToLong(StreamOpFlag.NOT_SORTED | StreamOpFlag.NOT_DISTINCT | StreamOpFlag.NOT_SIZED,
                             StreamShape.REFERENCE,
                             (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                 public void accept(U u) {
                                     mapper.explodeInto(u, (Sink.OfLong) downstream);
                                 }
                             });
    }

    @Override
    public Stream<U> distinct() {
        return pipeline(new UniqOp<U>());
    }

    @Override
    public Stream<U> sorted() {
        return pipeline(new SortedOp.OfRef<U>());
    }

    @Override
    public Stream<U> sorted(Comparator<? super U> comparator) {
        return pipeline(new SortedOp.OfRef<>(comparator));
    }

    @Override
    public void forEach(Consumer<? super U> consumer) {
        pipeline(ForEachOp.make(consumer));
    }

    @Override
    public void forEachUntil(Consumer<? super U> consumer, BooleanSupplier until) {
        pipeline(ForEachUntilOp.make(consumer, until));
    }

    @Override
    public Stream<U> peek(Consumer<? super U> tee) {
        Objects.requireNonNull(tee);
        return chainedToRef(0, StreamShape.REFERENCE,
                            (flags, sink) -> new Sink.ChainedReference<U>(sink) {
                                @Override
                                public void accept(U u) {
                                    tee.accept(u);
                                    downstream.accept(u);
                                }
                            });
    }

    @Override
    public Stream<U> limit(long maxSize) {
        if (maxSize < 0)
            throw new IllegalArgumentException(Long.toString(maxSize));
        return super.slice(0, maxSize);
    }

    @Override
    public Stream<U> substream(long startingOffset) {
        if (startingOffset < 0)
            throw new IllegalArgumentException(Long.toString(startingOffset));
        if (startingOffset == 0)
            return this;
        else
            return super.slice(startingOffset, -1);
    }

    @Override
    public Stream<U> substream(long startingOffset, long endingOffset) {
        if (startingOffset < 0 || endingOffset < startingOffset)
            throw new IllegalArgumentException(String.format("substream(%d, %d)", startingOffset, endingOffset));
        return super.slice(startingOffset, endingOffset - startingOffset);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A[] toArray(IntFunction<A[]> generator) {
        // Since A has no relation to U (not possible to declare that A is an upper bound of U)
        // there will be no static type checking.
        // Therefore use a raw type and assume A == U rather than propagating the separation of A and U
        // throughout the code-base.
        // The runtime type of U is never checked for equality with the component type of the runtime type of A[].
        // Runtime checking will be performed when an element is stored in A[], thus if A is not a
        // super type of U an ArrayStoreException will be thrown.
        IntFunction rawGenerator = (IntFunction) generator;
        return (A[]) collectOutput(true, rawGenerator).asArray(rawGenerator);
    }

    @Override
    public boolean anyMatch(Predicate<? super U> predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.ANY));
    }

    @Override
    public boolean allMatch(Predicate<? super U> predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.ALL));
    }

    @Override
    public boolean noneMatch(Predicate<? super U> predicate) {
        return pipeline(MatchOp.match(predicate, MatchOp.MatchKind.NONE));
    }

    @Override
    public Optional<U> findFirst() {
        return pipeline(FindOp.makeRef(true));
    }

    @Override
    public Optional<U> findAny() {
        return pipeline(FindOp.makeRef(false));
    }

    @Override
    public U reduce(final U identity, final BinaryOperator<U> reducer) {
        return pipeline(FoldOp.makeRef(identity, reducer, reducer));
    }

    @Override
    public Optional<U> reduce(BinaryOperator<U> reducer) {
        return pipeline(FoldOp.makeRef(reducer));
    }

    @Override
    public <R> R reduce(R identity, BiFunction<R, ? super U, R> accumulator, BinaryOperator<R> reducer) {
        return pipeline(FoldOp.makeRef(identity, accumulator, reducer));
    }

    @Override
    public <R> R collect(Collector<? super U, R> collector) {
        return pipeline(FoldOp.makeRef(collector));
    }

    @Override
    public <R> R collect(Supplier<R> resultFactory, BiConsumer<R, ? super U> accumulator, BiConsumer<R, R> reducer) {
        return pipeline(FoldOp.makeRef(resultFactory, accumulator, reducer));
    }

    @Override
    public <R> R collectUnordered(Collector<? super U, R> collector) {
        if (collector.isConcurrent()) {
            R container = collector.resultSupplier().get();
            BiConsumer<R, ? super U> accumulator = collector.accumulator();
            forEach(u -> accumulator.accept(container, u));
            return container;
        }
        else {
            return collect(collector);
        }
    }
}
