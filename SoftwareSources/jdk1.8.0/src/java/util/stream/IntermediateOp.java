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

/**
 * An operation performed upon elements from an input stream to produce elements to
 * an output stream.
 * <p>By default the operation is stateless and not short-circuiting.</p>
 *
 * @param <E_IN>  Type of input elements to the operation.
 * @param <E_OUT> Type of output elements to the operation.
 * @author Brian Goetz
 */
interface IntermediateOp<E_IN, E_OUT> {

    /**
     *
     * @return the input shape of this operation.
     */
    default StreamShape inputShape() { return StreamShape.REFERENCE; }

    /**
     *
     * @return the output shape of this operation.
     */
    default StreamShape outputShape() { return StreamShape.REFERENCE; }

    /**
     * Get the properties of the operation.
     * <p>The properties correspond to the properties the output stream is
     * known to have or is not known to have when this operation is applied, in
     * encounter order, to elements of an input stream.</p>
     *
     * @return the properties of the operation.
     * @see {@link StreamOpFlag}
     */
    default int getOpFlags() { return 0; }

    /**
     * If {@code true} then operation is stateful, accumulates state and
     * can be evaluated in parallel by invoking {@link #evaluateParallel(PipelineHelper)}.
     *
     * <p>The default implementation returns false.
     *
     * @return {@code true} then operation is stateful and accumulates state.
     */
    default boolean isStateful() { return false; }

    /**
     * Return a sink which will accept elements, perform the operation upon
     * each element and send it to the provided sink.
     *
     *
     * @param flags the combined stream and operation flags up to but not including this operation.
     * @param sink elements will be sent to this sink after the processing.
     * @return a sink which will accept elements and perform the operation upon
     *         each element.
     */
    Sink<E_IN> wrapSink(int flags, Sink<E_OUT> sink);

    /**
     * Evaluate the operation in parallel.
     *
     * <p>The default implementation throws an {@link UnsupportedOperationException}.
     * If {@link #isStateful()} returns true then sub-classes or interfaces must override
     * the default implementation.
     *
     * @param helper the pipeline helper.
     * @param <P_IN> the type of elements input to the pipeline.
     * @return a node encapsulated the result evaluated in parallel.
     */
    default <P_IN> Node<E_OUT> evaluateParallel(PipelineHelper<P_IN, E_OUT> helper) {
        throw new UnsupportedOperationException("Parallel evaluation is not supported");
    }
}
