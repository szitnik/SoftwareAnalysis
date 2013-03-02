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
package java.util;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A return object which may or may not contain a non-null value. If a value is present
 * then {@code isPresent()} will return {@code true} and {@code get()} will return successfully.
 * Additional methods that depend on presence or absence are provided, such as {@code orElse()}
 * (return a default value if not present) or {@code ifPresent()} (execute a block of code if
 * the value is present.)
 *
 * @author Brian Goetz
 */
public final class Optional<T> {
    /**
     * Common instance for {@code empty()}.
     */
    private final static Optional<?> EMPTY = new Optional<>();

    /**
     * If non-null, the value; if null, incdicates no value is present
     */
    private final T value;

    private Optional(T value) {
        this.value = Objects.requireNonNull(value);
    }

    private Optional() {
        this.value = null;
    }

    /**
     * An empty object.
     *
     * Note: Though it may be tempting to do so, avoid testing if an object
     * is empty by comparing with {@code ==} against instances returned
     * {@code Option.empty()}. There is no guarantee that it is a singleton.
     * Instead, use {@code isPresent()}.
     *
     * @param <T> Type of the non-existent value.
     * @return an empty object.
     */
    @SuppressWarnings("unchecked")
    public static<T> Optional<T> empty() {
        return (Optional<T>) EMPTY;
    }

    /**
     * Create a new Optional with a present value
     * @param value The value, must be non-null
     */
    public static <T> Optional<T> of(T value) {
        return new Optional<>(value);
    }

    /**
     * Returns the value held by this object.
     *
     * @return the value of this object.
     * @throws NoSuchElementException if there is no value present.
     */
    public T get() {
        if (value == null) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    /**
     * Return {@code true} if there is a value present otherwise {@code false}.
     * @return {@code true} if there is a value present otherwise {@code false}.
     */
    public boolean isPresent() {
        return value != null;
    }

    /**
     * Execute the specified block with the value if a value is present
     */
    public void ifPresent(Consumer<? super T> consumer) {
        if (value != null)
            consumer.accept(value);
    }

    /**
     * Return the value if present otherwise return {@code other}.
     *
     * @param other value to be returned if there is no value present.
     * @return the value if present otherwise return {@code other}.
     */
    public T orElse(T other) {
        return value != null ? value : other;
    }

    /**
     * Return the value if present otherwise return result of {@code other}.
     *
     * @param other Supplier who's result is returned if there is no value present.
     * @return the value if present otherwise return result of {@code other}.
     */
    public T orElse(Supplier<T> other) {
        return value != null ? value : other.get();
    }

    /**
     * Return the value otherwise throw an exception to be created by the
     * provided factory.
     *
     * @param <V> Type of the exception to be thrown.
     * @param exceptionFactory The factory which will return the exception to
     * be thrown.
     * @return the value.
     * @throws V if there is no value present.
     */
    public<V extends Throwable> T orElseThrow(Supplier<V> exceptionFactory) throws V {
        if (value != null) {
            return value;
        } else {
            throw exceptionFactory.get();
        }
    }

    /**
     * Return the value otherwise throw an exception of the provided class.
     * Exception will be thrown with the message "No value present".
     *
     * @param <V> Type of the exception to be thrown.
     * @param exceptionClass The class if exception to be thrown. Must support
     * the default zero arguments constructor.
     * @return the value.
     * @throws V if there is no value present.
     */
    public<V extends Throwable> T orElseThrow(Class<V> exceptionClass) throws V {
        if (value != null) {
            return value;
        } else {
            try {
                throw exceptionClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new IllegalStateException("Unexpected exception attempting to throw " + exceptionClass, e);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        else if (o == null || getClass() != o.getClass())
            return false;

        Optional other = (Optional) o;
        return (value == null) ? (other.value == null) : value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public String toString() {
        return value != null ? String.format("Optional[%s]", value) : "Optional.empty";
    }
}
