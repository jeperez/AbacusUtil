/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.landawn.abacus.util.stream;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.landawn.abacus.util.Array;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.OptionalDouble;
import com.landawn.abacus.util.OptionalShort;
import com.landawn.abacus.util.ShortList;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.ObjShortConsumer;
import com.landawn.abacus.util.function.ShortBinaryOperator;
import com.landawn.abacus.util.function.ShortConsumer;
import com.landawn.abacus.util.function.ShortFunction;
import com.landawn.abacus.util.function.ShortPredicate;
import com.landawn.abacus.util.function.ShortToIntFunction;
import com.landawn.abacus.util.function.ShortUnaryOperator;
import com.landawn.abacus.util.function.Supplier;

/**
 * Note: It's copied from OpenJDK at: http://hg.openjdk.java.net/jdk8u/hs-dev/jdk
 * <br />
 * 
 * A sequence of primitive int-valued elements supporting sequential and parallel
 * aggregate operations.  This is the {@code int} primitive specialization of
 * {@link Stream}.
 *
 * <p>The following example illustrates an aggregate operation using
 * {@link Stream} and {@link ShortStream}, computing the sum of the weights of the
 * red widgets:
 *
 * <pre>{@code
 *     int sum = widgets.stream()
 *                      .filter(w -> w.getColor() == RED)
 *                      .mapToShort(w -> w.getWeight())
 *                      .sum();
 * }</pre>
 *
 * See the class documentation for {@link Stream} and the package documentation
 * for <a href="package-summary.html">java.util.stream</a> for additional
 * specification of streams, stream operations, stream pipelines, and
 * parallelism.
 *
 * @since 1.8
 * @see Stream
 * @see <a href="package-summary.html">java.util.stream</a>
 */
public abstract class ShortStream implements BaseStream<Short, ShortStream> {
    static final Comparator<Short> SHORT_COMPARATOR = new Comparator<Short>() {
        @Override
        public int compare(Short o1, Short o2) {
            return Short.compare(o1, o2);
        }
    };

    /**
     * Returns a stream consisting of the elements of this stream that match
     * the given predicate.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param predicate a <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to each element to determine if it
     *                  should be included
     * @return the new stream
     */
    public abstract ShortStream filter(final ShortPredicate predicate);

    /**
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract ShortStream filter(final ShortPredicate predicate, final long max);

    /**
     * Keep the elements until the given predicate returns false.
     * 
     * @param predicate
     * @return
     */
    public abstract ShortStream takeWhile(final ShortPredicate predicate);

    /**
     * Keep the elements until the given predicate returns false.
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract ShortStream takeWhile(final ShortPredicate predicate, final long max);

    /**
     * Remove the elements until the given predicate returns false.
     * 
     * 
     * @param predicate
     * @return
     */
    public abstract ShortStream dropWhile(final ShortPredicate predicate);

    /**
     * Remove the elements until the given predicate returns false.
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract ShortStream dropWhile(final ShortPredicate predicate, final long max);

    /**
     * Returns a stream consisting of the results of applying the given
     * function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract ShortStream map(ShortUnaryOperator mapper);

    /**
     * Returns a {@code IntStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract IntStream mapToInt(ShortToIntFunction mapper);

    /**
     * Returns an object-valued {@code Stream} consisting of the results of
     * applying the given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">
     *     intermediate operation</a>.
     *
     * @param <U> the element type of the new stream
     * @param mapper a <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract <U> Stream<U> mapToObj(ShortFunction<? extends U> mapper);

    /**
     * Returns a stream consisting of the results of replacing each element of
     * this stream with the contents of a mapped stream produced by applying
     * the provided mapping function to each element.  Each mapped stream is
     * {@link java.util.stream.BaseStream#close() closed} after its contents
     * have been placed into this stream.  (If a mapped stream is {@code null}
     * an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces an
     *               {@code ShortStream} of new values
     * @return the new stream
     * @see Stream#flatMap(Function)
     */
    public abstract ShortStream flatMap(ShortFunction<? extends ShortStream> mapper);

