/*
 * Copyright (c) 2010, 2012, Oracle and/or its affiliates. All rights reserved.
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
package java.util.function;

import java.util.Objects;

/**
 * An operation which accepts a single input argument and returns no result.
 * (Unlike most other functional interfaces, {@code Consumer} is expected to
 * operate via side-effects.)
 *
 * @param <T> The type of input objects to {@code accept}
 *
 * @since 1.8
 */
@FunctionalInterface
public interface Consumer<T> {

    /**
     * Accept an input value.
     *
     * @param t the input object
     */
    public void accept(T t);

    /**
     * Returns a Consumer which performs in sequence the {@code apply} methods of
     * multiple Consumers. This Consumer's {@code apply} method is performed followed
     * by the {@code apply} method of the specified Consumer operation.
     *
     * @param other an additional Consumer which will be chained after this Consumer
     * @return a Consumer which performs in sequence the {@code apply} method of
     * this Consumer and the {@code apply} method of the specified Consumer operation
     */
    public default Consumer<T> chain(Consumer<? super T> other) {
        Objects.requireNonNull(other);
        return (T t) -> { accept(t); other.accept(t); };
    }
}
