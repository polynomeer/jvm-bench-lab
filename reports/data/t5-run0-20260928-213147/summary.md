# T5 run0

- heap -Xms1g -Xmx1g, container `cpus: 2`, host Darwin arm64
- JMH: 1 forks, 2 warmup + 2 measurement iterations
- commit `unknown`

## What JMH measured

| benchmark | ns/op | error | forks |
| --- | ---: | ---: | ---: |
| blackholeOverhead | 1.12 | - | 1 |
| megamorphic | 3.67 | - | 1 |
| mix | 1.06 | - | 1 |
| monomorphic | 0.80 | - | 1 |
| sumArray | 618.89 | - | 1 |

## What the naive harness measured, and by how much it is wrong

`vs JMH` compares the naive number with JMH's for the same work. `vs control`
compares it with the naive harness's own corrected version, which isolates the one
fault from everything else the harness gets wrong.

| fault | naive ns/op | JMH ns/op | vs JMH | vs control |
| --- | ---: | ---: | --- | --- |
| Dead code elimination | 0.042 | 1.06 | 25.2x faster | 21.7x faster |
| A static field instead of a Blackhole | 0.915 | 1.06 | 1.2x faster | - |
| Constant folding | 0.077 | 1.06 | 13.7x faster | 12.2x faster |
| Control for folding: a run-time argument | 0.948 | 1.06 | 1.1x faster | - |
| No warmup: the interpreter and C1 | 18.208 | 1.06 | 17.1x slower | 20.0x slower |
| Control for warmup: the same loop, compiled | 0.909 | 1.06 | 1.2x faster | - |
| A timer call around every operation | 23.103 | 1.06 | 21.7x slower | 16.5x slower |
| Control for the timer: one pair for the loop | 1.396 | 1.06 | 1.3x slower | - |
| One implementation at the call site | 0.722 | 0.80 | 1.1x faster | - |
| The same site after it saw three types | 0.756 | 0.80 | 1.1x faster | 1.0x slower |
| Array sum, warmed, result accumulated | 620.320 | 618.89 | 1.0x slower | - |
