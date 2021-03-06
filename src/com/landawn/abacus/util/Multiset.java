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

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.landawn.abacus.annotation.Beta;
import com.landawn.abacus.annotation.Internal;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.stream.Stream;

/**
 * A collection that supports order-independent equality, like {@link Set}, but
 * may have duplicate elements.
 *
 * <p>Elements of a Multiset that are equal to one another are referred to as
 * <i>occurrences</i> of the same single element. The total number of
 * occurrences of an element in a Multiset is called the <i>count</i> of that
 * element (the terms "frequency" and "multiplicity" are equivalent, but not
 * used in this API). Since the count of an element is represented as an {@code
 * int}, a Multiset may never contain more than {@link MutableInt#MAX_VALUE}
 * occurrences of any one element.
 *
 * @param <E>
 *
 * @since 0.8
 *
 * @author Haiyang Li
 */
public final class Multiset<E> implements Iterable<E> {
    private static final Comparator<Map.Entry<?, MutableInt>> cmpByCount = new Comparator<Map.Entry<?, MutableInt>>() {
        @Override
        public int compare(Entry<?, MutableInt> a, Entry<?, MutableInt> b) {
            return N.compare(a.getValue().intValue(), b.getValue().intValue());
        }
    };

    private final Map<E, MutableInt> valueMap;

    public Multiset() {
        this(HashMap.class);
    }

    public Multiset(int initialCapacity) {
        this(new HashMap<E, MutableInt>(initialCapacity));
    }

    @SuppressWarnings("rawtypes")
    public Multiset(final Class<? extends Map> valueMapType) {
        this(N.newInstance(valueMapType));
    }

    /**
     *
     * @param valueMap The valueMap and this Multiset share the same data; any changes to one will appear in the other.
     */
    @Internal
    Multiset(final Map<E, MutableInt> valueMap) {
        this.valueMap = valueMap;
    }

    public Multiset(final Collection<? extends E> c) {
        this();

        addAll(c);
    }

    public static <T> Multiset<T> of(final T... a) {
        return N.asMultiset(a);
    }

    //    @SuppressWarnings("rawtypes")
    //    static <T> Multiset<T> of(final Class<? extends Map> valueMapType, final T... a) {
    //        final Multiset<T> multiset = new Multiset<T>(valueMapType);
    //
    //        for (T e : a) {
    //            multiset.add(e);
    //        }
    //
    //        return multiset;
    //    }

    public static <T> Multiset<T> of(final Collection<? extends T> coll) {
        return new Multiset<T>(coll);
    }

    //    @SuppressWarnings("rawtypes")
    //    static <T> Multiset<T> of(final Class<? extends Map> valueMapType, final Collection<T> coll) {
    //        final Multiset<T> multiset = new Multiset<T>(valueMapType);
    //
    //        multiset.addAll(coll);
    //
    //        return multiset;
    //    }

    public static <T> Multiset<T> from(final Map<? extends T, Integer> m) {
        final Multiset<T> multiset = new Multiset<T>(N.initHashCapacity(m.size()));

        multiset.setAll(m);

        return multiset;
    }

    //    @SuppressWarnings("rawtypes")
    //    public static <T> Multiset<T> from(final Class<? extends Map> valueMapType, final Map<? extends T, Integer> m) {
    //        final Multiset<T> multiset = new Multiset<T>(valueMapType);
    //
    //        multiset.setAll(m);
    //
    //        return multiset;
    //    }

    /**
     *
     * @param e
     * @return the occurrences of the specified object. zero is returned if it's not in this set.
     */
    public int get(final Object e) {
        MutableInt count = valueMap.get(e);

        if (count == null) {
            return 0;
        } else {
            return count.intValue();
        }
    }

    /**
     * The element will be removed if the specified count is 0.
     *
     * @param e
     * @param occurrences
     * @return the previous count associated with element, or 0 if the element not exists. 
     * @throws IllegalArgumentException if the occurrences of element is less than 0
     */
    public int set(final E e, final int occurrences) {
        if (occurrences < 0) {
            throw new IllegalArgumentException("The specified 'occurrences' can not be less than 0");
        }

        if (occurrences == 0) {
            MutableInt value = valueMap.remove(e);

            return value == null ? 0 : value.intValue();
        } else {
            MutableInt value = valueMap.get(e);

            if (value == null) {
                valueMap.put(e, MutableInt.of(occurrences));

                return 0;
            } else {
                int result = value.intValue();
                value.setValue(occurrences);
                return result;
            }
        }
    }

