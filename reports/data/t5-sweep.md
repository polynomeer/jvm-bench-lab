# T5 - median of the runs

3 runs, heap -Xms1g -Xmx1g, container `cpus: 2`.

## JMH

| benchmark | ns/op |
| --- | ---: |
| blackholeOverhead | 1.08 |
| megamorphic | 3.52 |
| mix | 1.07 |
| monomorphic | 1.09 |
| sumArray | 616.25 |

## Naive harness against JMH

| fault | naive ns/op | JMH ns/op | vs JMH | vs control |
| --- | ---: | ---: | --- | --- |
| Dead code elimination | 0.043 | 1.07 | 25.5x faster | 21.4x faster |
| A static field instead of a Blackhole | 0.946 | 1.07 | 1.1x faster | - |
| Constant folding | 0.079 | 1.07 | 13.8x faster | 12.3x faster |
| Control for folding: a run-time argument | 0.968 | 1.07 | 1.1x faster | - |
| No warmup: the interpreter and C1 | 18.166 | 1.07 | 16.9x slower | 19.9x slower |
| Control for warmup: the same loop, compiled | 0.913 | 1.07 | 1.2x faster | - |
| A timer call around every operation | 20.592 | 1.07 | 19.0x slower | 14.8x slower |
| Control for the timer: one pair for the loop | 1.395 | 1.07 | 1.3x slower | - |
| A shared call site that has seen one type | 0.670 | 1.09 | 1.6x faster | - |
| The same site after three types passed through it (not reproduced) | 0.640 | 1.09 | 1.7x faster | 1.0x faster |
| Array sum, warmed, result accumulated | 606.906 | 616.25 | 1.0x faster | - |
