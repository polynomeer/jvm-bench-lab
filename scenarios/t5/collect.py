#!/usr/bin/env python3
"""Put the naive harness's answers next to JMH's for the same code.

The output is one table of faults. Each row is a naive measurement, the JMH measurement of the same
work, and the factor between them - which is the only number that matters here, because the
absolute values are a property of this host and the ratio is a property of the mistake.
"""

import json
import pathlib
import sys

# naive id -> (JMH benchmark method, what the naive number is wrong about, the control it is read
# against). A control of None means the JMH figure is the control.
FAULTS = [
    ("f1-dead-code", "mix", "Dead code elimination", "f1-with-sink"),
    ("f1-with-sink", "mix", "A static field instead of a Blackhole", None),
    ("f2-constant-folded", "mix", "Constant folding", "f2-non-constant"),
    ("f2-non-constant", "mix", "Control for folding: a run-time argument", None),
    ("f3-cold", "mix", "No warmup: the interpreter and C1", "f3-warm"),
    ("f3-warm", "mix", "Control for warmup: the same loop, compiled", None),
    ("f4-timer-per-iteration", "mix", "A timer call around every operation", "f4-timer-around-loop"),
    ("f4-timer-around-loop", "mix", "Control for the timer: one pair for the loop", None),
    ("f5-monomorphic", "monomorphic", "A shared call site that has seen one type", None),
    ("f5-after-pollution", "monomorphic",
     "The same site after three types passed through it (not reproduced)", "f5-monomorphic"),
    ("array-sum", "sumArray", "Array sum, warmed, result accumulated", None),
]


def load_jmh(path):
    """JMH JSON -> {method: {score, error, unit}}. Scores are already ns/op for AverageTime."""
    if not path.exists():
        return {}
    out = {}
    for entry in json.loads(path.read_text()):
        method = entry["benchmark"].rsplit(".", 1)[-1]
        metric = entry["primaryMetric"]
        out[method] = {
            "score": metric["score"],
            "error": metric.get("scoreError"),
            "unit": metric.get("scoreUnit"),
            "forks": entry.get("forks"),
        }
    return out


def load_naive(path):
    if not path.exists():
        return {}
    return {row["id"]: row for row in json.loads(path.read_text())}


def num(value, digits=2):
    """JMH writes scoreError as the string "NaN" when a single fork leaves it undefined."""
    if value is None:
        return "-"
    try:
        value = float(value)
    except (TypeError, ValueError):
        return "-"
    return "-" if value != value else f"{value:,.{digits}f}"


def factor(a, b):
    try:
        a, b = float(a), float(b)
    except (TypeError, ValueError):
        return None
    if not a or not b or a != a or b != b:
        return None
    return a / b if a >= b else -(b / a)


def factor_cell(value):
    if value is None:
        return "-"
    if value > 0:
        return f"{value:,.1f}x slower"
    return f"{-value:,.1f}x faster"


def main(out_dir):
    out = pathlib.Path(out_dir)
    meta = json.loads((out / "meta.json").read_text())
    jmh = load_jmh(out / "jmh.json")
    naive = load_naive(out / "naive.json")

    rows = []
    for naive_id, method, label, control_id in FAULTS:
        n = naive.get(naive_id)
        if not n:
            continue
        truth = jmh.get(method, {}).get("score")
        control = naive.get(control_id, {}).get("ns_per_op") if control_id else None
        rows.append({
            "id": naive_id, "label": label, "method": method,
            "naive_ns": n["ns_per_op"], "jmh_ns": truth,
            "vs_jmh": factor(n["ns_per_op"], truth),
            "vs_control": factor(n["ns_per_op"], control) if control else None,
            "control_id": control_id,
        })

    result = {"run": meta["run"], "heap": meta["heap"], "jmh": jmh, "rows": rows}
    (out / "summary.json").write_text(json.dumps(result, indent=1))

    lines = [f"# T5 run{meta['run']}", "",
             f"- heap {meta['heap']}, container `cpus: {meta['container_cpus']}`, host {meta['host']}",
             f"- JMH: {meta['jmh_forks']} forks, {meta['jmh_warmup_iterations']} warmup + "
             f"{meta['jmh_measurement_iterations']} measurement iterations",
             f"- commit `{meta['git_commit']}`", "",
             "## What JMH measured", "",
             "| benchmark | ns/op | error | forks |", "| --- | ---: | ---: | ---: |"]
    for method, data in sorted(jmh.items()):
        lines.append(f"| {method} | {num(data['score'])} | "
                     f"{num(data['error']) if num(data['error']) == '-' else '±' + num(data['error'])} | "
                     f"{data['forks']} |")

    lines += ["", "## What the naive harness measured, and by how much it is wrong", "",
              "`vs JMH` compares the naive number with JMH's for the same work. `vs control`",
              "compares it with the naive harness's own corrected version, which isolates the one",
              "fault from everything else the harness gets wrong.", "",
              "| fault | naive ns/op | JMH ns/op | vs JMH | vs control |",
              "| --- | ---: | ---: | --- | --- |"]
    for row in rows:
        lines.append(f"| {row['label']} | {num(row['naive_ns'], 3)} | {num(row['jmh_ns'])} | "
                     f"{factor_cell(row['vs_jmh'])} | {factor_cell(row['vs_control'])} |")

    (out / "summary.md").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
