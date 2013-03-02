/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

/**
 * Static utility methods pertaining to {@code Predicate} instances.
 *
 * <p>All of the returned predicates are serializable if given serializable
 * parameters.
 */
public final class Predicates {

    /**
     * a predicate that evaluates to {@code true} if the reference being tested
     * is {@code null}.
     */
    private static final Predicate<Object> IS_NULL = t -> t == null;
    /**
     * a predicate that evaluates to {@code true} if the reference being tested
     * is not {@code null}.
     */
    private static final Predicate<Object> NON_NULL = t -> t != null;
    /**
     * a predicate who's result is always {@code false}.
     */
    private static final Predicate<Object> FALSE = t -> false;
    /**
     * a predicate who's result is always {@code true}.
     */
    private static final Predicate<Object> TRUE = t -> true;

    /**
     * singleton utils
     */
    private Predicates() {
        throw new AssertionError("No instances!");
    }

    /**
     * Returns a predicate that evaluates to {@code true} if the reference being
     * tested is {@code null}.
     *
     * @param <T> the type of values evaluated by the predicate.
     * @return a predicate that evaluates to {@code true} if the reference being
     * tested is {@code null}
     */
    public static <T> Predicate<T> isNull() {
        return (Predicate<T>)IS_NULL;
    }

    /**
     * Returns a predicate that evaluates to {@code true} if the reference being
     * tested is non-{@code null}.
     *
     * @param <T> the type of values evaluated by the predicate.
     * @return a predicate that evaluates to {@code true} if the reference being
     * tested is is non-{@code null}
     */
    public static <T> Predicate<T> nonNull() {
        return (Predicate<T>)NON_NULL;
    }

    /**
     * Returns a predicate that always evaluates to {@code false}.
     *
     * @param <T> the type of values evaluated by the predicate.
     * @return a predicate that always evaluates to {@code false}.
     */
    public static <T> Predicate<T> alwaysFalse() {
        return (Predicate<T>)FALSE;
    }

    /**
     * Returns a predicate that always evaluates to {@code true}.
     *
     * @param <T> the type of values evaluated by the predicate.
     * @return a predicate that always evaluates to {@code true}.
     */
    public static <T> Predicate<T> alwaysTrue() {
        return (Predicate<T>)TRUE;
    }

    /**
     * Returns a predicate that evaluates to {@code true} if the object being
     * tested is an instance of the provided class. If the object being tested
     * is {@code null} this predicate evaluates to {@code false}.
     *
     * @param <T> the type of values evaluated by the predicate.
     * @param clazz The target class to be matched by the predicate.
     * @return a predicate that evaluates to {@code true} if the object being
     * tested is an instance of the provided class
     */
    public static <T> Predicate<T> instanceOf(Class<?> clazz) {
        return o -> clazz.isInstance(o);
    }

    /**
     * Returns a predicate that who's result is {@code target == object}.
     *
     * @param <T> the type of values evaluated by the predicate.
     * @param target The target value to be compared for identity equality.
     * @return a predicate that who's result is {@code target == object}.
     */
    public static <T> Predicate<T> isSame(Object target) {
        return obj -> obj == target;
    }

    /**
     * Returns a predicate who's result matches
     * {@code Objects.equals(target, t)}.
     *
     * @param <T> the type of values evaluated by the predicate.
     * @param target The target value to be compared for equality.
     * @return a predicate who's result matches
     * {@code Objects.equals(target, t)}
     */
    public static <T> Predicate<T> isEqual(Object target) {
        return (null == target)
                ? Predicates.isNull()
                : object -> target.equals(object);
    }

    /**
     * Creates a predicate that evaluates to {@code true} if the {@code test}
     * object is a member of the provided collection. The collection is not
     * defensively copied so changes to it will alter the behavior of the
     * predicate.
     *
     * @param <T> Type of predicate values.
     * @param target the collection against which objects will be tested.
     * @return a predicate that evaluates to {@code true} if the tested object
     * is a member of the provided collection. The collection is not defensively
     * copied so changes to it will alter the behavior of the predicate.
     */
    public static <T> Predicate<T> contains(Collection<?> target) {
        return t -> target.contains(t);
    }