    public abstract IntStream flatMapToInt(ShortFunction<? extends IntStream> mapper);

    public abstract <T> Stream<T> flatMapToObj(ShortFunction<? extends Stream<T>> mapper);

    /**
     * Returns Stream of ShortStream with consecutive sub sequences of the elements, each of the same size (the final sequence may be smaller).
     * 
     * @param size
     * @return
     */
    public abstract Stream<ShortStream> split(int size);

    /**
     * Returns a stream consisting of the distinct elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @return the new stream
     */
    public abstract ShortStream distinct();

    public abstract ShortStream top(int n);

    public abstract ShortStream top(final int n, Comparator<? super Short> comparator);

    /**
     * Returns a stream consisting of the elements of this stream in sorted
     * order.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @return the new stream
     */
    public abstract ShortStream sorted();

    /**
     * Returns a stream consisting of the elements of this stream, additionally
     * performing the provided action on each element as elements are consumed
     * from the resulting stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * <p>For parallel stream pipelines, the action may be called at
     * whatever time and in whatever thread the element is made available by the
     * upstream operation.  If the action modifies shared state,
     * it is responsible for providing the required synchronization.
     *
     * @apiNote This method exists mainly to support debugging, where you want
     * to see the elements as they flow past a certain point in a pipeline:
     * <pre>{@code
     *     ShortStream.of(1, 2, 3, 4)
     *         .filter(e -> e > 2)
     *         .peek(e -> System.out.println("Filtered value: " + e))
     *         .map(e -> e * e)
     *         .peek(e -> System.out.println("Mapped value: " + e))
     *         .sum();
     * }</pre>
     *
     * @param action a <a href="package-summary.html#NonShorterference">
     *               non-interfering</a> action to perform on the elements as
     *               they are consumed from the stream
     * @return the new stream
     */
    public abstract ShortStream peek(ShortConsumer action);

    /**
     * Returns a stream consisting of the elements of this stream, truncated
     * to be no longer than {@code maxSize} in length.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * stateful intermediate operation</a>.
     *
     * @apiNote
     * While {@code limit()} is generally a cheap operation on sequential
     * stream pipelines, it can be quite expensive on ordered parallel pipelines,
     * especially for large values of {@code maxSize}, since {@code limit(n)}
     * is constrained to return not just any <em>n</em> elements, but the
     * <em>first n</em> elements in the encounter order.  Using an unordered
     * stream source or removing the
     * ordering constraint with {@link #unordered()} may result in significant
     * speedups of {@code limit()} in parallel pipelines, if the semantics of
     * your situation permit.  If consistency with encounter order is required,
     * and you are experiencing poor performance or memory utilization with
     * {@code limit()} in parallel pipelines, switching to sequential execution
     * with {@link #sequential()} may improve performance.
     *
     * @param maxSize the number of elements the stream should be limited to
     * @return the new stream
     * @throws IllegalArgumentException if {@code maxSize} is negative
     */
    public abstract ShortStream limit(long maxSize);

    /**
     * Returns a stream consisting of the remaining elements of this stream
     * after discarding the first {@code n} elements of the stream.
     * If this stream contains fewer than {@code n} elements then an
     * empty stream will be returned.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @apiNote
     * While {@code skip()} is generally a cheap operation on sequential
     * stream pipelines, it can be quite expensive on ordered parallel pipelines,
     * especially for large values of {@code n}, since {@code skip(n)}
     * is constrained to skip not just any <em>n</em> elements, but the
     * <em>first n</em> elements in the encounter order.  Using an unordered
     * stream source or removing the
     * ordering constraint with {@link #unordered()} may result in significant
     * speedups of {@code skip()} in parallel pipelines, if the semantics of
     * your situation permit.  If consistency with encounter order is required,
     * and you are experiencing poor performance or memory utilization with
     * {@code skip()} in parallel pipelines, switching to sequential execution
     * with {@link #sequential()} may improve performance.
     *
     * @param n the number of leading elements to skip
     * @return the new stream
     * @throws IllegalArgumentException if {@code n} is negative
     */
    public abstract ShortStream skip(long n);