    /**
     * 
     * @param m
     * @throws IllegalArgumentException if the occurrences of element is less than 0.
     */
    public void setAll(final Map<? extends E, Integer> m) throws IllegalArgumentException {
        for (Map.Entry<? extends E, Integer> entry : m.entrySet()) {
            if (entry.getValue().intValue() < 0) {
                throw new IllegalArgumentException(
                        "The specified 'occurrences' can not be less than 0. " + N.toString(entry.getKey()) + " : " + N.toString(entry.getValue()));
            }
        }

        for (Map.Entry<? extends E, Integer> entry : m.entrySet()) {
            set(entry.getKey(), entry.getValue().intValue());
        }
    }

    /**
     * 
     * @param m
     * @throws IllegalArgumentException if the occurrences of element is less than 0.
     */
    public void setAll(final Multiset<? extends E> multiset) throws IllegalArgumentException {
        for (Map.Entry<? extends E, MutableInt> entry : multiset.entrySet()) {
            set(entry.getKey(), entry.getValue().intValue());
        }
    }

    public Optional<Map.Entry<E, Integer>> minOccurrences() {
        if (size() == 0) {
            return Optional.empty();
        }

        final Iterator<Map.Entry<E, MutableInt>> it = valueMap.entrySet().iterator();
        Map.Entry<E, MutableInt> entry = it.next();
        E minCountElement = entry.getKey();
        int minCount = entry.getValue().intValue();

        while (it.hasNext()) {
            entry = it.next();

            if (entry.getValue().intValue() < minCount) {
                minCountElement = entry.getKey();
                minCount = entry.getValue().intValue();
            }
        }

        return Optional.of((Map.Entry<E, Integer>) MapEntry.of(minCountElement, minCount));
    }

    public Optional<Map.Entry<E, Integer>> maxOccurrences() {
        if (size() == 0) {
            return Optional.empty();
        }

        final Iterator<Map.Entry<E, MutableInt>> it = valueMap.entrySet().iterator();
        Map.Entry<E, MutableInt> entry = it.next();
        E maxCountElement = entry.getKey();
        int maxCount = entry.getValue().intValue();

        while (it.hasNext()) {
            entry = it.next();

            if (entry.getValue().intValue() > maxCount) {
                maxCountElement = entry.getKey();
                maxCount = entry.getValue().intValue();
            }
        }

        return Optional.of((Map.Entry<E, Integer>) MapEntry.of(maxCountElement, maxCount));
    }

    public Long sumOfOccurrences() {
        long sum = 0;

        for (MutableInt count : valueMap.values()) {
            sum += count.intValue();
        }

        return sum;
    }

    public OptionalDouble averageOfOccurrences() {
        if (size() == 0) {
            return OptionalDouble.empty();
        }

        final double sum = sumOfOccurrences();

        return OptionalDouble.of(sum / size());
    }

    @Beta
    int countOf(final Object e) {
        return get(e);
    }

