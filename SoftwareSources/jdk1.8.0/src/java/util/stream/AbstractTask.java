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
import java.util.concurrent.ForkJoinPool;

/**
 * Abstract base class for most fork-join tasks used to implement stream ops.
 * Manages splitting logic, tracking of child tasks, and intermediate results.
 *
 * Splitting and setting up the child task links is done at {@code compute()} time
 * for non-leaf nodes.  At {@code compute()} time for leaf nodes, it is guaranteed
 * that the parent's child-related fields (including sibling links for the parent's children)
 * will be set up for all children.
 *
 * @param <P_IN> Type of elements input to the pipeline
 * @param <P_OUT> Type of elements output from the pipeline
 * @param <R> Type of intermediate result, which may be different from operation result type
 * @param <T> Type of child and sibling tasks
 */
abstract class AbstractTask<P_IN, P_OUT, R, T extends AbstractTask<P_IN, P_OUT, R, T>>
        extends CountedCompleter<R> {

    /** The pipeline helper, common to all tasks in a computation */
    protected final PipelineHelper<P_IN, P_OUT> helper;

    /** The spliterator for the portion of the input associated with the subtree rooted at this task */
    protected final Spliterator<P_IN> spliterator;

    /** Target leaf size */
    protected final long targetSize;

    /** How many children does this task have? */
    protected int numChildren;

    /** This task's first child.  Children are stored in a linked list, using the {@code nextSibling} field
     * as the link to the next child. */
    protected T children;

    /** Next sibling of this task */
    protected T nextSibling;

    /** The result of this node, if completed */
    private R localResult;

    /**
     * Constructor for root nodes.
     */
    protected AbstractTask(PipelineHelper<P_IN, P_OUT> helper) {
        super(null);
        this.helper = helper;
        this.spliterator = helper.sourceSpliterator();
        this.targetSize = suggestTargetSize(spliterator.estimateSize());
    }

    /**
     * Constructor for non-root nodes
     * @param parent This node's parent task
     * @param spliterator Spliterator describing the subtree rooted at this node,
     *                    obtained by splitting the parent spliterator
     */
    protected AbstractTask(T parent, Spliterator<P_IN> spliterator) {
        super(parent);
        this.spliterator = spliterator;
        this.helper = parent.helper;
        this.targetSize = parent.targetSize;
    }

    /** Construct a new node of type T whose parent is the receiver; must call
     * the AbstractTask(T, Spliterator) constructor with the receiver and the provided Spliterator. */
    protected abstract T makeChild(Spliterator<P_IN> spliterator);

    /** Compute the result associated with a leaf node */
    protected abstract R doLeaf();

    /** Suggest a target leaf size based on the initial size estimate */
    public static long suggestTargetSize(long sizeEstimate) {
        if (sizeEstimate == Long.MAX_VALUE)
            sizeEstimate = 1000;  // @@@ SWAG
        return 1 + ((sizeEstimate + 7) >>> 3) / ForkJoinPool.getCommonPoolParallelism();
    }

    /**
     * Suggest whether it is adviseable to split the provided spliterator based on
     * target size and other considerations, such as pool state
     */
    public static<P_IN, P_OUT> boolean suggestSplit(PipelineHelper<P_IN, P_OUT> helper,
                                                    Spliterator spliterator,
                                                    long targetSize) {
        long remaining = spliterator.estimateSize();
        return (remaining > targetSize);
        // @@@ May want to fold in pool characteristics such as surplus task count
    }

    /**
     * Suggest whether it is adviseable to split this task based on target size and other considerations
     */
    public boolean suggestSplit() {
        return suggestSplit(helper, spliterator, targetSize);
    }

    /** Returns the local result, if any */
    @Override
    public R getRawResult() {
        return localResult;
    }

    /** Does nothing; argument must be null, or an exception is thrown */
    @Override
    protected void setRawResult(R result) {
        if (result != null)
            throw new IllegalStateException();
    }

    /**
     * Retrieve a result previously stored with {@link #setLocalResult}
     */
    protected R getLocalResult() {
        return localResult;
    }

    /**
     * Associate the result with the task, can be retrieved with {@link #getLocalResult}
     */
    protected void setLocalResult(R localResult) {
        this.localResult = localResult;
    }

    /** Is this task a leaf node?  (Only valid after {@link #compute} has been called on this node).
     * If the node is not a leaf node, then children will be non-null and numChildren will be positive. */
    protected boolean isLeaf() {
        return children == null;
    }

    /** Is this task the root node? */
    protected boolean isRoot() {
        return getParent() == null;
    }

    /**
     * Return the parent of this task, or null if this task is the root
     */
    protected T getParent() {
        return (T) getCompleter();
    }

    /**
     * Decide whether or not to split this task further or compute it directly.
     * If computing directly, call {@code doLeaf} and pass the result to
     * {@code setRawResult}.  If splitting, set up the child-related fields,
     * create the child tasks, fork the rightmost child tasks, and compute the leftmost
     * child task.
     */
    @Override
    public void compute() {
        Spliterator<P_IN> split = null;
        if (!suggestSplit() || (split = spliterator.trySplit()) == null) {
            setLocalResult(doLeaf());
            tryComplete();
        }
        else {
            // Common case -- binary splits
            T leftChild = makeChild(split);
            T rightChild = makeChild(spliterator);
            setPendingCount(1);
            numChildren = 2;
            children = leftChild;
            leftChild.nextSibling = rightChild;
            rightChild.fork();
            leftChild.compute();
        }
    }
}

