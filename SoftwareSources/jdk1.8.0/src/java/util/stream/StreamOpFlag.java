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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Flags describing dynamic properties of streams, such as sortedness or distinctness.
 * <p/>
 * Streams either have, or do not have, the specified property.  Intermediate stream
 * operations can describe whether it preserves, clears, or injects the specified
 * property.  So, for example, a {@code Stream} derived from a {@code List} has the
 * "size known" property, and executing the {@code map()} operation on that stream
 * preserves sized-ness, whereas the {@code filter()} operation clears sized-ness.
 * Most flags can apply either to a stream or a stream operation, though some can only
 * apply to stream operations (such as {@code SHORT_CIRCUIT}.)
 * <p/>
 * Combinations of flags are generally described using int-sized bitmaps for efficiency,
 * with each flag (generally) taking two bits.  There are three forms of the bitmap:
 * <p/>
 * <ul>
 * <li>The stream form, where one bit is used to encode whether a
 * stream property is set or not set;</li>
 * <li>The operation form, where two bits are used to encode whether the
 * operation clears or injects a stream property,
 * or whether the operation sets a stream operation property; and</li>
 * <li>The combined operation form, where two bits are used to encode whether a
 * stream property is set, preserved or cleared, or whether a stream operation property is set.</li>
 * </ul>
 * <p/>
 * For each flag, there is an enum value ({@code SIZED}) as well as derived constants for
 * set ({@code IS_SIZED}) and cleared ({@code NOT_SIZED}).  To construct the stream form
 * of flags, OR together the {@code IS} flags ({@code IS_SORTED | IS_DISTINCT}), or to
 * clear, mask off the {@code IS} flags.  To construct the operation form, you need to
 * OR together the {@code IS} or {@code NOT} forms.
 * <p/>
 * To combine the stream flags with the operation flags invoke {@link #combineOpFlags(int, int)} using the
 * stream flags as the first parameter and {@link #INITIAL_OPS_VALUE} as the second parameter, then combine all the
 * operation flags with {@link #combineOpFlags(int, int)} to produce combined stream and operation flags,
 * finally if stream flags are required invoke {@link #toStreamFlags(int)} using the combined stream and operation
 * flags as the parameter.
 */
// @@@ When a new flag is added what should happen for existing operations?
//     Need to move to a builder approach used by ops where the masks for the new flag are
//     taken into account for default behaviour.
enum StreamOpFlag {

    /*
     * Mask table for flag types:
     *
     *                        DISTINCT  SORTED  ORDERED  SIZED  SHORT_CIRCUIT  PARALLEL
     *          SPLITERATOR      01       01       01      01        00           00
     *               STREAM      01       01       01      01        00           01
     *                   OP      11       11       11      10        01           10
     *          TERMINAL_OP      00       00       10      00        01           00
     * UPSTREAM_TERMINAL_OP      00       00       10      00        00           00
     *
     */

    // The following flags correspond to characteristics on Spliterator
    // and the values MUST be the equal.
    //
    // 0, 0x00000001
    // Matches Spliterator.DISTINCT
    DISTINCT(0,
             set(Type.SPLITERATOR).set(Type.STREAM).setAndClear(Type.OP)),
    // 1, 0x00000004
    // Matches Spliterator.SORTED
    SORTED(1,
           set(Type.SPLITERATOR).set(Type.STREAM).setAndClear(Type.OP)),
    // 2, 0x00000010
    // Matches Spliterator.ORDERED
    ORDERED(2,
            set(Type.SPLITERATOR).set(Type.STREAM).setAndClear(Type.OP).clear(Type.TERMINAL_OP).clear(Type.UPSTREAM_TERMINAL_OP)),
    // 3, 0x00000040
    // Matches Spliterator.SIZED
    SIZED(3,
          set(Type.SPLITERATOR).set(Type.STREAM).clear(Type.OP)),

    // The following Spliterator characteristics are not currently used but a
    // gap in the bit set is deliberately retained to enable corresponding stream
    // flags if//when required without modification to other flag values.
    //
    // 4, 0x00000100 INFINITE(4, ...
    // 5, 0x00000400 NONNULL(5, ...
    // 6, 0x00001000 IMMUTABLE(6, ...
    // 7, 0x00004000 CONCURRENT(7, ...
    // 8, 0x00010000 UNIFORM(8, ...)

    // The following 3 flags are currently undefined and a free for any further
    // spliterator characteristics.
    //
    //  9, 0x00040000
    // 10, 0x00100000
    // 11, 0x00400000

    // The following flags are specific to streams and operations
    //
    // 12, 0x01000000
    SHORT_CIRCUIT(12,
                  set(Type.OP).set(Type.TERMINAL_OP)),
    // 13, 0x04000000
    PARALLEL(13,
             set(Type.STREAM).clear(Type.OP));

    // The following 2 flags are currently undefined and a free for any further
    // stream flags if/when required
    //
    // 14, 0x10000000
    // 15, 0x40000000

    // Type of flag
    enum Type {
        SPLITERATOR,
        STREAM,
        OP,
        TERMINAL_OP,
        UPSTREAM_TERMINAL_OP
    }

    private static final int SET_BITS = 0b01;

    /**
     * The bit pattern for clearing a flag.
     */
    private static final int CLEAR_BITS = 0b10;

    /**
     * The bit pattern for preserving a flag.
     */
    private static final int PRESERVE_BITS = 0b11;

    private static MaskBuilder set(Type t) {
        return new MaskBuilder(new HashMap<Type, Integer>()).set(t);
    }

    private static class MaskBuilder {
        final Map<Type, Integer> map;

        MaskBuilder(Map<Type, Integer> map) {
            this.map = map;
        }

        MaskBuilder mask(Type t, Integer i) {
            map.put(t, i);
            return this;
        }

        MaskBuilder set(Type t) {
            return mask(t, SET_BITS);
        }

        MaskBuilder clear(Type t) {
            return mask(t, CLEAR_BITS);
        }

        MaskBuilder setAndClear(Type t) {
            return mask(t, PRESERVE_BITS);
        }

        Map<Type, Integer> build() {
            for (Type t : Type.values()) {
                map.putIfAbsent(t, 0b00);
            }
            return map;
        }
    }

    private final Map<Type, Integer> maskTable;

    private final int bitPosition;

    private final int set;

    private final int clear;

    private final int preserve;

    private StreamOpFlag(int position, MaskBuilder maskBuilder) {
        this.maskTable = maskBuilder.build();
        // Two bits per flag
        position *= 2;
        this.bitPosition = position;
        this.set = SET_BITS << position;
        this.clear = CLEAR_BITS << position;
        this.preserve = PRESERVE_BITS << position;
    }

    /**
     *
     * @return the set flag.
     */
    int set() {
        return set;
    }

    /**
     *
     * @return the clear flag.
     */
    int clear() {
        return clear;
    }

    /**
     *
     * @return true if a stream-based flag, otherwise false.
     */
    boolean isStreamFlag() {
        return maskTable.get(Type.STREAM) > 0;
    }

    /**
     * Check if this flag is set on stream flags or operation flags, or injected on combined operation flags.
     *
     * @param flags the stream flags, operation flags, or combined operation flags
     * @return true if this flag is known, otherwise false.
     */
    boolean isKnown(int flags) {
        return (flags & preserve) == set;
    }

    /**
     * Check if this flag is cleared on operation flags or combined operation flags.
     *
     * @param flags the operation flags or combined operations flags.
     * @return true if this flag is preserved, otherwise false.
     */
    boolean isCleared(int flags) {
        return (flags & preserve) == clear;
    }

    /**
     * Check if this flag is preserved on combined operation flags.
     *
     * @param flags the combined operations flags.
     * @return true if this flag is preserved, otherwise false.
     */
    boolean isPreserved(int flags) {
        return (flags & preserve) == preserve;
    }

    /**
     *
     * @param t the flag type.
     * @return true if this flag can be set for the flag type, otherwise false.
     */
    boolean canSet(Type t) {
        return (maskTable.get(t) & SET_BITS) > 0;
    }

    /**
     * The bit mask for spliterator characteristics
     */
    static final int SPLITERATOR_CHARACTERISTICS_MASK = createMask(Type.SPLITERATOR);

    /**
     * The bit mask for source stream flags.
     */
    static final int STREAM_MASK = createMask(Type.STREAM);

    /**
     * The bit mask for intermediate operation flags.
     */
    static final int OP_MASK = createMask(Type.OP);

    /**
     * The bit mask for terminal operation flags.
     */
    static final int TERMINAL_OP_MASK = createMask(Type.TERMINAL_OP);

    /**
     * The bit mask for upstream terminal operation flags.
     */
    static final int UPSTREAM_TERMINAL_OP_MASK = createMask(Type.UPSTREAM_TERMINAL_OP);

    private static int createMask(Type t) {
        int mask = 0;
        for (StreamOpFlag flag : StreamOpFlag.values()) {
            mask |= flag.maskTable.get(t) << flag.bitPosition;
        }
        return mask;
    }

    // Complete flag mask
    private static final int FLAG_MASK = createFlagMask();

    private static int createFlagMask() {
        int mask = 0;
        for (StreamOpFlag flag : StreamOpFlag.values()) {
            mask |= flag.preserve;
        }
        return mask;
    }

    // Flag mask for stream flags that are set
    private static final int FLAG_MASK_IS = STREAM_MASK;

    // Flag mask for stream flags that are cleared
    private static final int FLAG_MASK_NOT = STREAM_MASK << 1;

    /**
     * The initial value to be combined with the flags of the first operation in the pipeline.
     */
    static final int INITIAL_OPS_VALUE = FLAG_MASK_IS | FLAG_MASK_NOT;

    /**
     * Stream elements are known to be distinct. No two elements contained in the stream
     * are equivalent via {@code equals()} or equality ({@code ==}) operator.
     */
    static final int IS_DISTINCT = DISTINCT.set;

    /**
     * Stream elements are not known to be distinct.
     */
    static final int NOT_DISTINCT = DISTINCT.clear;

    /**
     * Stream elements are known to be sorted. Elements are {@code Comparable} and each
     * element is greater or equal to the element which preceed it (if any) and
     * less than or equal to the element which follows it (if any).
     */
    static final int IS_SORTED = SORTED.set;

    /**
     * Stream elements are not known to be sorted.
     */
    static final int NOT_SORTED = SORTED.clear;

    /**
     * Stream elements are known to have an encounter order.
     * <p>Certain collections have an expected order when encoutering elements in the collection while traversing,
     * such as an array or {@link java.util.List}. That order is referred to as encounter order.
     * Other collections may have no such order, such as a {@link java.util.HashSet},
     * or {@link java.util.HashMap} when traversing the keys.</p>
     * <p>Encounter order is important when the order at the input to a pipeline should be preserved at the output,
     * for example when a list of elements is mapped to another list of elements the encouter order of the output
     * list should correlate with the encouter order of the input list.</p>
     * <p>Encounter order is also relevant when choosing an algorithm to process elements in parallel.
     * If encounter order is to be preserved the parallel algorithm will need to apply associative only functions
     * i.e. a function can be applied to any grouping of elements but the order of elements cannot change.</p>
     * <p>Stream elements sourced from an array have an encounter order that is also a spatial order.</p>
     * <p>An infinite stream of elements may have an encounter order.</p>
     */
    static final int IS_ORDERED = ORDERED.set;

    /**
     * Stream elements are not known to be ordered.
     */
    static final int NOT_ORDERED = ORDERED.clear;

    /**
     * The size of the stream is known to be calculated in less than {@code O(n)} time and that
     * size is known to be equal to the size of the stream source.
     */
    static final int IS_SIZED = SIZED.set;

    /**
     * The size of the stream is not known that it is not known to be equal to the size of the stream source.
     */
    static final int NOT_SIZED = SIZED.clear;

    /**
     * The stream is known to be short circuited. Evaluation of the pipeline is constrained to be
     * pull-oriented.  This is true for operations that may truncate or otherwise
     * manipulate the stream contents.
     */
    static final int IS_SHORT_CIRCUIT = SHORT_CIRCUIT.set;

    /**
     * The stream can be decomposed for parallel evaluation
     */
    static final int IS_PARALLEL = PARALLEL.set;

    /**
     * The stream cannot be decomposed for parallel evaluation
     */
    static final int NOT_PARALLEL = PARALLEL.clear;

    private static int getMask(int flags) {
        return (flags == 0)
               ? FLAG_MASK
               : ~(flags | ((FLAG_MASK_IS & flags) << 1) | ((FLAG_MASK_NOT & flags) >> 1));
    }

    /**
     * Combine stream or operation flags with previously combined operation flags to produce updated combined
     * operation flags.
     * <p>
     * A flag set on the stream or operation flags and injected on the combined operation flags
     * will be injected on the updated combined operation flags.
     * </p>
     * <p>
     * A flag set on the stream or operation flags and cleared on the combined operation flags
     * will be cleared on the updated combined operation flags.
     * </p>
     * <p>
     * A flag set on the stream or operation flags and preserved on the combined operation flags
     * will be injected on the updated combined operation flags.
     * </p>
     * <p>
     * A flag not set on the stream or operation flags and injected on the combined operation flags
     * will be injected on the updated combined operation flags.
     * </p>
     * <p>
     * A flag not set on the stream or operation flags and cleared on the combined operation flags
     * will be cleared on the updated combined operation flags.
     * </p>
     * <p>
     * A flag not set on the stream or operation flags and preserved on the combined operation flags
     * will be preserved on the updated combined operation flags.
     * </p>
     *
     * @param newStreamOrOpFlags the stream or operation flags.
     * @param prevCombOpFlags previously combined operation flags.
     *                     The value {#link INITIAL_OPS_VALUE} must be used as the seed value.
     * @return the updated combined operations flags.
     */
    static int combineOpFlags(int newStreamOrOpFlags, int prevCombOpFlags) {
        // 0x01 or 0x10 nibbles are transformed to 0x11
        // 0x00 nibbles remain unchanged
        // Then all the bits are flipped
        // Then the result is logically or'ed with the operation flags.
        return (prevCombOpFlags & StreamOpFlag.getMask(newStreamOrOpFlags)) | newStreamOrOpFlags;
    }

    /**
     * Convert combined operation flags to stream flags.
     * <p>
     * Each flag injected on the combined operation flags will be set on the stream flags.
     * </p>
     *
     * @param combOpFlags the operation flags.
     * @return the stream flags.
     */
    static int toStreamFlags(int combOpFlags) {
        // By flipping the nibbles 0x11 become 0x00 and 0x01 become 0x10
        // Shift left 1 to restore set flags and mask off anything other than the set flags
        return ((~combOpFlags) >> 1) & FLAG_MASK_IS & combOpFlags;
    }

    static int toCharacteristics(int streamFlags) {
        return streamFlags & SPLITERATOR_CHARACTERISTICS_MASK;
    }

    static int fromCharacteristics(int characteristics) {
        return characteristics & SPLITERATOR_CHARACTERISTICS_MASK;
    }
}
