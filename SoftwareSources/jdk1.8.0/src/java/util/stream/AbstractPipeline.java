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
import java.util.Spliterator;
import java.util.function.Supplier;
import java.util.function.IntFunction;

/**
 * The abstract pipeline implementation from which concrete implementations extend from.
 * <p>
 * Evaluates the pipeline to one of the following: a result for given a terminal operation; to a Node; to an Iterator
 * or; to a Spliterator.
 * </p>
 * <p>
 * An operation may be chained to the end this pipeline. The pipeline chain is a linear chain, only one operation
 * may be chained, otherwise an {@link IllegalStateException} is thrown.
 * </p>
 * <p>
 * For a parallel stream the chaining of a stateful operation results in the creation of a new pipeline chain.
 * The source to this new chain is a supplier of a spliterator that is the delayed evaluation of the stateful
 * operation and this pipeline to a Node from which the spliterator is obtained.
 * </p>
 *
 * @param <E_IN>  Type of input elements.
 * @param <E_OUT> Type of output elements.
 * @author Brian Goetz
 */
abstract class AbstractPipeline<E_IN, E_OUT, S extends BaseStream<E_OUT, S>> /* implements BaseStream */ {
    protected final AbstractPipeline upstream;
    protected final int depth;
    protected final IntermediateOp op;
    protected final int combinedSourceAndOpFlags;

    // Shared between all stages of pipeline
    private final Supplier<? extends Spliterator<?>> source;

    private static enum PipelineState {
        UNLINKED() {
            @Override
            PipelineState transitionTo(PipelineState newState) {
                assert newState != UNLINKED;
                return newState;
            }
        },
        LINKED() {
            @Override
            PipelineState transitionTo(PipelineState newState) {
                throw new IllegalStateException("Stream is already linked to a child stream");
            }
        },
        CONSUMED() {
            @Override
            PipelineState transitionTo(PipelineState newState) {
                throw new IllegalStateException("Stream source is already consumed");
            }
        };

        abstract PipelineState transitionTo(PipelineState newState);
    }

    private PipelineState state = PipelineState.UNLINKED;

    private AbstractPipeline(AbstractPipeline upstream,
                             int depth,
                             IntermediateOp op,
                             int combinedSourceAndOpFlags,
                             Supplier<? extends Spliterator<?>> source) {
        this.upstream = upstream;
        this.depth = depth;
        this.op = op;
        this.combinedSourceAndOpFlags = combinedSourceAndOpFlags;
        this.source = source;
    }

    /**
     * Constructor for the element source of a pipeline.
     */
    protected AbstractPipeline(Supplier<Spliterator<?>> source, int sourceFlags) {
        this(null, 0, null,
             StreamOpFlag.combineOpFlags(sourceFlags, StreamOpFlag.INITIAL_OPS_VALUE),
             Objects.requireNonNull(source));
    }

    /**
     * Constructor for a pipeline operation.
     *
     * @param upstream the upstream element source.
     * @param op the operation performed upon elements.
     */
    protected AbstractPipeline(AbstractPipeline<?, E_IN, ?> upstream, IntermediateOp<E_IN, E_OUT> op) {
        this(upstream, upstream.depth + 1,
             op, StreamOpFlag.combineOpFlags(op.getOpFlags() & StreamOpFlag.OP_MASK,
                                             upstream.combinedSourceAndOpFlags),
             upstream.source);

        assert !op.isStateful() || !isParallel();
        assert getOutputShape() == op.outputShape();
        assert upstream.getOutputShape() == op.inputShape();

        upstream.transitionTo(PipelineState.LINKED);
    }

    private void transitionTo(PipelineState newSate) {
        state = state.transitionTo(newSate);
    }

    private static <T> IntFunction<T[]> objectArrayGenerator() {
        return size -> (T[]) new Object[size];
    }

    // Chaining and result methods

