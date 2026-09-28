package io.benchlab;

/**
 * The code under test. Both harnesses call exactly this, so any difference between their answers
 * is a property of the harness and not of the work.
 *
 * <p>Nothing here is designed to be fast or slow. It is designed to be small enough that a
 * measurement mistake is larger than the thing being measured, which is the situation every
 * microbenchmark is in.
 */
public final class Work {

    private Work() {}

    /** A seed a naive harness is likely to pass as a literal, which is the point of F2. */
    public static final long SEED = 0x9E3779B97F4A7C15L;

    /** splitmix64. Pure, cheap, and foldable when its argument is a constant. */
    public static long mix(long x) {
        long z = x + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Not foldable: the array is read at run time. Vulnerable to elimination, not to folding. */
    public static long sumArray(int[] data) {
        long total = 0;
        for (int value : data) {
            total += value;
        }
        return total;
    }

    /** Three implementations behind one interface, for the call-site profile in F5. */
    public interface Op {
        long apply(long x);
    }

    public static final class Plus implements Op {
        public long apply(long x) {
            return x + 1;
        }
    }

    public static final class Xor implements Op {
        public long apply(long x) {
            return x ^ 0x5DEECE66DL;
        }
    }

    public static final class Mul implements Op {
        public long apply(long x) {
            return x * 0x5851F42D4C957F2DL;
        }
    }

    public static int[] newData(int size) {
        int[] data = new int[size];
        for (int i = 0; i < size; i++) {
            data[i] = (int) mix(i);
        }
        return data;
    }
}
