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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.landawn.abacus.exception.AbacusIOException;
import com.landawn.abacus.util.Array;
import com.landawn.abacus.util.AsyncExecutor;
import com.landawn.abacus.util.Holder;
import com.landawn.abacus.util.IOUtil;
import com.landawn.abacus.util.LineIterator;
import com.landawn.abacus.util.MutableBoolean;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.ObjectList;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.RowIterator;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.IntFunction;
import com.landawn.abacus.util.function.NFunction;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToByteFunction;
import com.landawn.abacus.util.function.ToCharFunction;
import com.landawn.abacus.util.function.ToDoubleFunction;
import com.landawn.abacus.util.function.ToFloatFunction;
import com.landawn.abacus.util.function.ToIntFunction;
import com.landawn.abacus.util.function.ToLongFunction;
import com.landawn.abacus.util.function.ToShortFunction;
import com.landawn.abacus.util.function.TriFunction;
import com.landawn.abacus.util.function.UnaryOperator;

/**
 * Note: It's copied from OpenJDK at: http://hg.openjdk.java.net/jdk8u/hs-dev/jdk
 * <br />
 * 
 * A sequence of elements supporting sequential and parallel aggregate
 * operations.  The following example illustrates an aggregate operation using
 * {@link Stream} and {@link IntStream}:
 *
 * <pre>{@code
 *     int sum = widgets.stream()
 *                      .filter(w -> w.getColor() == RED)
 *                      .mapToInt(w -> w.getWeight())
 *                      .sum();
 * }</pre>
 *
 * In this example, {@code widgets} is a {@code Collection<Widget>}.  We create
 * a stream of {@code Widget} objects via {@link Collection#stream Collection.stream()},
 * filter it to produce a stream containing only the red widgets, and then
 * transform it into a stream of {@code int} values representing the weight of
 * each red widget. Then this stream is summed to produce a total weight.
 *
 * <p>In addition to {@code Stream}, which is a stream of object references,
 * there are primitive specializations for {@link IntStream}, {@link LongStream},
 * and {@link DoubleStream}, all of which are referred to as "streams" and
 * conform to the characteristics and restrictions described here.
 *
 * <p>To perform a computation, stream
 * <a href="package-summary.html#StreamOps">operations</a> are composed into a
 * <em>stream pipeline</em>.  A stream pipeline consists of a source (which
 * might be an array, a collection, a generator function, an I/O channel,
 * etc), zero or more <em>intermediate operations</em> (which transform a
 * stream into another stream, such as {@link Stream#filter(Predicate)}), and a
 * <em>terminal operation</em> (which produces a result or side-effect, such
 * as {@link Stream#count()} or {@link Stream#forEach(Consumer)}).
 * Streams are lazy; computation on the source data is only performed when the
 * terminal operation is initiated, and source elements are consumed only
 * as needed.
 *
 * <p>Collections and streams, while bearing some superficial similarities,
 * have different goals.  Collections are primarily concerned with the efficient
 * management of, and access to, their elements.  By contrast, streams do not
 * provide a means to directly access or manipulate their elements, and are
 * instead concerned with declaratively describing their source and the
 * computational operations which will be performed in aggregate on that source.
 * However, if the provided stream operations do not offer the desired
 * functionality, the {@link #iterator()} and {@link #spliterator()} operations
 * can be used to perform a controlled traversal.
 *
 * <p>A stream pipeline, like the "widgets" example above, can be viewed as
 * a <em>query</em> on the stream source.  Unless the source was explicitly
 * designed for concurrent modification (such as a {@link ConcurrentHashMap}),
 * unpredictable or erroneous behavior may result from modifying the stream
 * source while it is being queried.
 *
 * <p>Most stream operations accept parameters that describe user-specified
 * behavior, such as the lambda expression {@code w -> w.getWeight()} passed to
 * {@code mapToInt} in the example above.  To preserve correct behavior,
 * these <em>behavioral parameters</em>:
 * <ul>
 * <li>must be <a href="package-summary.html#NonInterference">non-interfering</a>
 * (they do not modify the stream source); and</li>
 * <li>in most cases must be <a href="package-summary.html#Statelessness">stateless</a>
 * (their result should not depend on any state that might change during execution
 * of the stream pipeline).</li>
 * </ul>
 *
 * <p>Such parameters are always instances of a
 * <a href="../function/package-summary.html">functional interface</a> such
 * as {@link java.util.function.Function}, and are often lambda expressions or
 * method references.  Unless otherwise specified these parameters must be
 * <em>non-null</em>.
 *
 * <p>A stream should be operated on (invoking an intermediate or terminal stream
 * operation) only once.  This rules out, for example, "forked" streams, where
 * the same source feeds two or more pipelines, or multiple traversals of the
 * same stream.  A stream implementation may throw {@link IllegalStateException}
 * if it detects that the stream is being reused. However, since some stream
 * operations may return their receiver rather than a new stream object, it may
 * not be possible to detect reuse in all cases.
 *
 * <p>Streams have a {@link #close()} method and implement {@link AutoCloseable},
 * but nearly all stream instances do not actually need to be closed after use.
 * Generally, only streams whose source is an IO channel (such as those returned
 * by {@link Files#lines(Path, Charset)}) will require closing.  Most streams
 * are backed by collections, arrays, or generating functions, which require no
 * special resource management.  (If a stream does require closing, it can be
 * declared as a resource in a {@code try}-with-resources statement.)
 *
 * <p>Stream pipelines may execute either sequentially or in
 * <a href="package-summary.html#Parallelism">parallel</a>.  This
 * execution mode is a property of the stream.  Streams are created
 * with an initial choice of sequential or parallel execution.  (For example,
 * {@link Collection#stream() Collection.stream()} creates a sequential stream,
 * and {@link Collection#parallelStream() Collection.parallelStream()} creates
 * a parallel one.)  This choice of execution mode may be modified by the
 * {@link #sequential()} or {@link #parallel()} methods, and may be queried with
 * the {@link #isParallel()} method.
 *
 * @param <T> the type of the stream elements
 * @since 1.8
 * @see IntStream
 * @see LongStream
 * @see DoubleStream
 * @see <a href="package-summary.html">java.util.stream</a>
 */
public abstract class Stream<T> implements BaseStream<T, Stream<T>> {
    private static final int DEFAULT_READING_THREAD_NUM = 64;

    @SuppressWarnings("rawtypes")
    static final Comparator OBJECT_COMPARATOR = new Comparator<Comparable>() {
        @Override
        public int compare(final Comparable a, final Comparable b) {
            return N.compare(a, b);
        }
    };

    static final Object NONE = new Object();
    static final Field listElementDataField;
    static final Field listSizeField;
    static volatile boolean isListElementDataFieldGettable = true;
    static volatile boolean isListElementDataFieldSettable = true;

    static {
        Field tmp = null;

        try {
            tmp = ArrayList.class.getDeclaredField("elementData");
        } catch (Exception e) {
            // ignore.
        }

        listElementDataField = tmp != null && tmp.getType().equals(Object[].class) ? tmp : null;

        if (listElementDataField != null) {
            listElementDataField.setAccessible(true);
        }

        tmp = null;

        try {
            tmp = ArrayList.class.getDeclaredField("size");
        } catch (Exception e) {
            // ignore.
        }

        listSizeField = tmp != null && tmp.getType().equals(int.class) ? tmp : null;

        if (listSizeField != null) {
            listSizeField.setAccessible(true);
        }
    }

    /**
     * Returns a stream consisting of the elements of this stream that match
     * the given predicate.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to each element to determine if it
     *                  should be included
     * @return the new stream
     */
    public abstract Stream<T> filter(final Predicate<? super T> predicate);

    /**
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract Stream<T> filter(final Predicate<? super T> predicate, final long max);

    /**
     * Keep the elements until the given predicate returns false.
     * 
     * @param predicate
     * @return
     */
    public abstract Stream<T> takeWhile(final Predicate<? super T> predicate);

    /**
     * Keep the elements until the given predicate returns false.
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract Stream<T> takeWhile(final Predicate<? super T> predicate, final long max);

    /**
     * Remove the elements until the given predicate returns false.
     * 
     * 
     * @param predicate
     * @return
     */
    public abstract Stream<T> dropWhile(final Predicate<? super T> predicate);

    /**
     * Remove the elements until the given predicate returns false.
     * 
     * @param predicate
     * @param max the maximum elements number to the new Stream.
     * @return
     */
    public abstract Stream<T> dropWhile(final Predicate<? super T> predicate, final long max);

    /**
     * Returns a stream consisting of the results of applying the given
     * function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param <R> The element type of the new stream
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract <R> Stream<R> map(Function<? super T, ? extends R> mapper);

    public abstract CharStream mapToChar(ToCharFunction<? super T> mapper);

    public abstract ByteStream mapToByte(ToByteFunction<? super T> mapper);

    public abstract ShortStream mapToShort(ToShortFunction<? super T> mapper);

    /**
     * Returns an {@code IntStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">
     *     intermediate operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element
     * @return the new stream
     */
    public abstract IntStream mapToInt(ToIntFunction<? super T> mapper);

