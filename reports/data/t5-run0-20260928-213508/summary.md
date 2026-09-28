# T5 run0

- heap -Xms1g -Xmx1g, container `cpus: 2`, host Darwin arm64
- JMH: 1 forks, 2 warmup + 2 measurement iterations
- commit `unknown`

## What JMH measured

| benchmark | ns/op | error | forks |
| --- | ---: | ---: | ---: |
| blackholeOverhead | 1.08 | - | 1 |
| megamorphic | 3.46 | - | 1 |
| mix | 1.08 | - | 1 |
| monomorphic | 1.10 | - | 1 |
| sumArray | 608.20 | - | 1 |

## What the naive harness measured, and by how much it is wrong

`vs JMH` compares the naive number with JMH's for the same work. `vs control`
compares it with the naive harness's own corrected version, which isolates the one
fault from everything else the harness gets wrong.

| fault | naive ns/op | JMH ns/op | vs JMH | vs control |
| --- | ---: | ---: | --- | --- |
| Dead code elimination | 0.040 | 1.08 | 26.9x faster | 22.3x faster |
| A static field instead of a Blackhole | 0.892 | 1.08 | 1.2x faster | - |
| Constant folding | 0.082 | 1.08 | 13.2x faster | 12.0x faster |
| Control for folding: a run-time argument | 0.979 | 1.08 | 1.1x faster | - |
| No warmup: the interpreter and C1 | 18.416 | 1.08 | 17.1x slower | 20.7x slower |
| Control for warmup: the same loop, compiled | 0.888 | 1.08 | 1.2x faster | - |
| A timer call around every operation | 20.273 | 1.08 | 18.8x slower | 15.5x slower |
| Control for the timer: one pair for the loop | 1.308 | 1.08 | 1.2x slower | - |
| One implementation at the call site | 0.659 | 1.10 | 1.7x faster | - |
| The same site after it saw three types | 0.633 | 1.10 | 1.7x faster | 1.0x faster |
| Array sum, warmed, result accumulated | 613.467 | 608.20 | 1.0x slower | - |
