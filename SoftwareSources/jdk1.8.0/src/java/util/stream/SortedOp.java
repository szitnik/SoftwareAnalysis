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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Comparators;
import java.util.Objects;


/**
 * An operation which sorts elements.
 *
 * @param <T> Type of elements to be sorted.
 *
 * @author Brian Goetz
 */
// @@@ If terminal op is not ORDERED then is there any point sorting
abstract class SortedOp<T> implements StatefulOp<T> {
    private final StreamShape shape;

    protected SortedOp(StreamShape shape) {
        this.shape = shape;
    }

    public static class OfRef<T> extends SortedOp<T> {
        /** Comparator used for sorting */
        private final Comparator<? super T> comparator;

        /**
         * Sort using natural order of {@literal <T>} which must be
         * {@code Comparable}.
         */
        public OfRef() {
            // Will throw CCE when we try to sort if T is not Comparable
            this((Comparator<? super T>) Comparators.naturalOrder());
        }

        /**
         * Sort using the provided comparator.
         *
         * @param comparator The comparator to be used to evaluate ordering.
         */
        public OfRef(Comparator<? super T> comparator) {
            super(StreamShape.REFERENCE);
            this.comparator = Objects.requireNonNull(comparator);
        }

        @Override
        public Sink<T> wrapSink(int flags, Sink sink) {
            Objects.requireNonNull(sink);

            if (StreamOpFlag.SORTED.isKnown(flags))
                return sink;
            else if (StreamOpFlag.SIZED.isKnown(flags))
                return new SizedRefSortingSink<>(sink, comparator);
            else
                return new RefSortingSink<>(sink, comparator);
        }

        public <P_IN> Node<T> evaluateParallel(PipelineHelper<P_IN, T> helper) {
            if (StreamOpFlag.SORTED.isKnown(helper.getStreamAndOpFlags())) {
                return helper.collectOutput(false);
            }
            else {
                // @@@ Weak two-pass parallel implementation; parallel collect, parallel sort
                T[] flattenedData = helper.collectOutput(true).asArray(helper.arrayGenerator());
                Arrays.parallelSort(flattenedData, comparator);
                return Nodes.node(flattenedData);
            }
        }
    }

    public static class OfInt extends SortedOp<Integer> {
        public OfInt() {
            super(StreamShape.INT_VALUE);
        }

        @Override
        public Sink<Integer> wrapSink(int flags, Sink sink) {
            Objects.requireNonNull(sink);

            if (StreamOpFlag.SORTED.isKnown(flags))
                return sink;
            else if (StreamOpFlag.SIZED.isKnown(flags))
                return new SizedIntSortingSink(sink);
            else
                return new IntSortingSink(sink);
        }

        @Override
        public <P_IN> Node<Integer> evaluateParallel(PipelineHelper<P_IN, Integer> helper) {
            Node.OfInt n = (Node.OfInt) helper.collectOutput(true);

            int[] content = n.asIntArray();
            Arrays.parallelSort(content);

            return Nodes.intNode(content);
        }
    }

    public static class OfLong extends SortedOp<Long> {
        public OfLong() {
            super(StreamShape.LONG_VALUE);
        }

        @Override
        public Sink<Long> wrapSink(int flags, Sink sink) {
            Objects.requireNonNull(sink);

            if (StreamOpFlag.SORTED.isKnown(flags))
                return sink;
            else if (StreamOpFlag.SIZED.isKnown(flags))
                return new SizedLongSortingSink(sink);
            else
                return new LongSortingSink(sink);
        }

        @Override
        public <P_IN> Node<Long> evaluateParallel(PipelineHelper<P_IN, Long> helper) {
            Node.OfLong n = (Node.OfLong) helper.collectOutput(true);

            long[] content = n.asLongArray();
            Arrays.parallelSort(content);

            return Nodes.longNode(content);
        }
    }

    public static class OfDouble extends SortedOp<Double> {
        public OfDouble() {
            super(StreamShape.DOUBLE_VALUE);
        }

        @Override
        public Sink<Double> wrapSink(int flags, Sink sink) {
            Objects.requireNonNull(sink);

            if (StreamOpFlag.SORTED.isKnown(flags))
                return sink;
            else if (StreamOpFlag.SIZED.isKnown(flags))
                return new SizedDoubleSortingSink(sink);
            else
                return new DoubleSortingSink(sink);
        }

        @Override
        public <P_IN> Node<Double> evaluateParallel(PipelineHelper<P_IN, Double> helper) {
            Node.OfDouble n = (Node.OfDouble) helper.collectOutput(true);

            double[] content = n.asDoubleArray();
            Arrays.parallelSort(content);

            return Nodes.doubleNode(content);
        }
    }

    @Override
    public int getOpFlags() {
        return StreamOpFlag.IS_SORTED | StreamOpFlag.IS_ORDERED;
    }

    @Override
    public StreamShape outputShape() {
        return shape;
    }

    @Override
    public StreamShape inputShape() {
        return shape;
    }

    private static class SizedRefSortingSink<T> extends Sink.ChainedReference<T> {
        private final Comparator<? super T> comparator;
        T[] array;
        int offset;

