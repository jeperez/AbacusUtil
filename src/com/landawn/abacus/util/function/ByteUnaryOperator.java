package com.landawn.abacus.util.function;

/**
 * Refer to JDK API documentation at: <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html</a>
 */
public interface ByteUnaryOperator {

    byte applyAsByte(byte operand);

    static ByteUnaryOperator identity() {
        return t -> t;
    }
}
