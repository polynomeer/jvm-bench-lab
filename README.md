# jvm-bench-lab

Measures how wrong a hand-written microbenchmark is, by running the naive version and JMH against
the same code in the same container and comparing them fault by fault.

This is a laboratory, not a product. The deliverable is not a benchmark suite but the tables in
[`reports/`](reports/). Every number here was measured in this repository under the resource limits
declared in [`ops/docker-compose.yml`](ops/docker-compose.yml); none is copied from anywhere.

## What it compares

`src/main/java/io/benchlab/Work.java` is the code under test. Both harnesses call exactly that, so
any difference between their answers is a property of the harness.

| | |
| --- | --- |
| `src/main/java/io/benchlab/naive/` | Benchmarks written with a loop, two `nanoTime` calls and a division |
| `src/jmh/java/io/benchlab/` | The same work as JMH benchmarks |

Each naive measurement is read against two things: JMH's figure for the same work, and the naive
harness's *own* corrected version of that one fault. The second comparison is what isolates a
single mistake from everything else a hand-rolled harness gets wrong.

## Faults measured

| | Fault | Control it is read against |
| --- | --- | --- |
| F1 | The result is never used, so there is nothing to compute | the same loop accumulating into a field |
| F2 | The argument is a compile-time constant, so the call folds away | the same call with a run-time argument |
| F3 | No warmup: the interpreter and C1, not the code that ships | the same loop after compilation |
| F4 | A timer call around every operation | one timer pair around the loop |
| F5 | A shared call site that has seen several implementations | the same site while it has seen one |

## Rules that shape the results

- **The ratio is the result, not the absolute number.** ns/op here is a property of this host; how
  many times wrong a mistake makes you is a property of the mistake.
- **Both harnesses run in one container with a fixed heap.** Left to defaults they would not even
  be the same JVM configuration.
- **A fault needs a control.** "Naive is 25x off" means nothing without the corrected naive number
  beside it, because the harness has more than one thing wrong with it at a time.
- **Measurement mistakes are recorded, including this repository's own.** A fault that failed to
  reproduce is kept with the reason.
- **JMH is not assumed to be immune.** Its `@State` field is deliberately non-final: a
  `static final` there would reproduce F2 inside JMH, and the benchmark says so.

## Running it

```bash
./scenarios/t5/run.sh 1
```

Output lands in `reports/data/t5-run<N>-<timestamp>/` with both harnesses' raw JSON, `meta.json`
holding the exact conditions, and a generated `summary.md`.
