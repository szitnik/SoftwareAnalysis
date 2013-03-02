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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;

import static java.util.stream.Collectors.toStringJoiner;

final class Nodes {

    private Nodes() {
        throw new Error("no instances");
    }

    public static<T> Node<T> emptyNode() {
        return (Node<T>) EMPTY_NODE;
    }

    public static<T> Node<T> node(final T[] array) {
        return new ArrayNode<>(array);
    }

    public static<T> Node<T> node(Collection<T> c) {
        return new CollectionNode<>(c);
    }

    public static<T> Node<T> truncateNode(Node<T> n, long from, long to, IntFunction<T[]> generator) {
        long size = truncatedSize(n.count(), from, to);
        if (size == 0)
            return emptyNode();

        Spliterator<T> spliterator = n.spliterator();
        Node.Builder<T> nodeBuilder = Nodes.makeBuilder(size, generator);
        nodeBuilder.begin(size);
        for (int i = 0; i < from && spliterator.tryAdvance(e -> { }); i++) { }
        for (int i = 0; (i < size) && spliterator.tryAdvance(nodeBuilder); i++) { }
        nodeBuilder.end();
        return nodeBuilder.build();
    }

    private static long truncatedSize(long size, long from, long to) {
        if (from >= 0)
            size = Math.max(0, size - from);
        long limit = to - from;
        if (limit >= 0)
            size = Math.min(size, limit);
        return size;
    }

    /**
     * Make a fixed size node builder of known size, unless the size is negative, in which case make a variable size
     * node builder.
     *
     *
     * @param size the known size, or -1 if the size is not known.
     * @return the node builder.
     */
    public static <T> Node.Builder<T> makeBuilder(long size, IntFunction<T[]> generator) {
        return (size >= 0 && size < Streams.MAX_ARRAY_SIZE) ? makeFixedSizeBuilder(size, generator)
                                                            : makeVariableSizeBuilder();
    }

    /**
     * Make a fixed size builder.
     *
     *
     * @param size the fixed size of the builder.
     * @return the node builder.
     */
    public static <T> Node.Builder<T> makeFixedSizeBuilder(long size, IntFunction<T[]> generator) {
        assert size < Streams.MAX_ARRAY_SIZE;
        return new FixedNodeBuilder<>(size, generator);
    }

    /**
     * Make a variable size node builder.
     *
     * @return the node builder.
     */
    public static <T> Node.Builder<T> makeVariableSizeBuilder() {
        return new SpinedNodeBuilder<>();
    }

    @SafeVarargs
    public static<T> Node<T> node(Node<T>... nodes) {
        Objects.requireNonNull(nodes);
        if (nodes.length < 2) {
            // @@@ The signature could be (Node<T> n1, Node<T> n2, Node<T>... rest)
            //     but requires more work to create the final array
            throw new IllegalArgumentException("The number of nodes must be > 1");
        }
        return new ConcNode<>(nodes);
    }

    // Int nodes

    public static Node.OfInt emptyIntNode() {
        return EMPTY_INT_NODE;
    }

    public static Node.OfInt intNode(final int[] array) {
        return new IntArrayNode(array);
    }

    @SafeVarargs
    public static Node.OfInt intNode(Node.OfInt... nodes) {
        Objects.requireNonNull(nodes);
        if (nodes.length > 1) {
            return new IntConcNode(nodes);
        }
        else if (nodes.length == 1) {
            return nodes[0];
        }
        else {
            return emptyIntNode();
        }
    }

    public static Node.OfInt truncateIntNode(Node.OfInt n, long from, long to) {
        long size = truncatedSize(n.count(), from, to);
        if (size == 0)
            return emptyIntNode();

        Spliterator.OfInt spliterator = n.spliterator();
        Node.Builder.OfInt nodeBuilder = Nodes.intMakeBuilder(size);
        nodeBuilder.begin(size);
        for (int i = 0; i < from && spliterator.tryAdvance((IntConsumer) e -> { }); i++) { }
        for (int i = 0; (i < size) && spliterator.tryAdvance((IntConsumer) nodeBuilder); i++) { }
        nodeBuilder.end();
        return nodeBuilder.build();
    }

    /**
     * Make a fixed size node builder of known size, unless the size is negative, in which case make a variable size
     * node builder.
     *
     * @param size the known size, or -1 if the size is not known.
     * @return the node builder.
     */
    public static Node.Builder.OfInt intMakeBuilder(long size) {
        return (size >= 0 && size < Streams.MAX_ARRAY_SIZE) ? intMakeFixedSizeBuilder(size)
                                                            : intMakeVariableSizeBuilder();
    }

    /**
     * Make a fixed size builder.
     *
     * @param size the fixed size of the builder.
     * @return the node builder.
     */
    public static Node.Builder.OfInt intMakeFixedSizeBuilder(long size) {
        assert size < Streams.MAX_ARRAY_SIZE;
        return new IntFixedNodeBuilder(size);
    }

    /**
     * Make a variable size node builder.
     *
     * @return the node builder.
     */
    public static Node.Builder.OfInt intMakeVariableSizeBuilder() {
        return new IntSpinedNodeBuilder();
    }

