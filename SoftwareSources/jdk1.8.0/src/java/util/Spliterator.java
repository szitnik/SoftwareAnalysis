/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A provider of element traversal operations for a possibly-parallel
 * computation.  These operations proceed either individually ({@link
 * #tryAdvance}) or in bulk ({@link #forEach}).  The source of
 * elements covered by a Spliterator may for example be an array, a
 * {@link Collection}, an IO channel, or a generator function.
 *
 * <p>Spliterators also report {@link #characteristics()} of their
 * structure, sources, and elements from among {@link #ORDERED},
 * {@link #DISTINCT}, {@link #SORTED}, {@link #SIZED}, {@link
 * #INFINITE}, {@link #NONNULL}, {@link #IMMUTABLE}, {@link
 * #CONCURRENT}, and {@link #UNIFORM}. These may be employed by
 * Spliterator clients to control, specialize or simplify computation.
 * Characteristics are reported as a simple unioned bit set that
 * reflects properties familiar from {@link Collection} subtypes (for
 * example a Spliterator for a {@link Set} must report {@code
 * DISTINCT} elements), as well as those that may hold only for a
 * particular Spliterator. Some characteristics additionally constrain
 * method behavior; for example if {@code ORDERED}, traversal methods
 * must conform to their documented ordering. New characteristics may
 * be defined in the future, so implementors should not assign
 * meanings to unlisted values.
 *
 * <p> To accommodate broad usage, this interface places no further
 * absolute demands on Spliterators. However, most default methods are
 * sub-optimal for most Spliterator implementations, and so should be
 * overridden whenever appropriate.
 *
 * <p>A Spliterator may partition off some of its elements (using
 * {@link #trySplit}) as another Spliterator, to be used in
 * possibly-parallel operations. Operations using a Spliterator that
 * cannot split, or does so in a highly imbalanced or inefficient
 * manner, are unlikely to benefit from parallelism.
 *
 * <p>Method {@link #estimateSize} provides an estimate of the number
 * of elements. Ideally, as reflected in characteristic {@link
 * #SIZED}, this value corresponds exactly to the number of elements
 * that would be encountered in a successful traversal.  However, even
 * when not exactly known, the value may be used to estimate the
 * utility of performing splits and related operations.
 *
 * <p>Spliterators are designed for use in traversal or splitting by
 * only one thread at a time.  Thus, for example, the effects of two
 * threads both concurrently invoking {@code tryAdvance()} on the same
 * Spliterator are undefined. Also, because traversal and splitting
 * exhaust elements, each Spliterator is useful for only a single bulk
 * computation.
 *
 * <p><b>Example.</b> Here is a class (not a very useful one except
 * for illustration) that maintains an array in which the actual data
 * are held in even locations, and unrelated tag data are held in odd
 * locations. Its Spliterator ignores the tags.
 *
 * <pre>{@code
 * class TaggedArray {
 *   private final Object[] elements; // immutable after construction
 *   TaggedArray(Object[] data, Object[] tags) {
 *     int size = data.length;
 *     if (tags.length != size) throw new IllegalArgumentException();
 *     this.elements = new Object[2 * size];
 *     for (int i = 0, j = 0; i < size; ++i) {
 *       elements[j++] = data[i];
 *       elements[j++] = tags[i];
 *     }
 *   }
 *   public Spliterator<Object> spliterator() {
 *     return new TaggedArraySpliterator(elements, 0, elements.length);
 *   }
 *
 *   static class TaggedArraySpliterator implements Spliterator<Object> {
 *     private final Object[] array;
 *     private int origin; // current index, advanced on split or traversal
 *     private final int fence; // one past the greatest index
 *
 *     TaggedArraySpliterator(Object[] array, int origin, int fence) {
 *       this.array = array; this.origin = origin; this.fence = fence;
 *     }
 *     public void forEach(Consumer<Object> action) {
 *       for (; origin < fence; origin += 2)
 *         action.accept(array[origin]);
 *     }
 *     public boolean tryAdvance(Consumer<Object> action) {
 *       if (origin < fence) {
 *         action.accept(array[origin]);
 *         origin += 2;
 *         return true;
 *       }
 *       else // cannot advance
 *         return false;
 *     }
 *     public Spliterator<Object> trySplit() {
 *       int lo = origin; // divide range in half
 *       int mid = ((lo + fence) >>> 1) & ~1; // force midpoint to be even
 *       if (lo < mid) {  // split out left half
 *         origin = mid;  // reset this Spliterator's origin
 *         return new TaggedArraySpliterator(array, lo, mid);
 *       }
 *       else             // too small to split
 *         return null;
 *     }
 *     public long estimateSize() {
 *       return (long)((fence - origin) / 2);
 *     }
 *     public int characteristics() {
 *       return ORDERED | SIZED | IMMUTABLE | UNIFORM;
 *     }
 *   }
 * }}</pre>
 *
 * <p>As an example how this might be used in a parallel computation,
 * here is one way to implement an associated parallel forEach, that
 * illustrates the primary usage idiom of splitting off subtasks until
 * the estimated amount of work is small enough to perform
 * sequentially. Here we assume that the order of processing across
 * subtasks doesn't matter; different (forked) tasks may further split
 * and process elements concurrently in undetermined order.  This
 * example uses a {@link java.util.concurrent.CountedCompleter};
 * similar usages apply to other parallel task constructions.
 *
 * <pre>{@code
 * static void parEach(TaggedArray a, Consumer<Object> action) {
 *   TaggedArraySpliterator s = a.spliterator();
 *   long targetBatchSize = s.estimateSize() / (ForkJoinPool.getCommonPoolParallelism() * 8);
 *   new ParEach(null, s, action, targetBatchSize).invoke();
 * }
 * static class ParEach extends CountedCompleter<Void> {
 *   final TaggedArraySpliterator spliterator;
 *   final Consumer<Object> action;
 *   final long targetBatchSize;
 *   ParEach(ParEach parent, TaggedArraySpliterator spliterator, 
 *           Consumer<Object> action, long targetBatchSize) {
 *     super(parent);
 *     this.spliterator = spliterator; this.action = action;
 *     this.targetBatchSize = targetBatchSize;
 *   }
 *   public void compute() {
 *     TaggedArraySpliterator sub;
 *     while (spliterator.estimateSize() > targetBatchSize &&
 *            (sub = spliterator.trySplit()) != null) {
 *       addToPendingCount(1);
 *       new ParEach(spliterator, sub, action, targetBatchSize).fork();
 *     }
 *     spliterator.forEach(action);
 *     propagateCompletion();
 *   }
 * }}</pre>
 *
 * @since 1.8
 */
public interface Spliterator<T> {
    /**
     * If a remaining element exists, performs the given action on it,
     * returning {@code true}; else returns {@code false}.  If this
     * Spliterator is {@link #ORDERED} the action is performed on the
     * next element in encounter order.  Exceptions occurring as a
     * result of performing this action are relayed to the caller.
     *
     * @param action The action
     * @return {@code false} if no remaining elements existed
     * upon entry to this method, else {@code true}.
     */
    boolean tryAdvance(Consumer<? super T> action);

    /**
     * Performs the given action for all untraversed elements.  If
     * this Spliterator is {@link #ORDERED}, actions are performed in
     * encounter order.  Exceptions occurring as a result of
     * performing this action are relayed to the caller.
     *
     * <p> The default implementation repeatedly invokes
     * {@link #tryAdvance} until it returns {@code false}.
     *
     * @param action The action
     */
    default void forEach(Consumer<? super T> action) {
        do { } while (tryAdvance(action));
    }

    /**
     * If this spliterator can be partitioned, returns a Spliterator
     * covering elements, that will, upon return from this method, not
     * be covered by this Spliterator.  If this Spliterator is {@link
     * #ORDERED}, the returned Spliterator must cover a strict prefix
     * of the elements.  Unless this Spliterator is {@link #INFINITE},
     * repeated calls to {@code trySplit()} must eventually return
     * {@code null}. Upon non-null return, the value reported for
     * {@code estimateSize()} for this and/or the returned Spliterator
     * must decrease unless already zero.
     *
     * <p>This method may return {@code null} for any reason,
     * including emptiness, inability to split after traversal has
     * commenced, data structure constraints, and efficiency
     * considerations.
     *
     * <p> An ideal {@code trySplit} method efficiently (without
     * traversal) divides its elements exactly in half, allowing
     * balanced parallel computation.  Most departures from this ideal
     * remain highly effective; for example, only approximately
     * splitting an approximately balanced tree, or for a tree in
     * which leaf nodes may contain either one or two elements,
     * failing to further split these nodes.  However, large
     * deviations in balance and/or overly inefficient {@code
     * trySplit} mechanics typically result in poor parallel
     * performance.
     *
     * <p> The default implementation of this method always returns
     * {@code null}. It should be overridden whenever possible.
     *
     * @return a {@code Spliterator} covering some portion of the
     * elements, or {@code null} if this spliterator cannot be split
     */
    default Spliterator<T> trySplit() {
        return null;
    }

    /**
     * Returns an estimate of the number of elements that would be
     * encountered by a {@link #forEach} traversal, or returns {@link
     * Long#MAX_VALUE} if infinite, unknowable, or if computing this
     * value itself requires one-by-one traversal or other significant
     * computation.  If this Spliterator is {@link #SIZED}, this
     * estimate must represent an accurate count of elements, but need
     * not reflect the effects of completed or in-progress traversal
     * steps. Or, if this Spliterator covers an infinite number of
     * elements, it must also report characteristic {@link #INFINITE}.
     * Otherwise, this estimate may be arbitrarily inaccurate, but
     * must decrease as specified across invocations of {@link
     * #trySplit}.
     *
     * <p>For example, a sub-spliterator of an approximately balanced
     * binary tree may return a value that estimates the number of
     * elements to be half of that of its parent. And if the root
     * Spliterator does not maintain an accurate count, it could
     * estimate size to be the power of two corresponding to its
     * maximum depth.
     *
     * <p>The default implementation returns {@code Long.MAX_VALUE},
     * signifying lack of an estimate. It should be overridden
     * whenever possible.
     *
     * @return the estimated size, or {@code Long.MAX_VALUE} if
     * infinite, unknown, or too expensive to compute.
     */
    default long estimateSize() {
        return Long.MAX_VALUE;
    }

    /**
     * Returns a set of characteristics of this Spliterator and its
     * elements. The result is represented as ORed values from {@link
     * #ORDERED}, {@link #DISTINCT}, {@link #SORTED}, {@link #SIZED},
     * {@link #INFINITE}, {@link #NONNULL}, {@link #IMMUTABLE}, {@link
     * #CONCURRENT}, {@link #UNIFORM}.  The returned value need not
     * reflect effects of any completed or in-progress traversal.
     *
     * <p> If a Spliterator reports an inconsistent set of
     * characteristics (either those returned from a single invocation
     * or across multiple invocations), no guarantees can be made
     * about about any computation using this Spliterator.
     *
     * @return a representation of characteristics
     */
    int characteristics();

    /**
     * Returns {@code true} if this Spliterator's {@link
     * #characteristics} contains the given characteristic.
     *
     * <p>The default implementation returns true if the corresponding
     * bit is set.
     *
     * @return {@code true} if present, else {@code false}
     */
    default boolean hasCharacteristic(int characteristic) {
        return (characteristics() & characteristic) != 0;
    }

    /**
     * If this source supports characteristic {@link #SORTED} based on
     * a {@link Comparator}, returns it, else returns {@code null}.
     *
     * <p>The default implementation returns {@code null}.
     *
     * @return a Comparator, or {@code null} if none.
     */
    default Comparator<? super T> getComparator() {
        return null;
    }

    /**
     * Convenience method that returns {@link #estimateSize()} if this
     * Spliterator is {@link #SIZED}, else {@code -1}.
     *
     * <p>The default implementation does exactly this.
     *
     * @return the size, if known, else {@code -1}.
     */
    default long getExactSizeIfKnown() {
        return (characteristics() & SIZED) == 0 ? -1L : estimateSize();
    }

    // Characteristic values are aligned with corresponding flag values
    // in java.util.stream.StreamOpFlag. This enables efficient
    // bit set conversion of Spliterator characteristics to and
    // from stream flags.
    // Each stream flag value occupies 2 bits to indicate whether
    // a flag is set, cleared or preserved by operations in a
    // pipeline. This is the reason why the characteristic values
    // are spaced with a padding bit and essentially occupy 2 bits
    // even through only the lower bit is utilized.
    // NOTE: if modifications, additions or deletions are
    // made to characteristic values it is important to ensure
    // such modifications are reflected in the corresponding mapping
    // of stream flags values.

    /**
     * Characteristic value signifying that, for each pair of
     * encountered elements {@code x, y}, {@code !x.equals(y)}. This
     * applies for example, to a Spliterator based on a {@link Set}.
     */
    public static final int DISTINCT   = 0x00000001;

    /**
     * Characteristic value signifying that encounter order follows a
     * defined sort order. If so, method {@link #getComparator()}
     * returns the associated Comparator, or {@code null} if all
     * elements are {@link Comparable} and use {@code compareTo} for
     * ordering.  For example, a Spliterator based on a {@link
     * NavigableSet} or {@link SortedSet} reports {@code SORTED}.
     */
    public static final int SORTED     = 0x00000004;

    /**
     * Characteristic value signifying that an encounter order is
     * defined for elements. If so, this Spliterator guarantees that
     * method {@link #trySplit} splits a strict prefix of elements,
     * that method {@link #tryAdvance} steps by one element in prefix
     * order, and that {@link #forEach} performs actions in encounter
     * order. For a {@link Collection}, encounter order is the
     * documented order of the corresponding {@link
     * Collection#iterator}, if such an order is defined.  For
     * example, encounter order is guaranteed to be ascending index
     * order for any {@link List}. But no order is guaranteed for
     * hash-based collections such as {@code HashSet}.  Clients of a
     * Spliterator that reports {@code ORDERED} are expected preserve
     * ordering constraints in non-commutative parallel computations.
     */
    public static final int ORDERED    = 0x00000010;

    /**
     * Characteristic value signifying that the value returned from
     * {@code estimateSize()} represents a finite size that, in the
     * absence of structural source modification, is fixed throughout
     * operations using this Spliterator.  Most Spliterators based on
     * Collections have this property.  Sub-spliterators that
     * approximate their reported size do not.
     */
    public static final int SIZED      = 0x00000040;

    /**
     * Characteristic value signifying that an unbounded number of
     * elements would be encountered in an invocation of {@code
     * forEach}, so it would never non-exceptionally terminate.
     * Unless a Spliterator reports {@code INFINITE}, uses of a
     * Spliterator may proceed under the assumption that such a
     * traversal will eventually complete.
     */
    public static final int INFINITE   = 0x00000100;

    /**
     * Characteristic value signifying that the source guarantees that
     * each encountered element cannot be {@code null}. This applies
     * for example to most concurrent collections, queues, and maps.
     */
    public static final int NONNULL    = 0x00000400;

    /**
     * Characteristic value signifying that the element source cannot
     * be structurally modified; that is, elements cannot be added,
     * replaced, or removed, so such changes cannot occur during
     * traversal. A Spliterator that does not report {@code IMMUTABLE}
     * or {@code CONCURRENT} is expected to have a documented policy
     * (for example throwing {@link ConcurrentModificationException})
     * concerning structural interference detected during traversal.
     */
    public static final int IMMUTABLE  = 0x00001000;

    /**
     * Characteristic value signifying that the element source may be
     * safely concurrently modified (allowing additions, replacements,
     * and/or removals) by multiple threads without external
     * synchronization. If so, the Spliterator is expected to have a
     * documented policy concerning the impact of modifications during
     * traversal. For example, most concurrent collections maintain a
     * consistency policy guaranteeing accuracy with respect to
     * elements present at the point of Spliterator construction, but
     * possibly not reflecting subsequent additions or removals.
     */
    public static final int CONCURRENT = 0x00004000;

    /**
     * Characteristic value signifying that all Spliterators resulting
     * from {@code trySplit()}, as well as any that they in turn
     * split, report the same {@code characteristics()} as this
     * Spliterator.  This does not hold, for example, for sources in
     * which the initial Spliterator is {@code SIZED}, but
     * sub-spliterators are not, because their reported {@code
     * estimateSize()} is only approximate.
     */
    public static final int UNIFORM    = 0x00010000;

    /**
     * Specialization for int.
     */
    public interface OfInt extends Spliterator<Integer> {

        @Override
        OfInt trySplit();

        boolean tryAdvance(IntConsumer consumer);

        default void forEach(IntConsumer consumer) {
            while (tryAdvance(consumer)) { }
        }

        @Override
        default boolean tryAdvance(Consumer<? super Integer> consumer) {
            if (consumer instanceof IntConsumer) {
                return tryAdvance((IntConsumer) consumer);
            }
            else {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "{0} using boxed int in forEach", getClass().getName());
                return tryAdvance((IntConsumer) consumer::accept);
            }
        }

        @Override
        default void forEach(Consumer<? super Integer> consumer) {
            if (consumer instanceof IntConsumer) {
                forEach((IntConsumer) consumer);
            }
            else {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "{0} using boxed int in forEach", getClass().getName());
                forEach((IntConsumer) consumer::accept);
            }
        }
    }

    /**
     * Specialization for long.
     */
    public interface OfLong extends Spliterator<Long> {

        @Override
        OfLong trySplit();

        boolean tryAdvance(LongConsumer consumer);

        default void forEach(LongConsumer consumer) {
            while (tryAdvance(consumer)) { }
        }

        @Override
        default boolean tryAdvance(Consumer<? super Long> consumer) {
            if (consumer instanceof LongConsumer) {
                return tryAdvance((LongConsumer) consumer);
            }
            else {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "{0} using boxed long in forEach", getClass().getName());
                return tryAdvance((LongConsumer) consumer::accept);
            }
        }

        @Override
        default void forEach(Consumer<? super Long> consumer) {
            if (consumer instanceof LongConsumer) {
                forEach((LongConsumer) consumer);
            }
            else {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "{0} using boxed long in forEach", getClass().getName());
                forEach((LongConsumer) consumer::accept);
            }
        }
    }

    /**
     * Specialization for double.
     */
    public interface OfDouble extends Spliterator<Double> {

        @Override
        OfDouble trySplit();

        boolean tryAdvance(DoubleConsumer consumer);

        default void forEach(DoubleConsumer consumer) {
            while (tryAdvance(consumer)) { }
        }

        @Override
        default boolean tryAdvance(Consumer<? super Double> consumer) {
            if (consumer instanceof DoubleConsumer) {
                return tryAdvance((DoubleConsumer) consumer);
            }
            else {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "{0} using boxed double in forEach", getClass().getName());
                return tryAdvance((DoubleConsumer) consumer::accept);
            }
        }

        @Override
        default void forEach(Consumer<? super Double> consumer) {
            if (consumer instanceof DoubleConsumer) {
                forEach((DoubleConsumer) consumer);
            }
            else {
                Logger.getLogger(getClass().getName()).log(Level.WARNING, "{0} using boxed double in forEach", getClass().getName());
                forEach((DoubleConsumer) consumer::accept);
            }
        }
    }
}
