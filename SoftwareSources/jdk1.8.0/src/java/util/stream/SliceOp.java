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

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CountedCompleter;

/**
 * SliceOp
 *
 * @author Brian Goetz
 */
class SliceOp<T> implements StatefulOp<T> {
    protected final long skip;
    protected final long limit; // -1 means infinite
    protected final StreamShape shape;
    protected final AbstractPipeline.SinkWrapper sinkWrapper;

    public SliceOp(long skip, long limit, StreamShape shape) {
        if (skip < 0)
            throw new IllegalArgumentException("Skip must be non-negative: " + skip);
        this.skip = skip;
        this.limit = limit;
        this.shape = shape;
        switch (shape) {
            case REFERENCE: sinkWrapper = this::wrapSinkRef; break;
            case INT_VALUE: sinkWrapper = this::wrapSinkInt; break;
            case LONG_VALUE: sinkWrapper = this::wrapSinkLong; break;
            case DOUBLE_VALUE: sinkWrapper = this::wrapSinkDouble; break;
            default:
                throw new IllegalStateException("Unknown shape: " + shape);
        }
    }

    public SliceOp(long skip, StreamShape shape) {
        this(skip, -1, shape);
    }

    private<T> Sink<T> wrapSinkRef(int flags, Sink sink) {
        return new Sink.ChainedReference<T>(sink) {
            long n = skip;
            long m = limit >= 0 ? limit : Long.MAX_VALUE;

            @Override
            public void accept(T t) {
                if (n == 0) {
                    if (m > 0) {
                        m--;
                        downstream.accept(t);
                    }
                }
                else {
                    n--;
                }
            }

            @Override
            public boolean cancellationRequested() {
                return m == 0 || downstream.cancellationRequested();
            }
        };
    }

    private Sink<Integer> wrapSinkInt(int flags, Sink sink) {
        return new Sink.ChainedInt(sink) {
            long n = skip;
            long m = limit >= 0 ? limit : Long.MAX_VALUE;

            @Override
            public void accept(int t) {
                if (n == 0) {
                    if (m > 0) {
                        m--;
                        downstream.accept(t);
                    }
                }
                else {
                    n--;
                }
            }

            @Override
            public boolean cancellationRequested() {
                return m == 0 || downstream.cancellationRequested();
            }
        };
    }

    private Sink<Long> wrapSinkLong(int flags, Sink sink) {
        return new Sink.ChainedLong(sink) {
            long n = skip;
            long m = limit >= 0 ? limit : Long.MAX_VALUE;

            @Override
            public void accept(long t) {
                if (n == 0) {
                    if (m > 0) {
                        m--;
                        downstream.accept(t);
                    }
                }
                else {
                    n--;
                }
            }

            @Override
            public boolean cancellationRequested() {
                return m == 0 || downstream.cancellationRequested();
            }
        };
    }

    private Sink<Double> wrapSinkDouble(int flags, Sink sink) {
        return new Sink.ChainedDouble(sink) {
            long n = skip;
            long m = limit >= 0 ? limit : Long.MAX_VALUE;

            @Override
            public void accept(double t) {
                if (n == 0) {
                    if (m > 0) {
                        m--;
                        downstream.accept(t);
                    }
                }
                else {
                    n--;
                }
            }

            @Override
            public boolean cancellationRequested() {
                return m == 0 || downstream.cancellationRequested();
            }
        };
    }

    @Override
    public StreamShape outputShape() {
        return shape;
    }

    @Override
    public StreamShape inputShape() {
        return shape;
    }

    @Override
    public int getOpFlags() {
        return StreamOpFlag.NOT_SIZED | ((limit != -1) ? StreamOpFlag.IS_SHORT_CIRCUIT : 0);
    }

    @Override
    public Sink<T> wrapSink(int flags, Sink sink) {
        return sinkWrapper.wrapSink(flags, sink);
    }

    private long getFinalSize(PipelineHelper helper, Spliterator spliterator) {
        long size = helper.exactOutputSizeIfKnown(spliterator);
        if (size >= 0) {
            size = Math.max(0, size - skip);
            if (limit >= 0)
                size = Math.min(size, limit);
        }
        return size;
    }

    private <S> Node<T> evaluateSequential(PipelineHelper<S, T> helper) {
        // @@@ If the input size is known then it is possible to know the output
        //     size given skip and limit (see getFinalSize).
        //     However it is not possible to make a builder of a fixed size since
        //     it is assumed that fixed size is the same as the known input size.
        Node.Builder<T> nb = helper.getOutputNodeFactory().makeNodeBuilder(-1, helper.arrayGenerator());
        Sink<S> wrappedSink = helper.wrapSink(wrapSink(helper.getStreamAndOpFlags(), nb));
        Spliterator<S> spliterator = helper.sourceSpliterator();

        wrappedSink.begin(spliterator.getExactSizeIfKnown());
        helper.intoWrappedWithCancel(wrappedSink, spliterator);
        wrappedSink.end();

        return nb.build();
    }

