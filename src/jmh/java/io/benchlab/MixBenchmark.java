package io.benchlab;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * The same work as {@link io.benchlab.naive.NaiveRunner}, measured the way the tool intends.
 *
 * <p>What JMH is doing that the naive harness is not: returning the value so it cannot be
 * eliminated, taking inputs from non-final {@code @State} fields so nothing folds, running warmup
 * iterations that are not measured, forking a fresh JVM per benchmark so no call site carries
 * another benchmark's profile, and timing a batch rather than each call.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class MixBenchmark {

    /**
     * Not final, and not a literal. A {@code static final} here would let the JIT fold the call,
     * which is exactly fault F2 - JMH does not protect a benchmark from its own constants.
     */
    public long value = 0x9E3779B97F4A7C15L;

    public int[] data;
    public Work.Op plus;
    public Work.Op[] all;
    private int counter;

    @Setup(Level.Trial)
    public void setUp() {
        data = Work.newData(1024);
        plus = new Work.Plus();
        all = new Work.Op[] {new Work.Plus(), new Work.Xor(), new Work.Mul()};
    }

    /** Ground truth for F1 through F4: one mix of a value the compiler cannot know. */
    @Benchmark
    public long mix() {
        return Work.mix(value++);
    }

    /** Ground truth for the array sum. */
    @Benchmark
    public long sumArray() {
        return Work.sumArray(data);
    }

    /**
     * Ground truth for F5. Its own fork means the call site here has seen exactly one type, no
     * matter which benchmarks run before it in the same invocation of the tool.
     */
    @Benchmark
    public long monomorphic() {
        return value = plus.apply(value) ^ counter++;
    }

    /** The same shape through a site that genuinely sees three types. */
    @Benchmark
    public long megamorphic() {
        return value = all[(counter++ & 0xFFFF) % 3].apply(value) ^ counter;
    }

    /**
     * What a Blackhole costs, so the other numbers can be read net of it. A benchmark that returns
     * its value does not pay this, but one that produces several values has to consume them.
     */
    @Benchmark
    public void blackholeOverhead(Blackhole blackhole) {
        blackhole.consume(Work.mix(value++));
    }
}
