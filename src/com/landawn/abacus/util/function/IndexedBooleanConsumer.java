package com.landawn.abacus.util.function;

import com.landawn.abacus.util.N;

/**
 * Refer to JDK API documentation at: <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html</a>
 */
public interface IndexedBooleanConsumer {

    void accept(int idx, boolean t);

    default IndexedBooleanConsumer andThen(IndexedBooleanConsumer after) {
        N.requireNonNull(after);
        return (int idx, boolean t) -> {
            accept(idx, t);
            after.accept(idx, t);
        };
    }
}
