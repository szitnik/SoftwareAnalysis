/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * Determines if the input objects match some criteria.
 *
 * @param <L> the type of input objects provided to {@code test}.
 * @param <R> the type of input objects provided to {@code test}.
 */
@FunctionalInterface
public interface BiPredicate<L, R> {

    /**
     * Return {@code true} if the inputs match some criteria.
     *
     * @param l an input object.
     * @param r an input object.
     * @return {@code true} if the inputs match some criteria.
     */
     boolean test(L l, R r);
    /**
     * Returns a predicate which evaluates to {@code true} only if this
     * predicate and the provided predicate both evaluate to {@code true}. If
     * this predicate returns {@code false} then the remaining predicate is not
     * evaluated.
     *
     * @return a new predicate which returns {@code true} only if both
     * predicates return {@code true}.
     */
    public default BiPredicate<L, R> and(BiPredicate<? super L,? super R> p) {
        Objects.requireNonNull(p);
        return (L l, R r) -> test(l,r) && p.test(l,r);
    }

    /**
     * Returns a predicate which negates the result of this predicate.
     *
     * @return a new predicate who's result is always the opposite of this
     * predicate.
     */
    public default BiPredicate<L, R> negate() {
        return (L l, R r) -> !test(l,r);
    }

    /**
     * Returns a predicate which evaluates to {@code true} if either this
     * predicate or the provided predicate evaluates to {@code true}. If this
     * predicate returns {@code true} then the remaining predicate is not
     * evaluated.
     *
     * @return a new predicate which returns {@code true} if either predicate
     * returns {@code true}.
     */
    public default BiPredicate<L, R> or(BiPredicate<? super L,? super R> p) {
        Objects.requireNonNull(p);
        return (L l, R r) -> test(l,r) || p.test(l,r);
    }

    /**
     * Returns a predicate that evaluates to {@code true} if all or none of the
     * component predicates evaluate to {@code true}.
     *
     * @return  a predicate that evaluates to {@code true} if all or none of the
     * component predicates evaluate to {@code true}
     */
    public default BiPredicate<L, R> xor(BiPredicate<? super L,? super R> p) {
        Objects.requireNonNull(p);
        return (L l, R r) -> test(l,r) ^ p.test(l,r);
    }
}
