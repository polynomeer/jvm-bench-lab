package io.benchlab.naive;

import io.benchlab.Work;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Benchmarks written the way they get written when nobody reaches for JMH: a loop, two calls to
 * {@link System#nanoTime()}, and a division.
 *
 * <p>Every method here is a mistake on purpose, and each one is a mistake people actually make.
 * The output is JSON on stdout so the numbers can be compared with JMH's without retyping them.
 */
public final class NaiveRunner {

    private static final int ITERATIONS = 50_000_000;
    private static final int WARMUP_ITERATIONS = 200_000_000;
    private static final int COLD_ITERATIONS = 1_000;
    private static final int ARRAY_SIZE = 1024;

    /** A plain static field. The usual "fix" for dead code, and not the same as a Blackhole. */
    public static long sink;

    public static void main(String[] args) {
        List<Map<String, Object>> results = new ArrayList<>();

        results.add(f1DeadCode());
        results.add(f1WithSink());
        results.add(f2ConstantFolded());
        results.add(f2NonConstant());
        results.add(f3Cold());
        results.add(f3Warm());
        results.add(f4TimerPerIteration());
        results.add(f4TimerAroundLoop());
        results.addAll(f5ProfilePollution());
        results.add(arraySum());

        System.out.println("[");
        for (int i = 0; i < results.size(); i++) {
            System.out.print("  " + toJson(results.get(i)));
            System.out.println(i < results.size() - 1 ? "," : "");
        }
        System.out.println("]");
    }

    /** F1. The result is never used, so there is nothing to compute. */
    private static Map<String, Object> f1DeadCode() {
        warm();
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            Work.mix(i);
        }
        long elapsed = System.nanoTime() - start;
        return result("f1-dead-code", "mix(i), result discarded", elapsed, ITERATIONS);
    }

    /** F1, the usual fix: accumulate into a field so the result escapes. */
    private static Map<String, Object> f1WithSink() {
        warm();
        long acc = 0;
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            acc += Work.mix(i);
        }
        long elapsed = System.nanoTime() - start;
        sink = acc;
        return result("f1-with-sink", "mix(i), accumulated into a static field", elapsed, ITERATIONS);
    }

    /** F2. The argument is a compile-time constant, so the whole call folds away. */
    private static Map<String, Object> f2ConstantFolded() {
        warm();
        long acc = 0;
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            acc += Work.mix(Work.SEED);
        }
        long elapsed = System.nanoTime() - start;
        sink = acc;
        return result("f2-constant-folded", "mix(SEED), a constant argument", elapsed, ITERATIONS);
    }

    /** F2 control: same call, argument the JIT cannot know. */
    private static Map<String, Object> f2NonConstant() {
        warm();
        long acc = 0;
        long value = System.nanoTime();
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            acc += Work.mix(value + i);
        }
        long elapsed = System.nanoTime() - start;
        sink = acc;
        return result("f2-non-constant", "mix(runtime value + i)", elapsed, ITERATIONS);
    }

    /** F3. Measured immediately, so this is the interpreter and C1, not the code that ships. */
    private static Map<String, Object> f3Cold() {
        long acc = 0;
        long start = System.nanoTime();
        for (int i = 0; i < COLD_ITERATIONS; i++) {
            acc += Work.mix(i);
        }
        long elapsed = System.nanoTime() - start;
        sink = acc;
        return result("f3-cold", "first 1,000 iterations, no warmup", elapsed, COLD_ITERATIONS);
    }

    /** F3 control: the same loop after the method is compiled. */
    private static Map<String, Object> f3Warm() {
        warm();
        long acc = 0;
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            acc += Work.mix(i);
        }
        long elapsed = System.nanoTime() - start;
        sink = acc;
        return result("f3-warm", "same loop, warmed", elapsed, ITERATIONS);
    }

    /** F4. Timing each iteration separately, which is what a per-op timer costs. */
    private static Map<String, Object> f4TimerPerIteration() {
        warm();
        int iterations = 5_000_000;
        long acc = 0;
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            acc += Work.mix(i);
            total += System.nanoTime() - start;
        }
        sink = acc;
        return result("f4-timer-per-iteration", "nanoTime around every call", total, iterations);
    }

    /** F4 control: one timer around the whole loop. */
    private static Map<String, Object> f4TimerAroundLoop() {
        warm();
        int iterations = 5_000_000;
        long acc = 0;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            acc += Work.mix(i);
        }
        long elapsed = System.nanoTime() - start;
        sink = acc;
        return result("f4-timer-around-loop", "one nanoTime pair for the loop", elapsed, iterations);
    }

    /**
     * F5. The same call site, measured with one implementation and then after three have passed
     * through it.
     *
     * <p>This one does not reproduce, and the reason is worth more than the result would have been.
     * Within a single measurement loop {@code op} holds one concrete type for all 50 million
     * iterations, so even a polluted inline cache is a branch that predicts perfectly. Dispatch
     * costs something only when the types actually alternate at the site, which is what JMH's
     * {@code megamorphic} benchmark does and this loop does not.
     *
     * <p>Kept, not deleted: a fault that fails to reproduce under a design is a fact about the
     * design.
     */
    private static List<Map<String, Object>> f5ProfilePollution() {
        List<Map<String, Object>> out = new ArrayList<>();
        long acc = 0;

        // The measured call site is the one inside runOp, and it is shared. A type profile belongs
        // to a call site, not to a class: polluting a *different* loop over the same interface
        // changes nothing, which is what the first version of this measurement did and why it
        // reported no effect at all.
        acc += runOp(new Work.Plus(), 20_000_000);
        long start = System.nanoTime();
        acc += runOp(new Work.Plus(), ITERATIONS);
        long elapsed = System.nanoTime() - start;
        out.add(result("f5-monomorphic", "runOp's call site has seen one type",
                elapsed, ITERATIONS));

        // Exactly what a second and third @Benchmark calling the same helper would do to it.
        acc += runOp(new Work.Xor(), 20_000_000);
        acc += runOp(new Work.Mul(), 20_000_000);

        start = System.nanoTime();
        acc += runOp(new Work.Plus(), ITERATIONS);
        elapsed = System.nanoTime() - start;
        sink = acc;
        out.add(result("f5-after-pollution", "the same call site, after it has seen three types",
                elapsed, ITERATIONS));
        return out;
    }

    /**
     * One shared loop, so every caller's types accumulate on one call site.
     *
     * <p>The accumulator is fed back into the call on purpose. Written as {@code acc += op.apply(i)}
     * with {@code Plus} this loop reduces to a sum over {@code i + 1}, which HotSpot vectorises -
     * and the first version of this measurement duly reported the polluted site as *faster* than
     * the clean one, at 0.372 ns against 0.717. It was measuring auto-vectorisation, not dispatch.
     * A loop-carried dependency keeps the virtual call on the critical path where the fault is.
     */
    private static long runOp(Work.Op op, int iterations) {
        long acc = 1;
        for (int i = 0; i < iterations; i++) {
            acc = op.apply(acc) ^ i;
        }
        return acc;
    }

    private static Map<String, Object> arraySum() {
        int[] data = Work.newData(ARRAY_SIZE);
        long acc = 0;
        for (int i = 0; i < 200_000; i++) {
            acc += Work.sumArray(data);
        }
        int iterations = 2_000_000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            acc += Work.sumArray(data);
        }
        long elapsed = System.nanoTime() - start;
        sink = acc;
        return result("array-sum", "sumArray over 1,024 ints, warmed", elapsed, iterations);
    }

    /** Enough iterations to get past the interpreter and C1 for everything except F3. */
    private static void warm() {
        long acc = 0;
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            acc += Work.mix(i);
        }
        sink = acc;
    }

    private static Map<String, Object> result(String id, String what, long elapsedNanos, long ops) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("what", what);
        row.put("ops", ops);
        row.put("elapsed_ns", elapsedNanos);
        row.put("ns_per_op", elapsedNanos / (double) ops);
        return row;
    }

    private static String toJson(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\": ");
            Object v = e.getValue();
            sb.append(v instanceof String ? "\"" + v + "\"" : String.valueOf(v));
        }
        return sb.append('}').toString();
    }
}
