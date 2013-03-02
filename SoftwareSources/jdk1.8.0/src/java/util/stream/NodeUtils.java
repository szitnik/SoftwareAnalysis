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
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.CountedCompleter;
import java.util.function.IntFunction;

/**
 * Utilities for generating and operating on reference-based Nodes.
 *
 * @author Brian Goetz
 */
final class NodeUtils {

    private NodeUtils() {
        throw new Error("no instances");
    }

    public static <P_IN, P_OUT> Node<P_OUT> collect(PipelineHelper<P_IN, P_OUT> helper,
                                                    boolean flattenTree) {
        Spliterator<P_IN> spliterator = helper.sourceSpliterator();
        long size = helper.exactOutputSizeIfKnown(spliterator);
        if (size >= 0 && spliterator.hasCharacteristic(Spliterator.UNIFORM)) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            P_OUT[] array = helper.arrayGenerator().apply((int) size);
            new SizedCollectorTask<>(spliterator, helper, array).invoke();
            return Nodes.node(array);
        } else {
            Node<P_OUT> node = new CollectorTask<>(helper).invoke();

            // @@@ using default F/J pool, will that be different from that used by helper.invoke?
            return flattenTree ? flatten(node, helper.arrayGenerator()) : node;
        }
    }

    public static <T> Node<T> flatten(Node<T> node, IntFunction<T[]> generator) {
        if (node.getChildCount() > 0) {
            T[] array = generator.apply((int) node.count());
            new ToArrayTask<>(node, array, 0).invoke();
            return Nodes.node(array);
        } else {
            return node;
        }
    }

    // Ints

    public static <P_IN> Node.OfInt intCollect(PipelineHelper<P_IN, Integer> helper,
                                                           boolean flattenTree) {
        Spliterator<P_IN> spliterator = helper.sourceSpliterator();
        long size = helper.exactOutputSizeIfKnown(spliterator);
        if (size >= 0 && spliterator.hasCharacteristic(Spliterator.UNIFORM)) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            int[] array = new int[(int) size];
            new IntSizedCollectorTask<>(spliterator, helper, array).invoke();
            return Nodes.intNode(array);
        }
        else {
            Node.OfInt node = new IntCollectorTask<>(helper).invoke();

            // @@@ using default F/J pool, will that be different from that used by helper.invoke?
            return flattenTree ? intFlatten(node) : node;
        }
    }

    public static Node.OfInt intFlatten(Node.OfInt node) {
        if (node.getChildCount() > 0) {
            int[] array = new int[(int) node.count()];
            new IntToArrayTask(node, array, 0).invoke();
            return Nodes.intNode(array);
        } else {
            return node;
        }
    }

    // Longs

    public static <P_IN> Node.OfLong longCollect(PipelineHelper<P_IN, Long> helper,
                                                              boolean flattenTree) {
        Spliterator<P_IN> spliterator = helper.sourceSpliterator();
        long size = helper.exactOutputSizeIfKnown(spliterator);
        if (size >= 0 && spliterator.hasCharacteristic(Spliterator.UNIFORM)) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            long[] array = new long[(int) size];
            new LongSizedCollectorTask<>(spliterator, helper, array).invoke();
            return Nodes.longNode(array);
        }
        else {
            Node.OfLong node = new LongCollectorTask<>(helper).invoke();

            // @@@ using default F/J pool, will that be different from that used by helper.invoke?
            return flattenTree ? longFlatten(node) : node;
        }
    }

    public static Node.OfLong longFlatten(Node.OfLong node) {
        if (node.getChildCount() > 0) {
            long[] array = new long[(int) node.count()];
            new LongToArrayTask(node, array, 0).invoke();
            return Nodes.longNode(array);
        } else {
            return node;
        }
    }

    // Doubles

    public static <P_IN> Node.OfDouble doubleCollect(PipelineHelper<P_IN, Double> helper,
                                                            boolean flattenTree) {
        Spliterator<P_IN> spliterator = helper.sourceSpliterator();
        long size = helper.exactOutputSizeIfKnown(spliterator);
        if (size >= 0 && spliterator.hasCharacteristic(Spliterator.UNIFORM)) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            double[] array = new double[(int) size];
            new DoubleSizedCollectorTask<>(spliterator, helper, array).invoke();
            return Nodes.doubleNode(array);
        }
        else {
            Node.OfDouble node = new DoubleCollectorTask<>(helper).invoke();

            // @@@ using default F/J pool, will that be different from that used by helper.invoke?
            return flattenTree ? doubleFlatten(node) : node;
        }
    }

    public static Node.OfDouble doubleFlatten(Node.OfDouble node) {
        if (node.getChildCount() > 0) {
            double[] array = new double[(int) node.count()];
            new DoubleToArrayTask(node, array, 0).invoke();
            return Nodes.doubleNode(array);
        } else {
            return node;
        }
    }


    // Reference implementations

    private static class CollectorTask<T, U> extends AbstractTask<T, U, Node<U>, CollectorTask<T, U>> {
        private final PipelineHelper<T, U> helper;

        private CollectorTask(PipelineHelper<T, U> helper) {
            super(helper);
            this.helper = helper;
        }

        private CollectorTask(CollectorTask<T, U> parent, Spliterator<T> spliterator) {
            super(parent, spliterator);
            helper = parent.helper;
        }

        @Override
        protected CollectorTask<T, U> makeChild(Spliterator<T> spliterator) {
            return new CollectorTask<>(this, spliterator);
        }

        @Override
        protected Node<U> doLeaf() {
            Node.Builder<U> builder = Nodes.makeBuilder(helper.exactOutputSizeIfKnown(spliterator),
                                                        helper.arrayGenerator());
            return helper.into(builder, spliterator).build();
        }

        @Override
        public void onCompletion(CountedCompleter caller) {
            if (!isLeaf()) {
                @SuppressWarnings("unchecked")
                Node<U>[] nodes = (Node<U>[]) new Node[numChildren];
                int idx = 0;
                for (CollectorTask<T, U> cur = children; cur != null; cur = cur.nextSibling)
                    nodes[idx++] = cur.getLocalResult();
                setLocalResult(Nodes.node(nodes));
            }
        }
    }

    private static class SizedCollectorTask<T, U> extends CountedCompleter<Void> {
        private final Spliterator<T> spliterator;
        private final PipelineHelper<T, U> helper;
        private final U[] array;
        private final long targetSize;
        private long offset;
        private long length;

        private SizedCollectorTask(Spliterator<T> spliterator, PipelineHelper<T, U> helper, U[] array) {
            assert spliterator.hasCharacteristic(Spliterator.UNIFORM);
            this.spliterator = spliterator;
            this.helper = helper;
            this.array = array;
            this.targetSize = AbstractTask.suggestTargetSize(spliterator.estimateSize());
            this.offset = 0;
            this.length = array.length;
        }

        private SizedCollectorTask(SizedCollectorTask<T, U> parent, Spliterator<T> spliterator, long offset, long length) {
            super(parent);
            assert spliterator.hasCharacteristic(Spliterator.UNIFORM);
            this.spliterator = spliterator;
            this.helper = parent.helper;
            this.array = parent.array;
            this.targetSize = parent.targetSize;
            this.offset = offset;
            this.length = length;

            if (offset < 0 || length < 0 || (offset + length - 1 >= array.length)) {
                throw new IllegalArgumentException(
                        String.format("offset and length interval [%d, %d + %d) is not within array size interval [0, %d)",
                                      offset, offset, length, array.length));
            }
        }

        @Override
        public void compute() {
            Spliterator<T> split;
            if (!AbstractTask.suggestSplit(helper, spliterator, targetSize) || ((split = spliterator.trySplit()) == null)) {
                if (offset+length >= Streams.MAX_ARRAY_SIZE)
                    throw new IllegalArgumentException("Stream size exceeds max array size");
                helper.into(new ArraySink<>(array, (int) offset, (int) length), spliterator);
                tryComplete();
            }
            else {
                setPendingCount(1);
                long thisSplitSize = split.estimateSize();
                new SizedCollectorTask<>(this, split, offset, thisSplitSize).fork();
                new SizedCollectorTask<>(this, spliterator, offset + thisSplitSize, length - thisSplitSize).compute();
            }
        }
    }

    private static class ToArrayTask<T> extends CountedCompleter<Void> {
        private final T[] array;
        private final Node<T> node;
        private final int offset;

        private ToArrayTask(Node<T> node, T[] array, int offset) {
            this.array = array;
            this.node = node;
            this.offset = offset;
        }

        private ToArrayTask(ToArrayTask<T> parent, Node<T> node, int offset) {
            super(parent);
            this.array = parent.array;
            this.node = node;
            this.offset = offset;
        }

        @Override
        public void compute() {
            if (node.getChildCount() > 0) {
                setPendingCount(node.getChildCount() - 1);

                final ToArrayTask<T> firstTask = new ToArrayTask<>(this, node.getChild(0), offset);
                int size = (int) firstTask.node.count();

                for (int i = 1; i < node.getChildCount(); i++) {
                    final ToArrayTask<T> task = new ToArrayTask<>(this, node.getChild(i), offset + size);
                    size += task.node.count();
                    task.fork();
                }
                firstTask.compute();
            } else {
                node.copyInto(array, offset);
                tryComplete();
            }
        }
    }

    private static class ArraySink<T> implements Sink<T> {
        private final T[] array;
        private final int offset;
        private final int length;
        private int index = 0;

        ArraySink(T[] array, int offset, int length) {
            Arrays.checkOffsetLenBounds(array.length, offset, length);
            this.array = Objects.requireNonNull(array);
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void begin(long size) {
            if(size > length)
                throw new IllegalStateException("size passed to ArraySink.begin exceeds array length");
            index = 0;
        }

        @Override
        public void accept(T t) {
            if (index >= length) {
                throw new IndexOutOfBoundsException(Integer.toString(index));
            }
            array[offset + (index++)] = t;
        }
    }

    // Int value implementations

    // @@@ Note that the size collector task and to array task for references and
    // primitives can unified with appropriate SAMs abstracting the details
    // of push leaf data to an array or copying node data to an array at an offset.

    private static class IntCollectorTask<T> extends AbstractTask<T, Integer, Node.OfInt, IntCollectorTask<T>> {
        private final PipelineHelper<T, Integer> helper;

        private IntCollectorTask(PipelineHelper<T, Integer> helper) {
            super(helper);
            this.helper = helper;
        }

        private IntCollectorTask(IntCollectorTask<T> parent, Spliterator<T> spliterator) {
            super(parent, spliterator);
            helper = parent.helper;
        }

        @Override
        protected IntCollectorTask<T> makeChild(Spliterator<T> spliterator) {
            return new IntCollectorTask<>(this, spliterator);
        }

        @Override
        protected Node.OfInt doLeaf() {
            Node.Builder.OfInt builder = Nodes.intMakeBuilder(helper.exactOutputSizeIfKnown(spliterator));
            return helper.into(builder, spliterator).build();
        }

        @Override
        public void onCompletion(CountedCompleter caller) {
            if (!isLeaf()) {
                Node.OfInt[] nodes = new Node.OfInt[numChildren];
                int idx = 0;
                for (IntCollectorTask<T> cur = children; cur != null; cur = cur.nextSibling)
                    nodes[idx++] = cur.getLocalResult();

                setLocalResult(Nodes.intNode(nodes));
            }
        }
    }

    private static class IntSizedCollectorTask<T> extends CountedCompleter<Void> {
        private final Spliterator<T> spliterator;
        private final PipelineHelper<T, Integer> helper;
        private final int[] array;
        private final long targetSize;
        private long offset;
        private long length;

        private IntSizedCollectorTask(Spliterator<T> spliterator, PipelineHelper<T, Integer> helper, int[] array) {
            assert spliterator.hasCharacteristic(Spliterator.UNIFORM);
            this.spliterator = spliterator;
            this.helper = helper;
            this.array = array;
            this.targetSize = AbstractTask.suggestTargetSize(spliterator.estimateSize());
            this.offset = 0;
            this.length = array.length;
        }

        private IntSizedCollectorTask(IntSizedCollectorTask<T> parent, Spliterator<T> spliterator, long offset, long length) {
            super(parent);
            assert spliterator.hasCharacteristic(Spliterator.UNIFORM);
            this.spliterator = spliterator;
            this.helper = parent.helper;
            this.array = parent.array;
            this.targetSize = parent.targetSize;
            this.offset = offset;
            this.length = length;

            if (offset < 0 || length < 0 || (offset + length - 1 >= array.length)) {
                throw new IllegalArgumentException(
                        String.format("offset and length interval [%d, %d + %d) is not within array size interval [0, %d)",
                                      offset, offset, length, array.length));
            }
        }

        @Override
        public void compute() {
            Spliterator<T> split;
            if (!AbstractTask.suggestSplit(helper, spliterator, targetSize) || ((split = spliterator.trySplit()) == null)) {
                if (offset+length >= Streams.MAX_ARRAY_SIZE)
                    throw new IllegalArgumentException("Stream size exceeds max array size");
                helper.into(new IntArraySink(array, (int) offset, (int) length), spliterator);
                tryComplete();
            }
            else {
                setPendingCount(1);
                long thisSplitSize = split.estimateSize();
                new IntSizedCollectorTask<>(this, split, offset, thisSplitSize).fork();
                new IntSizedCollectorTask<>(this, spliterator, offset + thisSplitSize, length - thisSplitSize).compute();
            }
        }
    }

    private static class IntToArrayTask extends CountedCompleter<Void> {
        private final int[] array;
        private final Node.OfInt node;
        private final int offset;

        private IntToArrayTask(Node.OfInt node, int[] array, int offset) {
            this.array = array;
            this.node = node;
            this.offset = offset;
        }

        private IntToArrayTask(IntToArrayTask parent, Node.OfInt node, int offset) {
            super(parent);
            this.array = parent.array;
            this.node = node;
            this.offset = offset;
        }

        @Override
        public void compute() {
            if (node.getChildCount() > 0) {
                setPendingCount(node.getChildCount() - 1);

                final IntToArrayTask firstTask = new IntToArrayTask(this, node.getChild(0), offset);
                int size = (int) firstTask.node.count();

                for (int i = 1; i < node.getChildCount(); i++) {
                    final IntToArrayTask task = new IntToArrayTask(this, node.getChild(i), offset + size);
                    size += task.node.count();
                    task.fork();
                }
                firstTask.compute();
            }
            else {
                node.copyInto(array, offset);
                tryComplete();
            }
        }
    }

    private static class IntArraySink implements Sink.OfInt {
        private final int[] array;
        private final int offset;
        private final int length;
        private int index = 0;

        IntArraySink(int[] array, int offset, int length) {
            Arrays.checkOffsetLenBounds(array.length, offset, length);
            this.array = Objects.requireNonNull(array);
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void begin(long size) {
            if(size > length)
                throw new IllegalStateException("size passed to IntArraySink.begin exceeds array length");
            index = 0;
        }

        @Override
        public void accept(int t) {
            if (index >= length) {
                throw new IndexOutOfBoundsException(Integer.toString(index));
            }
            array[offset + (index++)] = t;
        }
    }

    // Long value implementations

    private static class LongCollectorTask<T> extends AbstractTask<T, Long, Node.OfLong, LongCollectorTask<T>> {
        private final PipelineHelper<T, Long> helper;

        private LongCollectorTask(PipelineHelper<T, Long> helper) {
            super(helper);
            this.helper = helper;
        }

        private LongCollectorTask(LongCollectorTask<T> parent, Spliterator<T> spliterator) {
            super(parent, spliterator);
            helper = parent.helper;
        }

        @Override
        protected LongCollectorTask<T> makeChild(Spliterator<T> spliterator) {
            return new LongCollectorTask<>(this, spliterator);
        }

        @Override
        protected Node.OfLong doLeaf() {
            Node.Builder.OfLong builder = Nodes.longMakeBuilder(helper.exactOutputSizeIfKnown(spliterator));
            return helper.into(builder, spliterator).build();
        }

        @Override
        public void onCompletion(CountedCompleter caller) {
            if (!isLeaf()) {
                Node.OfLong[] nodes = new Node.OfLong[numChildren];
                int idx = 0;
                for (LongCollectorTask<T> cur = children; cur != null; cur = cur.nextSibling)
                    nodes[idx++] = cur.getLocalResult();

                setLocalResult(Nodes.longNode(nodes));
            }
        }
    }

    private static class LongSizedCollectorTask<T> extends CountedCompleter<Void> {
        private final Spliterator<T> spliterator;
        private final PipelineHelper<T, Long> helper;
        private final long[] array;
        private final long targetSize;
        private long offset;
        private long length;

        private LongSizedCollectorTask(Spliterator<T> spliterator, PipelineHelper<T, Long> helper, long[] array) {
            assert spliterator.hasCharacteristic(Spliterator.UNIFORM);
            this.spliterator = spliterator;
            this.helper = helper;
            this.array = array;
            this.targetSize = AbstractTask.suggestTargetSize(spliterator.estimateSize());
            this.offset = 0;
            this.length = array.length;
        }

        private LongSizedCollectorTask(LongSizedCollectorTask<T> parent, Spliterator<T> spliterator, long offset, long length) {
            super(parent);
            assert spliterator.hasCharacteristic(Spliterator.UNIFORM);
            this.spliterator = spliterator;
            this.helper = parent.helper;
            this.array = parent.array;
            this.targetSize = parent.targetSize;
            this.offset = offset;
            this.length = length;

            if (offset < 0 || length < 0 || (offset + length - 1 >= array.length)) {
                throw new IllegalArgumentException(
                        String.format("offset and length interval [%d, %d + %d) is not within array size interval [0, %d)",
                                      offset, offset, length, array.length));
            }
        }

        @Override
        public void compute() {
            Spliterator<T> split;
            if (!AbstractTask.suggestSplit(helper, spliterator, targetSize) || ((split = spliterator.trySplit()) == null)) {
                if (offset+length >= Streams.MAX_ARRAY_SIZE)
                    throw new IllegalArgumentException("Stream size exceeds max array size");
                helper.into(new LongArraySink(array, (int) offset, (int) length), spliterator);
                tryComplete();
            }
            else {
                setPendingCount(1);
                long thisSplitSize = split.estimateSize();
                new LongSizedCollectorTask<>(this, split, offset, thisSplitSize).fork();
                new LongSizedCollectorTask<>(this, spliterator, offset + thisSplitSize, length - thisSplitSize).compute();
            }
        }
    }

    private static class LongToArrayTask extends CountedCompleter<Void> {
        private final long[] array;
        private final Node.OfLong node;
        private final int offset;

        private LongToArrayTask(Node.OfLong node, long[] array, int offset) {
            this.array = array;
            this.node = node;
            this.offset = offset;
        }

        private LongToArrayTask(LongToArrayTask parent, Node.OfLong node, int offset) {
            super(parent);
            this.array = parent.array;
            this.node = node;
            this.offset = offset;
        }

        @Override
        public void compute() {
            if (node.getChildCount() > 0) {
                setPendingCount(node.getChildCount() - 1);

                final LongToArrayTask firstTask = new LongToArrayTask(this, node.getChild(0), offset);
                int size = (int) firstTask.node.count();

                for (int i = 1; i < node.getChildCount(); i++) {
                    final LongToArrayTask task = new LongToArrayTask(this, node.getChild(i), offset + size);
                    size += task.node.count();
                    task.fork();
                }
                firstTask.compute();
            }
            else {
                node.copyInto(array, offset);
                tryComplete();
            }
        }
    }

    private static class LongArraySink implements Sink.OfLong {
        private final long[] array;
        private final int offset;
        private final int length;
        private int index = 0;

        LongArraySink(long[] array, int offset, int length) {
            Arrays.checkOffsetLenBounds(array.length, offset, length);
            this.array = Objects.requireNonNull(array);
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void begin(long size) {
            if(size > length)
                throw new IllegalStateException("size passed to IntArraySink.begin exceeds array length");
            index = 0;
        }

        @Override
        public void accept(long t) {
            if (index >= length) {
                throw new IndexOutOfBoundsException(Integer.toString(index));
            }
            array[offset + (index++)] = t;
        }
    }

    // Double value implementations

    private static class DoubleCollectorTask<T> extends AbstractTask<T, Double, Node.OfDouble, DoubleCollectorTask<T>> {
        private final PipelineHelper<T, Double> helper;

        private DoubleCollectorTask(PipelineHelper<T, Double> helper) {
            super(helper);
            this.helper = helper;
        }

        private DoubleCollectorTask(DoubleCollectorTask<T> parent, Spliterator<T> spliterator) {
            super(parent, spliterator);
            helper = parent.helper;
        }

        @Override
        protected DoubleCollectorTask<T> makeChild(Spliterator<T> spliterator) {
            return new DoubleCollectorTask<>(this, spliterator);
        }

        @Override
        protected Node.OfDouble doLeaf() {
            Node.Builder.OfDouble builder = Nodes.doubleMakeBuilder(helper.exactOutputSizeIfKnown(spliterator));
            return helper.into(builder, spliterator).build();
        }

        @Override
        public void onCompletion(CountedCompleter caller) {
            if (!isLeaf()) {
                Node.OfDouble[] nodes = new Node.OfDouble[numChildren];
                int idx = 0;
                for (DoubleCollectorTask<T> cur = children; cur != null; cur = cur.nextSibling)
                    nodes[idx++] = cur.getLocalResult();

                setLocalResult(Nodes.doubleNode(nodes));
            }
        }
    }

    private static class DoubleSizedCollectorTask<T> extends CountedCompleter<Void> {
        private final Spliterator<T> spliterator;
        private final PipelineHelper<T, Double> helper;
        private final double[] array;
        private final long targetSize;
        private long offset;
        private long length;

        private DoubleSizedCollectorTask(Spliterator<T> spliterator, PipelineHelper<T, Double> helper, double[] array) {
            assert spliterator.hasCharacteristic(Spliterator.UNIFORM);
            this.spliterator = spliterator;
            this.helper = helper;
            this.array = array;
            this.targetSize = AbstractTask.suggestTargetSize(spliterator.estimateSize());
            this.offset = 0;
            this.length = array.length;
        }

        private DoubleSizedCollectorTask(DoubleSizedCollectorTask<T> parent, Spliterator<T> spliterator, long offset, long length) {
            super(parent);
            assert spliterator.hasCharacteristic(Spliterator.UNIFORM);
            this.spliterator = spliterator;
            this.helper = parent.helper;
            this.array = parent.array;
            this.targetSize = parent.targetSize;
            this.offset = offset;
            this.length = length;

            if (offset < 0 || length < 0 || (offset + length - 1 >= array.length)) {
                throw new IllegalArgumentException(
                        String.format("offset and length interval [%d, %d + %d) is not within array size interval [0, %d)",
                                      offset, offset, length, array.length));
            }
        }

        @Override
        public void compute() {
            Spliterator<T> split ;
            if (!AbstractTask.suggestSplit(helper, spliterator, targetSize) || ((split = spliterator.trySplit()) == null)) {
                if (offset+length >= Streams.MAX_ARRAY_SIZE)
                    throw new IllegalArgumentException("Stream size exceeds max array size");
                helper.into(new DoubleArraySink(array, (int) offset, (int) length), spliterator);
                tryComplete();
            }
            else {
                setPendingCount(1);
                long thisSplitSize = split.estimateSize();
                new DoubleSizedCollectorTask<>(this, split, offset, thisSplitSize).fork();
                new DoubleSizedCollectorTask<>(this, spliterator, offset + thisSplitSize, length - thisSplitSize).compute();
            }
        }
    }

    private static class DoubleToArrayTask extends CountedCompleter<Void> {
        private final double[] array;
        private final Node.OfDouble node;
        private final int offset;

        private DoubleToArrayTask(Node.OfDouble node, double[] array, int offset) {
            this.array = array;
            this.node = node;
            this.offset = offset;
        }

        private DoubleToArrayTask(DoubleToArrayTask parent, Node.OfDouble node, int offset) {
            super(parent);
            this.array = parent.array;
            this.node = node;
            this.offset = offset;
        }

        @Override
        public void compute() {
            if (node.getChildCount() > 0) {
                setPendingCount(node.getChildCount() - 1);

                final DoubleToArrayTask firstTask = new DoubleToArrayTask(this, node.getChild(0), offset);
                int size = (int) firstTask.node.count();

                for (int i = 1; i < node.getChildCount(); i++) {
                    final DoubleToArrayTask task = new DoubleToArrayTask(this, node.getChild(i), offset + size);
                    size += task.node.count();
                    task.fork();
                }
                firstTask.compute();
            }
            else {
                node.copyInto(array, offset);
                tryComplete();
            }
        }
    }

    private static class DoubleArraySink implements Sink.OfDouble {
        private final double[] array;
        private final int offset;
        private final int length;
        private int index = 0;

        DoubleArraySink(double[] array, int offset, int length) {
            Arrays.checkOffsetLenBounds(array.length, offset, length);
            this.array = Objects.requireNonNull(array);
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void begin(long size) {
            if(size > length)
                throw new IllegalStateException("size passed to IntArraySink.begin exceeds array length");
            index = 0;
        }

        @Override
        public void accept(double t) {
            if (index >= length) {
                throw new IndexOutOfBoundsException(Integer.toString(index));
            }
            array[offset + (index++)] = t;
        }
    }
}
