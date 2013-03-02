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

import java.util.Spliterator;
import java.util.concurrent.CountedCompleter;
import java.util.function.Supplier;

/**
 * OpUtils
 *
 * @author Brian Goetz
 */
class OpUtils {
    private OpUtils() {
        throw new IllegalStateException("no instances");
    }

    public static<P_IN, P_OUT> void parallelForEach(PipelineHelper<P_IN, P_OUT> helper,
                                                    Sink<P_IN> sink) {
        new ForEachTask<>(helper, sink).invoke();
    }

    private static class ForEachTask<S, T> extends AbstractTask<S, T, Void, ForEachTask<S, T>> {
        // @@@ Extending AbstractTask here is probably inefficient, since we don't really need to
        // keep track of the structure of the computation tree
        private final Sink<S> sink;

        private ForEachTask(PipelineHelper<S, T> helper, Sink<S> sink) {
            super(helper);
            this.sink = sink;
        }

        private ForEachTask(ForEachTask<S, T> parent, Spliterator<S> spliterator, Sink<S> sink) {
            super(parent, spliterator);
            this.sink = sink;
        }

        @Override
        public boolean suggestSplit() {
            boolean suggest = super.suggestSplit();
            if (StreamOpFlag.SHORT_CIRCUIT.isKnown(helper.getStreamAndOpFlags()))
                suggest = suggest && !sink.cancellationRequested();
            return suggest;
        }

        @Override
        protected ForEachTask<S, T> makeChild(Spliterator<S> spliterator) {
            return new ForEachTask<>(this, spliterator, sink);
        }

        @Override
        protected Void doLeaf() {
            helper.intoWrapped(sink, spliterator);
            return null;
        }
    }


    public interface AccumulatingSink<T, R, K extends AccumulatingSink<T, R, K>> extends TerminalSink<T, R> {

        public void combine(K other);
        public void clearState();
    }

    public static<P_IN, P_OUT, R, S extends AccumulatingSink<P_OUT, R, S>>
    R parallelReduce(PipelineHelper<P_IN, P_OUT> helper, Supplier<S> factory) {
        S sink = new ReduceTask<>(helper, factory).invoke();
        return sink.getAndClearState();
    }

    private static class ReduceTask<P_IN, P_OUT, R, S extends AccumulatingSink<P_OUT, R, S>>
            extends AbstractTask<P_IN, P_OUT, S, ReduceTask<P_IN, P_OUT, R, S>> {
        private final Supplier<S> sinkFactory;

        private ReduceTask(PipelineHelper<P_IN, P_OUT> helper, Supplier<S> sinkFactory) {
            super(helper);
            this.sinkFactory = sinkFactory;
        }

        private ReduceTask(ReduceTask<P_IN, P_OUT, R, S> parent, Spliterator<P_IN> spliterator) {
            super(parent, spliterator);
            this.sinkFactory = parent.sinkFactory;
        }

        @Override
        protected ReduceTask<P_IN, P_OUT, R, S> makeChild(Spliterator<P_IN> spliterator) {
            return new ReduceTask<>(this, spliterator);
        }

        @Override
        protected S doLeaf() {
            return helper.into(sinkFactory.get(), spliterator);
        }

        @Override
        public void onCompletion(CountedCompleter caller) {
            if (!isLeaf()) {
                ReduceTask<P_IN, P_OUT, R, S> child = children;
                S result = child.getLocalResult();
                child = child.nextSibling;
                for (; child != null; child = child.nextSibling) {
                    S otherResult = child.getLocalResult();
                    result.combine(otherResult);
                    otherResult.clearState();
                }
                setLocalResult(result);
            }
        }
    }
}