    /**
     * Chain an operation to the tail of this pipeline to create a new stream.
     *
     * @param newOp the operation to chain.
     * @param <E_NEXT> the type of elements output from the new stream.
     * @param <S_NEXT> the type of stream.     * @return the new stream.
     */
    @SuppressWarnings("unchecked")
    public <E_NEXT, S_NEXT extends BaseStream<E_NEXT, S_NEXT>> S_NEXT pipeline(IntermediateOp<E_OUT, E_NEXT> newOp) {
        if (newOp.isStateful() && isParallel()) {
            assert getOutputShape() == newOp.inputShape();
            transitionTo(PipelineState.LINKED);

            // @@@ the newFlags and the node.spliterator().characteristics() will be out of sync
            //     If a stream.spliterator() is obtained then what guarantees should be made about
            //     the characteristics?
            int newFlags = StreamOpFlag.toStreamFlags(
                    StreamOpFlag.combineOpFlags(newOp.getOpFlags() & StreamOpFlag.OP_MASK, combinedSourceAndOpFlags));
            return (S_NEXT) stream(
                    new NodeSpliteratorSupplier<E_NEXT>() {
                        Node<E_NEXT> n = null;

                        @Override
                        public Node<E_NEXT> getNode(PipelineHelperImpl downstream) {
                            if (n == null) {
                                n = newOp.evaluateParallel(new PipelineHelperImpl(downstream));
                            }
                            return n;
                        }
                    },
                    newFlags | StreamOpFlag.IS_SIZED);
        }
        else
            return (S_NEXT) chain(this, newOp);
    }

    /** Specialized version of pipeline for stateless reference-bearing intermediate ops */
    protected<V> Stream<V> chainedToRef(int opFlags, StreamShape inputShape, SinkWrapper<E_OUT> sinkWrapper) {
        return new ReferencePipeline<>(this, new StatelessOp<E_OUT, V>(opFlags, inputShape, StreamShape.REFERENCE,
                                                                       sinkWrapper));
    }

    /** Specialized version of pipeline for stateless int-bearing intermediate ops */
    protected IntStream chainedToInt(int opFlags, StreamShape inputShape, SinkWrapper<E_OUT> sinkWrapper) {
        return new IntPipeline<>(this, new StatelessOp<E_OUT, Integer>(opFlags, inputShape, StreamShape.INT_VALUE,
                                                                       sinkWrapper));
    }

    /** Specialized version of pipeline for stateless long-bearing intermediate ops */
    protected LongStream chainedToLong(int opFlags, StreamShape inputShape, SinkWrapper<E_OUT> sinkWrapper) {
        return new LongPipeline<>(this, new StatelessOp<E_OUT, Long>(opFlags, inputShape, StreamShape.LONG_VALUE,
                                                                     sinkWrapper));
    }

    /** Specialized version of pipeline for stateless double-bearing intermediate ops */
    protected DoubleStream chainedToDouble(int opFlags, StreamShape inputShape, SinkWrapper<E_OUT> sinkWrapper) {
        return new DoublePipeline<>(this, new StatelessOp<E_OUT, Double>(opFlags, inputShape,
                                                                         StreamShape.DOUBLE_VALUE, sinkWrapper));
    }

    /**
     * Evaluate the pipeline with a terminal operation to produce a result.
     *
     * @param terminalOp the terminal operation to be applied to the pipeline.
     * @param <R> the type of result.
     * @return the result.
     */
    public <R> R pipeline(TerminalOp<E_OUT, R> terminalOp) {
        return pipeline(terminalOp, AbstractPipeline.objectArrayGenerator());
    }

    private <R> R pipeline(TerminalOp<E_OUT, R> terminalOp, IntFunction<E_OUT[]> generator) {
        assert getOutputShape() == terminalOp.inputShape();
        transitionTo(PipelineState.CONSUMED);

        PipelineHelperImpl<E_IN> helper = new PipelineHelperImpl<>(terminalOp, generator);
        return isParallel() ? terminalOp.evaluateParallel(helper) : terminalOp.evaluateSequential(helper);
    }

