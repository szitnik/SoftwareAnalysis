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

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A return object which may or may not contain a <code>int</code> value. If a value is present
 * then {@code isPresent()} will return {@code true} and {@code get()} will return successfully.
 * Additional methods that depend on presence or absence are provided, such as {@code orElse()}
 * (return a default value if not present) or {@code ifPresent()} (execute a block of code if
 * the value is present.)
 *
 * @author Brian Goetz
 */
public final class OptionalInt {
    /**
     * Common instance for {@code empty()}.
     */
    private final static OptionalInt EMPTY = new OptionalInt();

    /**
     * If true then the value is present, otherwise indicates no value is present
     */
    private final boolean isPresent;
    private final int value;

    private OptionalInt(int value) {
        this.isPresent = true;
        this.value = value;
    }

    private OptionalInt() {
        this.isPresent = false;
        this.value = 0;
    }

    /**
     * An empty object.
     *
     * Note: Though it may be tempting to do so, avoid testing if an object
     * is empty by comparing with {@code ==} against instances returned
     * {@code Option.empty()}. There is no guarantee that it is a singleton.
     * Instead, use {@code isPresent()}.
     *
     * @return an empty object.
     */
    @SuppressWarnings("unchecked")
    public static OptionalInt empty() {
        return EMPTY;
    }

    /**
     * Create a new Optional with a present value
     * @param value The value
     */
    public static OptionalInt of(int value) {
        return new OptionalInt(value);
    }

    /**
     * Returns the value held by this object.
     *
     * @return the value of this object.
     * @throws java.util.NoSuchElementException if there is no value present.
     */
    public int getAsInt() {
        if (!isPresent) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    /**
     * Return {@code true} if there is a value present otherwise {@code false}.
     * @return {@code true} if there is a value present otherwise {@code false}.
     */
    public boolean isPresent() {
        return isPresent;
    }

    /**
     * Execute the specified block with the value if a value is present
     */
    public void ifPresent(IntConsumer block) {
        if (isPresent)
            block.accept(value);
    }

    /**
     * Return the value if present otherwise return {@code other}.
     *
     * @param other value to be returned if there is no value present.
     * @return the value if present otherwise return {@code other}.
     */
    public int orElse(int other) {
        return isPresent ? value : other;
    }

    /**
     * Return the value if present otherwise return result of {@code other}.
     *
     * @param other Supplier who's result is returned if there is no value present.
     * @return the value if present otherwise return result of {@code other}.
     */
    public int orElse(IntSupplier other) {
        return isPresent ? value : other.getAsInt();
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
    public<V extends Throwable> int orElseThrow(Supplier<V> exceptionFactory) throws V {
        if (isPresent) {
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
    public<V extends Throwable> int orElseThrow(Class<V> exceptionClass) throws V {
        if (isPresent) {
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

        OptionalInt other = (OptionalInt) o;
        return (isPresent && other.isPresent) ? value == other.value : isPresent == other.isPresent;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return isPresent ? String.format("IntOptional[%s]", value) : "IntOptional.empty";
    }
}
