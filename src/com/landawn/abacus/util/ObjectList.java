/*
 * Copyright (c) 2015, Haiyang Li.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.landawn.abacus.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.IndexedConsumer;
import com.landawn.abacus.util.function.IntFunction;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.ToBooleanFunction;
import com.landawn.abacus.util.function.ToByteFunction;
import com.landawn.abacus.util.function.ToCharFunction;
import com.landawn.abacus.util.function.ToDoubleFunction;
import com.landawn.abacus.util.function.ToFloatFunction;
import com.landawn.abacus.util.function.ToIntFunction;
import com.landawn.abacus.util.function.ToLongFunction;
import com.landawn.abacus.util.function.ToShortFunction;
import com.landawn.abacus.util.stream.Stream;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public class ObjectList<T> extends AbastractArrayList<Consumer<? super T>, Predicate<? super T>, T, T[], ObjectList<T>> {
    private T[] elementData = null;
    private int size = 0;

    /**
     * The specified array is used as the element array for this list without copying action.
     * 
     * @param a
     */
    public ObjectList(T[] a) {
        super();

        elementData = a;
        size = a.length;
    }

    public ObjectList(T[] a, int size) {
        super();

        if (a.length < size) {
            throw new IllegalArgumentException("The specified size is bigger than the length of the specified array");
        }

        this.elementData = a;
        this.size = size;
    }

    public static <T> ObjectList<T> of(T... a) {
        return new ObjectList<T>(a);
    }

    public static <T> ObjectList<T> of(T[] a, int size) {
        return new ObjectList<T>(a, size);
    }

    static <T> ObjectList<T> from(Class<T> cls, Collection<? extends T> c) {
        return of(c.toArray((T[]) N.newArray(cls, c.size())));
    }

    static <T> ObjectList<T> from(Class<T> cls, Collection<? extends T> c, T defaultValueForNull) {
        final T[] a = c.toArray((T[]) N.newArray(cls, c.size()));

        for (int i = 0, len = a.length; i < len; i++) {
            if (a[i] == null) {
                a[i] = defaultValueForNull;
            }
        }

        return of(a);
    }

    /**
     * Returns the original element array without copying.
     * 
     * @return
     */
    @Override
    public T[] array() {
        return elementData;
    }

    //    /**
    //     * Return the first element of the array list, or {@code OptionalNullable.empty()} if there is no element.
    //     * @return
    //     */
    //    @Beta
    //    public OptionalNullable<T> findFirst() {
    //        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(elementData[0]);
    //    }

    public OptionalNullable<T> findFirst(Predicate<? super T> predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(elementData[i])) {
                return OptionalNullable.of(elementData[i]);
            }
        }

        return OptionalNullable.empty();
    }

    //    /**
    //     * Return the last non-null element of the array list, or {@code OptionalNullable.empty()} if there is no non-null element.
    //     * @return
    //     */
    //    @Beta
    //    public OptionalNullable<T> findLast() {
    //        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(elementData[size - 1]);
    //    }

    public OptionalNullable<T> findLast(Predicate<? super T> predicate) {
        for (int i = size - 1; i >= 0; i--) {
            if (predicate.test(elementData[i])) {
                return OptionalNullable.of(elementData[i]);
            }
        }

        return OptionalNullable.empty();
    }

    public T get(int index) {
        rangeCheck(index);

        return elementData[index];
    }

    private void rangeCheck(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    /**
     * 
     * @param index
     * @param e
     * @return the old value in the specified position.
     */
    public T set(int index, T e) {
        rangeCheck(index);

        T oldValue = elementData[index];

        elementData[index] = e;

        return oldValue;
    }

    public void add(T e) {
        ensureCapacityInternal(size + 1);

        elementData[size++] = e;
    }

    public void add(int index, T e) {
        rangeCheckForAdd(index);

        ensureCapacityInternal(size + 1);

        int numMoved = size - index;

        if (numMoved > 0) {
            N.copy(elementData, index, elementData, index + 1, numMoved);
        }

        elementData[index] = e;

        size++;
    }

    @Override
    public void addAll(ObjectList<T> c) {
        int numNew = c.size();

        ensureCapacityInternal(size + numNew);

        N.copy(c.array(), 0, elementData, size, numNew);

        size += numNew;
    }

    @Override
    public void addAll(int index, ObjectList<T> c) {
        rangeCheckForAdd(index);

        int numNew = c.size();

        ensureCapacityInternal(size + numNew); // Increments modCount

        int numMoved = size - index;

        if (numMoved > 0) {
            N.copy(elementData, index, elementData, index + numNew, numMoved);
        }

        N.copy(c.array(), 0, elementData, index, numNew);

        size += numNew;
    }

    private void rangeCheckForAdd(int index) {
        if (index > size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    /**
     * 
     * @param e
     * @return <tt>true</tt> if this list contained the specified element
     */
    public boolean remove(T e) {
        for (int i = 0; i < size; i++) {
            if (N.equals(elementData[i], e)) {

                fastRemove(i);

                return true;
            }
        }

        return false;
    }

    /**
     * 
     * @param e
     * @param removeAllOccurrences
     * @return <tt>true</tt> if this list contained the specified element
     */
    public boolean removeAllOccurrences(T e) {
        int w = 0;

        for (int i = 0; i < size; i++) {
            if (!N.equals(elementData[i], e)) {
                elementData[w++] = elementData[i];
            }
        }

        int numRemoved = size - w;

        if (numRemoved > 0) {
            N.fill(elementData, w, size, null);

            size = w;
        }

        return numRemoved > 0;
    }

    private void fastRemove(int index) {
        int numMoved = size - index - 1;

        if (numMoved > 0) {
            N.copy(elementData, index + 1, elementData, index, numMoved);
        }

        elementData[--size] = null; // clear to let GC do its work
    }

    @Override
    public boolean removeAll(ObjectList<T> c) {
        return batchRemove(c, false) > 0;
    }

    @Override
    public boolean retainAll(ObjectList<T> c) {
        return batchRemove(c, true) > 0;
    }

    private int batchRemove(ObjectList<T> c, boolean complement) {
        final T[] elementData = this.elementData;

        int w = 0;

        for (int i = 0; i < size; i++) {
            if (c.contains(elementData[i]) == complement) {
                elementData[w++] = elementData[i];
            }
        }

        int numRemoved = size - w;

        if (numRemoved > 0) {
            N.fill(elementData, w, size, null);

            size = w;
        }

        return numRemoved;
    }

    /**
     * 
     * @param index
     * @return the deleted element
     */
    public T delete(int index) {
        rangeCheck(index);

        T oldValue = elementData[index];

        fastRemove(index);

        return oldValue;
    }

    public boolean contains(T e) {
        return indexOf(e) >= 0;
    }

    @Override
    public boolean containsAll(ObjectList<T> c) {
        final T[] srcElementData = c.array();

        for (int i = 0, srcSize = c.size(); i < srcSize; i++) {

            if (!contains(srcElementData[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ObjectList<T> subList(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return new ObjectList<T>(N.copyOfRange(elementData, fromIndex, toIndex));
    }

    public int indexOf(T e) {
        return indexOf(0, e);
    }

    public int indexOf(final int fromIndex, T e) {
        checkIndex(fromIndex, size);

        for (int i = fromIndex; i < size; i++) {
            if (N.equals(elementData[i], e)) {
                return i;
            }
        }

        return -1;
    }

    public int lastIndexOf(T e) {
        return lastIndexOf(size, e);
    }

    /**
     * 
     * @param fromIndex the start index to traverse backwards from. Inclusive.
     * @param e
     * @return
     */
    public int lastIndexOf(final int fromIndex, T e) {
        checkIndex(0, fromIndex);

        for (int i = fromIndex == size ? size - 1 : fromIndex; i >= 0; i--) {
            if (N.equals(elementData[i], e)) {
                return i;
            }
        }

        return -1;
    }

    public OptionalNullable<T> min() {
        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of((T) N.min((Comparable[]) elementData, 0, size));
    }

    public OptionalNullable<T> min(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? (OptionalNullable<T>) OptionalNullable.empty()
                : OptionalNullable.of((T) N.min((Comparable[]) elementData, fromIndex, toIndex));
    }

    public OptionalNullable<T> min(Comparator<? super T> cmp) {
        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(N.min(elementData, 0, size, cmp));
    }

    public OptionalNullable<T> min(final int fromIndex, final int toIndex, final Comparator<? super T> cmp) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(N.min(elementData, fromIndex, toIndex, cmp));
    }

    public OptionalNullable<T> median() {
        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of((T) N.median((Comparable[]) elementData, 0, size));
    }

    public OptionalNullable<T> median(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? (OptionalNullable<T>) OptionalNullable.empty()
                : OptionalNullable.of((T) N.median((Comparable[]) elementData, fromIndex, toIndex));
    }

    public OptionalNullable<T> median(Comparator<? super T> cmp) {
        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(N.median(elementData, 0, size, cmp));
    }

    public OptionalNullable<T> median(final int fromIndex, final int toIndex, final Comparator<? super T> cmp) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(N.median(elementData, fromIndex, toIndex, cmp));
    }

    public OptionalNullable<T> max() {
        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of((T) N.max((Comparable[]) elementData, 0, size));
    }

    public OptionalNullable<T> max(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? (OptionalNullable<T>) OptionalNullable.empty()
                : OptionalNullable.of((T) N.max((Comparable[]) elementData, fromIndex, toIndex));
    }

    public OptionalNullable<T> max(Comparator<? super T> cmp) {
        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(N.max(elementData, 0, size, cmp));
    }

    public OptionalNullable<T> max(final int fromIndex, final int toIndex, final Comparator<? super T> cmp) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(N.max(elementData, fromIndex, toIndex, cmp));
    }

    public OptionalNullable<T> kthLargest(final int k) {
        return kthLargest(0, size(), k);
    }

    public OptionalNullable<T> kthLargest(final int k, Comparator<? super T> cmp) {
        return kthLargest(0, size(), k, cmp);
    }

    public OptionalNullable<T> kthLargest(final int fromIndex, final int toIndex, final int k) {
        checkIndex(fromIndex, toIndex);

        return toIndex - fromIndex < k ? (OptionalNullable<T>) OptionalNullable.empty()
                : OptionalNullable.of((T) N.kthLargest((Comparable[]) elementData, fromIndex, toIndex, k));
    }

    public OptionalNullable<T> kthLargest(final int fromIndex, final int toIndex, final int k, final Comparator<? super T> cmp) {
        checkIndex(fromIndex, toIndex);

        return toIndex - fromIndex < k ? (OptionalNullable<T>) OptionalNullable.empty()
                : OptionalNullable.of(N.kthLargest(elementData, fromIndex, toIndex, k, cmp));
    }

    public Long sumInt() {
        return sumInt(0, size());
    }

    public Long sumInt(int fromIndex, int toIndex) {
        checkIndex(fromIndex, toIndex);

        if (fromIndex == toIndex) {
            return 0L;
        }

        long result = 0L;

        for (int i = fromIndex; i < toIndex; i++) {
            if (elementData[i] != null) {
                result += ((Number) elementData[i]).longValue();
            }
        }

        return result;
    }

    public Long sumInt(ToIntFunction<? super T> mapper) {
        return sumInt(0, size(), mapper);
    }

    public Long sumInt(int fromIndex, int toIndex, ToIntFunction<? super T> mapper) {
        checkIndex(fromIndex, toIndex);

        if (fromIndex == toIndex) {
            return 0L;
        }

        final ToIntFunction<Object> tmp = (ToIntFunction<Object>) mapper;
        long result = 0L;

        for (int i = fromIndex; i < toIndex; i++) {
            result += tmp.applyAsInt(elementData[i]);
        }

        return result;
    }

    public Long sumLong() {
        return sumLong(0, size());
    }

    public Long sumLong(int fromIndex, int toIndex) {
        checkIndex(fromIndex, toIndex);

        if (fromIndex == toIndex) {
            return 0L;
        }

        long result = 0L;

        for (int i = fromIndex; i < toIndex; i++) {
            if (elementData[i] != null) {
                result += ((Number) elementData[i]).longValue();
            }
        }

        return result;
    }

    public Long sumLong(ToLongFunction<? super T> mapper) {
        return sumLong(0, size(), mapper);
    }

    public Long sumLong(int fromIndex, int toIndex, ToLongFunction<? super T> mapper) {
        checkIndex(fromIndex, toIndex);

        if (fromIndex == toIndex) {
            return 0L;
        }

        final ToLongFunction<Object> tmp = (ToLongFunction<Object>) mapper;
        long result = 0L;

        for (int i = fromIndex; i < toIndex; i++) {
            result += tmp.applyAsLong(elementData[i]);
        }

        return result;
    }

    public Double sumDouble() {
        return sumDouble(0, size());
    }

    public Double sumDouble(int fromIndex, int toIndex) {
        return sumDouble(fromIndex, toIndex, (ToDoubleFunction<? super T>) new ToDoubleFunction<Number>() {
            @Override
            public double applyAsDouble(Number value) {
                return value == null ? 0d : value.doubleValue();
            }
        });
    }

    public Double sumDouble(ToDoubleFunction<? super T> mapper) {
        return sumDouble(0, size(), mapper);
    }

    public Double sumDouble(int fromIndex, int toIndex, ToDoubleFunction<? super T> mapper) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? 0d : Stream.of(elementData, fromIndex, toIndex).mapToDouble(mapper).sum();
    }

    public OptionalDouble averageInt() {
        return averageInt(0, size());
    }

    public OptionalDouble averageInt(int fromIndex, int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalDouble.empty() : OptionalDouble.of(sumInt(fromIndex, toIndex).doubleValue() / (toIndex - fromIndex));
    }

    public OptionalDouble averageInt(ToIntFunction<? super T> mapper) {
        return averageInt(0, size(), mapper);
    }

    public OptionalDouble averageInt(int fromIndex, int toIndex, ToIntFunction<? super T> mapper) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalDouble.empty() : OptionalDouble.of(sumInt(fromIndex, toIndex, mapper).doubleValue() / (toIndex - fromIndex));
    }

    public OptionalDouble averageLong() {
        return averageLong(0, size());
    }

    public OptionalDouble averageLong(int fromIndex, int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalDouble.empty() : OptionalDouble.of(sumLong(fromIndex, toIndex).doubleValue() / (toIndex - fromIndex));
    }

    public OptionalDouble averageLong(ToLongFunction<? super T> mapper) {
        return averageLong(0, size(), mapper);
    }

    public OptionalDouble averageLong(int fromIndex, int toIndex, ToLongFunction<? super T> mapper) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalDouble.empty() : OptionalDouble.of(sumLong(fromIndex, toIndex, mapper).doubleValue() / (toIndex - fromIndex));
    }

    public OptionalDouble averageDouble() {
        return averageDouble(0, size());
    }

    public OptionalDouble averageDouble(int fromIndex, int toIndex) {
        return averageDouble(fromIndex, toIndex, (ToDoubleFunction<? super T>) new ToDoubleFunction<Number>() {
            @Override
            public double applyAsDouble(Number value) {
                return value == null ? 0d : value.doubleValue();
            }
        });
    }

    public OptionalDouble averageDouble(ToDoubleFunction<? super T> mapper) {
        return averageDouble(0, size(), mapper);
    }

    public OptionalDouble averageDouble(int fromIndex, int toIndex, ToDoubleFunction<? super T> mapper) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalDouble.empty() : Stream.of(elementData, fromIndex, toIndex).mapToDouble(mapper).average();
    }

    @Override
    public void forEach(final int fromIndex, final int toIndex, Consumer<? super T> action) {
        checkIndex(fromIndex, toIndex);

        if (size > 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                action.accept(elementData[i]);
            }
        }
    }

    public void forEach(IndexedConsumer<T> action) {
        forEach(0, size(), action);
    }

    public void forEach(final int fromIndex, final int toIndex, IndexedConsumer<? super T> action) {
        checkIndex(fromIndex, toIndex);

        if (size > 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                action.accept(i, elementData[i]);
            }
        }
    }

    public boolean forEach2(final Function<? super T, Boolean> action) {
        return forEach2(0, size(), action);
    }

    /**
     * 
     * @param fromIndex
     * @param toIndex
     * @param action break if the action returns false.
     * @return false if it breaks, otherwise true.
     */
    public boolean forEach2(final int fromIndex, final int toIndex, final Function<? super T, Boolean> action) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (action.apply(elementData[i]).booleanValue() == false) {
                return false;
            }
        }

        return true;
    }

    public boolean forEach2(final BiFunction<Integer, ? super T, Boolean> action) {
        return forEach2(0, size(), action);
    }

    /**
     * 
     * @param fromIndex
     * @param toIndex
     * @param action break if the action returns false. The first parameter is the index.
     * @return false if it breaks, otherwise true.
     */
    public boolean forEach2(final int fromIndex, final int toIndex, final BiFunction<Integer, ? super T, Boolean> action) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (action.apply(i, elementData[i]).booleanValue() == false) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean allMatch(final int fromIndex, final int toIndex, Predicate<? super T> filter) {
        checkIndex(fromIndex, toIndex);

        if (size > 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                if (filter.test(elementData[i]) == false) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean anyMatch(final int fromIndex, final int toIndex, Predicate<? super T> filter) {
        checkIndex(fromIndex, toIndex);

        if (size > 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                if (filter.test(elementData[i])) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean noneMatch(final int fromIndex, final int toIndex, Predicate<? super T> filter) {
        checkIndex(fromIndex, toIndex);

        if (size > 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                if (filter.test(elementData[i])) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public int count(final int fromIndex, final int toIndex, Predicate<? super T> filter) {
        checkIndex(fromIndex, toIndex);

        return N.count(elementData, fromIndex, toIndex, filter);
    }

    @Override
    public ObjectList<T> filter(final int fromIndex, final int toIndex, Predicate<? super T> filter) {
        checkIndex(fromIndex, toIndex);

        return of(N.filter(elementData, fromIndex, toIndex, filter));
    }

    @Override
    public ObjectList<T> filter(final int fromIndex, final int toIndex, Predicate<? super T> filter, final int max) {
        checkIndex(fromIndex, toIndex);

        return of(N.filter(elementData, fromIndex, toIndex, filter, max));
    }

    // TODO 1, replace with Stream mapToXXX(...) APIs.

    public <R> ObjectList<R> mapTo(final Class<R> targetClass, final Function<? super T, ? extends R> func) {
        return mapTo(targetClass, 0, size(), func);
    }

    public <R> ObjectList<R> mapTo(final Class<R> targetClass, final int fromIndex, final int toIndex, final Function<? super T, ? extends R> func) {
        checkIndex(fromIndex, toIndex);

        final R[] res = N.newArray(targetClass, size());

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.apply(elementData[i]);
        }

        return ObjectList.of(res);
    }

    public BooleanList mapToBoolean(final ToBooleanFunction<? super T> func) {
        return mapToBoolean(0, size(), func);
    }

    public BooleanList mapToBoolean(final int fromIndex, final int toIndex, final ToBooleanFunction<? super T> func) {
        checkIndex(fromIndex, toIndex);

        final boolean[] res = new boolean[size()];

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.applyAsBoolean(elementData[i]);
        }

        return BooleanList.of(res);
    }

    public CharList mapToChar(final ToCharFunction<? super T> func) {
        return mapToChar(0, size(), func);
    }

    public CharList mapToChar(final int fromIndex, final int toIndex, final ToCharFunction<? super T> func) {
        checkIndex(fromIndex, toIndex);

        final char[] res = new char[size()];

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.applyAsChar(elementData[i]);
        }

        return CharList.of(res);
    }

    public ByteList mapToByte(final ToByteFunction<? super T> func) {
        return mapToByte(0, size(), func);
    }

    public ByteList mapToByte(final int fromIndex, final int toIndex, final ToByteFunction<? super T> func) {
        checkIndex(fromIndex, toIndex);

        final byte[] res = new byte[size()];

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.applyAsByte(elementData[i]);
        }

        return ByteList.of(res);
    }

    public ShortList mapToShort(final ToShortFunction<? super T> func) {
        return mapToShort(0, size(), func);
    }

    public ShortList mapToShort(final int fromIndex, final int toIndex, final ToShortFunction<? super T> func) {
        checkIndex(fromIndex, toIndex);

        final short[] res = new short[size()];

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.applyAsShort(elementData[i]);
        }

        return ShortList.of(res);
    }

    public IntList mapToInt(final ToIntFunction<? super T> func) {
        return mapToInt(0, size(), func);
    }

    public IntList mapToInt(final int fromIndex, final int toIndex, final ToIntFunction<? super T> func) {
        checkIndex(fromIndex, toIndex);

        final int[] res = new int[size()];

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.applyAsInt(elementData[i]);
        }

        return IntList.of(res);
    }

    public LongList mapToLong(final ToLongFunction<? super T> func) {
        return mapToLong(0, size(), func);
    }

    public LongList mapToLong(final int fromIndex, final int toIndex, final ToLongFunction<? super T> func) {
        checkIndex(fromIndex, toIndex);

        final long[] res = new long[size()];

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.applyAsLong(elementData[i]);
        }

        return LongList.of(res);
    }

    public FloatList mapToFloat(final ToFloatFunction<? super T> func) {
        return mapToFloat(0, size(), func);
    }

    public FloatList mapToFloat(final int fromIndex, final int toIndex, final ToFloatFunction<? super T> func) {
        checkIndex(fromIndex, toIndex);

        final float[] res = new float[size()];

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.applyAsFloat(elementData[i]);
        }

        return FloatList.of(res);
    }

    public DoubleList mapToDouble(final ToDoubleFunction<? super T> func) {
        return mapToDouble(0, size(), func);
    }

    public DoubleList mapToDouble(final int fromIndex, final int toIndex, final ToDoubleFunction<? super T> func) {
        checkIndex(fromIndex, toIndex);

        final double[] res = new double[size()];

        for (int i = fromIndex; i < toIndex; i++) {
            res[i - fromIndex] = func.applyAsDouble(elementData[i]);
        }

        return DoubleList.of(res);
    }

    // TODO 1, replace with Stream APIs. 2, "final Class<? extends V> collClass" should be replaced with IntFunction<List<R>> supplier

    //    public <R> List<R> map(final Function<? super T, ? extends R> func) {
    //        return map(0, size(), func);
    //    }
    //
    //    public <R> List<R> map(final int fromIndex, final int toIndex, final Function<? super T, ? extends R> func) {
    //        return map(List.class, fromIndex, toIndex, func);
    //    }
    //
    //    public <R, V extends Collection<R>> V map(final Class<? extends V> collClass, final Function<? super T, ? extends R> func) {
    //        return map(collClass, 0, size(), func);
    //    }
    //
    //    public <R, V extends Collection<R>> V map(final Class<? extends V> collClass, final int fromIndex, final int toIndex,
    //            final Function<? super T, ? extends R> func) {
    //        checkIndex(fromIndex, toIndex);
    //
    //        final V res = N.newInstance(collClass);
    //
    //        for (int i = fromIndex; i < toIndex; i++) {
    //            res.add(func.apply(elementData[i]));
    //        }
    //
    //        return res;
    //    }
    //
    //    public <R> List<R> flatMap(final Function<? super T, ? extends Collection<? extends R>> func) {
    //        return flatMap(0, size(), func);
    //    }
    //
    //    public <R> List<R> flatMap(final int fromIndex, final int toIndex, final Function<? super T, ? extends Collection<? extends R>> func) {
    //        return flatMap(List.class, fromIndex, toIndex, func);
    //    }
    //
    //    public <R, V extends Collection<R>> V flatMap(final Class<? extends V> collClass, final Function<? super T, ? extends Collection<? extends R>> func) {
    //        return flatMap(collClass, 0, size(), func);
    //    }
    //
    //    public <R, V extends Collection<R>> V flatMap(final Class<? extends V> collClass, final int fromIndex, final int toIndex,
    //            final Function<? super T, ? extends Collection<? extends R>> func) {
    //        checkIndex(fromIndex, toIndex);
    //
    //        final V res = N.newInstance(collClass);
    //
    //        for (int i = fromIndex; i < toIndex; i++) {
    //            res.addAll(func.apply(elementData[i]));
    //        }
    //
    //        return res;
    //    }
    //
    //    public <R> List<R> flatMap2(final Function<? super T, R[]> func) {
    //        return flatMap2(0, size(), func);
    //    }
    //
    //    public <R> List<R> flatMap2(final int fromIndex, final int toIndex, final Function<? super T, R[]> func) {
    //        return flatMap2(List.class, fromIndex, toIndex, func);
    //    }
    //
    //    public <R, V extends Collection<R>> V flatMap2(final Class<? extends V> collClass, final Function<? super T, R[]> func) {
    //        return flatMap2(collClass, 0, size(), func);
    //    }
    //
    //    public <R, V extends Collection<R>> V flatMap2(final Class<? extends V> collClass, final int fromIndex, final int toIndex,
    //            final Function<? super T, R[]> func) {
    //        checkIndex(fromIndex, toIndex);
    //
    //        final V res = N.newInstance(collClass);
    //
    //        for (int i = fromIndex; i < toIndex; i++) {
    //            res.addAll(Arrays.asList(func.apply(elementData[i])));
    //        }
    //
    //        return res;
    //    }
    //
    //    public <K> Map<K, List<T>> groupBy(final Function<? super T, ? extends K> func) {
    //        return groupBy(0, size(), func);
    //    }
    //
    //    public <K> Map<K, List<T>> groupBy(final int fromIndex, final int toIndex, final Function<? super T, ? extends K> func) {
    //        return groupBy(List.class, fromIndex, toIndex, func);
    //    }
    //
    //    @SuppressWarnings("rawtypes")
    //    public <K, V extends Collection<T>> Map<K, V> groupBy(final Class<? extends Collection> collClass, final Function<? super T, ? extends K> func) {
    //        return groupBy(HashMap.class, collClass, 0, size(), func);
    //    }
    //
    //    @SuppressWarnings("rawtypes")
    //    public <K, V extends Collection<T>> Map<K, V> groupBy(final Class<? extends Collection> collClass, final int fromIndex, final int toIndex,
    //            final Function<? super T, ? extends K> func) {
    //        return groupBy(HashMap.class, collClass, fromIndex, toIndex, func);
    //    }
    //
    //    public <K, V extends Collection<T>, M extends Map<? super K, V>> M groupBy(final Class<M> outputClass, final Class<? extends V> collClass,
    //            final Function<? super T, ? extends K> func) {
    //
    //        return groupBy(outputClass, collClass, 0, size(), func);
    //    }
    //
    //    public <K, V extends Collection<T>, M extends Map<? super K, V>> M groupBy(final Class<M> outputClass, final Class<? extends V> collClass,
    //            final int fromIndex, final int toIndex, final Function<? super T, ? extends K> func) {
    //        checkIndex(fromIndex, toIndex);
    //
    //        final M outputResult = N.newInstance(outputClass);
    //
    //        K key = null;
    //        V values = null;
    //
    //        for (int i = fromIndex; i < toIndex; i++) {
    //            key = func.apply(elementData[i]);
    //            values = outputResult.get(key);
    //
    //            if (values == null) {
    //                values = N.newInstance(collClass);
    //                outputResult.put(key, values);
    //            }
    //
    //            values.add(elementData[i]);
    //        }
    //
    //        return outputResult;
    //    }
    //
    //    public OptionalNullable<T> reduce(final BinaryOperator<T> accumulator) {
    //        return size() == 0 ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(reduce(null, accumulator));
    //    }
    //
    //    public OptionalNullable<T> reduce(final int fromIndex, final int toIndex, final BinaryOperator<T> accumulator) {
    //        checkIndex(fromIndex, toIndex);
    //
    //        return fromIndex == toIndex ? (OptionalNullable<T>) OptionalNullable.empty() : OptionalNullable.of(reduce(fromIndex, toIndex, null, accumulator));
    //    }
    //
    //    public T reduce(final T identity, final BinaryOperator<T> accumulator) {
    //        return reduce(0, size(), identity, accumulator);
    //    }
    //
    //    public T reduce(final int fromIndex, final int toIndex, final T identity, final BinaryOperator<T> accumulator) {
    //        checkIndex(fromIndex, toIndex);
    //
    //        T result = identity;
    //
    //        for (int i = fromIndex; i < toIndex; i++) {
    //            result = accumulator.apply(result, elementData[i]);
    //        }
    //
    //        return result;
    //    }

    @Override
    public ObjectList<T> distinct(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        if (toIndex - fromIndex > 1) {
            return of(N.removeDuplicates(elementData, fromIndex, toIndex, false));
        } else {
            return of(N.copyOfRange(elementData, fromIndex, toIndex));
        }
    }

    public ObjectList<T> distinct(final Comparator<? super T> comparator) {
        return distinct(0, size(), comparator);
    }

    public ObjectList<T> distinct(final int fromIndex, final int toIndex, final Comparator<? super T> comparator) {
        checkIndex(fromIndex, toIndex);

        if (toIndex - fromIndex > 1) {
            return of(N.distinct(elementData, fromIndex, toIndex, comparator).toArray((T[]) N.newArray(elementData.getClass().getComponentType(), 0)));
        } else {
            return of(N.copyOfRange(elementData, fromIndex, toIndex));
        }
    }

    public ObjectList<T> distinct(final Function<? super T, ?> keyMapper) {
        return distinct(0, size(), keyMapper);
    }

    public ObjectList<T> distinct(final int fromIndex, final int toIndex, final Function<? super T, ?> keyMapper) {
        checkIndex(fromIndex, toIndex);

        if (toIndex - fromIndex > 1) {
            return of(N.distinct(elementData, fromIndex, toIndex, keyMapper).toArray((T[]) N.newArray(elementData.getClass().getComponentType(), 0)));
        } else {
            return of(N.copyOfRange(elementData, fromIndex, toIndex));
        }
    }

    @Override
    public List<ObjectList<T>> split(final int fromIndex, final int toIndex, final int size) {
        checkIndex(fromIndex, toIndex);

        final List<T[]> list = N.split(elementData, fromIndex, toIndex, size);
        final List<ObjectList<T>> result = new ArrayList<>(list.size());

        for (T[] a : list) {
            result.add(of(a));
        }

        return result;
    }

    public ObjectList<T> top(final int n) {
        return top(0, size(), n);
    }

    public ObjectList<T> top(final int fromIndex, final int toIndex, final int n) {
        checkIndex(fromIndex, toIndex);

        return of((T[]) N.top((Comparable[]) elementData, fromIndex, toIndex, n));
    }

    public ObjectList<T> top(final int n, final Comparator<? super T> cmp) {
        return top(0, size(), n, cmp);
    }

    public ObjectList<T> top(final int fromIndex, final int toIndex, final int n, final Comparator<? super T> cmp) {
        checkIndex(fromIndex, toIndex);

        return of(N.top(elementData, fromIndex, toIndex, n, cmp));
    }

    @Override
    public void sort() {
        if (size > 1) {
            N.sort(elementData, 0, size);
        }
    }

    public void sort(final Comparator<? super T> cmp) {
        if (size > 1) {
            N.sort(elementData, 0, size, cmp);
        }
    }

    public void parallelSort() {
        if (size > 1) {
            N.parallelSort(elementData, 0, size);
        }
    }

    public void parallelSort(final Comparator<? super T> cmp) {
        if (size > 1) {
            N.parallelSort(elementData, 0, size, cmp);
        }
    }

    @Override
    public void reverse() {
        if (size > 1) {
            N.reverse(elementData, 0, size);
        }
    }

    @Override
    public void rotate(int distance) {
        if (size > 1) {
            N.rotate(elementData, distance);
        }
    }

    @Override
    public ObjectList<T> copy(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return new ObjectList<T>(N.copyOfRange(elementData, fromIndex, toIndex));
    }

    @Override
    public ObjectList<T> trimToSize() {
        if (elementData.length != size) {
            elementData = N.copyOfRange(elementData, 0, size);
        }

        return this;
    }

    @Override
    public void clear() {
        if (size > 0) {
            N.fill(elementData, 0, size, null);
        }

        size = 0;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    @SuppressWarnings("rawtypes")
    public <R extends com.landawn.abacus.util.ArrayList> R unboxed() {
        return unboxed(0, size);
    }

    @SuppressWarnings("rawtypes")
    public <R extends com.landawn.abacus.util.ArrayList> R unboxed(int fromIndex, int toIndex) {
        checkIndex(fromIndex, toIndex);

        final Class<?> cls = elementData.getClass().getComponentType();

        if (cls.equals(Integer.class)) {
            return (R) IntList.of(Array.unbox((Integer[]) elementData, fromIndex, toIndex, 0));
        } else if (cls.equals(Long.class)) {
            return (R) LongList.of(Array.unbox((Long[]) elementData, fromIndex, toIndex, 0));
        } else if (cls.equals(Float.class)) {
            return (R) FloatList.of(Array.unbox((Float[]) elementData, fromIndex, toIndex, 0));
        } else if (cls.equals(Double.class)) {
            return (R) DoubleList.of(Array.unbox((Double[]) elementData, fromIndex, toIndex, 0));
        } else if (cls.equals(Boolean.class)) {
            return (R) BooleanList.of(Array.unbox((Boolean[]) elementData, fromIndex, toIndex, false));
        } else if (cls.equals(Character.class)) {
            return (R) CharList.of(Array.unbox((Character[]) elementData, fromIndex, toIndex, (char) 0));
        } else if (cls.equals(Byte.class)) {
            return (R) ByteList.of(Array.unbox((Byte[]) elementData, fromIndex, toIndex, (byte) 0));
        } else if (cls.equals(Short.class)) {
            return (R) ShortList.of(Array.unbox((Short[]) elementData, fromIndex, toIndex, (short) 0));
        } else {
            throw new IllegalArgumentException(N.getClassName(cls) + " is not a wrapper of primitive type");
        }
    }

    @Override
    public List<T> toList(final int fromIndex, final int toIndex, final IntFunction<List<T>> supplier) {
        checkIndex(fromIndex, toIndex);

        final List<T> list = supplier.apply(toIndex - fromIndex);

        for (int i = fromIndex; i < toIndex; i++) {
            list.add(elementData[i]);
        }

        return list;
    }

    @Override
    public Set<T> toSet(final int fromIndex, final int toIndex, final IntFunction<Set<T>> supplier) {
        checkIndex(fromIndex, toIndex);

        final Set<T> set = supplier.apply(N.min(16, toIndex - fromIndex));

        for (int i = fromIndex; i < toIndex; i++) {
            set.add(elementData[i]);
        }

        return set;
    }

    @Override
    public Multiset<T> toMultiset(final int fromIndex, final int toIndex, final IntFunction<Multiset<T>> supplier) {
        checkIndex(fromIndex, toIndex);

        final Multiset<T> multiset = supplier.apply(N.min(16, toIndex - fromIndex));

        for (int i = fromIndex; i < toIndex; i++) {
            multiset.add(elementData[i]);
        }

        return multiset;
    }

    public <K, U> Map<K, U> toMap(final Function<? super T, ? extends K> keyMapper, final Function<? super T, ? extends U> valueMapper) {
        final IntFunction<Map<K, U>> supplier = createMapSupplier();

        return toMap(keyMapper, valueMapper, supplier);
    }

    public <K, U, M extends Map<K, U>> M toMap(final Function<? super T, ? extends K> keyMapper, final Function<? super T, ? extends U> valueMapper,
            final IntFunction<M> supplier) {
        return toMap(0, size(), keyMapper, valueMapper, supplier);
    }

    public <K, U> Map<K, U> toMap(final int fromIndex, final int toIndex, final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper) {
        final IntFunction<Map<K, U>> supplier = createMapSupplier();

        return toMap(fromIndex, toIndex, keyMapper, valueMapper, supplier);
    }

    public <K, U, M extends Map<K, U>> M toMap(final int fromIndex, final int toIndex, final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper, final IntFunction<M> supplier) {
        checkIndex(fromIndex, toIndex);

        final Map<K, U> map = supplier.apply(N.min(16, toIndex - fromIndex));

        for (int i = fromIndex; i < toIndex; i++) {
            map.put(keyMapper.apply(elementData[i]), valueMapper.apply(elementData[i]));
        }

        return (M) map;
    }

    public <K, U> Multimap<K, U, List<U>> toMultimap(final Function<? super T, ? extends K> keyMapper, final Function<? super T, ? extends U> valueMapper) {
        final IntFunction<Multimap<K, U, List<U>>> supplier = createMultimapSupplier();

        return toMultimap(keyMapper, valueMapper, supplier);
    }

    public <K, U, V extends Collection<U>> Multimap<K, U, V> toMultimap(final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper, final IntFunction<Multimap<K, U, V>> supplier) {
        return toMultimap(0, size(), keyMapper, valueMapper, supplier);
    }

    public <K, U> Multimap<K, U, List<U>> toMultimap(final int fromIndex, final int toIndex, final Function<? super T, ? extends K> keyMapper,
            final Function<? super T, ? extends U> valueMapper) {
        final IntFunction<Multimap<K, U, List<U>>> supplier = createMultimapSupplier();

        return toMultimap(fromIndex, toIndex, keyMapper, valueMapper, supplier);
    }

    public <K, U, V extends Collection<U>> Multimap<K, U, V> toMultimap(final int fromIndex, final int toIndex,
            final Function<? super T, ? extends K> keyMapper, final Function<? super T, ? extends U> valueMapper,
            final IntFunction<Multimap<K, U, V>> supplier) {
        checkIndex(fromIndex, toIndex);

        final Multimap<K, U, V> multimap = supplier.apply(N.min(16, toIndex - fromIndex));

        for (int i = fromIndex; i < toIndex; i++) {
            multimap.put(keyMapper.apply(elementData[i]), valueMapper.apply(elementData[i]));
        }

        return multimap;
    }

    public Stream<T> stream() {
        return stream(0, size());
    }

    public Stream<T> stream(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return Stream.of(elementData, fromIndex, toIndex);
    }

    @Override
    public int hashCode() {
        return N.hashCode(elementData, 0, size());
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof ObjectList && N.equals(elementData, 0, size(), ((ObjectList<T>) obj).elementData));

    }

    @Override
    public String toString() {
        return size == 0 ? "[]" : N.toString(elementData, 0, size);
    }

    private void ensureCapacityInternal(int minCapacity) {
        if (N.isNullOrEmpty(elementData)) {
            minCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);
        }

        ensureExplicitCapacity(minCapacity);
    }

    private void ensureExplicitCapacity(int minCapacity) {
        if (minCapacity - elementData.length > 0) {
            grow(minCapacity);
        }
    }

    private void grow(int minCapacity) {
        int oldCapacity = elementData.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);

        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }

        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            newCapacity = hugeCapacity(minCapacity);
        }

        elementData = Arrays.copyOf(elementData, newCapacity);
    }
}