    @Override
    public <S> Node<T> evaluateParallel(PipelineHelper<S, T> helper) {
        // Parallel strategy -- two cases
        // IF we have full size information
        // - decompose, keeping track of each leaf's (offset, size)
        // - calculate leaf only if intersection between (offset, size) and desired slice
        // - Construct a Node containing the appropriate sections of the appropriate leaves
        // IF we don't
        // - decompose, and calculate size of each leaf
        // - on complete of any node, compute completed initial size from the root, and if big enough, cancel later nodes
        // - @@@ this can be significantly improved

        // @@@ Currently we don't do the sized version at all

        // @@@ Should take into account ORDERED flag; if not ORDERED, we can limit in temporal order instead

//        Spliterator<S> spliterator = helper.spliterator();
//        int size = spliterator.getSizeIfKnown();
//        if (size >= 0 && helper.getOutputSizeIfKnown() == size && spliterator.isPredictableSplits())
//            return helper.invoke(new SizedSliceTask<>(helper, skip, getFinalSize(helper, spliterator)));
//        else
              return new SliceTask<>(this, helper, skip, limit).invoke();
    }

    @Override
    public String toString() {
        return String.format("SliceOp[skip=%d,limit=%d]", skip, limit);
    }

    private static class SliceTask<S, T> extends AbstractShortCircuitTask<S, T, Node<T>, SliceTask<S, T>> {
        private final SliceOp<T> op;
        private final long targetOffset, targetSize;
        private long thisNodeSize;

        private volatile boolean completed;

        private SliceTask(SliceOp<T> op, PipelineHelper<S, T> helper, long offset, long size) {
            super(helper);
            this.op = op;
            this.targetOffset = offset;
            this.targetSize = size;
        }

        private SliceTask(SliceTask<S, T> parent, Spliterator<S> spliterator) {
            super(parent, spliterator);
            this.op = parent.op;
            this.targetOffset = parent.targetOffset;
            this.targetSize = parent.targetSize;
        }

        @Override
        protected SliceTask<S, T> makeChild(Spliterator<S> spliterator) {
            return new SliceTask<>(this, spliterator);
        }

        @Override
        protected final Node<T> getEmptyResult() {
            return helper.getOutputNodeFactory().emptyNode();
        }

        @Override
        protected final Node<T> doLeaf() {
            if (isRoot()) {
                return op.evaluateSequential(helper);
            }
            else {
                Node<T> node = helper.into(helper.getOutputNodeFactory().makeNodeBuilder(-1, helper.arrayGenerator()),
                                           spliterator).build();
                thisNodeSize = node.count();
                completed = true;
                return node;
            }
        }

        @Override
        public final void onCompletion(CountedCompleter<?> caller) {
            if (!isLeaf()) {
                thisNodeSize = 0;
                for (SliceTask<S, T> child = children; child != null; child = child.nextSibling)
                    thisNodeSize += child.thisNodeSize;
                completed = true;

                if (isRoot()) {
                    // Only collect nodes once absolute size information is known

                    ArrayList<Node<T>> nodes = new ArrayList<>();
                    visit(nodes, 0);
                    Node<T> result;
                    if (nodes.size() == 0)
                        result = helper.getOutputNodeFactory().emptyNode();
                    else if (nodes.size() == 1)
                        result = nodes.get(0);
                    else
                        // This will create a tree of depth 1 and will not be a sub-tree
                        // for leaf nodes within the require range
                        result = helper.getOutputNodeFactory().concNodes(nodes);
                    setLocalResult(result);
                }
            }
            if (targetSize >= 0) {
                if (((SliceTask<S,T>) getRoot()).leftSize() >= targetOffset + targetSize)
                    cancelLaterNodes();
            }
        }

        private long leftSize() {
            if (completed)
                return thisNodeSize;
            else if (isLeaf())
                return 0;
            else {
                long leftSize = 0;
                for (SliceTask<S, T> child = children; child != null; child = child.nextSibling) {
                    if (child.completed)
                        leftSize += child.thisNodeSize;
                    else {
                        leftSize += child.leftSize();
                        break;
                    }
                }
                return leftSize;
            }
        }

