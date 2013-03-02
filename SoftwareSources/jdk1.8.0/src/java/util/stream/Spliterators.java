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
import java.util.Comparator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

/**
 * Spliterators
 *
 * @author Brian Goetz
 */
public class Spliterators {

    static abstract class AbstractWrappingSpliterator<P_IN, P_OUT, T_BUFFER extends AbstractSpinedBuffer<P_OUT>>
            implements Spliterator<P_OUT> {
        // True if this spliterator supports splitting
        protected final boolean isParallel;

        protected final PipelineHelper<P_IN, P_OUT> ph;

        // The source spliterator whose elements are traversed and pushed through bufferSink
        protected final Spliterator<P_IN> spliterator;

        // The source of the sink chain for the pipeline where the last sink
        // is connected to buffer
        // null if no partial traverse has occurred
        protected Sink<P_IN> bufferSink;

        // When invoked advances one element of spliterator pushing that element through
        // bufferSink and returns true, otherwise returns false if the are no more elements
        // of the spliterator to traverse
        // null if no partial traverse has occurred
        protected BooleanSupplier pusher;

        // The next element to consume from buffer
        // Used only for partial traversal
        protected long nextToConsume;

        // The buffer to push elements to when partially traversing with tryAdvance
        // null if no partial traverse has occurred
        protected T_BUFFER buffer;

        // True if full traversal of the source spliterator (with possible cancellation) has occurred
        // When buffer is not null there still may be elements in the buffer to be consumed
        protected boolean finished;

        private AbstractWrappingSpliterator(PipelineHelper<P_IN, P_OUT> ph, Spliterator<P_IN> spliterator, boolean parallel) {
            this.spliterator = spliterator;
            this.ph = ph;
            this.isParallel = parallel;
        }

        protected boolean doAdvance() {
            if (buffer == null) {
                if (finished)
                    return false;

                initPartialTraversalState();
                nextToConsume = 0;
                bufferSink.begin(spliterator.getExactSizeIfKnown());
                return fillBuffer();
            }
            else {
                ++nextToConsume;
                boolean hasNext = nextToConsume < buffer.count();
                if (!hasNext) {
                    nextToConsume = 0;
                    buffer.clear();
                    hasNext = fillBuffer();
                }
                return hasNext;
            }
        }

        protected abstract AbstractWrappingSpliterator<P_IN, P_OUT, ?> wrap(Spliterator<P_IN> s);

        protected abstract void initPartialTraversalState();

        @Override
        public Spliterator<P_OUT> trySplit() {
            if (isParallel && !finished) {
                Spliterator<P_IN> split = spliterator.trySplit();
                return (split == null) ? null : wrap(split);
            }
            else
                return null;
        }

        private boolean fillBuffer() {
            while (buffer.count() == 0) {
                if (bufferSink.cancellationRequested() || !pusher.getAsBoolean()) {
                    if (finished)
                        return false;
                    else {
                        bufferSink.end(); // might trigger more elements
                        finished = true;
                    }
                }
            }
            return true;
        }

        @Override
        public long estimateSize() {
            return StreamOpFlag.SIZED.isKnown(ph.getStreamAndOpFlags()) ? spliterator.estimateSize() : Long.MAX_VALUE;
        }

        @Override
        public long getExactSizeIfKnown() {
            return StreamOpFlag.SIZED.isKnown(ph.getStreamAndOpFlags()) ? spliterator.getExactSizeIfKnown() : -1;
        }

        @Override
        public int characteristics() {
            // Get the characteristics from the pipeline
            int c = StreamOpFlag.toCharacteristics(StreamOpFlag.toStreamFlags(ph.getStreamAndOpFlags()));

            // Mask off the size and uniform characteristics and replace with those of the spliterator
            // Note that a non-uniform spliterator can change from something with an exact size to an
            // estimate for a sub-split, for example with HashSet where the size is known at the top
            // level spliterator but for sub-splits only an estimate is known
            if ((c & Spliterator.SIZED) != 0) {
                c &= ~(Spliterator.SIZED | Spliterator.UNIFORM);
                c |= (spliterator.characteristics() & Spliterator.SIZED & Spliterator.UNIFORM);
            }

            return c;
        }

        @Override
        public String toString() {
            return getClass().getName() + "[" + spliterator + "]";
        }
    }

