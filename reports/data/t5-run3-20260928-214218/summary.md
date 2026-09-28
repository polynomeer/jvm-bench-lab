# T5 run3

- heap -Xms1g -Xmx1g, container `cpus: 2`, host Darwin arm64
- JMH: 3 forks, 5 warmup + 5 measurement iterations
- commit `unknown`

## What JMH measured

| benchmark | ns/op | error | forks |
| --- | ---: | ---: | ---: |
| blackholeOverhead | 1.08 | ±0.02 | 3 |
| megamorphic | 3.52 | ±0.08 | 3 |
| mix | 1.07 | ±0.02 | 3 |
| monomorphic | 1.09 | ±0.03 | 3 |
| sumArray | 608.28 | ±8.18 | 3 |

## What the naive harness measured, and by how much it is wrong

`vs JMH` compares the naive number with JMH's for the same work. `vs control`
compares it with the naive harness's own corrected version, which isolates the one
fault from everything else the harness gets wrong.

| fault | naive ns/op | JMH ns/op | vs JMH | vs control |
| --- | ---: | ---: | --- | --- |
| Dead code elimination | 0.041 | 1.07 | 26.5x faster | 23.4x faster |
| A static field instead of a Blackhole | 0.951 | 1.07 | 1.1x faster | - |
| Constant folding | 0.082 | 1.07 | 13.2x faster | 11.9x faster |
| Control for folding: a run-time argument | 0.968 | 1.07 | 1.1x faster | - |
| No warmup: the interpreter and C1 | 18.166 | 1.07 | 16.9x slower | 19.9x slower |
| Control for warmup: the same loop, compiled | 0.913 | 1.07 | 1.2x faster | - |
| A timer call around every operation | 20.399 | 1.07 | 19.0x slower | 15.6x slower |
| Control for the timer: one pair for the loop | 1.305 | 1.07 | 1.2x slower | - |
| A shared call site that has seen one type | 0.663 | 1.09 | 1.6x faster | - |
| The same site after three types passed through it (not reproduced) | 0.640 | 1.09 | 1.7x faster | 1.0x faster |
| Array sum, warmed, result accumulated | 602.014 | 608.28 | 1.0x faster | - |
