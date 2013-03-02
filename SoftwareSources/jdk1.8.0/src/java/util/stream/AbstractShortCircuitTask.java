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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract class for fork-join tasks used to implement short-circuiting
 * stream ops, which can produce a result without processing all elements of the stream.
 *
 * @param <P_IN> Type of elements input to the pipeline
 * @param <P_OUT> Type of elements output from the pipeline
 * @param <R> Type of intermediate result, may be different from operation result type
 * @param <T> Type of child and sibling tasks
 */
abstract class AbstractShortCircuitTask<P_IN, P_OUT, R, T extends AbstractShortCircuitTask<P_IN, P_OUT, R, T>>
        extends AbstractTask<P_IN, P_OUT, R, T> {
    /** The result for this computation; this is shared among all tasks and set exactly once */
    protected final AtomicReference<R> sharedResult;

    /**
     * Indicates whether this task has been canceled.  Tasks may cancel other tasks in the computation
     * under various conditions, such as in a find-first operation, a task that finds a value will cancel
     * all tasks that are later in the encounter order.
     */
    protected volatile boolean canceled;

    /** Constructor for root nodes */
    protected AbstractShortCircuitTask(PipelineHelper<P_IN, P_OUT> helper) {
        super(helper);
        sharedResult = new AtomicReference<>(null);
    }

    /** Constructor for non-root nodes */
    protected AbstractShortCircuitTask(T parent, Spliterator<P_IN> spliterator) {
        super(parent, spliterator);
        sharedResult = parent.sharedResult;
    }

    /**
     * Return the value indicating the computation completed with no task finding a short-circuitable result.
     * For example, for a "find" operation, this might be null or an empty {@code Optional}.
     */
    protected abstract R getEmptyResult();

    @Override
    public void compute() {
        // Have we already found an answer?
        if (sharedResult.get() != null)
            tryComplete();
        else if (taskCancelled()) {
            setLocalResult(getEmptyResult());
            tryComplete();
        }
        else
            super.compute();
    }

    /**
     * Declare that a globally valid result has been found.  If another task has not already found the answer,
     * the result is installed in {@code sharedResult}.  The {@code compute()} method will check {@code sharedResult}
     * before proceeding with computation, so this causes the computation to terminate early.
     */
    protected void shortCircuit(R result) {
        if (result != null)
            sharedResult.compareAndSet(null, result);
    }

    /**
     * Set a local result for this task.  If this task is the root, also set the shared result, if not already set.
     */
    @Override
    protected void setLocalResult(R localResult) {
        if (isRoot()) {
            if (localResult != null)
                sharedResult.compareAndSet(null, localResult);
        }
        else
            super.setLocalResult(localResult);
    }

    /** Retrieve the local result for this task */
    @Override
    public R getRawResult() {
        return getLocalResult();
    }

    /** Retrieve the local result for this task.  If this task is the root, retrieves the shared result instead. */
    @Override
    public R getLocalResult() {
        if (isRoot()) {
            R answer = sharedResult.get();
            return (answer == null) ? getEmptyResult() : answer;
        }
        else
            return super.getLocalResult();
    }

    /** Set this node as canceled */
    protected void cancel() {
        canceled = true;
    }

    /**
     * Query whether this task is canceled.  A task is considered canceled if it or any of its parents
     * have been canceled.
     */
    protected boolean taskCancelled() {
        boolean cancel = canceled;
        if (!cancel)
            for (T parent = getParent(); !cancel && parent != null; parent = parent.getParent())
                cancel = parent.canceled;
        return cancel;
    }

    /**
     * Cancel all tasks which succeed this one in the encounter order.  This includes canceling all the current
     * task's later siblings, as well as the later siblings of all its parents.
     */
    protected void cancelLaterNodes() {
        T parent = getParent();
        for (T sibling = this.nextSibling; sibling != null; sibling = sibling.nextSibling)
            if (!sibling.canceled)
                sibling.canceled = true;
        // Go up the tree, cancel later siblings of all parents
        if (parent != null)
            parent.cancelLaterNodes();
    }

    /** Returns whether this node has no prior leaf nodes in the encounter order */
    protected boolean isLeftSpine() {
        T node = (T) this;
        while (node != null) {
            T parent = node.getParent();
            if (parent != null && parent.children != node)
                return false;
            node = parent;
        }
        return true;
    }
}
