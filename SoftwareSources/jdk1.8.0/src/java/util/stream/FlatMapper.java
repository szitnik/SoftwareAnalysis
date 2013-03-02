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

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * FlatMapper
 *
 * @author Brian Goetz
 */
public interface FlatMapper<T, U> {
    void explodeInto(T element, Consumer<U> sink);

    interface ToInt<T> {
        void explodeInto(T element, IntConsumer sink);
    }

    interface ToLong<T> {
        void explodeInto(T element, LongConsumer sink);
    }

    interface ToDouble<T> {
        void explodeInto(T element, DoubleConsumer sink);
    }

    interface OfIntToInt {
        void explodeInto(int element, IntConsumer sink);
    }

    interface OfLongToLong {
        void explodeInto(long element, LongConsumer sink);
    }

    interface OfDoubleToDouble {
        void explodeInto(double element, DoubleConsumer sink);
    }
}
