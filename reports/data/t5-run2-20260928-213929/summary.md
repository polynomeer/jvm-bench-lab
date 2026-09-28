# T5 run2

- heap -Xms1g -Xmx1g, container `cpus: 2`, host Darwin arm64
- JMH: 3 forks, 5 warmup + 5 measurement iterations
- commit `unknown`

## What JMH measured

| benchmark | ns/op | error | forks |
| --- | ---: | ---: | ---: |
| blackholeOverhead | 1.07 | ±0.02 | 3 |
| megamorphic | 3.70 | ±0.37 | 3 |
| mix | 1.07 | ±0.01 | 3 |
| monomorphic | 1.09 | ±0.02 | 3 |
| sumArray | 616.99 | ±18.75 | 3 |

## What the naive harness measured, and by how much it is wrong

`vs JMH` compares the naive number with JMH's for the same work. `vs control`
compares it with the naive harness's own corrected version, which isolates the one
fault from everything else the harness gets wrong.

| fault | naive ns/op | JMH ns/op | vs JMH | vs control |
| --- | ---: | ---: | --- | --- |
| Dead code elimination | 0.045 | 1.07 | 23.8x faster | 21.1x faster |
| A static field instead of a Blackhole | 0.946 | 1.07 | 1.1x faster | - |
| Constant folding | 0.074 | 1.07 | 14.5x faster | 12.9x faster |
| Control for folding: a run-time argument | 0.954 | 1.07 | 1.1x faster | - |
| No warmup: the interpreter and C1 | 19.666 | 1.07 | 18.4x slower | 22.0x slower |
| Control for warmup: the same loop, compiled | 0.892 | 1.07 | 1.2x faster | - |
| A timer call around every operation | 20.592 | 1.07 | 19.3x slower | 14.8x slower |
| Control for the timer: one pair for the loop | 1.395 | 1.07 | 1.3x slower | - |
| A shared call site that has seen one type | 0.679 | 1.09 | 1.6x faster | - |
| The same site after three types passed through it (not reproduced) | 0.638 | 1.09 | 1.7x faster | 1.1x faster |
| Array sum, warmed, result accumulated | 606.906 | 616.99 | 1.0x faster | - |
