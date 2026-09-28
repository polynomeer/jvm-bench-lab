# T5 run1

- heap -Xms1g -Xmx1g, container `cpus: 2`, host Darwin arm64
- JMH: 3 forks, 5 warmup + 5 measurement iterations
- commit `unknown`

## What JMH measured

| benchmark | ns/op | error | forks |
| --- | ---: | ---: | ---: |
| blackholeOverhead | 1.09 | ±0.03 | 3 |
| megamorphic | 3.47 | ±0.03 | 3 |
| mix | 1.10 | ±0.03 | 3 |
| monomorphic | 1.10 | ±0.03 | 3 |
| sumArray | 616.25 | ±6.02 | 3 |

## What the naive harness measured, and by how much it is wrong

`vs JMH` compares the naive number with JMH's for the same work. `vs control`
compares it with the naive harness's own corrected version, which isolates the one
fault from everything else the harness gets wrong.

| fault | naive ns/op | JMH ns/op | vs JMH | vs control |
| --- | ---: | ---: | --- | --- |
| Dead code elimination | 0.043 | 1.10 | 25.5x faster | 21.3x faster |
| A static field instead of a Blackhole | 0.920 | 1.10 | 1.2x faster | - |
| Constant folding | 0.079 | 1.10 | 13.8x faster | 12.3x faster |
| Control for folding: a run-time argument | 0.977 | 1.10 | 1.1x faster | - |
| No warmup: the interpreter and C1 | 18.166 | 1.10 | 16.5x slower | 19.1x slower |
| Control for warmup: the same loop, compiled | 0.949 | 1.10 | 1.2x faster | - |
| A timer call around every operation | 20.919 | 1.10 | 19.0x slower | 13.6x slower |
| Control for the timer: one pair for the loop | 1.534 | 1.10 | 1.4x slower | - |
| A shared call site that has seen one type | 0.670 | 1.10 | 1.6x faster | - |
| The same site after three types passed through it (not reproduced) | 0.648 | 1.10 | 1.7x faster | 1.0x faster |
| Array sum, warmed, result accumulated | 623.554 | 616.25 | 1.0x slower | - |