    /**
     * Collect the elements output from the pipeline.
     *
     * @param flatten if true the Node returned will contain no children,
     *                otherwise the Node represents the root in a tree that
     *                reflects the computation tree.
     * @return a node that holds the collected output elements.
     */
    @SuppressWarnings("unchecked")
    public Node<E_OUT> collectOutput(boolean flatten) {
        return collectOutput(flatten, AbstractPipeline.objectArrayGenerator());
    }

    /**
     * Collect the elements output from the pipeline.
     *
     * @param flatten if true the Node returned will contain no children,
     *                otherwise the Node represents the root in a tree that
     *                reflects the computation tree.
     * @param generator the array generator to be used to create array instances.
     * @return a node that holds the collected output elements.
     */
    public Node<E_OUT> collectOutput(boolean flatten, IntFunction<E_OUT[]> generator) {
        return pipeline(
                new TerminalOp<E_OUT, Node<E_OUT>>() {
                    @Override
                    public StreamShape inputShape() {
                        return getOutputShape();
                    }

                    @Override
                    public <P_IN> Node<E_OUT> evaluateParallel(PipelineHelper<P_IN, E_OUT> helper) {
                        return helper.collectOutput(flatten);
                    }

                    @Override
                    public <P_IN> Node<E_OUT> evaluateSequential(PipelineHelper<P_IN, E_OUT> helper) {
                        return helper.collectOutput(flatten);
                    }
                },
                generator);
    }

    protected S slice(long skip, long limit) {
        // @@@ Optimize for case where depth=0 or pipeline is size-preserving
        return pipeline(new SliceOp<E_OUT>(skip, limit, getOutputShape()));
    }

    public S sequential() {
        if (isParallel()) {
            return pipeline(new StatefulOp<E_OUT>() {
                @Override
                public StreamShape outputShape() {
                    return getOutputShape();
                }

                @Override
                public StreamShape inputShape() {
                    return getOutputShape();
                }

                @Override
                public int getOpFlags() {
                    return StreamOpFlag.NOT_PARALLEL;
                }

                @Override
                public Sink<E_OUT> wrapSink(int flags, Sink<E_OUT> sink) {
                    return sink;
                }

                @Override
                public <P_IN> Node<E_OUT> evaluateParallel(PipelineHelper<P_IN, E_OUT> helper) {
                    return helper.collectOutput(false);
                }
            });
        } else {
            return (S) this;
        }
    }

    public S parallel() {
        if (isParallel()) {
            return (S) this;
        } else {
            transitionTo(PipelineState.LINKED);

            return (S) stream(spliteratorSupplier(), getStreamFlags() | StreamOpFlag.IS_PARALLEL);
        }
    }

    private Supplier<Spliterator<E_OUT>> spliteratorSupplier() {
        // @@@ Should we query the spliterator to see if it is splittable, and if not, get iterator and wrap?
        if (depth == 0) {
            return (Supplier<Spliterator<E_OUT>>) source;
        }
        else {
            return new UpstreamSpliteratorSupplier<E_OUT>() {
                @Override
                Spliterator<E_OUT> get(PipelineHelperImpl downstream) {
                    PipelineHelperImpl<E_IN> ph = new PipelineHelperImpl<>(downstream);
                    return AbstractPipeline.this.wrap(ph, ph.sourceSpliterator(), ph.isParallel());
                }
            };
        }
    }

    /**
     * Get the output shape of the pipeline.
     *
     * @return the output shape. If the pipeline is the head then it's output shape corresponds to the shape of the
     * source. Otherwise, it's output shape corresponds to the output shape of the associated operation.
     */
    protected abstract StreamShape getOutputShape();

