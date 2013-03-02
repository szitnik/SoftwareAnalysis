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

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;

/**
 * An immutable container of elements.
 * <p>The container may be flat, and have no children, or a tree whose shape corresponds to the computation
 * tree that produced the tree where the elements are contained in the leaf nodes.</p>
 * @param <T> the type of elements.
 */
interface Node<T> {

    /**
     *
     * @return the spliterator for this node.
     */
    Spliterator<T> spliterator();

    void forEach(Consumer<? super T> consumer);

    /**
     *
     * @return the number of child nodes
     */
    default int getChildCount() {
        return 0;
    }

    /**
     * Get a child node at a given index.
     *
     * @param i the index to the child node
     * @return the child node.
     * @throws IndexOutOfBoundsException if the index is less than 0 or greater than or equal to the
     * number of child nodes.
     */
    default Node<T> getChild(int i) {
        throw new IndexOutOfBoundsException();
    }

    /**
     * View this node as an array.
     * <p/>
     * <p>Depending on the underlying implementation this may return a reference to an internal array rather than
     * a copy. It is the callers responsibility to decide if either this node or the array is
     * utilized as the primary reference for the data.</p>
     *
     * @return the array.
     */
    T[] asArray(IntFunction<T[]> generator);

    /**
     * Copy the content of this node into an array at an offset.
     *
     * @param array the array to copy the data into.
     * @param offset the offset into the array.
     * @throws IndexOutOfBoundsException if copying would cause access of data outside array bounds.
     * @throws NullPointerException if <code>array</code> is <code>null</code>.
     */
    void copyInto(T[] array, int offset);

    default StreamShape getShape() {
        return StreamShape.REFERENCE;
    }

    /**
     * Return the number of elements contained by this node
     */
    long count();

    /**
     * A specialization of a {@link Sink} to build a flat node whose contents
     * is all the pushed elements.
     *
     */
    interface Builder<T> extends Sink<T> {

        /**
         * Build the node.
         * <p>This method should be called after all elements have been pushed and signalled with
         * an invocation of {@link Sink#end()}.</p>
         *
         * @return the built node.
         */
        Node<T> build();

        interface OfInt extends Node.Builder<Integer>, Sink.OfInt {
            @Override
            Node.OfInt build();
        }

        interface OfLong extends Node.Builder<Long>, Sink.OfLong {
            @Override
            Node.OfLong build();
        }

        interface OfDouble extends Node.Builder<Double>, Sink.OfDouble {
            @Override
            Node.OfDouble build();
        }
    }

    interface OfInt extends Node<Integer> {

        @Override
        Spliterator.OfInt spliterator();

        @Override
        default void forEach(Consumer<? super Integer> consumer) {
            if (consumer instanceof IntConsumer) {
                forEach((IntConsumer) consumer);
            }
            else {
                if (Tripwire.enabled)
                    Tripwire.trip(getClass(), "{0} calling Node.OfInt.forEach(Consumer)");
                spliterator().forEach(consumer);
            }
        }

        default void forEach(IntConsumer consumer) {
            spliterator().forEach(consumer);
        }

        //

        @Override
        default Integer[] asArray(IntFunction<Integer[]> generator) {
            throw new UnsupportedOperationException();
        }

        @Override
        default void copyInto(Integer[] array, int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        default Node.OfInt getChild(int i) {
            throw new IndexOutOfBoundsException();
        }

        int[] asIntArray();

        void copyInto(int[] array, int offset);

        default StreamShape getShape() {
            return StreamShape.INT_VALUE;
        }
    }

    interface OfLong extends Node<Long> {

        @Override
        Spliterator.OfLong spliterator();

        @Override
        default void forEach(Consumer<? super Long> consumer) {
            if (consumer instanceof LongConsumer) {
                forEach((LongConsumer) consumer);
            }
            else {
                if (Tripwire.enabled)
                    Tripwire.trip(getClass(), "{0} calling Node.OfLong.forEach(Consumer)");
                spliterator().forEach(consumer);
            }
        }

        default void forEach(LongConsumer consumer) {
            spliterator().forEach(consumer);
        }

        //

        @Override
        default Long[] asArray(IntFunction<Long[]> generator) {
            throw new UnsupportedOperationException();
        }

        @Override
        default void copyInto(Long[] array, int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        default Node.OfLong getChild(int i) {
            throw new IndexOutOfBoundsException();
        }

        long[] asLongArray();

        void copyInto(long[] array, int offset);

        default StreamShape getShape() {
            return StreamShape.LONG_VALUE;
        }
    }

    interface OfDouble extends Node<Double> {

        @Override
        Spliterator.OfDouble spliterator();

        @Override
        default void forEach(Consumer<? super Double> consumer) {
            if (consumer instanceof DoubleConsumer) {
                forEach((DoubleConsumer) consumer);
            }
            else {
                if (Tripwire.enabled)
                    Tripwire.trip(getClass(), "{0} calling Node.OfLong.forEach(Consumer)");
                spliterator().forEach(consumer);
            }
        }

        default void forEach(DoubleConsumer consumer) {
            spliterator().forEach(consumer);
        }

        //

        @Override
        default Double[] asArray(IntFunction<Double[]> generator) {
            throw new UnsupportedOperationException();
        }

        @Override
        default void copyInto(Double[] array, int offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        default Node.OfDouble getChild(int i) {
            throw new IndexOutOfBoundsException();
        }

        double[] asDoubleArray();

        void copyInto(double[] array, int offset);

        default StreamShape getShape() {
            return StreamShape.DOUBLE_VALUE;
        }
    }
}
