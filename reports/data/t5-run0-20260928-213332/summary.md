# T5 run0

- heap -Xms1g -Xmx1g, container `cpus: 2`, host Darwin arm64
- JMH: 1 forks, 2 warmup + 2 measurement iterations
- commit `unknown`

## What JMH measured

| benchmark | ns/op | error | forks |
| --- | ---: | ---: | ---: |
| blackholeOverhead | 1.07 | - | 1 |
| megamorphic | 3.63 | - | 1 |
| mix | 1.09 | - | 1 |
| monomorphic | 0.86 | - | 1 |
| sumArray | 604.54 | - | 1 |

## What the naive harness measured, and by how much it is wrong

`vs JMH` compares the naive number with JMH's for the same work. `vs control`
compares it with the naive harness's own corrected version, which isolates the one
fault from everything else the harness gets wrong.

| fault | naive ns/op | JMH ns/op | vs JMH | vs control |
| --- | ---: | ---: | --- | --- |
| Dead code elimination | 0.043 | 1.09 | 25.6x faster | 21.1x faster |
| A static field instead of a Blackhole | 0.897 | 1.09 | 1.2x faster | - |
| Constant folding | 0.073 | 1.09 | 14.9x faster | 13.3x faster |
| Control for folding: a run-time argument | 0.975 | 1.09 | 1.1x faster | - |
| No warmup: the interpreter and C1 | 18.333 | 1.09 | 16.8x slower | 20.4x slower |
| Control for warmup: the same loop, compiled | 0.896 | 1.09 | 1.2x faster | - |
| A timer call around every operation | 20.508 | 1.09 | 18.8x slower | 14.8x slower |
| Control for the timer: one pair for the loop | 1.382 | 1.09 | 1.3x slower | - |
| One implementation at the call site | 0.717 | 0.86 | 1.2x faster | - |
| The same site after it saw three types | 0.372 | 0.86 | 2.3x faster | 1.9x faster |
| Array sum, warmed, result accumulated | 610.016 | 604.54 | 1.0x slower | - |