    /**
     * Collect elements output from a pipeline into Node that holds elements of this shape.
     *
     * @param helper the parallel pipeline helper from which elements are obtained.
     * @param flattenTree true of the returned node should be flattened to one node holding an
     *                    array of elements.
     * @param <P_IN> the type of elements input into the pipeline.
     * @return the node holding elements output from the pipeline.
     */
    protected abstract<P_IN> Node<E_OUT> collect(PipelineHelper<P_IN, E_OUT> helper, boolean flattenTree);

    /**
     * Flatten a node.
     *
     * @param node the node to flatten.
     * @return the flattened node.
     */
    protected abstract <P_IN> Node<E_OUT> flatten(PipelineHelper<P_IN, E_OUT> helper, Node<E_OUT> node);

    /**
     * Create a spliterator that wraps a source spliterator, compatible with this stream shape,
     * and operations associated with a {@link PipelineHelper}.
     *
     * @param ph the pipeline helper.
     * @param spliterator
     * @return the wrapping spliterator compatible with this shape.
     */
    protected abstract<P_IN> Spliterator<E_OUT> wrap(PipelineHelper<P_IN, E_OUT> ph, Spliterator<P_IN> spliterator, boolean isParallel);

    /**
     * Create a lazy spliterator that wraps and obtains the supplied the spliterator
     * when method is invoked on the lazy spliterator.
     *
     */
    protected abstract Spliterator<E_OUT> lazySpliterator(Supplier<Spliterator<E_OUT>> supplier);

    /**
     * Create a pipeline from a spliterator compatible with this stream shape.
     *
     * @param supplier a Supplier for the compatible spliterator
     * @param flags the stream flags.
     * @return the pipeline, whose content is sourced from the spliterator.
     * @throws IllegalArgumentException if the spliterator is not compatible with this stream shape.
     */
    protected abstract <T, S> AbstractPipeline<?, T, ?> stream(Supplier<Spliterator<S>> supplier, int flags);

    /**
     * Traverse elements of a spliterator, compatible with this stream shape, pushing those elements into a sink.
     * <p>If the sink is cancelled no further elements will be pulled or pushed and this method will return.</p>
     *
     * @param spliterator the spliterator to pull elements from
     * @param sink the sink to push elements to.
     */
    protected abstract void forEachWithCancel(Spliterator<E_OUT> spliterator, Sink<E_OUT> sink);

    /**
     * Obtain the node factory for creating nodes compatible with this stream shape.
     *
     * @return the node factory.
     */
    protected abstract NodeFactory<E_OUT> getNodeFactory();

    /**
     * Create a new pipeline by chaining an intermediate operation to an upstream pipeline.
     * <p>
     * The output shape if the upstream pipeline must be the same as the input shape of
     * the intermediate operation.
     * </p>
     * @param upstream the upstream pipeline.
     * @param op the intermediate operation.
     * @param <U> the type of elements output from the upstream pipeline and input to the new stream.
     * @param <V> the type of elements output from the new pipeline.
     * @return a the new pipeline.
     */
    static <U, V> AbstractPipeline<U, V, ?> chain(AbstractPipeline<?, U, ?> upstream, IntermediateOp<U, V> op) {
        switch (op.outputShape()) {
            case REFERENCE:    return new ReferencePipeline<>(upstream, op);
            case INT_VALUE:    return new IntPipeline(upstream, op);
            case LONG_VALUE:   return new LongPipeline(upstream, op);
            case DOUBLE_VALUE: return new DoublePipeline(upstream, op);
            default: throw new IllegalStateException("Unknown shape: " + op.outputShape());
        }
    }

    // BaseStream

    // from BaseStream
    @SuppressWarnings("unchecked")
    public Spliterator<E_OUT> spliterator() {
        transitionTo(PipelineState.CONSUMED);

        return lazySpliterator(spliteratorSupplier());
    }