    /**
     * Creates a predicate that is a composition of a mapper and a predicate.
     * The returned predicate's result is
     * {@code predicate.test(mapper.apply(t))}.
     *
     * @return the composition of the provided mapper and predicate
     */
    public static <T, V> Predicate<T> compose(
            Predicate<? super V> predicate, Function<? super T, ? extends V> mapper) {
        return t -> predicate.test(mapper.apply(t));
    }

    /**
     * Returns a predicate that evaluates to {@code true} if the provided
     * predicate evaluates to {@code false}
     *
     * @param <T> the type of values evaluated by the predicate.
     * @param predicate The predicate to be evaluated.
     * @return A predicate who's result is the logical inverse of the provided
     * predicate.
     */
    public static <T> Predicate<T> negate(Predicate<? super T> predicate) {
        return t -> !predicate.test(t);
    }

    /**
     * Returns a predicate that evaluates to {@code true} if all of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will terminate upon the first
     * {@code false} predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param first initial component predicate to be evaluated.
     * @param second additional component predicate to be evaluated.
     * @return A predicate who's result is {@code true} iff all component
     * predicates are {@code true}.
     */
    public static <T> Predicate<T> and(
            Predicate<? super T> first, Predicate<? super T> second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);

        return t -> first.test(t) && second.test(t);
    }

    /**
     * Returns a predicate that evaluates to {@code true} if all of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end upon the first {@code false}
     * predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return A predicate who's result is {@code true} iff all component
     * predicates are {@code true}.
     */
    public static <T> Predicate<T> and(
            Iterable<? extends Predicate<? super T>> predicates) {
        Objects.requireNonNull(predicates);

        return t
                    -> {
            for (Predicate<? super T> predicate : predicates) {
                        if (!predicate.test(t)) {
                            return false;
                        }
                    }
                    return true;
                };
    }

    /**
     * Returns a predicate that evaluates to {@code true} if all of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end upon the first {@code false}
     * predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param first An initial predicate to be evaluated before the others.
     * @param predicates The predicates to be evaluated.
     * @return A predicate who's result is {@code true} iff all component
     * predicates are {@code true}.
     */
    static <T> Predicate<T> and(
            Predicate<? super T> first, Iterable<? extends Predicate<? super T>> predicates) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(predicates);

        return t
                    -> {
            if (!first.test(t)) {
                        return false;
                    }
                    for (Predicate<? super T> predicate : predicates) {
                        if (!predicate.test(t)) {
                            return false;
                        }
                    }
                    return true;
                };
    }

    /**
     * Returns a predicate that evaluates to {@code true} if all of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end upon the first {@code false}
     * predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return A predicate who's result is {@code true} iff all component
     * predicates are {@code true}.
     */
    @SafeVarargs
    public static <T> Predicate<T> and(
            Predicate<? super T>... predicates) {
        return and(Arrays.asList(predicates));
    }

    /**
     * Returns a predicate that evaluates to {@code true} if all of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end upon the first {@code false}
     * predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param first An initial predicate to be evaluated.
     * @param predicates The predicates to be evaluated.
     * @return A predicate who's result is {@code true} iff all component
     * predicates are {@code true}.
     */
    @SafeVarargs
    static <T> Predicate<T> and(
            Predicate<? super T> first, Predicate<? super T>... predicates) {
        return and(first, Arrays.asList(predicates));
    }

    /**
     * Returns a predicate that evaluates to {@code true} if any of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end upon the first {@code true}
     * predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param first initial component predicate to be evaluated.
     * @param second additional component predicate to be evaluated.
     * @return A predicate who's result is {@code true} if any component
     * predicate's result is {@code true}.
     */
    public static <T> Predicate<T> or(
            Predicate<? super T> first, Predicate<? super T> second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);

        return t -> first.test(t) || second.test(t);
    }

    /**
     * Returns a predicate that evaluates to {@code true} if any of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end upon the first {@code true}
     * predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return A predicate who's result is {@code true} if any component
     * predicate's result is {@code true}.
     */
    public static <T> Predicate<T> or(
            Iterable<? extends Predicate<? super T>> predicates) {
        Objects.requireNonNull(predicates);

        return t
                    -> {
            for (Predicate<? super T> predicate : predicates) {
                        if (predicate.test(t)) {
                            return true;
                        }
                    }
                    return false;
                };
    }

    /**
     * Returns a predicate that evaluates to {@code true} if any of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end upon the first {@code true}
     * predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return A predicate who's result is {@code true} if any component
     * predicate's result is {@code true}.
     */
    static <T> Predicate<T> or(
            Predicate<? super T> first, Iterable<? extends Predicate<? super T>> predicates) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(predicates);

        return t
                    -> {
            if (first.test(t)) {
                        return true;
                    }
                    for (Predicate<? super T> predicate : predicates) {
                        if (predicate.test(t)) {
                            return true;
                        }
                    }
                    return false;
                };
    }

    /**
     * Returns a predicate that evaluates to {@code true} if any of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will terminate upon the first
     * {@code true} predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return A predicate who's result is {@code true} if any component
     * predicate's result is {@code true}.
     */
    @SafeVarargs
    public static <T> Predicate<T> or(
            Predicate<? super T>... predicates) {
        return or(Arrays.asList(predicates));
    }

    /**
     * Returns a predicate that evaluates to {@code true} if any of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will terminate upon the first
     * {@code true} predicate.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return A predicate who's result is {@code true} if any component
     * predicate's result is {@code true}.
     */
    @SafeVarargs
    static <T> Predicate<T> or(
            Predicate<? super T> first, Predicate<? super T>... predicates) {
        return or(first, Arrays.asList(predicates));
    }

    /**
     * Returns a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end if a predicate result fails
     * to match the first predicate's result.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param first initial component predicate to be evaluated.
     * @param second additional component predicate to be evaluated.
     * @return a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}
     */
    public static <T> Predicate<T> xor(
            Predicate<? super T> first, Predicate<? super T> second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);

        return t -> first.test(t) ^ second.test(t);
    }

    /**
     * Returns a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end if a predicate result fails
     * to match the first predicate's result.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}
     */
    public static <T> Predicate<T> xor(
            Iterable<? extends Predicate<? super T>> predicates) {
        Objects.requireNonNull(predicates);


        return t
                    -> {
            Iterator<? extends Predicate<? super T>> iterator = predicates.iterator();
                    if (!iterator.hasNext()) {
                        return false;
                    }
                    boolean initial = iterator.next().test(t);
                    while (iterator.hasNext()) {
                        boolean current = iterator.next().test(t);
                        if (!(initial ^ current)) {
                            return false;
                        }
                    }
                    return true;
                };
    }

    /**
     * Returns a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end if a predicate result fails
     * to match the first predicate's result.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}
     */
    static <T> Predicate<T> xor(
            Predicate<? super T> first, Iterable<? extends Predicate<? super T>> predicates) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(predicates);

        return t
                    -> {
            boolean initial = first.test(t);
                    for (Predicate<? super T> predicate : predicates) {
                        if (!(initial ^ predicate.test(t))) {
                            return false;
                        }
                    }
                    return true;
                };
    }

    /**
     * Returns a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will terminate if a predicate result
     * fails to match the first predicate's result.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}
     */
    @SafeVarargs
    public static <T> Predicate<T> xor(Predicate<? super T>... predicates) {
        return xor(Arrays.asList(predicates));
    }

    /**
     * Returns a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}. The components are
     * evaluated in order, and evaluation will end if a predicate result fails
     * to match the first predicate's result.
     *
     * @param <T> the type of values evaluated by the predicates.
     * @param predicates The predicates to be evaluated.
     * @return a predicate that evaluates to {@code false} if all or none of the
     * component predicates evaluate to {@code true}
     */
    @SafeVarargs
    static <T> Predicate<T> xor(
            Predicate<? super T> first, Predicate<? super T>... predicates) {
        return xor(first, Arrays.asList(predicates));
    }
}