    // Long nodes

    public static Node.OfLong emptyLongNode() {
        return EMPTY_LONG_NODE;
    }

    public static Node.OfLong longNode(final long[] array) {
        return new LongArrayNode(array);
    }

    @SafeVarargs
    public static Node.OfLong longNode(Node.OfLong... nodes) {
        Objects.requireNonNull(nodes);
        if (nodes.length > 1) {
            return new LongConcNode(nodes);
        }
        else if (nodes.length == 1) {
            return nodes[0];
        }
        else {
            return emptyLongNode();
        }
    }

    public static Node.OfLong truncateLongNode(Node.OfLong n, long from, long to) {
        long size = truncatedSize(n.count(), from, to);
        if (size == 0)
            return emptyLongNode();

        Spliterator.OfLong spliterator = n.spliterator();
        Node.Builder.OfLong nodeBuilder = Nodes.longMakeBuilder(size);
        nodeBuilder.begin(size);
        for (int i = 0; i < from && spliterator.tryAdvance((LongConsumer) e -> { }); i++) { }
        for (int i = 0; (i < size) && spliterator.tryAdvance((LongConsumer) nodeBuilder); i++) { }
        nodeBuilder.end();
        return nodeBuilder.build();
    }

    /**
     * Make a fixed size node builder of known size, unless the size is negative, in which case make a variable size
     * node builder.
     *
     * @param size the known size, or -1 if the size is not known.
     * @return the node builder.
     */
    public static Node.Builder.OfLong longMakeBuilder(long size) {
        return (size >= 0 && size < Streams.MAX_ARRAY_SIZE) ? longMakeFixedSizeBuilder(size)
                                                            : longMakeVariableSizeBuilder();
    }

    /**
     * Make a fixed size builder.
     *
     * @param size the fixed size of the builder.
     * @return the node builder.
     */
    public static Node.Builder.OfLong longMakeFixedSizeBuilder(long size) {
        assert size < Streams.MAX_ARRAY_SIZE;
        return new LongFixedNodeBuilder(size);
    }

    /**
     * Make a variable size node builder.
     *
     * @return the node builder.
     */
    public static Node.Builder.OfLong longMakeVariableSizeBuilder() {
        return new LongSpinedNodeBuilder();
    }

    // Double nodes

    public static Node.OfDouble emptyDoubleNode() {
        return EMPTY_DOUBLE_NODE;
    }

    public static Node.OfDouble doubleNode(final double[] array) {
        return new DoubleArrayNode(array);
    }

    @SafeVarargs
    public static Node.OfDouble doubleNode(Node.OfDouble... nodes) {
        Objects.requireNonNull(nodes);
        if (nodes.length > 1) {
            return new DoubleConcNode(nodes);
        }
        else if (nodes.length == 1) {
            return nodes[0];
        }
        else {
            return emptyDoubleNode();
        }
    }

    public static Node.OfDouble truncateDoubleNode(Node.OfDouble n, long from, long to) {
        long size = truncatedSize(n.count(), from, to);
        if (size == 0)
            return emptyDoubleNode();

        Spliterator.OfDouble spliterator = n.spliterator();
        Node.Builder.OfDouble nodeBuilder = Nodes.doubleMakeBuilder(size);
        nodeBuilder.begin(size);
        for (int i = 0; i < from && spliterator.tryAdvance((DoubleConsumer) e -> { }); i++) { }
        for (int i = 0; (i < size) && spliterator.tryAdvance((DoubleConsumer) nodeBuilder); i++) { }
        nodeBuilder.end();
        return nodeBuilder.build();
    }

    /**
     * Make a fixed size node builder of known size, unless the size is negative, in which case make a variable size
     * node builder.
     *
     * @param size the known size, or -1 if the size is not known.
     * @return the node builder.
     */
    public static Node.Builder.OfDouble doubleMakeBuilder(long size) {
        return (size >= 0 && size < Streams.MAX_ARRAY_SIZE) ? doubleMakeFixedSizeBuilder(size)
                                                            : doubleMakeVariableSizeBuilder();
    }

    /**
     * Make a fixed size builder.
     *
     * @param size the fixed size of the builder.
     * @return the node builder.
     */
    public static Node.Builder.OfDouble doubleMakeFixedSizeBuilder(long size) {
        assert size < Streams.MAX_ARRAY_SIZE;
        return new DoubleFixedNodeBuilder(size);
    }

    /**
     * Make a variable size node builder.
     *
     * @return the node builder.
     */
    public static Node.Builder.OfDouble doubleMakeVariableSizeBuilder() {
        return new DoubleSpinedNodeBuilder();
    }

    //

    private static final Node EMPTY_NODE = new EmptyNode();

    private static class EmptyNode<T> implements Node<T> {
        @Override
        public Spliterator<T> spliterator() {
            return Streams.emptySpliterator();
        }

        @Override
        public T[] asArray(IntFunction<T[]> generator) {
            return generator.apply(0);
        }

        @Override
        public void copyInto(T[] array, int offset) { }

        @Override
        public long count() {
            return 0;
        }

        @Override
        public void forEach(Consumer<? super T> consumer) { }
    }