    /**
     * Performs an action for each element of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * <p>For parallel stream pipelines, this operation does <em>not</em>
     * guarantee to respect the encounter order of the stream, as doing so
     * would sacrifice the benefit of parallelism.  For any given element, the
     * action may be performed at whatever time and in whatever thread the
     * library chooses.  If the action accesses shared state, it is
     * responsible for providing the required synchronization.
     *
     * @param action a <a href="package-summary.html#NonShorterference">
     *               non-interfering</a> action to perform on the elements
     */
    public abstract void forEach(ShortConsumer action);

    /**
     * Returns an array containing the elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return an array containing the elements of this stream
     */
    public abstract short[] toArray();

    public abstract ShortList toShortList();

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using the provided identity value and an
     * <a href="package-summary.html#Associativity">associative</a>
     * accumulation function, and returns the reduced value.  This is equivalent
     * to:
     * <pre>{@code
     *     int result = identity;
     *     for (int element : this stream)
     *         result = accumulator.applyAsShort(result, element)
     *     return result;
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code identity} value must be an identity for the accumulator
     * function. This means that for all {@code x},
     * {@code accumulator.apply(identity, x)} is equal to {@code x}.
     * The {@code accumulator} function must be an
     * <a href="package-summary.html#Associativity">associative</a> function.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote Sum, min, max, and average are all special cases of reduction.
     * Summing a stream of numbers can be expressed as:
     *
     * <pre>{@code
     *     int sum = integers.reduce(0, (a, b) -> a+b);
     * }</pre>
     *
     * or more compactly:
     *
     * <pre>{@code
     *     int sum = integers.reduce(0, Shorteger::sum);
     * }</pre>
     *
     * <p>While this may seem a more roundabout way to perform an aggregation
     * compared to simply mutating a running total in a loop, reduction
     * operations parallelize more gracefully, without needing additional
     * synchronization and with greatly reduced risk of data races.
     *
     * @param identity the identity value for the accumulating function
     * @param op an <a href="package-summary.html#Associativity">associative</a>,
     *           <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *           <a href="package-summary.html#Statelessness">stateless</a>
     *           function for combining two values
     * @return the result of the reduction
     * @see #sum()
     * @see #min()
     * @see #max()
     * @see #average()
     */
    public abstract short reduce(short identity, ShortBinaryOperator op);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using an
     * <a href="package-summary.html#Associativity">associative</a> accumulation
     * function, and returns an {@code OptionalShort} describing the reduced value,
     * if any. This is equivalent to:
     * <pre>{@code
     *     boolean foundAny = false;
     *     int result = null;
     *     for (int element : this stream) {
     *         if (!foundAny) {
     *             foundAny = true;
     *             result = element;
     *         }
     *         else
     *             result = accumulator.applyAsShort(result, element);
     *     }
     *     return foundAny ? OptionalShort.of(result) : OptionalShort.empty();
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code accumulator} function must be an
     * <a href="package-summary.html#Associativity">associative</a> function.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param op an <a href="package-summary.html#Associativity">associative</a>,
     *           <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *           <a href="package-summary.html#Statelessness">stateless</a>
     *           function for combining two values
     * @return the result of the reduction
     * @see #reduce(int, ShortBinaryOperator)
     */
    public abstract OptionalShort reduce(ShortBinaryOperator op);

