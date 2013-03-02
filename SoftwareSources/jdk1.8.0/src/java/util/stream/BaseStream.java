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

import java.util.Iterator;
import java.util.Spliterator;

/**
 * BaseStream
 *
 * @param <T> Type of stream elements.
 *
 * @author Brian Goetz
 */
interface BaseStream<T, S extends BaseStream<T, S>> {
    /**
     * Return an iterator for the elements of this stream. The same iterator
     * instance is returned for every invocation.  Once the elements of the
     * stream are consumed it is not possible to "rewind" the stream.
     *
     * @return the element iterator for this stream.
     */
    default Iterator<T> iterator() {
        return Streams.iteratorFrom(spliterator());
    }

    /**
     * Return a spliterator for the elements of this stream. The same spliterator
     * instance is returned for every invocation.  Once the elements of the
     * stream are consumed it is not possible to "rewind" the stream.
     *
     * @return the element iterator for this stream.
     */
    Spliterator<T> spliterator();

    /**
     * Returns {@code true} if this stream may be split for parallel
     * processing.
     *
     * @return {@code true} if this stream may be split for parallel processing.
     */
    boolean isParallel();

    /**
     * Return the composition of stream flags of the stream source and all intermediate
     * operations.
     */
    int getStreamFlags();

    S sequential();

    S parallel();
}
