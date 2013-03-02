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

import java.lang.reflect.Constructor;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Map;
import java.util.Objects;

/**
 * Static utility methods pertaining to {@code Mapper} instances.
 *
 * <p>All of the returned functions are serializable if provided serializable
 * parameters.
 */
public final class Functions {

    /**
     * singleton utils
     */
    private Functions() {
        throw new AssertionError("No instances!");
    }

     /**
     * Returns a mapper which who's {@code apply} method returns the provided
     * input. Useful when a Mapper is required and {@code <T>} and {@code <U>}
     * are the same type.
     */
    public static <T> Function<T, T> identity() {
        return t -> t;
    }

//    /**
//     * Returns a mapper which who's {@code apply} method returns a clone of the
//     * provided input.
//     */
//    public static <T> Mapper<T, T> cloneOf() {
//        return t -> {
//            try {
//                return t.clone();
//            } catch (CloneNotSupportedException ex) {
//                throw new UndeclaredThrowableException(ex);
//            }
//        };
//    }
//
    /**
     * Returns a mapper which performs a mapping from {@code <T>} to it's
     * string representation.
     *
     * @param <T> Type of input values
     * @return a mapper which performs a mapping from {@code <T>} to it's
     * string representation
     */
    public static <T> Function<T, String> string() {
        return String::valueOf;
    }

    /**
     * Returns a mapper which performs a mapping from {@code <T>} to {@code <U>}
     * followed by a mapping from {@code <U>} to {@code <V>}.
     *
     * @param <R> Type for final mapped values. May be the same type as
     * {@code <U>}.
     * @param <T> Type for input values
     * @param <U> Type for intermediate mapped values. May be the same type as
     * {@code <T>}.
     * @param first Initial mapping from {@code <T>} to {@code <U>}.
     * @param second additional mapping from {@code <U>} to {@code <V>}.
     */
    public static <R, T, U> Function<T, R> chain(
            Function<? super T, ? extends U> first,
            Function<? super U, ? extends R> second) {
        Objects.requireNonNull(first);
        Objects.requireNonNull(second);

        return t -> second.apply(first.apply(t));
    }

    /**
     * Returns a constant output regardless of input.
     *
     * @param constant The value to be returned by the {@code apply} method.
     * @return a mapper who's {@code apply} method provides a constant result.
     */
    public static <R, T> Function<T, R> constant(R constant) {
        return t -> constant;
    }

    /**
     * A mapper that substitutes a single input value with a specified
     * replacement. Input values are compared using {@code equals()}.
     *
     * @param <T> The type of values.
     * @param subOut The input value to be substituted out.
     * @param subIn The replacement value for matching inputs.
     * @return a mapper that substitutes a single input value with a specified
     * replacement.
     */
    public static <T> Function<T, T> substitute(T subOut, T subIn) {
        return t -> Objects.equals(subOut, t) ? subIn : t;
    }

    /**
     * Returns a new instance of {@code <U>} constructed with provided
     * {@code <T>}.
     *
     * @param <R> Type of output values from mapping
     * @param <T> Type of input values to mapping
     * @param clazzT The {@code Class} which defines objects of type {@code <T>}
     * @param clazzR The {@code Class} which defines objects of type {@code <U>}
     * @return a mapper which creates instances of {@code <R>} using {@code <T>}
     * as the constructor parameter.
     * @throws NoSuchMethodException when {@code <R>} has no constructor which
     * takes a {@code <T>} as a parameter.
     */
    public static <R, T> Function<T, R> instantiate(Class<? extends T> clazzT, Class<? extends R> clazzR) {
        Objects.requireNonNull(clazzT);
        Objects.requireNonNull(clazzR);

        final Constructor<? extends R> constructor;
        try {
            constructor = clazzR.getConstructor(clazzT);
        } catch(NoSuchMethodException noConstructor) {
            throw new IllegalArgumentException("no constructor for "+ clazzR.getSimpleName() + "(" + clazzT.getSimpleName() + ")", noConstructor);
        }

        return t -> {
            try {
                return constructor.newInstance(t);
            } catch (ReflectiveOperationException ex) {
                // XXX mduigou argument for exception transparency?
                throw new UndeclaredThrowableException(ex);
            }
        };
    }

    /**
     * Map according to the provided apply. Attempting to apply a value not from the
     * given apply will cause an {@code IllegalArgumentException} to be thrown.
     * A copy is <strong>not</strong> made of the apply. Care should be taken to
     * avoid changes to the apply during operation may produce results which
     * violate the {@code apply} method contract.
     *
     * @param <R> output type from mapping operation
     * @param <T> input type to mapping operation
     * @param map provides the mappings from {@code <T>} to {@code <U>}
     * @throws IllegalArgumentException for all values of {@code <T>} not
     * present in the apply
     */
    public static <R, T> Function<T, R> forMap(Map<? super T, ? extends R> map) {
        Objects.requireNonNull(map);

        return t -> {
            if (map.containsKey(t)) {
                return map.get(t);
            } else {
                throw new IllegalArgumentException("unmappable <T> : " + t);
            }
        };
    }

    /**
     * Map according to the provided mapping. The provided default value is
     * returned for all {@code <T>} keys not found in the apply. A copy is
     * <strong>not</strong> made of the apply and care should be taken to avoid
     * changes to the apply during operation may produce results which violate the
     * {@code apply} method contract.
     *
     * @param <R> output type from mapping
     * @param <T> input type to apply
     * @param map provides the mappings from {@code <T>} to {@code <U>}
     * @param defaultValue the value returned by {@code apply} method for
     * {@code <T>} values not contained in the provided apply
     */
    public static <R, T> Function<T, R> forMap(Map<? super T, ? extends R> map, R defaultValue) {
        Objects.requireNonNull(map);

        return t -> map.containsKey(t) ? map.get(t) : defaultValue;
    }

    /**
     * Map according to the provided predicate. Two output values are provided
     * {@code forTrue} is returned if the predicate returns {@code true}
     * otherwise the {@code forFalse} value is returned.
     *
     * @param <R> output type from mapping
     * @param <T> input type to apply
     * @param predicate decides which value {@code apply} method should return
     * @param forTrue value to be returned for {@code true} predicate results
     * @param forFalse value to be returned for {@code false} predicate results
     * @return a Mapper who's {@code apply} method provides results according to
     * the provided predicate.
     */
    public static <R, T> Function<T, R> forPredicate(Predicate<? super T> predicate, R forTrue, R forFalse) {
        Objects.requireNonNull(predicate);

        return t -> predicate.test(t) ? forTrue : forFalse;
    }
}