    // from BaseStream
    public int getStreamFlags() {
        return StreamOpFlag.toStreamFlags(combinedSourceAndOpFlags);
    }

    // from BaseStream
    public boolean isParallel() {
        return StreamOpFlag.PARALLEL.isKnown(combinedSourceAndOpFlags);
    }

    interface SinkWrapper<T> {
        public Sink<T> wrapSink(int flags, Sink sink);
    }

    static class StatelessOp<T,U> implements IntermediateOp<T,U> {
        private final int opFlags;
        private final StreamShape inputShape, outputShape;
        private final SinkWrapper<T> sinkWrapper;

        StatelessOp(int opFlags, StreamShape inputShape, StreamShape outputShape, SinkWrapper<T> wrapper) {
            this.opFlags = opFlags;
            this.inputShape = inputShape;
            this.outputShape = outputShape;
            sinkWrapper = wrapper;
        }

        public StreamShape outputShape() {
            return outputShape;
        }

        public StreamShape inputShape() {
            return inputShape;
        }

        public int getOpFlags() {
            return opFlags;
        }

        @Override
        public Sink<T> wrapSink(int flags, Sink<U> sink) {
            return sinkWrapper.wrapSink(flags, sink);
        }
    }

    abstract class UpstreamSpliteratorSupplier<T> implements Supplier<Spliterator<T>> {
        @Override
        public Spliterator<T> get() {
            // This may occur when a spliterator is obtained for a pipeline of zero depth
            return get(null);
        }

        abstract Spliterator<T> get(PipelineHelperImpl downstream);
    }

    abstract class NodeSpliteratorSupplier<T> extends UpstreamSpliteratorSupplier<T> {

        @Override
        public Spliterator<T> get(PipelineHelperImpl downstream) {
            return getNode(downstream).spliterator();
        }

        abstract Node<T> getNode(PipelineHelperImpl downstream);
    }

    class PipelineHelperImpl<P_IN> implements PipelineHelper<P_IN, E_OUT> {
        final int terminalFlags;
        final int streamAndOpFlags;
        final IntFunction<E_OUT[]> generator;

        // Source spliterator
        Spliterator<P_IN> spliterator;

        private PipelineHelperImpl(int terminalFlags, IntFunction<E_OUT[]> generator) {
            // If a sequential pipeline then unset the clearing of ORDERED
            // @@@ Should a general mask be used e.g. TERMINAL_OP_SEQUENTIAL_MASK
            this.terminalFlags = isParallel() ? terminalFlags : terminalFlags & ~StreamOpFlag.ORDERED.clear();
            this.streamAndOpFlags = StreamOpFlag.combineOpFlags(this.terminalFlags, combinedSourceAndOpFlags);
            this.generator = generator;
        }

        PipelineHelperImpl(PipelineHelperImpl downstream) {
            // If downstream == null if there is no downstream pipeline and this helper encapsulates
            // the terminal pipeline for a spliterator() terminal operation

            // If the depth is zero then propagate back the generator
            // Since stateful ops have the same input and output type it is guaranteed
            // that the array generator will generate instances of the correct array type
            // (assuming no user-error).
            this(downstream != null ? downstream.terminalFlags() & StreamOpFlag.UPSTREAM_TERMINAL_OP_MASK : 0,
                 downstream != null && downstream.depth() == 0 ? downstream.arrayGenerator() : AbstractPipeline.objectArrayGenerator());
        }

        PipelineHelperImpl(TerminalOp terminalOp, IntFunction<E_OUT[]> generator) {
            this(terminalOp.getOpFlags() & StreamOpFlag.TERMINAL_OP_MASK,
                 generator);
        }

        boolean isTraversing() {
            return spliterator != null;
        }

        Node<E_OUT> getSourceNodeIfAvailable() {
            if (depth == 0 && source instanceof NodeSpliteratorSupplier) {
                return ((NodeSpliteratorSupplier<E_OUT>) source).getNode(this);
            }
            else {
                return null;
            }
        }