    static class WrappingSpliterator<P_IN, P_OUT>
            extends AbstractWrappingSpliterator<P_IN, P_OUT, SpinedBuffer<P_OUT>> {

        WrappingSpliterator(PipelineHelper<P_IN, P_OUT> ph, Spliterator<P_IN> spliterator, boolean parallel) {
            super(ph, spliterator, parallel);
        }

        @Override
        protected WrappingSpliterator<P_IN, P_OUT> wrap(Spliterator<P_IN> s) {
            return new WrappingSpliterator<>(ph, s, isParallel);
        }

        @Override
        protected void initPartialTraversalState() {
            SpinedBuffer<P_OUT> b = new SpinedBuffer<>();
            buffer = b;
            bufferSink = ph.wrapSink(b::accept);
            pusher = () -> spliterator.tryAdvance(bufferSink);
        }

        @Override
        public boolean tryAdvance(Consumer<? super P_OUT> consumer) {
            boolean hasNext = doAdvance();
            if (hasNext)
                consumer.accept(buffer.get(nextToConsume));
            return hasNext;
        }

        @Override
        public void forEach(Consumer<? super P_OUT> consumer) {
            if (buffer == null && !finished) {
                ph.into((Sink<P_OUT>) consumer::accept, spliterator);
                finished = true;
            }
            else {
                while(tryAdvance(consumer)) { }
            }
        }
    }

    static class IntWrappingSpliterator<P_IN>
            extends AbstractWrappingSpliterator<P_IN, Integer, SpinedBuffer.OfInt>
            implements Spliterator.OfInt {

        IntWrappingSpliterator(PipelineHelper<P_IN, Integer> ph, Spliterator<P_IN> spliterator, boolean parallel) {
            super(ph, spliterator, parallel);
        }

        @Override
        protected AbstractWrappingSpliterator<P_IN, Integer, ?> wrap(Spliterator<P_IN> s) {
            return new IntWrappingSpliterator<>(ph, s, isParallel);
        }

        @Override
        protected void initPartialTraversalState() {
            SpinedBuffer.OfInt b = new SpinedBuffer.OfInt();
            buffer = b;
            bufferSink = ph.wrapSink((Sink.OfInt) b::accept);
            pusher = () -> spliterator.tryAdvance(bufferSink);
        }

        @Override
        public Spliterator.OfInt trySplit() {
            return (Spliterator.OfInt) super.trySplit();
        }

        @Override
        public boolean tryAdvance(IntConsumer consumer) {
            boolean hasNext = doAdvance();
            if (hasNext)
                consumer.accept(buffer.get(nextToConsume));
            return hasNext;
        }

        @Override
        public void forEach(IntConsumer consumer) {
            if (buffer == null && !finished) {
                ph.into((Sink.OfInt) consumer::accept, spliterator);
                finished = true;
            }
            else {
                while(tryAdvance(consumer));
            }
        }
    }

    static class LongWrappingSpliterator<P_IN>
            extends AbstractWrappingSpliterator<P_IN, Long, SpinedBuffer.OfLong>
            implements Spliterator.OfLong {

        LongWrappingSpliterator(PipelineHelper<P_IN, Long> ph, Spliterator<P_IN> spliterator, boolean parallel) {
            super(ph, spliterator, parallel);
        }

        @Override
        protected AbstractWrappingSpliterator<P_IN, Long, ?> wrap(Spliterator<P_IN> s) {
            return new LongWrappingSpliterator<>(ph, s, isParallel);
        }

        @Override
        protected void initPartialTraversalState() {
            SpinedBuffer.OfLong b = new SpinedBuffer.OfLong();
            buffer = b;
            bufferSink = ph.wrapSink((Sink.OfLong) b::accept);
            pusher = () -> spliterator.tryAdvance(bufferSink);
        }

        @Override
        public Spliterator.OfLong trySplit() {
            return (Spliterator.OfLong) super.trySplit();
        }

        @Override
        public boolean tryAdvance(LongConsumer consumer) {
            boolean hasNext = doAdvance();
            if (hasNext)
                consumer.accept(buffer.get(nextToConsume));
            return hasNext;
        }

        @Override
        public void forEach(LongConsumer consumer) {
            if (buffer == null && !finished) {
                ph.into((Sink.OfLong) consumer::accept, spliterator);
                finished = true;
            }
            else {
                while(tryAdvance(consumer));
            }
        }
    }

