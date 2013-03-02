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
package java.util.function;

import java.util.Objects;

/**
 * An operation which accepts two input arguments and returns no result.
 * (Unlike most other functional interfaces, {@code BiConsumer} is expected to
 * operate via side-effects.)
 *
 * @param <T> the type of input objects provided to {@code accept}.
 * @param <U> the type of input objects provided to {@code accept}.
 *
 * @see Consumer
 */
@FunctionalInterface
public interface BiConsumer<T, U> {

    /**
     * Performs operations upon the provided object which may modify that object
     * and/or external state.
     *
     * @param t an input object
     */
    void accept(T t, U u);

    /**
     * Returns a BiConsumer which performs in sequence the {@code apply} methods of
     * multiple Consumers. This BiConsumer's {@code apply} method is performed followed
     * by the {@code apply} method of the specified BiConsumer operation.
     *
     * @param other an additional BiConsumer which will be chained after this BiConsumer
     * @return a BiConsumer which performs in sequence the {@code apply} method of
     * this BiConsumer and the {@code apply} method of the specified BiConsumer operation
     */
    public default BiConsumer<T, U> chain(BiConsumer<? super T, ? super U> other) {
        Objects.requireNonNull(other);
        
        return (l, r) -> {
            accept(l, r);
            other.accept(l, r);
        };
    }
}
