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
 * A terminal operation.
 *
 * @param <E_IN> The type of input elements.
 * @param <R>    The type of the result.
 * @author Brian Goetz
 */
interface TerminalOp<E_IN, R> {
    /**
     *
     * @return the input shape of this operation.
     */
    default StreamShape inputShape() { return StreamShape.REFERENCE; }

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
     * Evaluate the result of the operation in parallel.
     *
     *
     * @param helper
     * @return the result of the operation.
     */
    default <P_IN> R evaluateParallel(PipelineHelper<P_IN, E_IN> helper) {
        if (Tripwire.enabled)
            Tripwire.trip(getClass(), "{0} triggering StreamOp.evaluateParallel serial default");
        return evaluateSequential(helper);
    }

    /**
     * Evaluate the result of the operation sequentially.
     *
     * @param helper
     * @return the result of the operation.
     */
    <P_IN> R evaluateSequential(PipelineHelper<P_IN, E_IN> helper);
}