    static class DoubleWrappingSpliterator<P_IN>
            extends AbstractWrappingSpliterator<P_IN, Double, SpinedBuffer.OfDouble>
            implements Spliterator.OfDouble {

        DoubleWrappingSpliterator(PipelineHelper<P_IN, Double> ph, Spliterator<P_IN> spliterator, boolean parallel) {
            super(ph, spliterator, parallel);
        }

        @Override
        protected AbstractWrappingSpliterator<P_IN, Double, ?> wrap(Spliterator<P_IN> s) {
            return new DoubleWrappingSpliterator<>(ph, s, isParallel);
        }

        @Override
        protected void initPartialTraversalState() {
            SpinedBuffer.OfDouble b = new SpinedBuffer.OfDouble();
            buffer = b;
            bufferSink = ph.wrapSink((Sink.OfDouble) b::accept);
            pusher = () -> spliterator.tryAdvance(bufferSink);
        }

        @Override
        public Spliterator.OfDouble trySplit() {
            return (Spliterator.OfDouble) super.trySplit();
        }

        @Override
        public boolean tryAdvance(DoubleConsumer consumer) {
            boolean hasNext = doAdvance();
            if (hasNext)
                consumer.accept(buffer.get(nextToConsume));
            return hasNext;
        }

        @Override
        public void forEach(DoubleConsumer consumer) {
            if (buffer == null && !finished) {
                ph.into((Sink.OfDouble) consumer::accept, spliterator);
                finished = true;
            }
            else {
                while(tryAdvance(consumer));
            }
        }
    }

    static class DelegatingSpliterator<T> implements Spliterator<T> {
        private final Supplier<Spliterator<T>> supplier;

        private Spliterator<T> s;

        @SuppressWarnings("unchecked")
        public DelegatingSpliterator(Supplier<? extends Spliterator<T>> supplier) {
            this.supplier = (Supplier<Spliterator<T>>) supplier;
        }

        Spliterator<T> get() {
            if (s == null) {
                s = supplier.get();
            }
            return s;
        }

        @Override
        public Spliterator<T> trySplit() {
            return get().trySplit();
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> consumer) {
            return get().tryAdvance(consumer);
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            get().forEach(consumer);
        }

        @Override
        public long estimateSize() {
            return get().estimateSize();
        }

        @Override
        public int characteristics() {
            return get().characteristics();
        }

        @Override
        public Comparator<? super T> getComparator() {
            return get().getComparator();
        }

        @Override
        public long getExactSizeIfKnown() {
            return get().getExactSizeIfKnown();
        }

        @Override
        public String toString() {
            return getClass().getName() + "[" + get() + "]";
        }

        public static class OfInt extends DelegatingSpliterator<Integer> implements Spliterator.OfInt {

            private Spliterator.OfInt s;

            public OfInt(Supplier<Spliterator.OfInt> supplier) {
                super(supplier);
            }

            @Override
            Spliterator.OfInt get() {
                if (s == null) {
                    s = (Spliterator.OfInt) super.get();
                }
                return s;
            }

            @Override
            public Spliterator.OfInt trySplit() {
                return get().trySplit();
            }

            @Override
            public boolean tryAdvance(IntConsumer consumer) {
                return get().tryAdvance(consumer);
            }

            @Override
            public void forEach(IntConsumer consumer) {
                get().forEach(consumer);
            }
        }

        public static class OfLong extends DelegatingSpliterator<Long> implements Spliterator.OfLong {

            Spliterator.OfLong s;

            public OfLong(Supplier<Spliterator.OfLong> supplier) {
                super(supplier);
            }

            @Override
            Spliterator.OfLong get() {
                if (s == null) {
                    s = (Spliterator.OfLong) super.get();
                }
                return s;
            }

            @Override
            public Spliterator.OfLong trySplit() {
                return get().trySplit();
            }

            @Override
            public boolean tryAdvance(LongConsumer consumer) {
                return get().tryAdvance(consumer);
            }

            @Override
            public void forEach(LongConsumer consumer) {
                get().forEach(consumer);
            }
        }

        public static class OfDouble extends DelegatingSpliterator<Double> implements Spliterator.OfDouble {

            Spliterator.OfDouble s;

            public OfDouble(Supplier<Spliterator.OfDouble> supplier) {
                super(supplier);
            }

            @Override
            Spliterator.OfDouble get() {
                if (s == null) {
                    s = (Spliterator.OfDouble) super.get();
                }
                return s;
            }

            @Override
            public Spliterator.OfDouble trySplit() {
                return get().trySplit();
            }

            @Override
            public boolean tryAdvance(DoubleConsumer consumer) {
                return get().tryAdvance(consumer);
            }

            @Override
            public void forEach(DoubleConsumer consumer) {
                get().forEach(consumer);
            }
        }
    }


    private static abstract class AbstractArraySpliterator<T> implements Spliterator<T> {
        // The current inclusive offset into the array
        protected int curOffset;
        // The exclusive end offset into the array
        protected final int endOffset;

