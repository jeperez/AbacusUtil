package com.landawn.abacus.util.function;

/**
 * Refer to JDK API documentation at: <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html</a>
 */
public interface ShortUnaryOperator {

    short applyAsShort(short operand);

    static ShortUnaryOperator identity() {
        return t -> t;
    }
}
