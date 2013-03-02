/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Classes to support functional-style operations on streams of values, as in the following:
 *
 * <pre>
 *     int sumOfWeights = blocks.stream().filter(b -> b.getColor() == RED)
 *                                       .map(b -> b.getWeight())
 *                                       .sum();
 * </pre>
 *
 * <p>Here we use {@code blocks}, which might be a {@code Collection}, as a source for a stream,
 * and then perform a filter-map-reduce on the stream to obtain the sum of the weights of the
 * red blocks.
 *
 * <p>The key abstraction used in this approach is {@link java.util.stream.Stream}, as well as its primitive
 * specializations {@link java.util.stream.IntStream}, {@link java.util.stream.LongStream},
 * and {@link java.util.stream.DoubleStream}.  Streams differ from Collections in several ways:
 *
 * <ul>
 *     <li>No storage.  A stream is not a data structure that stores elements; instead, they
 *     carry values from a source (which could be a data structure, a generator, an IO channel, etc)
 *     through a pipeline of computational operations.</li>
 *     <li>Functional in nature.  An operation on a stream produces a result, but does not modify
 *     its underlying data source.  For example, filtering a {@code Stream} produces a new {@code Stream},
 *     rather than removing elements from the underlying source.</li>
 *     <li>Laziness-seeking.  Many stream operations, such as filtering, mapping, or duplicate removal,
 *     can be implemented lazily, exposing opportunities for optimization.  (For example, "find the first
 *     {@code String} matching a pattern" need not examine all the input strings.)  Stream operations
 *     are divided into intermediate ({@code Stream}-producing) operations and terminal (value-producing)
 *     operations; all intermediate operations are lazy.</li>
 *     <li>Possibly unbounded.  While collections have a finite size, streams need not.  Operations
 *     such as {@code limit(n)} or {@code findFirst()} can allow computations on infinite streams
 *     to complete in finite time.</li>
 * </ul>
 *
 * <h2>Stream pipelines</h2>
 *
 * <p>Streams are used to create <em>pipelines</em> of operations.  A complete stream pipeline has
 * several components: a source (which may be a {@code Collection}, an array, a generator function,
 * or an IO channel); zero or more <em>intermediate operations</em> such as {@code Stream#filter} or
 * {@code Stream#map}; and a <em>terminal operation</em> such as {@code Stream#forEach}
 * or {@code Stream#reduce}.  Stream operations may take as parameters <em>function values</em>
 * (which are often lambda expressions, but could be method references or objects) which parameterize
 * the behavior of the operation, such as a {@code Predicate} passed to the {@code Stream#filter} method.
 *
 * <p>Intermediate operations return a new {@code Stream}.  They are lazy; executing an intermediate
 * operation such as {@code Stream#filter} does not actually perform any filtering, instead creating a new
 * {@code Stream} that, when traversed, contains the elements of the initial {@code Stream} that match the
 * given {@code Predicate}.  Consuming elements from the stream source does not begin until the terminal
 * operation is executed.
 *
 * <p>Terminal operations consume the {@code Stream} and produce a result or a side-effect.  After a terminal
 * operation is performed, the stream can no longer be used.
 *
 * <h3 name="#StreamOps">Stream operations</h3>
 *
 * Stream operations are divided into two categories: <em>intermediate</em> and <em>terminal</em>.  An
 * intermediate operation (such as {@code filter} or {@code sorted}) produces a new {@code Stream}; a terminal
 * operation (such as {@code forEach} or {@code findFirst}) produces a non-{@code Stream} result, such as a
 * primitive value or a {@code Collection}.
 *
 * All intermediate operations are <em>lazy</em>, which means that executing a lazy operations does not trigger
 * processing of the stream contents; all processing is deferred until the terminal operation commences.
 * Processing streams lazily allows for significant efficiencies; in a pipeline such as the filter-map-sum
 * example above, filtering, mapping, and addition can be fused into a single pass, with minimal intermediate
 * state.  Laziness also enables us to avoid examining all the data when it is not necessary; for operations such as
 * "find the first string longer than 1000 characters", one need not examine all the input strings, just enough to
 * find one that has the desired characteristics.  (This behavior becomes even more important when the input stream
 * is infinite and not merely large.)
 *
 * Intermediate operations are further divided into <em>stateless</em> and <em>stateful</em> operations.  Stateless
 * operations retain no state from previously seen values when processing a new value; examples of stateless
 * intermediate operations include {@code filter} and {@code map}.  Stateful operations may incorporate state
 * from previously seen elements in processing new values; examples of stateful intermediate operations include
 * {@code distict} and {@code sorted}.  Stateful operations may need to process the entire input before producing a
 * result; for example, one cannot produce any results from sorting a stream until one has seen all elements of the
 * stream.  As a result, under parallel computation, some pipelines containing stateful intermediate operations
 * have to be executed in multiple passes.  All pipelines containing exclusively stateless intermediate operations
 * can be processed in a single pass, whether sequential or parallel.
 *
 * <h3>Parallelism</h3>
 *
 * <p>By recasting aggregate operations as a pipeline of operations on a stream of values, many
 * aggregate operations can be more easily parallelized.  A {@code Stream} can execute either in serial or
 * in parallel, depending on how it was created.  The {@code Stream} implementations in the JDK take
 * the approach of creating serial streams unless parallelism is explicitly requested.  For example,
 * {@code Collection} has methods {@link java.util.Collection#stream}
 * and {@link java.util.Collection#parallelStream}, which produce serial and parallel streams respectively.
 * The set of operations on serial and parallel streams is identical.  There are also stream operations
 * {@code Stream#sequential} and {@code Stream#parallel} to convert between sequential and parallel execution.
 * To execute the "sum of weights of blocks" query in parallel, we would do:
 *
 * <pre>
 *     int sumOfWeights = blocks.parallelStream().filter(b -> b.getColor() == RED)
 *                                               .map(b -> b.getWeight())
 *                                               .sum();
 * </pre>
 *
 * <p>The only difference between the serial and parallel code is the creation of the initial
 * {@code Stream}.  Whether a {@code Stream} is serial or parallel can be determined by the
 * {@code Stream#isParallel} method.
 *
 * <p>In order for the results of parallel operations to be deterministic and consistent with their
 * serial equivalent, it is important to ensure that the function values passed into the various
 * stream operations be <em>non-interfering</em>.
 *
 * <h3>Ordering</h3>
 *
 * <p>Streams may or may not have an <em>encounter order</em>.  Whether or not there is an encounter order
 * depends on the source, the intermediate operations, and the terminal operation.  Certain stream sources
 * (such as {@code List} or arrays) are intrinsically ordered, whereas others (such as {@code HashSet}) are
 * not.  Some intermediate operations may impose an encounter order on an otherwise unordered stream, such
 * as {@code Stream#sorted}.  Finally, some terminal operations may ignore encounter order, such as
 * {@code Stream#forEach}, and others may have optimized implementations in the case where there is no
 * defined encounter order.
 *
 * <p>If a Stream is ordered, certain operations are constrained to operate on the elements in their
 * encounter order.  If the source of a stream is a {@code List} containing {@code [1, 2, 3]}, then the
 * result of executing {@code map(x -> x*2)} must be {@code [2, 4, 6]}.  However, if the source has no
 * defined encounter order, than any permutation of the values {@code [2, 4, 6]} would be a valid result.
 *
 * <h2>Non-interference</h2>
 *
 * @@@ Multiple forms of interference: mutating source from outside, mutating source from lambda,
 * lambdas sharing mutable state
 *
 * <h2>Side-effects</h2>
 */

package java.util.stream;
