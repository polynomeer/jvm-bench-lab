# Benchmark report

Every number in this file was measured in this repository. Raw output for each run is in
`reports/data/t5-run<N>-<timestamp>/`, holding both harnesses' JSON, `meta.json` with the exact
conditions, and a generated `summary.md`.

## Environment

| | |
| --- | --- |
| Host | Darwin arm64 (Apple silicon laptop), Docker Desktop |
| Container limits | `cpus: 2, mem_limit: 2g` |
| JVM | Temurin 21, `-Xms1g -Xmx1g` for both harnesses |
| JMH | 1.37, 3 forks, 5 warmup + 5 measurement iterations, `AverageTime`, ns/op |
| Naive harness | `System.nanoTime()` around a loop, 50,000,000 iterations unless stated |

Both harnesses call the same class, `io.benchlab.Work`, in the same container with the same heap.
Any difference between their answers is a property of the harness.

## T5 - What a microbenchmark measures when it is not JMH

**Question.** A hand-written benchmark is wrong. By how much, in which direction, and for which
specific reason - and is it always wrong?

**Method.** Each naive measurement is read against two things: JMH's figure for the same work, and
the naive harness's *own* corrected version of that one fault. The second comparison is what
isolates a single mistake, because a hand-rolled harness has more than one thing wrong with it at
a time.

Medians of three runs.

### What JMH measured

| benchmark | ns/op |
| --- | ---: |
| `mix` (splitmix64 of a value the compiler cannot know) | 1.07 |
| `monomorphic` (one implementation through an interface) | 1.09 |
| `megamorphic` (three implementations alternating at one site) | 3.52 |
| `sumArray` (1,024 ints) | 616.25 |
| `blackholeOverhead` | 1.08 |

### What the naive harness measured

| fault | naive ns/op | vs JMH | vs its own control |
| --- | ---: | --- | --- |
| Dead code elimination | **0.043** | 25.5x faster | **21.4x faster** |
| Constant folding | **0.079** | 13.8x faster | **12.3x faster** |
| No warmup: the interpreter and C1 | **18.166** | 16.9x slower | **19.9x slower** |
| A timer call around every operation | **20.592** | 19.0x slower | **14.8x slower** |
| A shared call site after three types (not reproduced) | 0.640 | 1.7x faster | 1.0x faster |
| Array sum, warmed, accumulated | 606.906 | 1.0x faster | - |

Controls, which are the same loops written correctly:

| control | ns/op | vs JMH |
| --- | ---: | --- |
| Accumulated into a static field | 0.946 | 1.1x faster |
| A run-time argument instead of a constant | 0.968 | 1.1x faster |
| The same loop, warmed | 0.913 | 1.2x faster |
| One timer pair around the loop | 1.395 | 1.3x slower |

### What the numbers say

**The two faults that make code disappear are worth an order of magnitude each, in the direction
that flatters you.** Dead code elimination reports 0.043 ns/op for work that takes 1.07 - the loop
was removed, and 0.043 ns is roughly the cost of the loop counter. Constant folding reports 0.079:
the argument was a literal, so the call was computed once at compile time. Both produce a number
that looks like a triumph.

**The two faults that add work are worth an order of magnitude in the other direction.** Measuring
before compilation reports 18.166 ns/op against 0.913 for the same loop warmed - 19.9x - because it
measures the interpreter and C1, not the code that ships. Calling `nanoTime` around each operation
reports 20.592 against 1.395 - the timer costs about ten times the thing being timed.

**A correctly written naive benchmark still reads about 15% fast.** The controls land at 0.913 to
0.968 ns/op against JMH's 1.07. A bare loop over a predictable index is more optimizable than JMH's
generated harness, which has to consume a return value and cross a benchmark method boundary every
call. Getting the four faults right does not make a hand-rolled loop equal to JMH; it makes it
close enough to be in the right order of magnitude.

**Fewer iterations cost accuracy in the slow direction.** The timer control ran 5,000,000
iterations and read 1.395 ns/op; the same loop at 50,000,000 read 0.913. More of the shorter
window is ramp.

**And the whole thing is irrelevant when the operation is large enough.** The naive array sum reads
606.906 ns/op against JMH's 616.25 - a 1.5% difference, in the noise. Faults worth 20x on a 1 ns
operation are invisible on a 600 ns one, because they are absolute costs of a few nanoseconds. The
question is never "is my benchmark naive" but "is my error smaller than my effect".

### The fault that did not reproduce

Profile pollution - measuring several implementations in one JVM and thereby changing the
compilation of the call site under test - was designed as the fifth fault and **does not appear**:
0.670 ns/op with one type at the site, 0.640 after three had passed through it.

The reason is instructive. Within a single measurement loop the parameter holds one concrete type
for all 50,000,000 iterations, so even a polluted inline cache is a branch that predicts perfectly.
Dispatch costs something when the types actually alternate at the site, which is what JMH's
`megamorphic` does - 3.52 ns/op against 1.09 monomorphic, a real 3.2x - and what this loop does not.

The measurement is kept rather than deleted. A fault that fails to reproduce under a design is a
fact about the design.

### Measurement faults

All four of these are in this repository's own harness, which is the material the scenario is made
of.

| # | Fault | Effect | Fix |
| --- | --- | --- | --- |
| 1 | F5 polluted a *different* loop over the same interface than the one being measured | no effect, reported as a clean negative. A type profile belongs to a call site, not to a class or an interface | both paths pushed through one shared `runOp` helper |
| 2 | With the shared helper, the loop was `acc += op.apply(i)` and `Plus` is `x + 1` | the loop reduces to a sum over `i + 1`, which HotSpot vectorises. The polluted site read **faster** than the clean one, 0.372 against 0.717 ns/op - the measurement was of auto-vectorisation, not dispatch | a loop-carried dependency, `acc = op.apply(acc) ^ i`, keeps the call on the critical path |
| 3 | Even then, F5 showed nothing | one type per loop execution means a perfectly predicted check; the fault as framed needs alternation, not just a polluted profile | recorded as not reproduced, with the reason |
| 4 | `scoreError` parsed as a number | JMH writes the string `"NaN"` when a single fork leaves the error undefined, and the summary crashed on it | non-numeric values render as `-` |

Faults 1 through 3 are all the same mistake in different clothes: the harness measured something
other than what it was named after. That is what this scenario is about, so three of them inside
the scenario's own instrument is the most useful thing in it.

### Limits

- **One host, no CPU pinning.** Docker Desktop on macOS runs a VM; there is no `cpuset` pinning to
  physical cores, no control of frequency scaling, and other containers were running. The absolute
  ns/op figures are properties of this machine. The ratios are what transfer.
- **One JDK.** Temurin 21 on aarch64. Elimination, folding and vectorisation decisions are the
  compiler's, and another JDK or architecture will move them.
- **Five faults, not a catalogue.** False sharing, allocation and GC inside the measured window,
  branch prediction shaped by ordered input, and `@State` scope mistakes are all absent.
- **The operations are tiny on purpose.** That is the regime where harness error dominates, and the
  array-sum row shows what happens outside it. Nothing here says how to benchmark a 10 ms operation.
- **JMH is treated as ground truth and is not itself validated.** Its `@State` field is non-final on
  purpose, because a `static final` there would reproduce fault F2 inside JMH - the tool does not
  protect a benchmark from its own constants.
- **Three runs.** Enough to say the ratios are reproducible, not enough to state a distribution.