        public SizedRefSortingSink(Sink sink, Comparator<? super T> comparator) {
            super(sink);
            this.comparator = comparator;
        }

        @Override
        public void begin(long size) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            array = (T[]) new Object[(int) size];
        }

        @Override
        public void end() {
            // Need to use offset rather than array.length since the downstream
            // many be short-circuiting
            // @@@ A better approach is to know if the downstream short-circuits
            //     and check sink.cancellationRequested
            Arrays.sort(array, 0, offset, comparator);
            downstream.begin(offset);
            for (int i = 0; i < offset; i++)
                downstream.accept(array[i]);
            downstream.end();
            array = null;
        }

        @Override
        public void accept(T t) {
            array[offset++] = t;
        }
    }

    private static class RefSortingSink<T> extends Sink.ChainedReference<T> {
        private final Comparator<? super T> comparator;
        ArrayList<T> list;

        public RefSortingSink(Sink sink, Comparator<? super T> comparator) {
            super(sink);
            this.comparator = comparator;
        }

        @Override
        public void begin(long size) {
            list = (size >= 0) ? new ArrayList<T>((int) size) : new ArrayList<T>();
        }

        @Override
        public void end() {
            list.sort(comparator);
            downstream.begin(list.size());
            list.forEach(downstream::accept);
            downstream.end();
            list = null;
        }

        @Override
        public void accept(T t) {
            list.add(t);
        }
    }

    private static class SizedIntSortingSink extends Sink.ChainedInt {
        int[] array;
        int offset;

        public SizedIntSortingSink(Sink downstream) {
            super(downstream);
        }

        @Override
        public void begin(long size) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            array = new int[(int) size];
        }

        @Override
        public void end() {
            Arrays.sort(array, 0, offset);
            downstream.begin(offset);
            for (int i = 0; i < offset; i++)
                downstream.accept(array[i]);
            downstream.end();
            array = null;
        }

        @Override
        public void accept(int t) {
            array[offset++] = t;
        }
    }

    private static class IntSortingSink extends Sink.ChainedInt {
        SpinedBuffer.OfInt b;

        public IntSortingSink(Sink sink) {super(sink);}

        @Override
        public void begin(long size) {
            b = (size > 0) ? new SpinedBuffer.OfInt((int) size) : new SpinedBuffer.OfInt();
        }

        @Override
        public void end() {
            int[] ints = b.asIntArray();
            Arrays.sort(ints);
            downstream.begin(ints.length);
            for (int anInt : ints)
                downstream.accept(anInt);
            downstream.end();
        }

        @Override
        public void accept(int t) {
            b.accept(t);
        }
    }

    private static class SizedLongSortingSink extends Sink.ChainedLong {
        long[] array;
        int offset;

        public SizedLongSortingSink(Sink downstream) {
            super(downstream);
        }

        @Override
        public void begin(long size) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            array = new long[(int) size];
        }

        @Override
        public void end() {
            Arrays.sort(array, 0, offset);
            downstream.begin(offset);
            for (int i = 0; i < offset; i++)
                downstream.accept(array[i]);
            downstream.end();
            array = null;
        }

        @Override
        public void accept(long t) {
            array[offset++] = t;
        }
    }

    private static class LongSortingSink extends Sink.ChainedLong {
        SpinedBuffer.OfLong b;

        public LongSortingSink(Sink sink) {super(sink);}

        @Override
        public void begin(long size) {
            b = (size > 0) ? new SpinedBuffer.OfLong((int) size) : new SpinedBuffer.OfLong();
        }

        @Override
        public void end() {
            long[] longs = b.asLongArray();
            Arrays.sort(longs);
            downstream.begin(longs.length);
            for (long aLong : longs)
                downstream.accept(aLong);
            downstream.end();
        }

        @Override
        public void accept(long t) {
            b.accept(t);
        }
    }

    private static class SizedDoubleSortingSink extends Sink.ChainedDouble {
        double[] array;
        int offset;

        public SizedDoubleSortingSink(Sink downstream) {
            super(downstream);
        }

        @Override
        public void begin(long size) {
            if (size >= Streams.MAX_ARRAY_SIZE)
                throw new IllegalArgumentException("Stream size exceeds max array size");
            array = new double[(int) size];
        }

        @Override
        public void end() {
            Arrays.sort(array, 0, offset);
            downstream.begin(offset);
            for (int i = 0; i < offset; i++)
                downstream.accept(array[i]);
            downstream.end();
            array = null;
        }

        @Override
        public void accept(double t) {
            array[offset++] = t;
        }
    }

    private static class DoubleSortingSink extends Sink.ChainedDouble {
        SpinedBuffer.OfDouble b;

        public DoubleSortingSink(Sink sink) {super(sink);}

        @Override
        public void begin(long size) {
            b = (size > 0) ? new SpinedBuffer.OfDouble((int) size) : new SpinedBuffer.OfDouble();
        }

        @Override
        public void end() {
            double[] doubles = b.asDoubleArray();
            Arrays.sort(doubles);
            downstream.begin(doubles.length);
            for (double aDouble : doubles)
                downstream.accept(aDouble);
            downstream.end();
        }

        @Override
        public void accept(double t) {
            b.accept(t);
        }
    }
}