    /**
     * Performs a <a href="package-summary.html#MutableReduction">mutable
     * reduction</a> operation on the elements of this stream.  A mutable
     * reduction is one in which the reduced value is a mutable result container,
     * such as an {@code ArrayList}, and elements are incorporated by updating
     * the state of the result rather than by replacing the result.  This
     * produces a result equivalent to:
     * <pre>{@code
     *     R result = supplier.get();
     *     for (int element : this stream)
     *         accumulator.accept(result, element);
     *     return result;
     * }</pre>
     *
     * <p>Like {@link #reduce(int, ShortBinaryOperator)}, {@code collect} operations
     * can be parallelized without requiring additional synchronization.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param <R> type of the result
     * @param supplier a function that creates a new result container. For a
     *                 parallel execution, this function may be called
     *                 multiple times and must return a fresh value each time.
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for incorporating an additional element into a result
     * @param combiner an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for combining two values, which must be
     *                    compatible with the accumulator function
     * @return the result of the reduction
     * @see Stream#collect(Supplier, BiConsumer, BiConsumer)
     */
    public abstract <R> R collect(Supplier<R> supplier, ObjShortConsumer<R> accumulator, BiConsumer<R, R> combiner);

    public abstract Long sum();

    /**
     * Returns an {@code OptionalShort} describing the minimum element of this
     * stream, or an empty optional if this stream is empty.  This is a special
     * case of a <a href="package-summary.html#Reduction">reduction</a>
     * and is equivalent to:
     * <pre>{@code
     *     return reduce(Shorteger::min);
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal operation</a>.
     *
     * @return an {@code OptionalShort} containing the minimum element of this
     * stream, or an empty {@code OptionalShort} if the stream is empty
     */
    public abstract OptionalShort min();

    /**
     * Returns an {@code OptionalShort} describing the maximum element of this
     * stream, or an empty optional if this stream is empty.  This is a special
     * case of a <a href="package-summary.html#Reduction">reduction</a>
     * and is equivalent to:
     * <pre>{@code
     *     return reduce(Shorteger::max);
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return an {@code OptionalShort} containing the maximum element of this
     * stream, or an empty {@code OptionalShort} if the stream is empty
     */
    public abstract OptionalShort max();

    /**
     * 
     * @param k
     * @return OptionalByte.empty() if there is no element or count less than k, otherwise the kth largest element.
     */
    public abstract OptionalShort kthLargest(int k);

    /**
     * Returns the count of elements in this stream.  This is a special case of
     * a <a href="package-summary.html#Reduction">reduction</a> and is
     * equivalent to:
     * <pre>{@code
     *     return mapToLong(e -> 1L).sum();
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal operation</a>.
     *
     * @return the count of elements in this stream
     */
    public abstract long count();

    public abstract OptionalDouble average();

    /**
     * Returns whether any elements of this stream match the provided
     * predicate.  May not evaluate the predicate on all elements if not
     * necessary for determining the result.  If the stream is empty then
     * {@code false} is returned and the predicate is not evaluated.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @apiNote
     * This method evaluates the <em>existential quantification</em> of the
     * predicate over the elements of the stream (for some x P(x)).
     *
     * @param predicate a <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if any elements of the stream match the provided
     * predicate, otherwise {@code false}
     */
    public abstract boolean anyMatch(ShortPredicate predicate);

    /**
     * Returns whether all elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for
     * determining the result.  If the stream is empty then {@code true} is
     * returned and the predicate is not evaluated.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @apiNote
     * This method evaluates the <em>universal quantification</em> of the
     * predicate over the elements of the stream (for all x P(x)).  If the
     * stream is empty, the quantification is said to be <em>vacuously
     * satisfied</em> and is always {@code true} (regardless of P(x)).
     *
     * @param predicate a <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if either all elements of the stream match the
     * provided predicate or the stream is empty, otherwise {@code false}
     */
    public abstract boolean allMatch(ShortPredicate predicate);

    /**
     * Returns whether no elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for
     * determining the result.  If the stream is empty then {@code true} is
     * returned and the predicate is not evaluated.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @apiNote
     * This method evaluates the <em>universal quantification</em> of the
     * negated predicate over the elements of the stream (for all x ~P(x)).  If
     * the stream is empty, the quantification is said to be vacuously satisfied
     * and is always {@code true}, regardless of P(x).
     *
     * @param predicate a <a href="package-summary.html#NonShorterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if either no elements of the stream match the
     * provided predicate or the stream is empty, otherwise {@code false}
     */
    public abstract boolean noneMatch(ShortPredicate predicate);

