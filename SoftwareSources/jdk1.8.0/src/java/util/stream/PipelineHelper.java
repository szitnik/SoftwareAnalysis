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
import java.util.function.IntFunction;

/**
 * @param <P_IN>  Type of elements input to the pipeline.
 * @param <P_OUT> Type of elements output from the pipeline.
 */
interface PipelineHelper<P_IN, P_OUT> {

    /**
     *
     * @return
     */
    StreamShape getInputShape();

    /**
     *
     * @return
     */
    StreamShape getOutputShape();

    /**
     * @return the combined stream and operation flags for the output of the pipeline.
     * @see StreamOpFlag
     */
    int getStreamAndOpFlags();

    /**
     * @return the operation flags for the terminal operation.
     * @see StreamOpFlag
     */
    int getTerminalOpFlags();

    /**
     * @return true if the pipeline is a parallel pipeline, otherwise false and the pipeline is a sequential pipeline.
     */
    boolean isParallel();

    /**
     * Get the spliterator for the source of hte pipeline.
     *
     * @return the source spliterator.
     */
    Spliterator<P_IN> sourceSpliterator();

    /**
     * Returns the exact output size of the pipeline if known.
     *
     * <p>The exact output size is known if {@link Spliterator#getExactSizeIfKnown()} ()}
     * returns a non negative value, and  {@link StreamOpFlag#SIZED} is known on the combined stream
     * and operation flags.
     *
     * @param spliterator the spliterator.
     * @return the exact size if known, or a negative value if infinite or unknown.
     */
    long exactOutputSizeIfKnown(Spliterator<P_IN> spliterator);

    /**
     * Wrap a sink (see {@link #wrapSink(Sink)} that corresponds to the sink that
     * accepts elements output from the pipeline, then push all elements obtained
     * from the spliterator into that wrapped sink.
     *
     * @param sink the sink in which to wrap.
     * @param spliterator the spliterator.
     */
    <S extends Sink<P_OUT>> S into(S sink, Spliterator<P_IN> spliterator);

    /**
     * Push all elements obtained from the spliterator into the wrapped sink.
     *
     * @param wrappedSink the wrapped sink.
     * @param spliterator the spliterator.
     */
    void intoWrapped(Sink<P_IN> wrappedSink, Spliterator<P_IN> spliterator);

    /**
     * Push elements obtained from the spliterator into the wrapped sink
     * until the sink is cancelled or all elements have been pushed.</p>
     *
     * @param wrappedSink the sink to push elements to.
     * @param spliterator the spliterator to pull elements from
     */
    void intoWrappedWithCancel(Sink<P_IN> wrappedSink, Spliterator<P_IN> spliterator);

    /**
     * Create a sink chain.
     *
     * @param sink the last sink in the chain that accepts output elements.
     * @return the first sink in the chain that accepts input elements.
     */
    Sink<P_IN> wrapSink(Sink<P_OUT> sink);

    /**
     * Get the node factory corresponding to the output and shape of this pipeline.
     *
     * @return the node factory.
     */
    NodeFactory<P_OUT> getOutputNodeFactory();

    /**
     * Collect all output elements to a {@link Node}.
     *
     * @param flatten if true the Node returned will contain no children,
     *                otherwise the Node represents the root in a tree that
     *                reflects the computation tree.
     * @return the node containing all output elements.
     */
    Node<P_OUT> collectOutput(boolean flatten);

    /**
     *
     * @return the array generator for creating an array of elements output from this pipeline.
     */
    IntFunction<P_OUT[]> arrayGenerator();
}
