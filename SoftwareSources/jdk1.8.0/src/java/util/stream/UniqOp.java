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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An stateful operation which eliminates duplicates from the stream.
 *
 * @param <T> Type of elements to be processed.
 *
 * @author Brian Goetz
 */
// @@@ Does not support null elements
class UniqOp<T> implements StatefulOp<T> {
    @Override
    public int getOpFlags() {
        return StreamOpFlag.IS_DISTINCT | StreamOpFlag.NOT_SIZED;
    }

    @Override
    public Sink<T> wrapSink(int flags, Sink sink) {
        Objects.requireNonNull(sink);

        if (StreamOpFlag.DISTINCT.isKnown(flags)) {
            return sink;
        }
        else if (StreamOpFlag.SORTED.isKnown(flags)) {
            return new Sink.ChainedReference<T>(sink) {
                boolean seenNull;
                T lastSeen;

                @Override
                public void begin(long size) {
                    seenNull = false;
                    lastSeen = null;
                    downstream.begin(-1);
                }

                @Override
                public void end() {
                    seenNull = false;
                    lastSeen = null;
                    downstream.end();
                }

                @Override
                public void accept(T t) {
                    if (t == null) {
                        if (!seenNull) {
                            seenNull = true;
                            downstream.accept(lastSeen = null);
                        }
                    } else if (lastSeen == null || !t.equals(lastSeen)) {
                        downstream.accept(lastSeen = t);
                    }
                }
            };
        }
        else {
            return new Sink.ChainedReference<T>(sink) {
                Set<T> seen;

                @Override
                public void begin(long size) {
                    seen = new HashSet<>();
                    downstream.begin(-1);
                }

                @Override
                public void end() {
                    seen = null;
                    downstream.end();
                }

                @Override
                public void accept(T t) {
                    if (!seen.contains(t)) {
                        seen.add(t);
                        downstream.accept(t);
                    }
                }
            };
        }
    }

    @Override
    public <S> Node<T> evaluateParallel(PipelineHelper<S, T> helper) {
        if (StreamOpFlag.DISTINCT.isKnown(helper.getStreamAndOpFlags())) {
            // No-op
            return helper.collectOutput(false);
        }
        else {
            if (StreamOpFlag.SORTED.isKnown(helper.getStreamAndOpFlags())) {
                // @@@ Method ref
                Set<T> s = OpUtils.parallelReduce(helper, () -> new UniqOrderedSortedSink<T>());
                return Nodes.node(s);
            }
            else {
                if (StreamOpFlag.ORDERED.isKnown(helper.getStreamAndOpFlags())) {
                    // @@@ Method ref
                    Set<T> s = OpUtils.parallelReduce(helper, () -> new UniqOrderedSink<T>());
                    return Nodes.node(s);
                }
                else {
                    final AtomicBoolean seenNull = new AtomicBoolean(false);
                    final ConcurrentHashMap<T, Boolean> map = new ConcurrentHashMap<>();

                    // Cache the sink chain, so it can be reused by all F/J leaf tasks
                    Sink<S> sinkChain = helper.wrapSink(new Sink<T>() {
                        @Override
                        public void accept(T t) {
                            if (t == null)
                                seenNull.set(true);
                            else
                                map.putIfAbsent(t, Boolean.TRUE);
                        }
                    });

                    OpUtils.parallelForEach(helper, sinkChain);

                    Set<T> keys = map.keySet();
                    if (seenNull.get()) {
                        keys = new HashSet<>(keys);
                        keys.add(null);
                    }
                    return Nodes.node(keys);
                }
            }
        }
    }

    private static abstract class AbstractUniqOrderedSink<T, S extends AbstractUniqOrderedSink<T, S>>
            implements OpUtils.AccumulatingSink<T, Set<T>, S> {
        Set<T> set;

        @Override
        public void begin(long size) {
            set = new LinkedHashSet<>();
        }

        @Override
        public void clearState() {
            set = null;
        }

        @Override
        public Set<T> getAndClearState() {
            Set<T> result = set;
            set = null;
            return result;
        }

        @Override
        public void accept(T t) {
            set.add(t);
        }

        @Override
        public void combine(S other) {
            set.addAll(other.set);
        }
    }

    // Keep the type system happy
    private static class UniqOrderedSink<T>
            extends AbstractUniqOrderedSink<T, UniqOrderedSink<T>> { }

    private static class UniqOrderedSortedSink<T>
            extends AbstractUniqOrderedSink<T, UniqOrderedSortedSink<T>> {
        boolean seenNull;
        T lastSeen;

        @Override
        public void begin(long size) {
            seenNull = false;
            lastSeen = null;
            super.begin(size);
        }

        @Override
        public void clearState() {
            seenNull = false;
            lastSeen = null;
            super.clearState();
        }

        @Override
        public Set<T> getAndClearState() {
            seenNull = false;
            lastSeen = null;
            return super.getAndClearState();
        }

        @Override
        public void accept(T t) {
            if (t == null) {
                if (!seenNull) {
                    seenNull = true;
                    super.accept(lastSeen = null);
                }
            }
            else if (lastSeen == null || !t.equals(lastSeen)) {
                super.accept(lastSeen = t);
            }
        }
    }
}
