package com.landawn.abacus.util.function;

/**
 * Refer to JDK API documentation at: <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html</a>
 */
public interface ObjLongConsumer<T> extends java.util.function.ObjLongConsumer<T> {

    @Override
    void accept(T t, long value);
}