        private void visit(List<Node<T>> results, int offset) {
            if (!isLeaf()) {
                for (SliceTask<S, T> child = children; child != null; child = child.nextSibling) {
                    child.visit(results, offset);
                    offset += child.thisNodeSize;
                }
            }
            else {
                if (results.size() == 0) {
                    if (offset + thisNodeSize >= targetOffset)
                        results.add(truncateNode(getLocalResult(),
                                                 Math.max(0, targetOffset - offset),
                                                 targetSize >= 0 ? Math.max(0, offset + thisNodeSize - (targetOffset + targetSize)) : 0));
                }
                else {
                    if (targetSize == -1 || offset < targetOffset + targetSize) {
                        results.add(truncateNode(getLocalResult(),
                                                 0,
                                                 targetSize >= 0 ? Math.max(0, offset + thisNodeSize - (targetOffset + targetSize)) : 0));
                    }
                }
            }
        }

        private Node<T> truncateNode(Node<T> input, long skipLeft, long skipRight) {
            if (skipLeft == 0 && skipRight == 0)
                return input;
            else {
                switch (input.getShape()) {
                    case REFERENCE:
                        return Nodes.truncateNode(input, skipLeft, thisNodeSize - skipRight, helper.arrayGenerator());
                    case INT_VALUE:
                        return (Node<T>) Nodes.truncateIntNode((Node.OfInt) input, skipLeft, thisNodeSize - skipRight);
                    case LONG_VALUE:
                        return (Node<T>) Nodes.truncateLongNode((Node.OfLong) input, skipLeft, thisNodeSize - skipRight);
                    case DOUBLE_VALUE:
                        return (Node<T>) Nodes.truncateDoubleNode((Node.OfDouble) input, skipLeft, thisNodeSize - skipRight);
                    default:
                        throw new IllegalStateException("Unknown shape " + input.getShape());
                }
            }
        }
    }

    // @@@ Currently unused -- optimization for when all sizes are known
//    private static class SizedSliceTask<S, T> extends AbstractShortCircuitTask<S, T, Node<T>, SizedSliceTask<S, T>> {
//        private final int targetOffset, targetSize;
//        private final int offset, size;
//
//        private SizedSliceTask(ParallelPipelineHelper<S, T> helper, int offset, int size) {
//            super(helper);
//            targetOffset = offset;
//            targetSize = size;
//            this.offset = 0;
//            this.size = spliterator.getSizeIfKnown();
//        }
//
//        private SizedSliceTask(SizedSliceTask<S, T> parent, Spliterator<S> spliterator) {
//            // Makes assumptions about order in which siblings are created and linked into parent!
//            super(parent, spliterator);
//            targetOffset = parent.targetOffset;
//            targetSize = parent.targetSize;
//            int siblingSizes = 0;
//            for (SizedSliceTask<S, T> sibling = parent.children; sibling != null; sibling = sibling.nextSibling)
//                siblingSizes += sibling.size;
//            size = spliterator.getSizeIfKnown();
//            offset = parent.offset + siblingSizes;
//        }
//
//        @Override
//        protected SizedSliceTask<S, T> makeChild(Spliterator<S> spliterator) {
//            return new SizedSliceTask<>(this, spliterator);
//        }
//
//        @Override
//        protected Node<T> getEmptyResult() {
//            return Nodes.emptyNode();
//        }
//
//        @Override
//        public boolean taskCancelled() {
//            if (offset > targetOffset+targetSize || offset+size < targetOffset)
//                return true;
//            else
//                return super.taskCancelled();
//        }
//
//        @Override
//        protected Node<T> doLeaf() {
//            int skipLeft = Math.max(0, targetOffset - offset);
//            int skipRight = Math.max(0, offset + size - (targetOffset + targetSize));
//            if (skipLeft == 0 && skipRight == 0)
//                return helper.into(Nodes.<T>makeBuilder(spliterator.getSizeIfKnown())).build();
//            else {
//                // If we're the first or last node that intersects the target range, peel off irrelevant elements
//                int truncatedSize = size - skipLeft - skipRight;
//                NodeBuilder<T> builder = Nodes.<T>makeBuilder(truncatedSize);
//                Sink<S> wrappedSink = helper.wrapSink(builder);
//                wrappedSink.begin(truncatedSize);
//                Iterator<S> iterator = spliterator.iterator();
//                for (int i=0; i<skipLeft; i++)
//                    iterator.next();
//                for (int i=0; i<truncatedSize; i++)
//                    wrappedSink.apply(iterator.next());
//                wrappedSink.end();
//                return builder.build();
//            }
//        }
//
//        @Override
//        public void onCompletion(CountedCompleter<?> caller) {
//            if (!isLeaf()) {
//                Node<T> result = null;
//                for (SizedSliceTask<S, T> child = children.nextSibling; child != null; child = child.nextSibling) {
//                    Node<T> childResult = child.getRawResult();
//                    if (childResult == null)
//                        continue;
//                    else if (result == null)
//                        result = childResult;
//                    else
//                        result = Nodes.node(result, childResult);
//                }
//                setRawResult(result);
//                if (offset <= targetOffset && offset+size >= targetOffset+targetSize)
//                    shortCircuit(result);
//            }
//        }
//    }

}