    /**
     * Returns a {@code LongStream} consisting of the results of applying the
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
    public abstract LongStream mapToLong(ToLongFunction<? super T> mapper);

    public abstract FloatStream mapToFloat(ToFloatFunction<? super T> mapper);

    /**
     * Returns a {@code DoubleStream} consisting of the results of applying the
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
    public abstract DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper);

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
     * @apiNote
     * The {@code flatMap()} operation has the effect of applying a one-to-many
     * transformation to the elements of the stream, and then flattening the
     * resulting elements into a new stream.
     *
     * <p><b>Examples.</b>
     *
     * <p>If {@code orders} is a stream of purchase orders, and each purchase
     * order contains a collection of line items, then the following produces a
     * stream containing all the line items in all the orders:
     * <pre>{@code
     *     orders.flatMap(order -> order.getLineItems().stream())...
     * }</pre>
     *
     * <p>If {@code path} is the path to a file, then the following produces a
     * stream of the {@code words} contained in that file:
     * <pre>{@code
     *     Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8);
     *     Stream<String> words = lines.flatMap(line -> Stream.of(line.split(" +")));
     * }</pre>
     * The {@code mapper} function passed to {@code flatMap} splits a line,
     * using a simple regular expression, into an array of words, and then
     * creates a stream of words from that array.
     *
     * @param <R> The element type of the new stream
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces a stream
     *               of new values
     * @return the new stream
     */
    public abstract <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper);

    public abstract <R> Stream<R> flatMap2(Function<? super T, ? extends R[]> mapper);

    public abstract <R> Stream<R> flatMap3(Function<? super T, ? extends Collection<? extends R>> mapper);

    public abstract CharStream flatMapToChar(Function<? super T, ? extends CharStream> mapper);

    public abstract CharStream flatMapToChar2(Function<? super T, char[]> mapper);

    public abstract CharStream flatMapToChar3(Function<? super T, ? extends Collection<Character>> mapper);

    public abstract ByteStream flatMapToByte(Function<? super T, ? extends ByteStream> mapper);

    public abstract ByteStream flatMapToByte2(Function<? super T, byte[]> mapper);

    public abstract ByteStream flatMapToByte3(Function<? super T, ? extends Collection<Byte>> mapper);

    public abstract ShortStream flatMapToShort(Function<? super T, ? extends ShortStream> mapper);

    public abstract ShortStream flatMapToShort2(Function<? super T, short[]> mapper);

    public abstract ShortStream flatMapToShort3(Function<? super T, ? extends Collection<Short>> mapper);

    /**
     * Returns an {@code IntStream} consisting of the results of replacing each
     * element of this stream with the contents of a mapped stream produced by
     * applying the provided mapping function to each element.  Each mapped
     * stream is {@link java.util.stream.BaseStream#close() closed} after its
     * contents have been placed into this stream.  (If a mapped stream is
     * {@code null} an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces a stream
     *               of new values
     * @return the new stream
     * @see #flatMap(Function)
     */
    public abstract IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper);

    public abstract IntStream flatMapToInt2(Function<? super T, int[]> mapper);

    public abstract IntStream flatMapToInt3(Function<? super T, ? extends Collection<Integer>> mapper);

    /**
     * Returns an {@code LongStream} consisting of the results of replacing each
     * element of this stream with the contents of a mapped stream produced by
     * applying the provided mapping function to each element.  Each mapped
     * stream is {@link java.util.stream.BaseStream#close() closed} after its
     * contents have been placed into this stream.  (If a mapped stream is
     * {@code null} an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces a stream
     *               of new values
     * @return the new stream
     * @see #flatMap(Function)
     */
    public abstract LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper);

    public abstract LongStream flatMapToLong2(Function<? super T, long[]> mapper);

    public abstract LongStream flatMapToLong3(Function<? super T, ? extends Collection<Long>> mapper);

    public abstract FloatStream flatMapToFloat(Function<? super T, ? extends FloatStream> mapper);

    public abstract FloatStream flatMapToFloat2(Function<? super T, float[]> mapper);

    public abstract FloatStream flatMapToFloat3(Function<? super T, ? extends Collection<Float>> mapper);

    /**
     * Returns an {@code DoubleStream} consisting of the results of replacing
     * each element of this stream with the contents of a mapped stream produced
     * by applying the provided mapping function to each element.  Each mapped
     * stream is {@link java.util.stream.BaseStream#close() closed} after its
     * contents have placed been into this stream.  (If a mapped stream is
     * {@code null} an empty stream is used, instead.)
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *               <a href="package-summary.html#Statelessness">stateless</a>
     *               function to apply to each element which produces a stream
     *               of new values
     * @return the new stream
     * @see #flatMap(Function)
     */
    public abstract DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper);

    public abstract DoubleStream flatMapToDouble2(Function<? super T, double[]> mapper);

    public abstract DoubleStream flatMapToDouble3(Function<? super T, ? extends Collection<Double>> mapper);

    public abstract <K> Stream<Map.Entry<K, List<T>>> groupBy(final Function<? super T, ? extends K> classifier);

    public abstract <K> Stream<Map.Entry<K, List<T>>> groupBy(final Function<? super T, ? extends K> classifier, final Supplier<Map<K, List<T>>> mapFactory);

    public abstract <K, A, D> Stream<Map.Entry<K, D>> groupBy(final Function<? super T, ? extends K> classifier, final Collector<? super T, A, D> downstream);

    public abstract <K, D, A> Stream<Map.Entry<K, D>> groupBy(final Function<? super T, ? extends K> classifier, final Collector<? super T, A, D> downstream,
            final Supplier<Map<K, D>> mapFactory);

    public abstract <K, U> Stream<Map.Entry<K, U>> groupBy(final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper);

    public abstract <K, U> Stream<Map.Entry<K, U>> groupBy(final Function<? super T, ? extends K> keyMapper, final Function<? super T, ? extends U> valueMapper,
            final Supplier<Map<K, U>> mapFactory);

    public abstract <K, U> Stream<Map.Entry<K, U>> groupBy(final Function<? super T, ? extends K> keyMapper, final Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction);

    public abstract <K, U> Stream<Map.Entry<K, U>> groupBy(final Function<? super T, ? extends K> keyMapper, final Function<? super T, ? extends U> valueMapper,
            final BinaryOperator<U> mergeFunction, final Supplier<Map<K, U>> mapFactory);

    /**
     * Returns Stream of Stream with consecutive sub sequences of the elements, each of the same size (the final sequence may be smaller).
     * 
     * @param size
     * @return
     */
    public abstract Stream<Stream<T>> split(int size);

    /**
     * Returns Stream of Stream with consecutive sub sequences of the elements, each of the same size (the final sequence may be smaller).
     * 
     * @param size
     * @return
     */
    public abstract Stream<List<T>> splitIntoList(int size);

    /**
     * Returns Stream of Stream with consecutive sub sequences of the elements, each of the same size (the final sequence may be smaller).
     * 
     * @param size
     * @return
     */
    public abstract Stream<Set<T>> splitIntoSet(int size);

    /**
     * Returns a stream consisting of the distinct elements (according to
     * {@link Object#equals(Object)}) of this stream.
     *
     * <p>For ordered streams, the selection of distinct elements is stable
     * (for duplicated elements, the element appearing first in the encounter
     * order is preserved.)  For unordered streams, no stability guarantees
     * are made.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @apiNote
     * Preserving stability for {@code distinct()} in parallel pipelines is
     * relatively expensive (requires that the operation act as a full barrier,
     * with substantial buffering overhead), and stability is often not needed.
     * Using an unordered stream source
     * or removing the ordering constraint with {@link #unordered()} may result
     * in significantly more efficient execution for {@code distinct()} in parallel
     * pipelines, if the semantics of your situation permit.  If consistency
     * with encounter order is required, and you are experiencing poor performance
     * or memory utilization with {@code distinct()} in parallel pipelines,
     * switching to sequential execution with {@link #sequential()} may improve
     * performance.
     *
     * @return the new stream
     */
    public abstract Stream<T> distinct();

    public abstract Stream<T> distinct(Comparator<? super T> comparator);

    public abstract Stream<T> distinct(Function<? super T, ?> keyMapper);

    public abstract Stream<T> top(int n);

    public abstract Stream<T> top(int n, Comparator<? super T> comparator);

    /**
     * Returns a stream consisting of the elements of this stream, sorted
     * according to natural order.  If the elements of this stream are not
     * {@code Comparable}, a {@code java.lang.ClassCastException} may be thrown
     * when the terminal operation is executed.
     *
     * <p>For ordered streams, the sort is stable.  For unordered streams, no
     * stability guarantees are made.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @return the new stream
     */
    public abstract Stream<T> sorted();

    /**
     * Returns a stream consisting of the elements of this stream, sorted
     * according to the provided {@code Comparator}.
     *
     * <p>For ordered streams, the sort is stable.  For unordered streams, no
     * stability guarantees are made.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @param comparator a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                   <a href="package-summary.html#Statelessness">stateless</a>
     *                   {@code Comparator} to be used to compare stream elements
     * @return the new stream
     */
    public abstract Stream<T> sorted(Comparator<? super T> comparator);

    public abstract Stream<T> parallelSorted();

    public abstract Stream<T> parallelSorted(Comparator<? super T> comparator);

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
     *     Stream.of("one", "two", "three", "four")
     *         .filter(e -> e.length() > 3)
     *         .peek(e -> System.out.println("Filtered value: " + e))
     *         .map(String::toUpperCase)
     *         .peek(e -> System.out.println("Mapped value: " + e))
     *         .collect(Collectors.toList());
     * }</pre>
     *
     * @param action a <a href="package-summary.html#NonInterference">
     *                 non-interfering</a> action to perform on the elements as
     *                 they are consumed from the stream
     * @return this stream or a new stream with same elements.
     */
    public abstract Stream<T> peek(Consumer<? super T> action);

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
    public abstract Stream<T> limit(long maxSize);

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
    public abstract Stream<T> skip(long n);

    /**
     * Performs an action for each element of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * <p>The behavior of this operation is explicitly nondeterministic.
     * For parallel stream pipelines, this operation does <em>not</em>
     * guarantee to respect the encounter order of the stream, as doing so
     * would sacrifice the benefit of parallelism.  For any given element, the
     * action may be performed at whatever time and in whatever thread the
     * library chooses.  If the action accesses shared state, it is
     * responsible for providing the required synchronization.
     *
     * @param action a <a href="package-summary.html#NonInterference">
     *               non-interfering</a> action to perform on the elements
     */
    public abstract void forEach(Consumer<? super T> action);

    /**
     * 
     * @param action break if the action returns false.
     * @return false if it breaks, otherwise true.
     */
    public abstract boolean forEach2(Function<? super T, Boolean> action);

    /**
     * Returns an array containing the elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return an array containing the elements of this stream
     */
    public abstract Object[] toArray();

    /**
     * Returns an array containing the elements of this stream, using the
     * provided {@code generator} function to allocate the returned array, as
     * well as any additional arrays that might be required for a partitioned
     * execution or for resizing.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote
     * The generator function takes an integer, which is the size of the
     * desired array, and produces an array of the desired size.  This can be
     * concisely expressed with an array constructor reference:
     * <pre>{@code
     *     Person[] men = people.stream()
     *                          .filter(p -> p.getGender() == MALE)
     *                          .toArray(Person[]::new);
     * }</pre>
     *
     * @param <A> the element type of the resulting array
     * @param generator a function which produces a new array of the desired
     *                  type and the provided length
     * @return an array containing the elements in this stream
     * @throws ArrayStoreException if the runtime type of the array returned
     * from the array generator is not a supertype of the runtime type of every
     * element in this stream
     */
    public abstract <A> A[] toArray(IntFunction<A[]> generator);

    public abstract <A> ObjectList<A> toObjectList(Class<A> cls);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using the provided identity value and an
     * <a href="package-summary.html#Associativity">associative</a>
     * accumulation function, and returns the reduced value.  This is equivalent
     * to:
     * <pre>{@code
     *     T result = identity;
     *     for (T element : this stream)
     *         result = accumulator.apply(result, element)
     *     return result;
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code identity} value must be an identity for the accumulator
     * function. This means that for all {@code t},
     * {@code accumulator.apply(identity, t)} is equal to {@code t}.
     * The {@code accumulator} function must be an
     * <a href="package-summary.html#Associativity">associative</a> function.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote Sum, min, max, average, and string concatenation are all special
     * cases of reduction. Summing a stream of numbers can be expressed as:
     *
     * <pre>{@code
     *     Integer sum = integers.reduce(0, (a, b) -> a+b);
     * }</pre>
     *
     * or:
     *
     * <pre>{@code
     *     Integer sum = integers.reduce(0, Integer::sum);
     * }</pre>
     *
     * <p>While this may seem a more roundabout way to perform an aggregation
     * compared to simply mutating a running total in a loop, reduction
     * operations parallelize more gracefully, without needing additional
     * synchronization and with greatly reduced risk of data races.
     *
     * @param identity the identity value for the accumulating function
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for combining two values
     * @return the result of the reduction
     */
    public abstract T reduce(T identity, BinaryOperator<T> accumulator);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using an
     * <a href="package-summary.html#Associativity">associative</a> accumulation
     * function, and returns an {@code Optional} describing the reduced value,
     * if any. This is equivalent to:
     * <pre>{@code
     *     boolean foundAny = false;
     *     T result = null;
     *     for (T element : this stream) {
     *         if (!foundAny) {
     *             foundAny = true;
     *             result = element;
     *         }
     *         else
     *             result = accumulator.apply(result, element);
     *     }
     *     return foundAny ? Optional.of(result) : Optional.empty();
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
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for combining two values
     * @return an {@link Optional} describing the result of the reduction
     * @throws NullPointerException if the result of the reduction is null
     * @see #reduce(Object, BinaryOperator)
     * @see #min(Comparator)
     * @see #max(Comparator)
     */
    public abstract Optional<T> reduce(BinaryOperator<T> accumulator);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using the provided identity, accumulation and
     * combining functions.  This is equivalent to:
     * <pre>{@code
     *     U result = identity;
     *     for (T element : this stream)
     *         result = accumulator.apply(result, element)
     *     return result;
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code identity} value must be an identity for the combiner
     * function.  This means that for all {@code u}, {@code combiner(identity, u)}
     * is equal to {@code u}.  Additionally, the {@code combiner} function
     * must be compatible with the {@code accumulator} function; for all
     * {@code u} and {@code t}, the following must hold:
     * <pre>{@code
     *     combiner.apply(u, accumulator.apply(identity, t)) == accumulator.apply(u, t)
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote Many reductions using this form can be represented more simply
     * by an explicit combination of {@code map} and {@code reduce} operations.
     * The {@code accumulator} function acts as a fused mapper and accumulator,
     * which can sometimes be more efficient than separate mapping and reduction,
     * such as when knowing the previously reduced value allows you to avoid
     * some computation.
     *
     * @param <U> The type of the result
     * @param identity the identity value for the combiner function
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for incorporating an additional element into a result
     * @param combiner an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for combining two values, which must be
     *                    compatible with the accumulator function
     * @return the result of the reduction
     * @see #reduce(BinaryOperator)
     * @see #reduce(Object, BinaryOperator)
     */
    public abstract <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner);

    /**
     * Performs a <a href="package-summary.html#MutableReduction">mutable
     * reduction</a> operation on the elements of this stream.  A mutable
     * reduction is one in which the reduced value is a mutable result container,
     * such as an {@code ArrayList}, and elements are incorporated by updating
     * the state of the result rather than by replacing the result.  This
     * produces a result equivalent to:
     * <pre>{@code
     *     R result = supplier.get();
     *     for (T element : this stream)
     *         accumulator.accept(result, element);
     *     return result;
     * }</pre>
     *
     * <p>Like {@link #reduce(Object, BinaryOperator)}, {@code collect} operations
     * can be parallelized without requiring additional synchronization.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote There are many existing classes in the JDK whose signatures are
     * well-suited for use with method references as arguments to {@code collect()}.
     * For example, the following will accumulate strings into an {@code ArrayList}:
     * <pre>{@code
     *     List<String> asList = stringStream.collect(ArrayList::new, ArrayList::add,
     *                                                ArrayList::addAll);
     * }</pre>
     *
     * <p>The following will take a stream of strings and concatenates them into a
     * single string:
     * <pre>{@code
     *     String concat = stringStream.collect(StringBuilder::new, StringBuilder::append,
     *                                          StringBuilder::append)
     *                                 .toString();
     * }</pre>
     *
     * @param <R> type of the result
     * @param supplier a function that creates a new result container. For a
     *                 parallel execution, this function may be called
     *                 multiple times and must return a fresh value each time.
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for incorporating an additional element into a result
     * @param combiner an <a href="package-summary.html#Associativity">associative</a>,
     *                    <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                    <a href="package-summary.html#Statelessness">stateless</a>
     *                    function for combining two values, which must be
     *                    compatible with the accumulator function
     * @return the result of the reduction
     */
    public abstract <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner);

    /**
     * Performs a <a href="package-summary.html#MutableReduction">mutable
     * reduction</a> operation on the elements of this stream using a
     * {@code Collector}.  A {@code Collector}
     * encapsulates the functions used as arguments to
     * {@link #collect(Supplier, BiConsumer, BiConsumer)}, allowing for reuse of
     * collection strategies and composition of collect operations such as
     * multiple-level grouping or partitioning.
     *
     * <p>If the stream is parallel, and the {@code Collector}
     * is {@link Collector.Characteristics#CONCURRENT concurrent}, and
     * either the stream is unordered or the collector is
     * {@link Collector.Characteristics#UNORDERED unordered},
     * then a concurrent reduction will be performed (see {@link Collector} for
     * details on concurrent reduction.)
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * <p>When executed in parallel, multiple intermediate results may be
     * instantiated, populated, and merged so as to maintain isolation of
     * mutable data structures.  Therefore, even when executed in parallel
     * with non-thread-safe data structures (such as {@code ArrayList}), no
     * additional synchronization is needed for a parallel reduction.
     *
     * @apiNote
     * The following will accumulate strings into an ArrayList:
     * <pre>{@code
     *     List<String> asList = stringStream.collect(Collectors.toList());
     * }</pre>
     *
     * <p>The following will classify {@code Person} objects by city:
     * <pre>{@code
     *     Map<String, List<Person>> peopleByCity
     *         = personStream.collect(Collectors.groupingBy(Person::getCity));
     * }</pre>
     *
     * <p>The following will classify {@code Person} objects by state and city,
     * cascading two {@code Collector}s together:
     * <pre>{@code
     *     Map<String, Map<String, List<Person>>> peopleByStateAndCity
     *         = personStream.collect(Collectors.groupingBy(Person::getState,
     *                                                      Collectors.groupingBy(Person::getCity)));
     * }</pre>
     *
     * @param <R> the type of the result
     * @param <A> the intermediate accumulation type of the {@code Collector}
     * @param collector the {@code Collector} describing the reduction
     * @return the result of the reduction
     * @see #collect(Supplier, BiConsumer, BiConsumer)
     * @see Collectors
     */
    public abstract <R, A> R collect(Collector<? super T, A, R> collector);

    /**
     * Returns the minimum element of this stream according to the provided
     * {@code Comparator}.  This is a special case of a
     * <a href="package-summary.html#Reduction">reduction</a>.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal operation</a>.
     *
     * @param comparator a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                   <a href="package-summary.html#Statelessness">stateless</a>
     *                   {@code Comparator} to compare elements of this stream
     * @return an {@code Optional} describing the minimum element of this stream,
     * or an empty {@code Optional} if the stream is empty
     * @throws NullPointerException if the minimum element is null
     */
    public abstract Optional<T> min(Comparator<? super T> comparator);

    /**
     * Returns the maximum element of this stream according to the provided
     * {@code Comparator}.  This is a special case of a
     * <a href="package-summary.html#Reduction">reduction</a>.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param comparator a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                   <a href="package-summary.html#Statelessness">stateless</a>
     *                   {@code Comparator} to compare elements of this stream
     * @return an {@code Optional} describing the maximum element of this stream,
     * or an empty {@code Optional} if the stream is empty
     * @throws NullPointerException if the maximum element is null
     */
    public abstract Optional<T> max(Comparator<? super T> comparator);

    /**
     * 
     * @param k
     * @param cmp
     * @return Optional.empty() if there is no element or count less than k, otherwise the kth largest element.
     */
    public abstract Optional<T> kthLargest(int k, Comparator<? super T> cmp);

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
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if any elements of the stream match the provided
     * predicate, otherwise {@code false}
     */
    public abstract boolean anyMatch(Predicate<? super T> predicate);

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
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if either all elements of the stream match the
     * provided predicate or the stream is empty, otherwise {@code false}
     */
    public abstract boolean allMatch(Predicate<? super T> predicate);

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
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering</a>,
     *                  <a href="package-summary.html#Statelessness">stateless</a>
     *                  predicate to apply to elements of this stream
     * @return {@code true} if either no elements of the stream match the
     * provided predicate or the stream is empty, otherwise {@code false}
     */
    public abstract boolean noneMatch(Predicate<? super T> predicate);

    /**
     * Returns an {@link Optional} describing the first element of this stream,
     * or an empty {@code Optional} if the stream is empty.  If the stream has
     * no encounter order, then any element may be returned.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @return an {@code Optional} describing the first element of this stream,
     * or an empty {@code Optional} if the stream is empty
     * @throws NullPointerException if the element selected is null
     */
    // public abstract Optional<T> findFirst();

    public abstract Optional<T> findFirst(Predicate<? super T> predicate);

    // public abstract Optional<T> findLast();

    public abstract Optional<T> findLast(Predicate<? super T> predicate);

    /**
     * Returns an {@link Optional} describing some element of the stream, or an
     * empty {@code Optional} if the stream is empty.
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
     * @return an {@code Optional} describing some element of this stream, or an
     * empty {@code Optional} if the stream is empty
     * @throws NullPointerException if the element selected is null
     * @see #findFirst()
     */
    // public abstract Optional<T> findAny();

    public abstract Optional<T> findAny(Predicate<? super T> predicate);

    // Static factories

    // Static factories

    public static <T> Stream<T> empty() {
        return of((T[]) N.EMPTY_OBJECT_ARRAY);
    }

    public static <T> Stream<T> of(final T... a) {
        return of(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>Stream</code>.
     *
     * @param a
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static <T> Stream<T> of(final T[] a, final int startIndex, final int endIndex) {
        return new ArrayStream<T>(a, startIndex, endIndex);
    }

    /**
     * Returns a sequential, stateful and immutable <code>Stream</code>.
     *
     * @param c
     * @return
     */
    public static <T> Stream<T> of(final Collection<? extends T> c) {
        return of(c, 0, c.size());
    }

    /**
     * Returns a sequential, stateful and immutable <code>Stream</code>.
     * 
     * @param c
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static <T> Stream<T> of(final Collection<? extends T> c, int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex < startIndex || endIndex > c.size()) {
            throw new IllegalArgumentException("startIndex(" + startIndex + ") or endIndex(" + endIndex + ") is invalid");
        }

        // return new CollectionStream<T>(c);
        // return new ArrayStream<T>((T[]) c.toArray()); // faster

        if (isListElementDataFieldGettable && listElementDataField != null && c instanceof ArrayList) {
            T[] array = null;

            try {
                array = (T[]) listElementDataField.get(c);
            } catch (Exception e) {
                // ignore;
                isListElementDataFieldGettable = false;
            }

            if (array != null) {
                return of(array, startIndex, endIndex);
            }
        }

        if (startIndex == 0 && endIndex == c.size()) {
            // return (c.size() > 10 && (c.size() < 1000 || (c.size() < 100000 && c instanceof ArrayList))) ? streamOf((T[]) c.toArray()) : c.stream();
            return of(c.iterator());
        } else {
            return of(c.iterator(), startIndex, endIndex);
        }
    }

    /**
     * Returns a sequential, stateful and immutable <code>Stream</code>.
     *
     * @param iterator
     * @return
     */
    public static <T> Stream<T> of(final Iterator<? extends T> iterator) {
        return new IteratorStream<T>(iterator);
    }

    /**
     * Returns a sequential, stateful and immutable <code>Stream</code>.
     * 
     * @param c
     * @param startIndex
     * @param endIndex
     * @return
     */
    static <T> Stream<T> of(final Iterator<? extends T> iterator, int startIndex, int endIndex) {
        if (startIndex < 0 || endIndex < startIndex) {
            throw new IllegalArgumentException("startIndex(" + startIndex + ") or endIndex(" + endIndex + ") is invalid");
        }

        final Stream<T> stream = of(iterator);
        return stream.skip(startIndex).limit(endIndex - startIndex);
    }

    /**
     * <p> The returned stream encapsulates a {@link Reader}.  If timely
     * disposal of file system resources is required, the try-with-resources
     * construct should be used to ensure that the stream's
     * {@link Stream#close close} method is invoked after the stream operations
     * are completed.
     * 
     * @param file
     * @return
     */
    static Stream<String> of(File file) {
        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(file));
            final BufferedReader tmp = br;

            return of(br).onClose(new Runnable() {
                @Override
                public void run() {
                    IOUtil.close(tmp);
                }
            });
        } catch (IOException e) {
            IOUtil.close(br);
            throw new AbacusIOException(e);
        }
    }

    /**
     * It's user's responsibility to close the input <code>reader</code> after the stream is finished.
     * 
     * @param reader
     * @return
     */
    public static Stream<String> of(final Reader reader) {
        return of(new LineIterator(reader));
    }

    static Stream<String> of(final Reader reader, int startIndex, int endIndex) {
        return of(new LineIterator(reader), startIndex, endIndex);
    }

    /**
     * It's user's responsibility to close the input <code>resultSet</code> after the stream is finished.
     * 
     * @param resultSet
     * @return
     */
    public static Stream<Object[]> of(final ResultSet resultSet) {
        return of(new RowIterator(resultSet));
    }

    static Stream<Object[]> of(final ResultSet resultSet, int startIndex, int endIndex) {
        return of(new RowIterator(resultSet), startIndex, endIndex);
    }

    public static <T> Stream<T> iterate(final Supplier<Boolean> hasNext, final Supplier<? extends T> next) {
        return of(new ImmutableIterator<T>() {
            private boolean hasNextVal = false;

            @Override
            public boolean hasNext() {
                if (hasNextVal == false) {
                    hasNextVal = hasNext.get().booleanValue();
                }

                return hasNextVal;
            }

            @Override
            public T next() {
                if (hasNextVal == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNextVal = false;
                return next.get();
            }
        });
    }

    /**
     * Returns a sequential ordered {@code Stream} produced by iterative
     * application of a function {@code f} to an initial element {@code seed},
     * producing a {@code Stream} consisting of {@code seed}, {@code f(seed)},
     * {@code f(f(seed))}, etc.
     *
     * <p>The first element (position {@code 0}) in the {@code Stream} will be
     * the provided {@code seed}.  For {@code n > 0}, the element at position
     * {@code n}, will be the result of applying the function {@code f} to the
     * element at position {@code n - 1}.
     * 
     * @param seed
     * @param hasNext
     * @param f
     * @return
     */
    public static <T> Stream<T> iterate(final T seed, final Supplier<Boolean> hasNext, final UnaryOperator<T> f) {
        return of(new ImmutableIterator<T>() {
            private T t = (T) Stream.NONE;
            private boolean hasNextVal = false;

            @Override
            public boolean hasNext() {
                if (hasNextVal == false) {
                    hasNextVal = hasNext.get().booleanValue();
                }

                return hasNextVal;
            }

            @Override
            public T next() {
                if (hasNextVal == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNextVal = false;
                return t = (t == Stream.NONE) ? seed : f.apply(t);
            }
        });
    }

    /**
     * Returns a sequential, stateful and immutable <code>CharStream</code>.
     *
     * @param e
     * @return
     */
    static CharStream from(final char e) {
        return from(Array.of(e));
    }

    /**
     * Returns a sequential, stateful and immutable <code>CharStream</code>.
     *
     * @param a
     * @return
     */
    public static CharStream from(final char[] a) {
        return from(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>CharStream</code>.
     *
     * @param a
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static CharStream from(final char[] a, final int startIndex, final int endIndex) {
        //        final int[] values = new int[endIndex - startIndex];
        //
        //        for (int i = 0, j = startIndex; j < endIndex; i++, j++) {
        //            values[i] = a[j];
        //        }
        //
        //        return new CharStreamImpl(values);

        return new ArrayCharStream(a, startIndex, endIndex);
    }

    /**
     * Returns a sequential, stateful and immutable <code>ByteStream</code>.
     *
     * @param e
     * @return
     */
    static ByteStream from(final byte e) {
        return from(Array.of(e));
    }

    /**
     * Returns a sequential, stateful and immutable <code>ByteStream</code>.
     *
     * @param a
     * @return
     */
    public static ByteStream from(final byte[] a) {
        return from(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>ByteStream</code>.
     *
     * @param a
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static ByteStream from(final byte[] a, final int startIndex, final int endIndex) {
        //        final int[] values = new int[endIndex - startIndex];
        //
        //        for (int i = 0, j = startIndex; j < endIndex; i++, j++) {
        //            values[i] = a[j];
        //        }
        //
        //        return new IntStreamImpl(values);

        return new ArrayByteStream(a, startIndex, endIndex);
    }

    /**
     * Returns a sequential, stateful and immutable <code>ShortStream</code>.
     *
     * @param e
     * @return
     */
    static ShortStream from(final short e) {
        return from(Array.of(e));
    }

    /**
     * Returns a sequential, stateful and immutable <code>ShortStream</code>.
     *
     * @param a
     * @return
     */
    public static ShortStream from(final short[] a) {
        return from(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>ShortStream</code>.
     *
     * @param a
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static ShortStream from(final short[] a, final int startIndex, final int endIndex) {
        //        final int[] values = new int[endIndex - startIndex];
        //
        //        for (int i = 0, j = startIndex; j < endIndex; i++, j++) {
        //            values[i] = a[j];
        //        }
        //
        //        return new IntStreamImpl(values);

        return new ArrayShortStream(a, startIndex, endIndex);
    }

    /**
     * Returns a sequential, stateful and immutable <code>IntStream</code>.
     *
     * @param e
     * @return
     */
    static IntStream from(final int e) {
        return from(Array.of(e));
    }

    /**
     * Returns a sequential, stateful and immutable <code>IntStream</code>.
     *
     * @param a
     * @return
     */
    public static IntStream from(final int[] a) {
        return from(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>IntStream</code>.
     *
     * @param a
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static IntStream from(final int[] a, final int startIndex, final int endIndex) {
        return new ArrayIntStream(a, startIndex, endIndex);
    }

    /**
     * Returns a sequential, stateful and immutable <code>IntStream</code>.
     *
     * @param e
     * @return
     */
    static LongStream from(final long e) {
        return from(Array.of(e));
    }

    /**
     * Returns a sequential, stateful and immutable <code>LongStream</code>.
     *
     * @param a
     * @return
     */
    public static LongStream from(final long[] a) {
        return from(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>LongStream</code>.
     *
     * @param a
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static LongStream from(final long[] a, final int startIndex, final int endIndex) {
        return new ArrayLongStream(a, startIndex, endIndex);
    }

    /**
     * Returns a sequential, stateful and immutable <code>FloatStream</code>.
     *
     * @param e
     * @return
     */
    static FloatStream from(final float e) {
        return from(Array.of(e));
    }

    /**
     * Returns a sequential, stateful and immutable <code>FloatStream</code>.
     *
     * @param a
     * @return
     */
    public static FloatStream from(final float[] a) {
        return from(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>FloatStream</code>.
     *
     * @param a
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static FloatStream from(final float[] a, final int startIndex, final int endIndex) {
        //        final double[] values = new double[endIndex - startIndex];
        //
        //        for (int i = 0, j = startIndex; j < endIndex; i++, j++) {
        //            values[i] = a[j];
        //        }
        //
        //        return new DoubleStreamImpl(values);

        return new ArrayFloatStream(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>DoubleStream</code>.
     *
     * @param e
     * @return
     */
    static DoubleStream from(final double e) {
        return from(Array.of(e));
    }

    /**
     * Returns a sequential, stateful and immutable <code>DoubleStream</code>.
     *
     * @param a
     * @return
     */
    public static DoubleStream from(final double[] a) {
        return from(a, 0, a.length);
    }

    /**
     * Returns a sequential, stateful and immutable <code>DoubleStream</code>.
     *
     * @param a
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static DoubleStream from(final double[] a, final int startIndex, final int endIndex) {
        return new ArrayDoubleStream(a, startIndex, endIndex);
    }

    public static CharStream range(final char startInclusive, final char endExclusive) {
        return from(Array.range(startInclusive, endExclusive));
    }

    public static ByteStream range(final byte startInclusive, final byte endExclusive) {
        return from(Array.range(startInclusive, endExclusive));
    }

    public static ShortStream range(final short startInclusive, final short endExclusive) {
        return from(Array.range(startInclusive, endExclusive));
    }

    public static IntStream range(final int startInclusive, final int endExclusive) {
        return from(Array.range(startInclusive, endExclusive));
    }

    public static LongStream range(final long startInclusive, final long endExclusive) {
        return from(Array.range(startInclusive, endExclusive));
    }

    public static CharStream rangeClosed(final char startInclusive, final char endInclusive) {
        return from(Array.rangeClosed(startInclusive, endInclusive));
    }

    public static ByteStream rangeClosed(final byte startInclusive, final byte endInclusive) {
        return from(Array.rangeClosed(startInclusive, endInclusive));
    }

    public static ShortStream rangeClosed(final short startInclusive, final short endInclusive) {
        return from(Array.rangeClosed(startInclusive, endInclusive));
    }

    public static IntStream rangeClosed(final int startInclusive, final int endInclusive) {
        return from(Array.rangeClosed(startInclusive, endInclusive));
    }

    public static LongStream rangeClosed(final long startInclusive, final long endInclusive) {
        return from(Array.rangeClosed(startInclusive, endInclusive));
    }

    public static <T> Stream<T> repeat(T element, int n) {
        return of(Array.repeat(element, n));
    }

    public static <T> Stream<T> queued(Iterator<? extends T> iterator) {
        return queued(iterator, 128);
    }

    /**
     * Returns a Stream with elements from a temporary queue which is filled by reading the elements from the specified iterator asynchronously.
     * 
     * @param iterator
     * @param queueSize Default value is 128
     * @return
     */
    public static <T> Stream<T> queued(Iterator<? extends T> iterator, int queueSize) {
        if (iterator instanceof QueuedImmutableIterator && ((QueuedImmutableIterator<? extends T>) iterator).max() >= queueSize) {
            return of(iterator);
        } else {
            return parallelConcat(N.asList(iterator), 1, queueSize);
        }
    }

    public static <T> Stream<T> concat(final T[]... a) {
        final Iterator<? extends T>[] iter = new Iterator[a.length];

        for (int i = 0, len = a.length; i < len; i++) {
            iter[i] = of(a[i]).iterator();
        }

        return concat(iter);
    }

    //    static <T> Stream<T> concat(Collection<? extends T>... a) {
    //        final List<Iterator<? extends T>> iterators = new ArrayList<>(a.length);
    //
    //        for (int i = 0, len = a.length; i < len; i++) {
    //            iterators.add(a[i].iterator());
    //        }
    //
    //        return concat(iterators);
    //    }

    public static <T> Stream<T> concat(Stream<? extends T>... a) {
        final Iterator<? extends T>[] iter = new Iterator[a.length];

        for (int i = 0, len = a.length; i < len; i++) {
            iter[i] = a[i].iterator();
        }

        return concat(iter);
    }

    public static <T> Stream<T> concat(final Iterator<? extends T>... a) {
        return concat(N.asList(a));
    }

    public static <T> Stream<T> concat(final Collection<? extends Iterator<? extends T>> c) {
        return of(new ImmutableIterator<T>() {
            private final Iterator<? extends Iterator<? extends T>> iterators = c.iterator();
            private Iterator<? extends T> cur;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && iterators.hasNext()) {
                    cur = iterators.next();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public T next() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.next();
            }
        });
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelConcat(iter1, iter2...)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @return
     */
    public static <T> Stream<T> parallelConcat(final Iterator<? extends T>... a) {
        return parallelConcat(a, DEFAULT_READING_THREAD_NUM, N.min(1024, N.max(128, a.length * 32)));
    }

    /**
     * Returns a Stream with elements from a temporary queue which is filled by reading the elements from the specified iterators in parallel.
     * 
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelConcat(iter1, iter2...)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param iteratorReadThreadNum - count of threads used to read elements from iterator to queue. Default value is min(64, a.length)
     * @param queueSize Default value is N.min(1024, N.max(128, a.length * 32))
     * @return
     */
    public static <T> Stream<T> parallelConcat(final Iterator<? extends T>[] a, final int iteratorReadThreadNum, final int queueSize) {
        return parallelConcat(N.asList(a), iteratorReadThreadNum, queueSize);
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelConcat(iter1, iter2...)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param c
     * @return
     */
    public static <T> Stream<T> parallelConcat(final Collection<? extends Iterator<? extends T>> c) {
        return parallelConcat(c, DEFAULT_READING_THREAD_NUM, N.min(1024, N.max(128, c.size() * 32)));
    }

    /**
     * Returns a Stream with elements from a temporary queue which is filled by reading the elements from the specified iterators in parallel.
     * 
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelConcat(iter1, iter2...)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param iteratorReadThreadNum - count of threads used to read elements from iterator to queue. Default value is min(64, c.size())
     * @param queueSize Default value is N.min(1024, N.max(128, c.size() * 32))
     * @return
     */
    public static <T> Stream<T> parallelConcat(final Collection<? extends Iterator<? extends T>> c, final int iteratorReadThreadNum, final int queueSize) {
        if (c.size() == 0) {
            return Stream.empty();
        }

        final AsyncExecutor asyncExecutor = new AsyncExecutor(N.min(iteratorReadThreadNum, c.size()), 300L, TimeUnit.SECONDS);
        final AtomicInteger threadCounter = new AtomicInteger(c.size());
        final BlockingQueue<T> queue = new ArrayBlockingQueue<T>(queueSize);
        final Holder<Throwable> errorHolder = new Holder<>();
        final MutableBoolean onGoing = MutableBoolean.of(true);

        for (Iterator<? extends T> e : c) {
            final Iterator<? extends T> iter = e;

            asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        T next = null;

                        while (onGoing.booleanValue() && iter.hasNext()) {
                            next = iter.next();

                            if (next == null) {
                                next = (T) NONE;
                            }

                            while (onGoing.booleanValue() && queue.offer(next, 100, TimeUnit.MILLISECONDS) == false) {
                                // continue.
                            }
                        }
                    } catch (Throwable e) {
                        setError(errorHolder, e, onGoing);
                    } finally {
                        threadCounter.decrementAndGet();
                    }
                }
            });
        }

        return of(new QueuedImmutableIterator<T>(queueSize) {
            T next = null;

            @Override
            public boolean hasNext() {
                try {
                    while (next == null && onGoing.booleanValue() && (threadCounter.get() > 0 || queue.size() > 0)) { // (queue.size() > 0 || counter.get() > 0) is wrong. has to check counter first
                        next = queue.poll(100, TimeUnit.MILLISECONDS);
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                }

                if (errorHolder.value() != null) {
                    throwError(errorHolder, onGoing);
                }

                return next != null;
            }

            @Override
            public T next() {
                if (next == null && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                T result = next == NONE ? null : next;
                next = null;
                return result;
            }
        }).onClose(new Runnable() {
            @Override
            public void run() {
                onGoing.setFalse();
            }
        });
    }

    /**
     * Zip together the "a" and "b" arrays until one of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static <A, B, R> Stream<R> zip(final A[] a, final B[] b, final BiFunction<A, B, R> combiner) {
        return zip(Stream.of(a).iterator(), Stream.of(b).iterator(), combiner);
    }

    /**
     * Zip together the "a", "b" and "c" arrays until one of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static <A, B, C, R> Stream<R> zip(final A[] a, final B[] b, final C[] c, final TriFunction<A, B, C, R> combiner) {
        return zip(Stream.of(a).iterator(), Stream.of(b).iterator(), Stream.of(c).iterator(), combiner);
    }

    /**
     * Zip together the "a" and "b" streams until one of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static <A, B, R> Stream<R> zip(final Stream<? extends A> a, final Stream<? extends B> b, final BiFunction<A, B, R> combiner) {
        return zip(a.iterator(), b.iterator(), combiner);
    }

    /**
     * Zip together the "a", "b" and "c" streams until one of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static <A, B, C, R> Stream<R> zip(final Stream<? extends A> a, final Stream<? extends B> b, final Stream<? extends C> c,
            final TriFunction<A, B, C, R> combiner) {
        return zip(a.iterator(), b.iterator(), c.iterator(), combiner);
    }

    /**
     * Zip together the "a" and "b" iterators until one of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static <A, B, R> Stream<R> zip(final Iterator<? extends A> a, final Iterator<? extends B> b, final BiFunction<A, B, R> combiner) {
        return new IteratorStream<R>(new ImmutableIterator<R>() {
            @Override
            public boolean hasNext() {
                return a.hasNext() && b.hasNext();
            }

            @Override
            public R next() {
                return combiner.apply(a.next(), b.next());
            }
        });
    }

    /**
     * Zip together the "a", "b" and "c" iterators until one of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @return
     */
    public static <A, B, C, R> Stream<R> zip(final Iterator<? extends A> a, final Iterator<? extends B> b, final Iterator<? extends C> c,
            final TriFunction<A, B, C, R> combiner) {
        return new IteratorStream<R>(new ImmutableIterator<R>() {
            @Override
            public boolean hasNext() {
                return a.hasNext() && b.hasNext() && c.hasNext();
            }

            @Override
            public R next() {
                return combiner.apply(a.next(), b.next(), c.next());
            }
        });
    }

    /**
     * Zip together the iterators until one of them runs out of values.
     * Each array of values is combined into a single value using the supplied combiner function.
     * 
     * @param c
     * @param combiner
     * @return
     */
    public static <R> Stream<R> zip(final Collection<? extends Iterator<?>> c, final NFunction<R> combiner) {
        if (c.size() == 0) {
            return Stream.empty();
        }

        final int len = c.size();

        return new IteratorStream<R>(new ImmutableIterator<R>() {

            @Override
            public boolean hasNext() {
                for (Iterator<?> e : c) {
                    if (e.hasNext() == false) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            public R next() {
                final Object[] args = new Object[len];
                int idx = 0;

                for (Iterator<?> e : c) {
                    args[idx++] = e.next();
                }

                return combiner.apply(args);
            }
        });
    }

    /**
     * Zip together the "a" and "b" iterators until all of them runs out of values.
     * Each pair of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @param combiner
     * @param valueForNoneA value to fill if "a" runs out of values first.
     * @param valueForNoneB value to fill if "b" runs out of values first.
     * @return
     */
    public static <A, B, R> Stream<R> zip(final Iterator<? extends A> a, final Iterator<? extends B> b, final BiFunction<A, B, R> combiner,
            final A valueForNoneA, final B valueForNoneB) {
        return new IteratorStream<R>(new ImmutableIterator<R>() {
            @Override
            public boolean hasNext() {
                return a.hasNext() || b.hasNext();
            }

            @Override
            public R next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return combiner.apply(a.hasNext() ? a.next() : valueForNoneA, b.hasNext() ? b.next() : valueForNoneB);
            }
        });
    }

    /**
     * Zip together the "a", "b" and "c" iterators until all of them runs out of values.
     * Each triple of values is combined into a single value using the supplied combiner function.
     * 
     * @param a
     * @param b
     * @param c
     * @param combiner
     * @param valueForNoneA value to fill if "a" runs out of values.
     * @param valueForNoneB value to fill if "b" runs out of values.
     * @param valueForNoneC value to fill if "c" runs out of values.
     * @return
     */
    public static <A, B, C, R> Stream<R> zip(final Iterator<? extends A> a, final Iterator<? extends B> b, final Iterator<? extends C> c,
            final TriFunction<A, B, C, R> combiner, final A valueForNoneA, final B valueForNoneB, final C valueForNoneC) {
        return new IteratorStream<R>(new ImmutableIterator<R>() {
            @Override
            public boolean hasNext() {
                return a.hasNext() || b.hasNext() || c.hasNext();
            }

            @Override
            public R next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return combiner.apply(a.hasNext() ? a.next() : valueForNoneA, b.hasNext() ? b.next() : valueForNoneB, c.hasNext() ? c.next() : valueForNoneC);
            }
        });
    }

    /**
     * Zip together the iterators until all of them runs out of values.
     * Each array of values is combined into a single value using the supplied combiner function.
     * 
     * @param c
     * @param combiner
     * @param valuesForNone value to fill for any iterator runs out of values.
     * @return
     */
    public static <R> Stream<R> zip(final Collection<? extends Iterator<?>> c, final NFunction<R> combiner, final Object[] valuesForNone) {
        if (c.size() != valuesForNone.length) {
            throw new IllegalArgumentException("The size of 'valuesForNone' must be same as the size of the collection of iterators");
        }

        if (c.size() == 0) {
            return Stream.empty();
        }

        final int len = c.size();

        return new IteratorStream<R>(new ImmutableIterator<R>() {
            @Override
            public boolean hasNext() {
                for (Iterator<?> e : c) {
                    if (e.hasNext()) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public R next() {
                final Object[] args = new Object[len];
                int idx = 0;
                boolean hasNext = false;

                for (Iterator<?> e : c) {
                    if (e.hasNext()) {
                        hasNext = true;
                        args[idx] = e.next();
                    } else {
                        args[idx] = valuesForNone[idx];
                    }
                    idx++;
                }

                if (hasNext == false) {
                    throw new NoSuchElementException();
                }

                return combiner.apply(args);
            }
        });
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param b
     * @param combiner
     * @return
     */
    public static <A, B, R> Stream<R> parallelZip(final Iterator<? extends A> a, final Iterator<? extends B> b, final BiFunction<A, B, R> combiner) {
        return parallelZip(a, b, combiner, 32);
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param b
     * @param combiner
     * @param queueSize for each iterator. Default value is 32
     * @return
     */
    public static <A, B, R> Stream<R> parallelZip(final Iterator<? extends A> a, final Iterator<? extends B> b, final BiFunction<A, B, R> combiner,
            final int queueSize) {
        final AsyncExecutor asyncExecutor = new AsyncExecutor(2, 300L, TimeUnit.SECONDS);
        final AtomicInteger threadCounterA = new AtomicInteger(1);
        final AtomicInteger threadCounterB = new AtomicInteger(1);
        final BlockingQueue<A> queueA = new ArrayBlockingQueue<>(queueSize);
        final BlockingQueue<B> queueB = new ArrayBlockingQueue<>(queueSize);
        final Holder<Throwable> errorHolder = new Holder<>();
        final MutableBoolean onGoing = MutableBoolean.of(true);

        readToQueue(a, b, asyncExecutor, threadCounterA, threadCounterB, queueA, queueB, errorHolder, onGoing);

        return of(new QueuedImmutableIterator<R>(queueSize) {
            A nextA = null;
            B nextB = null;

            @Override
            public boolean hasNext() {
                try {
                    while (nextA == null && onGoing.booleanValue() && (threadCounterA.get() > 0 || queueA.size() > 0)) { // (threadCounterA.get() > 0 || queueA.size() > 0) is wrong. has to check counter first
                        nextA = queueA.poll(100, TimeUnit.MILLISECONDS);
                    }

                    if (nextA == null) {
                        onGoing.setFalse();

                        return false;
                    }

                    while (nextB == null && onGoing.booleanValue() && (threadCounterB.get() > 0 || queueB.size() > 0)) { // (threadCounterB.get() > 0 || queueB.size() > 0) is wrong. has to check counter first
                        nextB = queueB.poll(100, TimeUnit.MILLISECONDS);
                    }

                    if (nextB == null) {
                        onGoing.setFalse();

                        return false;
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                }

                if (errorHolder.value() != null) {
                    throwError(errorHolder, onGoing);
                }

                return true;
            }

            @Override
            public R next() {
                if ((nextA == null || nextB == null) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                boolean isOK = false;

                try {
                    final R result = combiner.apply(nextA == NONE ? null : nextA, nextB == NONE ? null : nextB);
                    nextA = null;
                    nextB = null;
                    isOK = true;
                    return result;
                } finally {
                    // error happened
                    if (isOK == false) {
                        onGoing.setFalse();
                    }
                }
            }
        }).onClose(new Runnable() {
            @Override
            public void run() {
                onGoing.setFalse();
            }
        });
    }

    public static <A, B, C, R> Stream<R> parallelZip(final Iterator<? extends A> a, final Iterator<? extends B> b, final Iterator<? extends C> c,
            final TriFunction<A, B, C, R> combiner) {
        return parallelZip(a, b, c, combiner, 32);
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param b
     * @param c
     * @param combiner
     * @param queueSize for each iterator. Default value is 32
     * @return
     */
    public static <A, B, C, R> Stream<R> parallelZip(final Iterator<? extends A> a, final Iterator<? extends B> b, final Iterator<? extends C> c,
            final TriFunction<A, B, C, R> combiner, final int queueSize) {
        final AsyncExecutor asyncExecutor = new AsyncExecutor(3, 300L, TimeUnit.SECONDS);
        final AtomicInteger threadCounterA = new AtomicInteger(1);
        final AtomicInteger threadCounterB = new AtomicInteger(1);
        final AtomicInteger threadCounterC = new AtomicInteger(1);
        final BlockingQueue<A> queueA = new ArrayBlockingQueue<>(queueSize);
        final BlockingQueue<B> queueB = new ArrayBlockingQueue<>(queueSize);
        final BlockingQueue<C> queueC = new ArrayBlockingQueue<>(queueSize);
        final Holder<Throwable> errorHolder = new Holder<>();
        final MutableBoolean onGoing = MutableBoolean.of(true);

        readToQueue(a, b, c, asyncExecutor, threadCounterA, threadCounterB, threadCounterC, queueA, queueB, queueC, errorHolder, onGoing);

        return of(new QueuedImmutableIterator<R>(queueSize) {
            A nextA = null;
            B nextB = null;
            C nextC = null;

            @Override
            public boolean hasNext() {
                try {
                    while (nextA == null && onGoing.booleanValue() && (threadCounterA.get() > 0 || queueA.size() > 0)) { // (threadCounterA.get() > 0 || queueA.size() > 0) is wrong. has to check counter first
                        nextA = queueA.poll(100, TimeUnit.MILLISECONDS);
                    }

                    if (nextA == null) {
                        onGoing.setFalse();

                        return false;
                    }

                    while (nextB == null && onGoing.booleanValue() && (threadCounterB.get() > 0 || queueB.size() > 0)) { // (threadCounterB.get() > 0 || queueB.size() > 0) is wrong. has to check counter first
                        nextB = queueB.poll(100, TimeUnit.MILLISECONDS);
                    }

                    if (nextB == null) {
                        onGoing.setFalse();

                        return false;
                    }

                    while (nextC == null && onGoing.booleanValue() && (threadCounterC.get() > 0 || queueC.size() > 0)) { // (threadCounterC.get() > 0 || queueC.size() > 0) is wrong. has to check counter first
                        nextC = queueC.poll(100, TimeUnit.MILLISECONDS);
                    }

                    if (nextC == null) {
                        onGoing.setFalse();

                        return false;
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                }

                if (errorHolder.value() != null) {
                    throwError(errorHolder, onGoing);
                }

                return true;
            }

            @Override
            public R next() {
                if ((nextA == null || nextB == null || nextC == null) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                boolean isOK = false;

                try {
                    final R result = combiner.apply(nextA == NONE ? null : nextA, nextB == NONE ? null : nextB, nextC == NONE ? null : nextC);
                    nextA = null;
                    nextB = null;
                    nextC = null;
                    isOK = true;
                    return result;
                } finally {
                    // error happened
                    if (isOK == false) {
                        onGoing.setFalse();
                    }
                }
            }
        }).onClose(new Runnable() {
            @Override
            public void run() {
                onGoing.setFalse();
            }
        });
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param c
     * @param combiner
     * @return
     */
    public static <R> Stream<R> parallelZip(final Collection<? extends Iterator<?>> c, final NFunction<R> combiner) {
        return parallelZip(c, combiner, 32);
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param b
     * @param c
     * @param combiner
     * @param queueSize for each iterator. Default value is 32
     * @return
     */
    public static <R> Stream<R> parallelZip(final Collection<? extends Iterator<?>> c, final NFunction<R> combiner, final int queueSize) {
        if (c.size() == 0) {
            return Stream.empty();
        }

        final int len = c.size();
        final AsyncExecutor asyncExecutor = new AsyncExecutor(len, 300L, TimeUnit.SECONDS);
        final AtomicInteger[] counters = new AtomicInteger[len];
        final BlockingQueue<Object>[] queues = new ArrayBlockingQueue[len];
        final Holder<Throwable> errorHolder = new Holder<>();
        final MutableBoolean onGoing = MutableBoolean.of(true);

        readToQueue(c, queueSize, asyncExecutor, counters, queues, errorHolder, onGoing);

        return of(new QueuedImmutableIterator<R>(queueSize) {
            Object[] next = null;

            @Override
            public boolean hasNext() {
                if (next == null) {
                    next = new Object[len];
                }

                for (int i = 0; i < len; i++) {
                    try {
                        while (next[i] == null && onGoing.booleanValue() && (counters[i].get() > 0 || queues[i].size() > 0)) { // (counters[i].get() > 0 || queues[i].size() > 0) is wrong. has to check counter first
                            next[i] = queues[i].poll(100, TimeUnit.MILLISECONDS);
                        }

                        if (next[i] == null) {
                            onGoing.setFalse();

                            return false;
                        }
                    } catch (Throwable e) {
                        setError(errorHolder, e, onGoing);
                    }

                    if (errorHolder.value() != null) {
                        throwError(errorHolder, onGoing);
                    }
                }

                return true;
            }

            @Override
            public R next() {
                if (next == null) {
                    if (hasNext() == false) {
                        throw new NoSuchElementException();
                    }
                } else {
                    for (int i = 0; i < len; i++) {
                        if (next[i] == null) {
                            if (hasNext() == false) {
                                throw new NoSuchElementException();
                            } else {
                                break;
                            }
                        }
                    }
                }

                for (int i = 0; i < len; i++) {
                    if (next[i] == NONE) {
                        next[i] = null;
                    }
                }

                boolean isOK = false;

                try {
                    R result = combiner.apply(next);
                    next = null;
                    isOK = true;
                    return result;
                } finally {
                    // error happened
                    if (isOK == false) {
                        onGoing.setFalse();
                    }
                }
            }
        }).onClose(new Runnable() {
            @Override
            public void run() {
                onGoing.setFalse();
            }
        });
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param b
     * @param combiner
     * @param valueForNoneA
     * @param valueForNoneB
     * @return
     */
    public static <A, B, R> Stream<R> parallelZip(final Iterator<? extends A> a, final Iterator<? extends B> b, final BiFunction<A, B, R> combiner,
            final A valueForNoneA, final B valueForNoneB) {
        return parallelZip(a, b, combiner, 32, valueForNoneA, valueForNoneB);
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param b
     * @param combiner
     * @param queueSize for each iterator. Default value is 32
     * @param valueForNoneA
     * @param valueForNoneB
     * @return
     */
    public static <A, B, R> Stream<R> parallelZip(final Iterator<? extends A> a, final Iterator<? extends B> b, final BiFunction<A, B, R> combiner,
            final int queueSize, final A valueForNoneA, final B valueForNoneB) {
        final AsyncExecutor asyncExecutor = new AsyncExecutor(2, 300L, TimeUnit.SECONDS);
        final AtomicInteger threadCounterA = new AtomicInteger(1);
        final AtomicInteger threadCounterB = new AtomicInteger(1);
        final BlockingQueue<A> queueA = new ArrayBlockingQueue<>(queueSize);
        final BlockingQueue<B> queueB = new ArrayBlockingQueue<>(queueSize);
        final Holder<Throwable> errorHolder = new Holder<>();
        final MutableBoolean onGoing = MutableBoolean.of(true);

        readToQueue(a, b, asyncExecutor, threadCounterA, threadCounterB, queueA, queueB, errorHolder, onGoing);

        return of(new QueuedImmutableIterator<R>(queueSize) {
            A nextA = null;
            B nextB = null;

            @Override
            public boolean hasNext() {
                try {
                    while (nextA == null && onGoing.booleanValue() && (threadCounterA.get() > 0 || queueA.size() > 0)) { // (threadCounterA.get() > 0 || queueA.size() > 0) is wrong. has to check counter first
                        nextA = queueA.poll(100, TimeUnit.MILLISECONDS);
                    }

                    while (nextB == null && onGoing.booleanValue() && (threadCounterB.get() > 0 || queueB.size() > 0)) { // (threadCounterB.get() > 0 || queueB.size() > 0) is wrong. has to check counter first
                        nextB = queueB.poll(100, TimeUnit.MILLISECONDS);
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                }

                if (errorHolder.value() != null) {
                    throwError(errorHolder, onGoing);
                }

                if (nextA != null || nextB != null) {
                    return true;
                } else {
                    onGoing.setFalse();
                    return false;
                }
            }

            @Override
            public R next() {
                if ((nextA == null && nextB == null) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                nextA = nextA == NONE ? null : (nextA == null ? valueForNoneA : nextA);
                nextB = nextB == NONE ? null : (nextB == null ? valueForNoneB : nextB);
                boolean isOK = false;

                try {
                    final R result = combiner.apply(nextA, nextB);
                    nextA = null;
                    nextB = null;
                    isOK = true;
                    return result;
                } finally {
                    // error happened
                    if (isOK == false) {
                        onGoing.setFalse();
                    }
                }
            }
        }).onClose(new Runnable() {
            @Override
            public void run() {
                onGoing.setFalse();
            }
        });
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param b
     * @param c
     * @param combiner
     * @param valueForNoneA
     * @param valueForNoneB
     * @param valueForNoneC
     * @return
     */
    public static <A, B, C, R> Stream<R> parallelZip(final Iterator<? extends A> a, final Iterator<? extends B> b, final Iterator<? extends C> c,
            final TriFunction<A, B, C, R> combiner, final A valueForNoneA, final B valueForNoneB, final C valueForNoneC) {
        return parallelZip(a, b, c, combiner, 32, valueForNoneA, valueForNoneB, valueForNoneC);
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param a
     * @param b
     * @param c
     * @param combiner
     * @param queueSize for each iterator. Default value is 32
     * @param valueForNoneA
     * @param valueForNoneB
     * @param valueForNoneC
     * @return
     */
    public static <A, B, C, R> Stream<R> parallelZip(final Iterator<? extends A> a, final Iterator<? extends B> b, final Iterator<? extends C> c,
            final TriFunction<A, B, C, R> combiner, final int queueSize, final A valueForNoneA, final B valueForNoneB, final C valueForNoneC) {
        final AsyncExecutor asyncExecutor = new AsyncExecutor(3, 300L, TimeUnit.SECONDS);
        final AtomicInteger threadCounterA = new AtomicInteger(1);
        final AtomicInteger threadCounterB = new AtomicInteger(1);
        final AtomicInteger threadCounterC = new AtomicInteger(1);
        final BlockingQueue<A> queueA = new ArrayBlockingQueue<>(queueSize);
        final BlockingQueue<B> queueB = new ArrayBlockingQueue<>(queueSize);
        final BlockingQueue<C> queueC = new ArrayBlockingQueue<>(queueSize);
        final Holder<Throwable> errorHolder = new Holder<>();
        final MutableBoolean onGoing = MutableBoolean.of(true);

        readToQueue(a, b, c, asyncExecutor, threadCounterA, threadCounterB, threadCounterC, queueA, queueB, queueC, errorHolder, onGoing);

        return of(new QueuedImmutableIterator<R>(queueSize) {
            A nextA = null;
            B nextB = null;
            C nextC = null;

            @Override
            public boolean hasNext() {
                try {
                    while (nextA == null && onGoing.booleanValue() && (threadCounterA.get() > 0 || queueA.size() > 0)) { // (threadCounterA.get() > 0 || queueA.size() > 0) is wrong. has to check counter first
                        nextA = queueA.poll(100, TimeUnit.MILLISECONDS);
                    }

                    while (nextB == null && onGoing.booleanValue() && (threadCounterB.get() > 0 || queueB.size() > 0)) { // (threadCounterB.get() > 0 || queueB.size() > 0) is wrong. has to check counter first
                        nextB = queueB.poll(100, TimeUnit.MILLISECONDS);
                    }

                    while (nextC == null && onGoing.booleanValue() && (threadCounterC.get() > 0 || queueC.size() > 0)) { // (threadCounterC.get() > 0 || queueC.size() > 0) is wrong. has to check counter first
                        nextC = queueC.poll(100, TimeUnit.MILLISECONDS);
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                }

                if (errorHolder.value() != null) {
                    throwError(errorHolder, onGoing);
                }

                if (nextA != null || nextB != null || nextC != null) {
                    return true;
                } else {
                    onGoing.setFalse();

                    return false;
                }
            }

            @Override
            public R next() {
                if ((nextA == null && nextB == null && nextC == null) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                nextA = nextA == NONE ? null : (nextA == null ? valueForNoneA : nextA);
                nextB = nextB == NONE ? null : (nextB == null ? valueForNoneB : nextB);
                nextC = nextC == NONE ? null : (nextC == null ? valueForNoneC : nextC);
                boolean isOK = false;

                try {
                    final R result = combiner.apply(nextA, nextB, nextC);
                    nextA = null;
                    nextB = null;
                    nextC = null;
                    isOK = true;
                    return result;
                } finally {
                    // error happened
                    if (isOK == false) {
                        onGoing.setFalse();
                    }
                }
            }
        }).onClose(new Runnable() {
            @Override
            public void run() {
                onGoing.setFalse();
            }
        });
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param c
     * @param combiner
     * @param valuesForNone
     * @return
     */
    public static <R> Stream<R> parallelZip(final Collection<? extends Iterator<?>> c, final NFunction<R> combiner, final Object[] valuesForNone) {
        return parallelZip(c, combiner, 32, valuesForNone);
    }

    /**
     * Put the stream in try-catch to stop the back-end reading thread if error happens
     * <br />
     * <code>
     * try (Stream<Integer> stream = Stream.parallelZip(iterA, iterB, combiner)) {
     *            stream.forEach(N::println);
     *        }
     * </code>
     * 
     * @param c
     * @param combiner
     * @param queueSize for each iterator. Default value is 32
     * @param valuesForNone
     * @return
     */
    public static <R> Stream<R> parallelZip(final Collection<? extends Iterator<?>> c, final NFunction<R> combiner, final int queueSize,
            final Object[] valuesForNone) {
        if (c.size() != valuesForNone.length) {
            throw new IllegalArgumentException("The size of 'valuesForNone' must be same as the size of the collection of iterators");
        }

        if (c.size() == 0) {
            return Stream.empty();
        }

        final int len = c.size();
        final AsyncExecutor asyncExecutor = new AsyncExecutor(len, 300L, TimeUnit.SECONDS);
        final Holder<Throwable> errorHolder = new Holder<>();
        final MutableBoolean onGoing = MutableBoolean.of(true);
        final AtomicInteger[] counters = new AtomicInteger[len];
        final BlockingQueue<Object>[] queues = new ArrayBlockingQueue[len];

        readToQueue(c, queueSize, asyncExecutor, counters, queues, errorHolder, onGoing);

        return of(new QueuedImmutableIterator<R>(queueSize) {
            Object[] next = null;

            @Override
            public boolean hasNext() {
                if (next == null) {
                    next = new Object[len];
                }

                for (int i = 0; i < len; i++) {
                    try {
                        while (next[i] == null && onGoing.booleanValue() && (counters[i].get() > 0 || queues[i].size() > 0)) { // (counters[i].get() > 0 || queues[i].size() > 0) is wrong. has to check counter first
                            next[i] = queues[i].poll(100, TimeUnit.MILLISECONDS);
                        }
                    } catch (Throwable e) {
                        setError(errorHolder, e, onGoing);
                    }

                    if (errorHolder.value() != null) {
                        throwError(errorHolder, onGoing);
                    }
                }

                for (int i = 0; i < len; i++) {
                    if (next[i] != null) {
                        return true;
                    }
                }

                onGoing.setFalse();
                return false;
            }

            @Override
            public R next() {
                if (next == null) {
                    if (hasNext() == false) {
                        throw new NoSuchElementException();
                    }
                } else {
                    for (int i = 0; i < len; i++) {
                        if (next[i] == null) {
                            if (hasNext() == false) {
                                throw new NoSuchElementException();
                            } else {
                                break;
                            }
                        }
                    }
                }

                for (int i = 0; i < len; i++) {
                    next[i] = next[i] == NONE ? null : (next[i] == null ? valuesForNone[i] : next[i]);
                }

                boolean isOK = false;
                try {
                    R result = combiner.apply(next);
                    next = null;
                    isOK = true;
                    return result;
                } finally {
                    // error happened
                    if (isOK == false) {
                        onGoing.setFalse();
                    }
                }
            }
        }).onClose(new Runnable() {
            @Override
            public void run() {
                onGoing.setFalse();
            }
        });
    }

    private static <B, A> void readToQueue(final Iterator<? extends A> a, final Iterator<? extends B> b, final AsyncExecutor asyncExecutor,
            final AtomicInteger threadCounterA, final AtomicInteger threadCounterB, final BlockingQueue<A> queueA, final BlockingQueue<B> queueB,
            final Holder<Throwable> errorHolder, final MutableBoolean onGoing) {
        asyncExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    A nextA = null;

                    while (onGoing.booleanValue() && a.hasNext()) {
                        nextA = a.next();

                        if (nextA == null) {
                            nextA = (A) NONE;
                        }

                        while (onGoing.booleanValue() && queueA.offer(nextA, 100, TimeUnit.MILLISECONDS) == false) {
                            // continue
                        }
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                } finally {
                    threadCounterA.decrementAndGet();
                }
            }
        });

        asyncExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    B nextB = null;

                    while (onGoing.booleanValue() && b.hasNext()) {
                        nextB = b.next();

                        if (nextB == null) {
                            nextB = (B) NONE;
                        }

                        while (onGoing.booleanValue() && queueB.offer(nextB, 100, TimeUnit.MILLISECONDS) == false) {
                            // continue
                        }
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                } finally {
                    threadCounterB.decrementAndGet();
                }
            }
        });
    }

    private static <B, C, A> void readToQueue(final Iterator<? extends A> a, final Iterator<? extends B> b, final Iterator<? extends C> c,
            final AsyncExecutor asyncExecutor, final AtomicInteger threadCounterA, final AtomicInteger threadCounterB, final AtomicInteger threadCounterC,
            final BlockingQueue<A> queueA, final BlockingQueue<B> queueB, final BlockingQueue<C> queueC, final Holder<Throwable> errorHolder,
            final MutableBoolean onGoing) {
        asyncExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    A nextA = null;

                    while (onGoing.booleanValue() && a.hasNext()) {
                        nextA = a.next();

                        if (nextA == null) {
                            nextA = (A) NONE;
                        }

                        while (onGoing.booleanValue() && queueA.offer(nextA, 100, TimeUnit.MILLISECONDS) == false) {
                            // continue
                        }
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                } finally {
                    threadCounterA.decrementAndGet();
                }
            }
        });

        asyncExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    B nextB = null;

                    while (onGoing.booleanValue() && b.hasNext()) {
                        nextB = b.next();

                        if (nextB == null) {
                            nextB = (B) NONE;
                        }

                        while (onGoing.booleanValue() && queueB.offer(nextB, 100, TimeUnit.MILLISECONDS) == false) {
                            // continue
                        }
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                } finally {
                    threadCounterB.decrementAndGet();
                }
            }
        });

        asyncExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    C nextC = null;

                    while (onGoing.booleanValue() && c.hasNext()) {
                        nextC = c.next();

                        if (nextC == null) {
                            nextC = (C) NONE;
                        }

                        while (onGoing.booleanValue() && queueC.offer(nextC, 100, TimeUnit.MILLISECONDS) == false) {
                            // continue
                        }
                    }
                } catch (Throwable e) {
                    setError(errorHolder, e, onGoing);
                } finally {
                    threadCounterC.decrementAndGet();
                }
            }
        });
    }

    private static void readToQueue(final Collection<? extends Iterator<?>> c, final int queueSize, final AsyncExecutor asyncExecutor,
            final AtomicInteger[] counters, final BlockingQueue<Object>[] queues, final Holder<Throwable> errorHolder, final MutableBoolean onGoing) {
        int idx = 0;

        for (Iterator<?> e : c) {
            counters[idx] = new AtomicInteger(1);
            queues[idx] = new ArrayBlockingQueue<>(queueSize);

            final Iterator<?> iter = e;
            final AtomicInteger count = counters[idx];
            final BlockingQueue<Object> queue = queues[idx];

            asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Object next = null;

                        while (onGoing.booleanValue() && iter.hasNext()) {
                            next = iter.next();

                            if (next == null) {
                                next = NONE;
                            }

                            while (onGoing.booleanValue() && queue.offer(next, 100, TimeUnit.MILLISECONDS) == false) {
                                // continue
                            }
                        }
                    } catch (Throwable e) {
                        setError(errorHolder, e, onGoing);
                    } finally {
                        count.decrementAndGet();
                    }
                }
            });

            idx++;
        }
    }

    private static void setError(final Holder<Throwable> errorHolder, Throwable e, final MutableBoolean onGoing) {
        onGoing.setFalse();

        synchronized (errorHolder) {
            if (errorHolder.value() == null) {
                errorHolder.setValue(e);
            } else {
                errorHolder.value().addSuppressed(e);
            }
        }
    }

    private static void throwError(final Holder<Throwable> errorHolder, final MutableBoolean onGoing) {
        onGoing.setFalse();

        throw N.toRuntimeException(errorHolder.value());
    }

    static void checkIndex(int fromIndex, int toIndex, int length) {
        if (fromIndex < 0 || toIndex < fromIndex || toIndex > length) {
            throw new IllegalArgumentException("Invalid fromIndex(" + fromIndex + ") or toIndex(" + toIndex + ")");
        }
    }

    static int toInt(long max) {
        return max > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) max;
    }
}