        // When curOffset >= endOffset then this spliterator is fully traversed

        private AbstractArraySpliterator(int offset, int length) {
            this.curOffset = offset;
            this.endOffset = offset + length;
        }

        @Override
        public long estimateSize() {
            return endOffset - curOffset;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.UNIFORM;
        }
    }

    public static class ArraySpliterator<T> extends AbstractArraySpliterator<T> {
        private final T[] elements;

        public ArraySpliterator(T[] elements, int offset, int length) {
            super(offset, length);
            Arrays.checkOffsetLenBounds(elements.length, offset, length);
            this.elements = Objects.requireNonNull(elements);
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> consumer) {
            if (curOffset < endOffset) {
                consumer.accept(elements[curOffset++]);
                return true;
            }
            return false;
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            int hEndOffset = endOffset;
            for (int i = curOffset; i < hEndOffset; i++)
                consumer.accept(elements[i]);
            curOffset = endOffset; // update only once -- reduce heap write traffic
        }

        @Override
        public Spliterator<T> trySplit() {
            int t = (endOffset - curOffset) / 2;
            if (t == 0)
                return null;
            else {
                Spliterator<T> ret = new ArraySpliterator<>(elements, curOffset, t);
                curOffset += t;
                return ret;
            }
        }
    }

    public static class IntArraySpliterator
            extends AbstractArraySpliterator<Integer> implements Spliterator.OfInt {
        private final int[] elements;

        public IntArraySpliterator(int[] elements, int offset, int length) {
            super(offset, length);
            this.elements = Objects.requireNonNull(elements);
        }

        @Override
        public boolean tryAdvance(IntConsumer consumer) {
            if (curOffset < endOffset) {
                consumer.accept(elements[curOffset++]);
                return true;
            }
            return false;
        }

        @Override
        public void forEach(IntConsumer consumer) {
            int hEndOffset = endOffset;
            for (int i = curOffset; i < hEndOffset; i++)
                consumer.accept(elements[i]);
            curOffset = endOffset; // update only once -- reduce heap write traffic
        }

        @Override
        public OfInt trySplit() {
            int t = (endOffset - curOffset) / 2;
            if (t == 0)
                return null;
            else {
                OfInt ret = new IntArraySpliterator(elements, curOffset, t);
                curOffset += t;
                return ret;
            }
        }
    }

    public static class LongArraySpliterator
            extends AbstractArraySpliterator<Long> implements Spliterator.OfLong {
        private final long[] elements;

        public LongArraySpliterator(long[] elements, int offset, int length) {
            super(offset, length);
            this.elements = Objects.requireNonNull(elements);
        }

        @Override
        public boolean tryAdvance(LongConsumer consumer) {
            if (curOffset < endOffset) {
                consumer.accept(elements[curOffset++]);
                return true;
            }
            return false;
        }

        @Override
        public void forEach(LongConsumer consumer) {
            int hEndOffset = endOffset;
            for (int i = curOffset; i < hEndOffset; i++)
                consumer.accept(elements[i]);
            curOffset = endOffset; // update only once -- reduce heap write traffic
        }

        @Override
        public OfLong trySplit() {
            int t = (endOffset - curOffset) / 2;
            if (t == 0)
                return null;
            else {
                OfLong ret = new LongArraySpliterator(elements, curOffset, t);
                curOffset += t;
                return ret;
            }
        }
    }

    public static class DoubleArraySpliterator
            extends AbstractArraySpliterator<Double> implements Spliterator.OfDouble {
        private final double[] elements;

        public DoubleArraySpliterator(double[] elements, int offset, int length) {
            super(offset, length);
            this.elements = Objects.requireNonNull(elements);
        }

        @Override
        public boolean tryAdvance(DoubleConsumer consumer) {
            if (curOffset < endOffset) {
                consumer.accept(elements[curOffset++]);
                return true;
            }
            return false;
        }

        @Override
        public void forEach(DoubleConsumer consumer) {
            int hEndOffset = endOffset;
            for (int i = curOffset; i < hEndOffset; i++)
                consumer.accept(elements[i]);
            curOffset = endOffset; // update only once -- reduce heap write traffic
        }

        @Override
        public OfDouble trySplit() {
            int t = (endOffset - curOffset) / 2;
            if (t == 0)
                return null;
            else {
                OfDouble ret = new DoubleArraySpliterator(elements, curOffset, t);
                curOffset += t;
                return ret;
            }
        }
    }
}