    public Map<E, Integer> toMap() {
        final Map<E, Integer> result = new LinkedHashMap<>(N.initHashCapacity(size()));

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue().intValue());
        }

        return result;
    }

    @SuppressWarnings("rawtypes")
    public Map<E, Integer> toMapSortedByOccurrences() {
        return toMapSortedBy((Comparator) cmpByCount);
    }

    public Map<E, Integer> toMapSortedBy(final Comparator<Map.Entry<E, MutableInt>> cmp) {
        if (N.isNullOrEmpty(valueMap)) {
            return new LinkedHashMap<>();
        }

        final Map.Entry<E, MutableInt>[] entries = entrySet().toArray(new Map.Entry[size()]);
        Arrays.sort(entries, cmp);

        final Map<E, Integer> sortedValues = new LinkedHashMap<>(N.initHashCapacity(size()));

        for (Map.Entry<E, MutableInt> entry : entries) {
            sortedValues.put(entry.getKey(), entry.getValue().intValue());
        }

        return sortedValues;
    }

    /**
     *
     * @param e
     * @return the count of the element after the operation.
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public int add(final E e) throws IllegalArgumentException {
        return add(e, 1);
    }

    /**
     *
     * @param e
     * @param occurrences
     * @return the count of the element after the operation.
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public int add(final E e, final int occurrences) throws IllegalArgumentException {
        /*
        if (occurrences < 1) {
            throw new IllegalArgumentException("The specified 'occurrences' must be greater than 0");
        }
        */

        MutableInt count = valueMap.get(e);

        if (count != null && occurrences > (Integer.MAX_VALUE - count.intValue())) {
            throw new IllegalArgumentException("The total count is out of the bound of integer");
        }

        if (count == null) {
            count = MutableInt.of(occurrences);

            if (count.intValue() > 0) {
                valueMap.put(e, count);
            }
        } else {
            count.add(occurrences);

            if (count.intValue() <= 0) {
                valueMap.remove(e);
            }
        }

        return count.intValue();
    }

    public int addAndGet(final E e) {
        add(e);

        return get(e);
    }

    public int getAndAdd(final E e) {
        final int result = get(e);

        add(e);

        return result;
    }

    public int addAndGet(final E e, final int occurrences) {
        add(e, occurrences);

        return get(e);
    }

    public int getAndAdd(final E e, final int occurrences) {
        final int result = get(e);

        add(e, occurrences);

        return result;
    }

    /**
     * 
     * @param m
     * @throws IllegalArgumentException if the occurrences of element is less than 0.
     */
    public void addAll(final Multiset<? extends E> multiset) throws IllegalArgumentException {
        for (Map.Entry<? extends E, MutableInt> entry : multiset.entrySet()) {
            add(entry.getKey(), entry.getValue().intValue());
        }
    }

    /**
     * 
     * @param c
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public void addAll(final Collection<? extends E> c) throws IllegalArgumentException {
        addAll(c, 1);
    }

    /**
     * 
     * @param c
     * @param occurrences
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public void addAll(final Collection<? extends E> c, final int occurrences) throws IllegalArgumentException {
        for (E e : c) {
            add(e, occurrences);
        }
    }

    /**
     * 
     * @param m
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public void addAll(final Map<? extends E, Integer> m) throws IllegalArgumentException {
        for (Map.Entry<? extends E, Integer> entry : m.entrySet()) {
            add(entry.getKey(), entry.getValue().intValue());
        }
    }

    public boolean contains(final Object o) {
        return valueMap.containsKey(o);
    }

    public boolean containsAll(final Collection<? extends E> c) {
        return valueMap.keySet().containsAll(c);
    }

    /**
     * The element will be removed from this set if the occurrences equals to or less than 0 after the operation.
     *
     * @param e
     * @param occurrences
     * @return the count of the element after the operation. It could be a negative number if the present occurrences is less than the specified <code>occurrences</code> to remove.
     */
    public int remove(final E e) throws IllegalArgumentException {
        return remove(e, 1);
    }

    /**
     * The element will be removed from this set if the occurrences equals to or less than 0 after the operation.
     *
     * @param e
     * @param occurrences
     * @return the count of the element after the operation. It could be a negative number if the present occurrences is less than the specified <code>occurrences</code> to remove.
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public int remove(final E e, final int occurrences) throws IllegalArgumentException {
        /*
        if (occurrences < 1) {
            throw new IllegalArgumentException("The specified 'occurrences' must be greater than 0");
        }
        */

        MutableInt count = valueMap.get(e);

        if (count != null && occurrences < (count.intValue() - Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("The total count is out of the bound of integer");
        }

        if (count == null) {
            count = MutableInt.of(-occurrences);

            if (count.intValue() > 0) {
                valueMap.put(e, count);
            }
        } else {
            count.subtract(occurrences);

            if (count.intValue() <= 0) {
                valueMap.remove(e);
            }
        }

        return count.intValue();
    }

    public int removeAndGet(final E e) {
        remove(e);

        return get(e);
    }

    public int getAndRemove(final E e) {
        final int result = get(e);

        remove(e);

        return result;
    }

    public int removeAndGet(final E e, final int occurrences) {
        remove(e, occurrences);

        return get(e);
    }

    public int getAndRemove(final E e, final int occurrences) {
        final int result = get(e);

        remove(e, occurrences);

        return result;
    }

    public void removeAllOccurrences(final E e) {
        valueMap.remove(e);
    }

    /**
     * The elements will be removed from this set if the occurrences equals to or less than 0 after the operation.
     *
     * @param c
     * @return <tt>true</tt> if this set changed as a result of the call
     */
    public boolean removeAll(final Collection<? extends E> c) {
        return removeAll(c, 1);
    }

    /**
     * The elements will be removed from this set if the occurrences equals to or less than 0 after the operation.
     *
     * @param c
     * @param occurrences
     *            the occurrences to remove if the element is in the specified collection <code>c</code>.
     * @return <tt>true</tt> if this set changed as a result of the call
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public boolean removeAll(final Collection<? extends E> c, final int occurrences) throws IllegalArgumentException {
        boolean result = false;

        for (E e : c) {
            if (result == false) {
                result = valueMap.containsKey(e);
            }

            remove(e, occurrences);
        }

        return result;
    }

    /**
     * 
     * @param m
     * @return
     * @throws IllegalArgumentException if the occurrences of element after this operation is bigger than Integer.MAX_VALUE.
     */
    public boolean removeAll(final Map<? extends E, Integer> m) throws IllegalArgumentException {
        boolean result = false;

        for (Map.Entry<? extends E, Integer> entry : m.entrySet()) {
            if (result == false) {
                result = valueMap.containsKey(entry.getKey());
            }

            remove(entry.getKey(), entry.getValue().intValue());
        }

        return result;
    }

    /**
     * 
     * @param m
     * @throws IllegalArgumentException if the occurrences of element is less than 0.
     */
    public boolean removeAll(final Multiset<? extends E> multiset) throws IllegalArgumentException {
        boolean result = false;

        for (Map.Entry<? extends E, MutableInt> entry : multiset.entrySet()) {
            if (result == false) {
                result = valueMap.containsKey(entry.getKey());
            }

            remove(entry.getKey(), entry.getValue().intValue());
        }

        return result;
    }

    /**
     * Retains only the elements in this collection that are contained in the
     * specified collection (optional operation).  In other words, removes from
     * this collection all of its elements that are not contained in the
     * specified collection.
     *
     * @param c
     * @return <tt>true</tt> if this set changed as a result of the call
     */
    public boolean retainAll(final Collection<? extends E> c) {
        Set<E> others = null;

        for (E e : valueMap.keySet()) {
            if (!c.contains(e)) {
                if (others == null) {
                    others = new HashSet<>(valueMap.size());
                }

                others.add(e);
            }
        }

        return N.isNullOrEmpty(others) ? false : removeAll(others, Integer.MAX_VALUE);
    }

    public int size() {
        return valueMap.size();
    }

    public boolean isEmpty() {
        return valueMap.isEmpty();
    }

    public void clear() {
        valueMap.clear();
    }

    @Override
    public Iterator<E> iterator() {
        return valueMap.keySet().iterator();
    }

    public Set<E> keySet() {
        return valueMap.keySet();
    }

    public Set<Map.Entry<E, MutableInt>> entrySet() {
        return valueMap.entrySet();
    }

    public Object[] toArray() {
        return valueMap.keySet().toArray();
    }

    public <T> T[] toArray(final T[] a) {
        return valueMap.keySet().toArray(a);
    }

    /**
     * 
     * @return a list with all elements, each of them is repeated with the occurrences in this <code>Multiset</code>   
     */
    public List<E> flat() {
        final Object[] a = new Object[sumOfOccurrences().intValue()];

        int fromIndex = 0;
        int toIndex = 0;

        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            toIndex = fromIndex + entry.getValue().intValue();

            Arrays.fill(a, fromIndex, toIndex, entry.getKey());
            fromIndex = toIndex;
        }

        return N.asList((E[]) a);
    }

    public void forEach(BiConsumer<? super E, MutableInt> action) {
        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            action.accept(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 
     * @param action break if the action returns false.
     * @return false if it breaks, otherwise true.
     */
    public boolean forEach2(BiFunction<? super E, MutableInt, Boolean> action) {
        for (Map.Entry<E, MutableInt> entry : valueMap.entrySet()) {
            if (action.apply(entry.getKey(), entry.getValue()).booleanValue() == false) {
                return false;
            }
        }

        return true;
    }

    public Stream<Map.Entry<E, MutableInt>> stream() {
        return Stream.of(valueMap.entrySet());
    }

    @Override
    public int hashCode() {
        return valueMap.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return obj == this || (obj instanceof Multiset && valueMap.equals(((Multiset<E>) obj).valueMap));
    }

    @Override
    public String toString() {
        return valueMap.toString();
    }
}