    /**
     * Returns an {@link OptionalShort} describing the first element of this
     * stream, or an empty {@code OptionalShort} if the stream is empty.  If the
     * stream has no encounter order, then any element may be returned.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @return an {@code OptionalShort} describing the first element of this stream,
     * or an empty {@code OptionalShort} if the stream is empty
     */
    // public abstract OptionalShort findFirst();

    public abstract OptionalShort findFirst(ShortPredicate predicate);

    // public abstract OptionalShort findLast();

    public abstract OptionalShort findLast(ShortPredicate predicate);

    /**
     * Returns an {@link OptionalShort} describing some element of the stream, or
     * an empty {@code OptionalShort} if the stream is empty.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * <p>The behavior of this operation is explicitly nondeterministic; it is
     * free to select any element in the stream.  This is to allow for maximal
     * performance in parallel operations; the cost is that multiple invocations
     * on the same source may not return the same result.  (If a stable result
     * is desired, use {@link #findFirst()} instead.)
     *
     * @return an {@code OptionalShort} describing some element of this stream, or
     * an empty {@code OptionalShort} if the stream is empty
     * @see #findFirst()
     */
    // public abstract OptionalShort findAny();

    public abstract OptionalShort findAny(ShortPredicate predicate);

    /**
     * Returns a {@code LongStream} consisting of the elements of this stream,
     * converted to {@code long}.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a {@code LongStream} consisting of the elements of this stream,
     * converted to {@code long}
     */
    public abstract IntStream asIntStream();

    /**
     * Returns a {@code Stream} consisting of the elements of this stream,
     * each boxed to an {@code Shorteger}.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @return a {@code Stream} consistent of the elements of this stream,
     * each boxed to an {@code Shorteger}
     */
    public abstract Stream<Short> boxed();

    abstract ImmutableShortIterator shortIterator();

    // Static factories

    public static ShortStream empty() {
        return of(N.EMPTY_SHORT_ARRAY);
    }

    public static ShortStream of(final short... a) {
        return Stream.from(a);
    }

    public static ShortStream of(final short[] a, final int startIndex, final int endIndex) {
        return new ArrayShortStream(a, startIndex, endIndex);
    }

    static ShortStream from(final int... a) {
        return Stream.from(ShortList.from(a).trimToSize().array());
    }

    public static ShortStream range(final short startInclusive, final short endExclusive) {
        return Stream.from(Array.range(startInclusive, endExclusive));
    }

    public static ShortStream rangeClosed(final short startInclusive, final short endInclusive) {
        return Stream.from(Array.rangeClosed(startInclusive, endInclusive));
    }

    public static ShortStream repeat(short element, int n) {
        return of(Array.repeat(element, n));
    }

    public static ShortStream concat(final short[]... a) {
        return new IteratorShortStream(new ImmutableShortIterator() {
            private final Iterator<short[]> iter = N.asList(a).iterator();
            private ImmutableShortIterator cur;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && iter.hasNext()) {
                    cur = new ImmutableShortIterator() {
                        private final short[] cur = iter.next();
                        private int cursor = 0;

                        @Override
                        public boolean hasNext() {
                            return cursor < cur.length;
                        }

                        @Override
                        public short next() {
                            if (cursor >= cur.length) {
                                throw new NoSuchElementException();
                            }

                            return cur[cursor++];
                        }
                    };
                }
                return cur != null && cur.hasNext();
            }

            @Override
            public short next() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.next();
            }
        });
    }

    public static ShortStream concat(final ShortStream... a) {
        return new IteratorShortStream(new ImmutableShortIterator() {
            private final Iterator<ShortStream> iter = N.asList(a).iterator();
            private ImmutableShortIterator cur;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && iter.hasNext()) {
                    cur = iter.next().shortIterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public short next() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.next();
            }
        });
    }
}