        int depth() {
            return depth;
        }

        int terminalFlags() {
            return terminalFlags;
        }

        @Override
        public long exactOutputSizeIfKnown(Spliterator<P_IN> spliterator) {
            return StreamOpFlag.SIZED.isKnown(getStreamAndOpFlags()) ? spliterator.getExactSizeIfKnown() : -1;
        }

        @Override
        public<S extends Sink<E_OUT>> S into(S sink, Spliterator<P_IN> spliterator) {
            intoWrapped(wrapSink(Objects.requireNonNull(sink)), spliterator);
            return sink;
        }

        @Override
        public void intoWrapped(Sink<P_IN> wrappedSink, Spliterator<P_IN> spliterator) {
            Objects.requireNonNull(wrappedSink);

            wrappedSink.begin(spliterator.getExactSizeIfKnown());
            if (!StreamOpFlag.SHORT_CIRCUIT.isKnown(getStreamAndOpFlags())) {
                spliterator.forEach(wrappedSink);
            }
            else {
                intoWrappedWithCancel(wrappedSink, spliterator);
            }
            wrappedSink.end();
        }

        @Override
        public void intoWrappedWithCancel(Sink<P_IN> wrappedSink, Spliterator<P_IN> spliterator) {
            AbstractPipeline p = AbstractPipeline.this;
            while (p.depth > 0) {
                p = p.upstream;
            }
            p.forEachWithCancel(spliterator, wrappedSink);
        }

        @Override
        public StreamShape getInputShape() {
            AbstractPipeline p = AbstractPipeline.this;
            while (p.depth > 0) {
                p = p.upstream;
            }
            return p.getOutputShape();
        }

        @Override
        public StreamShape getOutputShape() {
            return AbstractPipeline.this.getOutputShape();
        }

        @Override
        public int getStreamAndOpFlags() {
            return streamAndOpFlags;
        }

        @Override
        public int getTerminalOpFlags() {
            return terminalFlags;
        }

        @Override
        public Spliterator<P_IN> sourceSpliterator() {
            if (spliterator == null) {
                spliterator = (source instanceof UpstreamSpliteratorSupplier)
                              ? ((UpstreamSpliteratorSupplier<P_IN>) source).get(this)
                              : (Spliterator<P_IN>) source.get();
            }
            return spliterator;
        }

        @Override
        public Sink<P_IN> wrapSink(Sink sink) {
            Objects.requireNonNull(sink);

            int upstreamTerminalFlags = terminalFlags & StreamOpFlag.UPSTREAM_TERMINAL_OP_MASK;
            for (AbstractPipeline p=AbstractPipeline.this; p.depth > 0; p=p.upstream) {
                sink = p.op.wrapSink(StreamOpFlag.combineOpFlags(upstreamTerminalFlags, p.upstream.combinedSourceAndOpFlags), sink);
            }
            return sink;
        }

        @Override
        public NodeFactory<E_OUT> getOutputNodeFactory() {
            return AbstractPipeline.this.getNodeFactory();
        }

        @Override
        public boolean isParallel() {
            return AbstractPipeline.this.isParallel();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Node<E_OUT> collectOutput(boolean flatten) {
            if (isTraversing())
                throw new IllegalStateException();

            Node<E_OUT> n = getSourceNodeIfAvailable();
            if (n != null) {
                return AbstractPipeline.this.flatten(this, n);
            } else {
                if (isParallel()) {
                    return AbstractPipeline.this.collect(this, flatten);
                }
                else {
                    Node.Builder<E_OUT> nb = getOutputNodeFactory().makeNodeBuilder(
                            exactOutputSizeIfKnown(sourceSpliterator()),
                            arrayGenerator());
                    return into(nb, sourceSpliterator()).build();
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public IntFunction<E_OUT[]> arrayGenerator() {
            return generator;
        }
    }
}