    private static class ArrayNode<T> implements Node<T> {

        protected final T[] array;
        protected int curSize;

        @SuppressWarnings("unchecked")
        ArrayNode(long size, IntFunction<T[]> generator) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            this.array = generator.apply((int) size);
            this.curSize = 0;
        }

        ArrayNode(T[] array) {
            this.array = array;
            this.curSize = array.length;
        }

        // Node

        @Override
        public Spliterator<T> spliterator() {
            return Arrays.spliterator(array, 0, curSize);
        }

        @Override
        public void copyInto(T[] dest, int destOffset) {
            System.arraycopy(array, 0, dest, destOffset, curSize);
        }

        @Override
        public T[] asArray(IntFunction<T[]> generator) {
            if (array.length == curSize) {
                return array;
            } else {
                throw new IllegalStateException();
            }
        }

        @Override
        public long count() {
            return curSize;
        }

        // Traversable

        @Override
        public void forEach(Consumer<? super T> consumer) {
            for (int i = 0; i < curSize; i++) {
                consumer.accept(array[i]);
            }
        }

        //

        @Override
        public String toString() {
            return String.format("ArrayNode[%d][%s]", array.length - curSize, Arrays.toString(array));
        }
    }

    private static class CollectionNode<T> implements Node<T> {

        final Collection<T> c;

        CollectionNode(Collection<T> c) {
            this.c = c;
        }

        // Node

        @Override
        public Spliterator<T> spliterator() {
            return c.stream().spliterator();
        }

        @Override
        public void copyInto(T[] array, int offset) {
            for (T t : c)
                array[offset++] = t;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T[] asArray(IntFunction<T[]> generator) {
            return c.toArray(generator.apply(c.size()));
        }

        @Override
        public long count() {
            return c.size();
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            c.forEach(consumer);
        }

        //

        @Override
        public String toString() {
            return String.format("CollectionNode[%d][%s]", c.size(), c);
        }
    }

    private static class ConcNode<T> implements Node<T> {
        final Node<T>[] nodes;
        int size = 0;

        private ConcNode(Node<T>[] nodes) {
            this.nodes = nodes;
        }

        // Node

        @Override
        public Spliterator<T> spliterator() {
            return new Nodes.InternalNodeSpliterator.OfRef<>(this);
        }

        @Override
        public int getChildCount() {
            return nodes.length;
        }

        @Override
        public Node<T> getChild(int i) {
            return nodes[i];
        }

        @Override
        public void copyInto(T[] array, int offset) {
            Objects.requireNonNull(array);
            for (Node<T> n : nodes) {
                n.copyInto(array, offset);
                offset += n.count();
            }
        }

        @Override
        public T[] asArray(IntFunction<T[]> generator) {
            T[] array = generator.apply((int) count());
            copyInto(array, 0);
            return array;
        }

        @Override
        public long count() {
            if (size == 0) {
                for (Node<T> n : nodes)
                    size += n.count();
            }
            return size;
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            for (Node<T> n : nodes)
                n.forEach(consumer);
        }

        @Override
        public String toString() {
            if (count() < 32) {
                return String.format("ConcNode[%s]", Arrays.stream(nodes).map(Object::toString).collect(toStringJoiner(",")).toString());
            } else {
                return String.format("ConcNode[size=%d]", count());
            }
        }
    }

    private static abstract class InternalNodeSpliterator<T, S extends Spliterator<T>, N extends Node<T>, C>
            implements Spliterator<T> {
        // Node we are pointing to
        // null if full traversal has occurred
        protected N curNode;

        // next child of curNode to consume
        protected int curChildIndex;

        // The spliterator of the curNode if that node is the last node and has no children.
        // This spliterator will be delegated to for splitting and traversing.
        // null if curNode has children
        protected S lastNodeSpliterator;

        // spliterator used while traversing with tryAdvance
        // null if no partial traversal has occurred
        protected S tryAdvanceSpliterator;

        private InternalNodeSpliterator(N curNode) {
            this.curNode = curNode;
        }

        protected boolean internalTryAdvance(C consumer) {
            if (curNode == null)
                return false;

            if (tryAdvanceSpliterator == null) {
                tryAdvanceSpliterator = lastNodeSpliterator != null
                                        ? lastNodeSpliterator
                                        : (S) curNode.getChild(curChildIndex).spliterator();
            }

            boolean hasNext = tryAdvance(tryAdvanceSpliterator, consumer);
            if (!hasNext) {
                while (!hasNext && ++curChildIndex < curNode.getChildCount()) {
                    tryAdvanceSpliterator = (S) curNode.getChild(curChildIndex).spliterator();
                    hasNext = tryAdvance(tryAdvanceSpliterator, consumer);
                }
                if (!hasNext)
                    curNode = null;
            }
            return hasNext;
        }

        protected abstract boolean tryAdvance(S spliterator, C consumer);

        @Override
        public S trySplit() {
            if (curNode == null || tryAdvanceSpliterator != null)
                return null; // Cannot split if fully or partially traversed
            else if (lastNodeSpliterator != null)
                return (S) lastNodeSpliterator.trySplit();
            else if (curChildIndex < curNode.getChildCount() - 1)
                return (S) curNode.getChild(curChildIndex++).spliterator();
            else {
                curNode = (N) curNode.getChild(curChildIndex);
                if (curNode.getChildCount() == 0) {
                    lastNodeSpliterator = (S) curNode.spliterator();
                    return (S) lastNodeSpliterator.trySplit();
                }
                else {
                    curChildIndex = 0;
                    return (S) curNode.getChild(curChildIndex++).spliterator();
                }
            }
        }

        @Override
        public long estimateSize() {
            if (curNode == null)
                return 0;

            // Will not reflect the effects of partial traversal.
            // This is compliant with the specification
            if (lastNodeSpliterator != null)
                return lastNodeSpliterator.estimateSize();
            else {
                long size = 0;
                for (int i = curChildIndex; i < curNode.getChildCount(); i++) {
                    long thisSize = curNode.getChild(i).count();
                    if (thisSize == -1)
                        return -1;
                    size += curNode.getChild(i).count();
                }
                return size;
            }
        }

        @Override
        public int characteristics() {
            return Spliterator.SIZED;
        }

        private static final class OfRef<T> extends InternalNodeSpliterator<T, Spliterator<T>, Node<T>, Consumer<? super T>> {
            OfRef(Node<T> curNode) {
                super(curNode);
            }

            @Override
            public boolean tryAdvance(Consumer<? super T> consumer) {
                return internalTryAdvance(consumer);
            }

            @Override
            protected boolean tryAdvance(Spliterator<T> spliterator, Consumer<? super T> consumer) {
                return spliterator.tryAdvance(consumer);
            }

            @Override
            public void forEach(Consumer<? super T> consumer) {
                if (curNode == null)
                    return;

                if (tryAdvanceSpliterator == null) {
                    if (lastNodeSpliterator != null)
                        lastNodeSpliterator.forEach(consumer);
                    else
                        for (int i = curChildIndex; i < curNode.getChildCount(); i++)
                            curNode.getChild(i).forEach(consumer);
                    curNode = null;
                } else
                    while(tryAdvance(consumer)) { }
            }
        }

        private static final class OfInt extends InternalNodeSpliterator<Integer, Spliterator.OfInt, Node.OfInt, IntConsumer>
                implements Spliterator.OfInt {

            public OfInt(Node.OfInt cur) {
                super(cur);
            }

            @Override
            public boolean tryAdvance(IntConsumer consumer) {
                return internalTryAdvance(consumer);
            }

            @Override
            protected boolean tryAdvance(Spliterator.OfInt spliterator, IntConsumer consumer) {
                return spliterator.tryAdvance(consumer);
            }

            @Override
            public void forEach(IntConsumer consumer) {
                if (curNode == null)
                    return;

                if (tryAdvanceSpliterator == null) {
                    if (lastNodeSpliterator != null)
                        lastNodeSpliterator.forEach(consumer);
                    else
                        for (int i = curChildIndex; i < curNode.getChildCount(); i++)
                            curNode.getChild(i).forEach(consumer);
                    curNode = null;
                } else
                    while(tryAdvance(consumer)) { }
            }
        }

        private static final class OfLong extends InternalNodeSpliterator<Long, Spliterator.OfLong, Node.OfLong, LongConsumer>
                implements Spliterator.OfLong {

            public OfLong(Node.OfLong cur) {
                super(cur);
            }

            @Override
            public boolean tryAdvance(LongConsumer consumer) {
                return internalTryAdvance(consumer);
            }

            @Override
            protected boolean tryAdvance(Spliterator.OfLong spliterator, LongConsumer consumer) {
                return spliterator.tryAdvance(consumer);
            }

            @Override
            public void forEach(LongConsumer consumer) {
                if (curNode == null)
                    return;

                if (tryAdvanceSpliterator == null) {
                    if (lastNodeSpliterator != null)
                        lastNodeSpliterator.forEach(consumer);
                    else
                        for (int i = curChildIndex; i < curNode.getChildCount(); i++)
                            curNode.getChild(i).forEach(consumer);
                    curNode = null;
                } else
                    while(tryAdvance(consumer)) { }
            }
        }

        private static final class OfDouble extends InternalNodeSpliterator<Double, Spliterator.OfDouble, Node.OfDouble, DoubleConsumer>
                implements Spliterator.OfDouble {

            public OfDouble(Node.OfDouble cur) {
                super(cur);
            }

            @Override
            public boolean tryAdvance(DoubleConsumer consumer) {
                return internalTryAdvance(consumer);
            }

            @Override
            protected boolean tryAdvance(Spliterator.OfDouble spliterator, DoubleConsumer consumer) {
                return spliterator.tryAdvance(consumer);
            }

            @Override
            public void forEach(DoubleConsumer consumer) {
                if (curNode == null)
                    return;

                if (tryAdvanceSpliterator == null) {
                    if (lastNodeSpliterator != null)
                        lastNodeSpliterator.forEach(consumer);
                    else
                        for (int i = curChildIndex; i < curNode.getChildCount(); i++)
                            curNode.getChild(i).forEach(consumer);
                    curNode = null;
                } else
                    while(tryAdvance(consumer)) { }
            }
        }
    }

    private static class FixedNodeBuilder<T> extends ArrayNode<T> implements Node.Builder<T> {

        private FixedNodeBuilder(long size, IntFunction<T[]> generator) {
            super(size, generator);
            assert size < Streams.MAX_ARRAY_SIZE;
        }

        //

        @Override
        public Node<T> build() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("Current size %d is less than fixed size %d", curSize, array.length));
            }

            return this;
        }

        //

        @Override
        public void begin(long size) {
            if (size != array.length) {
                throw new IllegalStateException(String.format("Begin size %d is not equal to fixed size %d", size, array.length));
            }

            curSize = 0;
        }

        @Override
        public void accept(T t) {
            if (curSize < array.length) {
                array[curSize++] = t;
            } else {
                throw new IllegalStateException(String.format("Accept exceeded fixed size of %d", array.length));
            }
        }

        @Override
        public void end() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("End size %d is less than fixed size %d", curSize, array.length));
            }
        }

        @Override
        public String toString() {
            return String.format("FixedNodeBuilder[%d][%s]", array.length - curSize, Arrays.toString(array));
        }
    }

    private static class SpinedNodeBuilder<T> extends SpinedBuffer<T> implements Node<T>, Node.Builder<T> {

        private boolean building = false;

        @Override
        public Spliterator<T> spliterator() {
            assert !building : "during building";
            return super.spliterator();
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            assert !building : "during building";
            super.forEach(consumer);
        }

        //
        @Override
        public void begin(long size) {
            assert !building : "was already building";
            building = true;
            clear();
            ensureCapacity(size);
        }

        @Override
        public void accept(T t) {
            assert building : "not building";
            super.accept(t);
        }

        @Override
        public void end() {
            assert building : "was not building";
            building = false;
            // @@@ check begin(size) and size
        }

        @Override
        public void copyInto(T[] array, int offset) {
            assert !building : "during building";
            super.copyInto(array, offset);
        }

        @Override
        public T[] asArray(IntFunction<T[]> arrayFactory) {
            assert !building : "during building";
            return super.asArray(arrayFactory);
        }

        @Override
        public Node<T> build() {
            assert !building : "during building";
            return this;
        }
    }

    //

    private static final int[] EMPTY_INT_ARRAY = new int[0];
    private static final long[] EMPTY_LONG_ARRAY = new long[0];
    private static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

    private static final Node.OfInt EMPTY_INT_NODE = new EmptyIntNode();

    private static final Node.OfLong EMPTY_LONG_NODE = new EmptyLongNode();

    private static final Node.OfDouble EMPTY_DOUBLE_NODE = new EmptyDoubleNode();

    private static class EmptyIntNode extends EmptyNode<Integer> implements Node.OfInt {
        @Override
        public Spliterator.OfInt spliterator() {
            return Streams.emptyIntSpliterator();
        }

        @Override
        public int[] asIntArray() {
            return EMPTY_INT_ARRAY;
        }

        @Override
        public void copyInto(int[] array, int offset) { }

        @Override
        public void forEach(IntConsumer consumer) { }
    };

    private static class EmptyLongNode extends EmptyNode<Long> implements Node.OfLong {
        @Override
        public Spliterator.OfLong spliterator() {
            return Streams.emptyLongSpliterator();
        }

        @Override
        public long[] asLongArray() {
            return EMPTY_LONG_ARRAY;
        }

        @Override
        public void copyInto(long[] array, int offset) { }

        @Override
        public void forEach(LongConsumer consumer) { }
    };

    private static class EmptyDoubleNode extends EmptyNode<Double> implements Node.OfDouble {
        @Override
        public Spliterator.OfDouble spliterator() {
            return Streams.emptyDoubleSpliterator();
        }

        @Override
        public double[] asDoubleArray() {
            return EMPTY_DOUBLE_ARRAY;
        }

        @Override
        public void copyInto(double[] array, int offset) { }

        @Override
        public void forEach(DoubleConsumer consumer) { }
    };

    private abstract static class AbstractPrimitiveConcNode<E, N extends Node<E>> implements Node<E> {
        final N[] nodes;
        int size = 0;

        public AbstractPrimitiveConcNode(N[] nodes) {
            this.nodes = nodes;
        }

        // Node

        @Override
        public int getChildCount() {
            return nodes.length;
        }

        @Override
        public N getChild(int i) {
            return nodes[i];
        }

        @Override
        public long count() {
            if (size == 0) {
                for (N n : nodes)
                    size += n.count();
            }
            return size;
        }

        @Override
        public String toString() {
            if (count() < 32)
                return String.format("%s[%s]", this.getClass().getName(),
                                     Arrays.stream(nodes).map(Object::toString).collect(toStringJoiner(",")).toString());
            else
                return String.format("%s[size=%d]", this.getClass().getName(), count());
        }
    }

    private static class IntArrayNode implements Node.OfInt {

        final int[] array;
        int curSize;

        IntArrayNode(long size) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            this.array = new int[(int) size];
            this.curSize = 0;
        }

        IntArrayNode(int[] array) {
            this.array = array;
            this.curSize = array.length;
        }

        // Node

        @Override
        public Spliterator.OfInt spliterator() {
            return Arrays.spliterator(array, 0, curSize);
        }

        @Override
        public int[] asIntArray() {
            if (array.length == curSize) {
                return array;
            } else {
                return Arrays.copyOf(array, curSize);
            }
        }

        @Override
        public void copyInto(int[] dest, int destOffset) {
            System.arraycopy(array, 0, dest, destOffset, curSize);
        }

        @Override
        public long count() {
            return curSize;
        }

        // Traversable

        @Override
        public void forEach(IntConsumer consumer) {
            for (int i = 0; i < curSize; i++) {
                consumer.accept(array[i]);
            }
        }

        //

        @Override
        public String toString() {
            return String.format("IntArrayNode[%d][%s]", array.length - curSize, Arrays.toString(array));
        }
    }

    private static class LongArrayNode implements Node.OfLong {

        final long[] array;
        int curSize;

        LongArrayNode(long size) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            this.array = new long[(int) size];
            this.curSize = 0;
        }

        LongArrayNode(long[] array) {
            this.array = array;
            this.curSize = array.length;
        }

        // Node

        @Override
        public Spliterator.OfLong spliterator() {
            return Arrays.spliterator(array, 0, curSize);
        }

        @Override
        public long[] asLongArray() {
            if (array.length == curSize) {
                return array;
            } else {
                return Arrays.copyOf(array, curSize);
            }
        }

        @Override
        public void copyInto(long[] dest, int destOffset) {
            System.arraycopy(array, 0, dest, destOffset, curSize);
        }

        @Override
        public long count() {
            return curSize;
        }

        // Traversable

        @Override
        public void forEach(LongConsumer consumer) {
            for (int i = 0; i < curSize; i++) {
                consumer.accept(array[i]);
            }
        }

        //

        @Override
        public String toString() {
            return String.format("LongArrayNode[%d][%s]", array.length - curSize, Arrays.toString(array));
        }
    }

    private static class DoubleArrayNode implements Node.OfDouble {

        final double[] array;
        int curSize;

        DoubleArrayNode(long size) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            this.array = new double[(int) size];
            this.curSize = 0;
        }

        DoubleArrayNode(double[] array) {
            this.array = array;
            this.curSize = array.length;
        }

        // Node

        @Override
        public Spliterator.OfDouble spliterator() {
            return Arrays.spliterator(array, 0, curSize);
        }

        @Override
        public double[] asDoubleArray() {
            if (array.length == curSize) {
                return array;
            } else {
                return Arrays.copyOf(array, curSize);
            }
        }

        @Override
        public void copyInto(double[] dest, int destOffset) {
            System.arraycopy(array, 0, dest, destOffset, curSize);
        }

        @Override
        public long count() {
            return curSize;
        }

        // Traversable

        @Override
        public void forEach(DoubleConsumer consumer) {
            for (int i = 0; i < curSize; i++) {
                consumer.accept(array[i]);
            }
        }

        //

        @Override
        public String toString() {
            return String.format("DoubleArrayNode[%d][%s]", array.length - curSize, Arrays.toString(array));
        }
    }

    private static class IntConcNode extends AbstractPrimitiveConcNode<Integer, Node.OfInt> implements Node.OfInt {

        public IntConcNode(Node.OfInt[] nodes) {
            super(nodes);
        }

        @Override
        public void forEach(IntConsumer consumer) {
            for (Node.OfInt n : nodes)
                n.forEach(consumer);
        }

        @Override
        public Spliterator.OfInt spliterator() {
            return new InternalNodeSpliterator.OfInt(this);
        }

        @Override
        public void copyInto(int[] array, int offset) {
            for (Node.OfInt n : nodes) {
                n.copyInto(array, offset);
                offset += n.count();
            }
        }

        @Override
        public int[] asIntArray() {
            int[] array = new int[(int) count()];
            copyInto(array, 0);
            return array;
        }
    }

    private static class LongConcNode extends AbstractPrimitiveConcNode<Long, Node.OfLong> implements Node.OfLong {

        public LongConcNode(Node.OfLong[] nodes) {
            super(nodes);
        }

        @Override
        public void forEach(LongConsumer consumer) {
            for (Node.OfLong n : nodes)
                n.forEach(consumer);
        }

        @Override
        public Spliterator.OfLong spliterator() {
            return new InternalNodeSpliterator.OfLong(this);
        }

        @Override
        public void copyInto(long[] array, int offset) {
            for (Node.OfLong n : nodes) {
                n.copyInto(array, offset);
                offset += n.count();
            }
        }

        @Override
        public long[] asLongArray() {
            long[] array = new long[(int) count()];
            copyInto(array, 0);
            return array;
        }
    }

    private static class DoubleConcNode extends AbstractPrimitiveConcNode<Double, Node.OfDouble> implements Node.OfDouble {

        public DoubleConcNode(Node.OfDouble[] nodes) {
            super(nodes);
        }

        @Override
        public void forEach(DoubleConsumer consumer) {
            for (Node.OfDouble n : nodes)
                n.forEach(consumer);
        }

        @Override
        public Spliterator.OfDouble spliterator() {
            return new InternalNodeSpliterator.OfDouble(this);
        }

        @Override
        public void copyInto(double[] array, int offset) {
            for (Node.OfDouble n : nodes) {
                n.copyInto(array, offset);
                offset += n.count();
            }
        }

        @Override
        public double[] asDoubleArray() {
            double[] array = new double[(int) count()];
            copyInto(array, 0);
            return array;
        }
    }

    private static class IntFixedNodeBuilder extends IntArrayNode implements Node.Builder.OfInt {

        private IntFixedNodeBuilder(long size) {
            super(size);
            assert size < Streams.MAX_ARRAY_SIZE;
        }

        //

        @Override
        public Node.OfInt build() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("Current size %d is less than fixed size %d", curSize, array.length));
            }

            return this;
        }

        //

        @Override
        public void begin(long size) {
            if (size != array.length) {
                throw new IllegalStateException(String.format("Begin size %d is not equal to fixed size %d", size, array.length));
            }

            curSize = 0;
        }

        @Override
        public void accept(int i) {
            if (curSize < array.length) {
                array[curSize++] = i;
            } else {
                throw new IllegalStateException(String.format("Accept exceeded fixed size of %d", array.length));
            }
        }

        @Override
        public void end() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("End size %d is less than fixed size %d", curSize, array.length));
            }
        }

        @Override
        public String toString() {
            return String.format("IntFixedNodeBuilder[%d][%s]", array.length - curSize, Arrays.toString(array));
        }
    }

    private static class LongFixedNodeBuilder extends LongArrayNode implements Node.Builder.OfLong {

        private LongFixedNodeBuilder(long size) {
            super(size);
            assert size < Streams.MAX_ARRAY_SIZE;
        }

        //

        @Override
        public Node.OfLong build() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("Current size %d is less than fixed size %d", curSize, array.length));
            }

            return this;
        }

        //

        @Override
        public void begin(long size) {
            if (size != array.length) {
                throw new IllegalStateException(String.format("Begin size %d is not equal to fixed size %d", size, array.length));
            }

            curSize = 0;
        }

        @Override
        public void accept(long i) {
            if (curSize < array.length) {
                array[curSize++] = i;
            } else {
                throw new IllegalStateException(String.format("Accept exceeded fixed size of %d", array.length));
            }
        }

        @Override
        public void end() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("End size %d is less than fixed size %d", curSize, array.length));
            }
        }

        @Override
        public String toString() {
            return String.format("LongFixedNodeBuilder[%d][%s]", array.length - curSize, Arrays.toString(array));
        }
    }

    private static class DoubleFixedNodeBuilder extends DoubleArrayNode implements Node.Builder.OfDouble {

        private DoubleFixedNodeBuilder(long size) {
            super(size);
            assert size < Streams.MAX_ARRAY_SIZE;
        }

        //

        @Override
        public Node.OfDouble build() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("Current size %d is less than fixed size %d", curSize, array.length));
            }

            return this;
        }

        //

        @Override
        public void begin(long size) {
            if (size != array.length) {
                throw new IllegalStateException(String.format("Begin size %d is not equal to fixed size %d", size, array.length));
            }

            curSize = 0;
        }

        @Override
        public void accept(double i) {
            if (curSize < array.length) {
                array[curSize++] = i;
            } else {
                throw new IllegalStateException(String.format("Accept exceeded fixed size of %d", array.length));
            }
        }

        @Override
        public void end() {
            if (curSize < array.length) {
                throw new IllegalStateException(String.format("End size %d is less than fixed size %d", curSize, array.length));
            }
        }

        @Override
        public String toString() {
            return String.format("DoubleFixedNodeBuilder[%d][%s]", array.length - curSize, Arrays.toString(array));
        }
    }

    private static class IntSpinedNodeBuilder extends SpinedBuffer.OfInt implements Node.OfInt, Node.Builder.OfInt {
        private boolean building = false;

        @Override
        public Spliterator.OfInt spliterator() {
            assert !building : "during building";
            return super.spliterator();
        }

        @Override
        public void forEach(IntConsumer consumer) {
            assert !building : "during building";
            super.forEach(consumer);
        }

        //
        @Override
        public void begin(long size) {
            assert !building : "was already building";
            building = true;
            clear();
            ensureCapacity(size);
        }

        @Override
        public void accept(int i) {
            assert building : "not building";
            super.accept(i);
        }

        @Override
        public void end() {
            assert building : "was not building";
            building = false;
            // @@@ check begin(size) and size
        }

        @Override
        public void copyInto(int[] array, int offset) throws IndexOutOfBoundsException {
            assert !building : "during building";
            super.copyInto(array, offset);
        }

        @Override
        public int[] asIntArray() {
            assert !building : "during building";
            return super.asIntArray();
        }

        @Override
        public Node.OfInt build() {
            assert !building : "during building";
            return this;
        }
    }

    private static class LongSpinedNodeBuilder extends SpinedBuffer.OfLong implements Node.OfLong, Node.Builder.OfLong {
        private boolean building = false;

        @Override
        public Spliterator.OfLong spliterator() {
            assert !building : "during building";
            return super.spliterator();
        }

        @Override
        public void forEach(LongConsumer consumer) {
            assert !building : "during building";
            super.forEach(consumer);
        }

        //
        @Override
        public void begin(long size) {
            assert !building : "was already building";
            building = true;
            clear();
            ensureCapacity(size);
        }

        @Override
        public void accept(long i) {
            assert building : "not building";
            super.accept(i);
        }

        @Override
        public void end() {
            assert building : "was not building";
            building = false;
            // @@@ check begin(size) and size
        }

        @Override
        public void copyInto(long[] array, int offset) {
            assert !building : "during building";
            super.copyInto(array, offset);
        }

        @Override
        public long[] asLongArray() {
            assert !building : "during building";
            return super.asLongArray();
        }

        @Override
        public Node.OfLong build() {
            assert !building : "during building";
            return this;
        }
    }

    private static class DoubleSpinedNodeBuilder extends SpinedBuffer.OfDouble implements Node.OfDouble, Node.Builder.OfDouble {
        private boolean building = false;

        @Override
        public Spliterator.OfDouble spliterator() {
            assert !building : "during building";
            return super.spliterator();
        }

        @Override
        public void forEach(DoubleConsumer consumer) {
            assert !building : "during building";
            super.forEach(consumer);
        }

        //
        @Override
        public void begin(long size) {
            assert !building : "was already building";
            building = true;
            clear();
            ensureCapacity(size);
        }

        @Override
        public void accept(double i) {
            assert building : "not building";
            super.accept(i);
        }

        @Override
        public void end() {
            assert building : "was not building";
            building = false;
            // @@@ check begin(size) and size
        }

        @Override
        public void copyInto(double[] array, int offset) {
            assert !building : "during building";
            super.copyInto(array, offset);
        }

        @Override
        public double[] asDoubleArray() {
            assert !building : "during building";
            return super.asDoubleArray();
        }

        @Override
        public Node.OfDouble build() {
            assert !building : "during building";
            return this;
        }
    }

    private static final NodeFactory<Object> NODE_FACTORY = new RefNodeFactory<>();

    public static <T> NodeFactory<T> getNodeFactory() {
        return (NodeFactory<T>) NODE_FACTORY;
    }

    private static final NodeFactory<Integer> INT_NODE_FACTORY = new IntNodeFactory();

    public static NodeFactory<Integer> getIntNodeFactory() {
        return INT_NODE_FACTORY;
    }

    private static final NodeFactory<Long> LONG_NODE_FACTORY = new LongNodeFactory();

    public static NodeFactory<Long> getLongNodeFactory() {
        return LONG_NODE_FACTORY;
    }

    private static final NodeFactory<Double> DOUBLE_NODE_FACTORY = new DoubleNodeFactory();

    public static NodeFactory<Double> getDoubleNodeFactory() {
        return DOUBLE_NODE_FACTORY;
    }

    private static class RefNodeFactory<T> implements NodeFactory<T> {
        @Override
        public Node<T> emptyNode() {
            return Nodes.emptyNode();
        }

        @Override
        public Node<T> concNodes(List<Node<T>> nodes) {
            return node(nodes.toArray(new Node[nodes.size()]));
        }

        @Override
        public Node.Builder<T> makeNodeBuilder(long sizeIfKnown, IntFunction<T[]> generator) {
            return Nodes.makeBuilder(sizeIfKnown, generator);
        }
    }

    private static class IntNodeFactory implements NodeFactory<Integer> {
        @Override
        public Node.OfInt emptyNode() {
            return Nodes.emptyIntNode();
        }

        @Override
        public Node.OfInt concNodes(List<Node<Integer>> nodes) {
            return Nodes.intNode(nodes.toArray(new Node.OfInt[nodes.size()]));
        }

        @Override
        public Node.Builder.OfInt makeNodeBuilder(long sizeIfKnown, IntFunction<Integer[]> generator) {
            return Nodes.intMakeBuilder(sizeIfKnown);
        }
    }

    private static class LongNodeFactory implements NodeFactory<Long> {
        @Override
        public Node.OfLong emptyNode() {
            return Nodes.emptyLongNode();
        }

        @Override
        public Node.OfLong concNodes(List<Node<Long>> nodes) {
            return Nodes.longNode(nodes.toArray(new Node.OfLong[nodes.size()]));
        }

        @Override
        public Node.Builder.OfLong makeNodeBuilder(long sizeIfKnown,
                                                               IntFunction<Long[]> generator) {
            return Nodes.longMakeBuilder(sizeIfKnown);
        }
    }

    private static class DoubleNodeFactory implements NodeFactory<Double> {
        @Override
        public Node.OfDouble emptyNode() {
            return Nodes.emptyDoubleNode();
        }

        @Override
        public Node.OfDouble concNodes(List<Node<Double>> nodes) {
            return Nodes.doubleNode(nodes.toArray(new Node.OfDouble[nodes.size()]));
        }

        @Override
        public Node.Builder.OfDouble makeNodeBuilder(long sizeIfKnown,
                                                                 IntFunction<Double[]> generator) {
            return Nodes.doubleMakeBuilder(sizeIfKnown);
        }
    }

}
