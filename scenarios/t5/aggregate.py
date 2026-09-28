#!/usr/bin/env python3
"""Aggregate the T5 runs: median across runs for each fault and each JMH benchmark."""

import json
import pathlib
import statistics
import sys


def med(values):
    values = [v for v in values if v is not None]
    return round(statistics.median(values), 3) if values else None


def num(value, digits=2):
    return "-" if value is None else f"{value:,.{digits}f}"


def factor_cell(value):
    if value is None:
        return "-"
    return f"{value:,.1f}x slower" if value > 0 else f"{-value:,.1f}x faster"


def main(root="reports/data"):
    runs = [json.loads(p.read_text())
            for p in sorted(pathlib.Path(root).glob("t5-run[1-9]-*/summary.json"))]
    if not runs:
        print("no runs")
        return

    methods = sorted({m for r in runs for m in r["jmh"]})
    lines = ["# T5 - median of the runs", "",
             f"{len(runs)} runs, heap {runs[0]['heap']}, container `cpus: 2`.", "",
             "## JMH", "", "| benchmark | ns/op |", "| --- | ---: |"]
    for method in methods:
        lines.append(f"| {method} | {num(med([r['jmh'].get(method, {}).get('score') for r in runs]))} |")

    ids = []
    for r in runs:
        for row in r["rows"]:
            if row["id"] not in ids:
                ids.append(row["id"])

    lines += ["", "## Naive harness against JMH", "",
              "| fault | naive ns/op | JMH ns/op | vs JMH | vs control |",
              "| --- | ---: | ---: | --- | --- |"]
    for row_id in ids:
        rows = [row for r in runs for row in r["rows"] if row["id"] == row_id]
        label = rows[0]["label"]
        lines.append(
            f"| {label} | {num(med([x['naive_ns'] for x in rows]), 3)} | "
            f"{num(med([x['jmh_ns'] for x in rows]))} | "
            f"{factor_cell(med([x['vs_jmh'] for x in rows]))} | "
            f"{factor_cell(med([x['vs_control'] for x in rows]))} |")

    text = "\n".join(lines)
    pathlib.Path(root, "t5-sweep.md").write_text(text + "\n")
    print(text)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "reports/data")
