#!/usr/bin/env python3
"""Summarize an Openwheel Racing physics telemetry CSV around a braking spin."""

import argparse
import csv
from pathlib import Path


WHEELS = ("fl", "fr", "rl", "rr")


def number(row, key):
    try:
        return float(row[key])
    except (KeyError, TypeError, ValueError):
        return 0.0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path)
    parser.add_argument("--window", type=int, default=20, help="samples either side of peak yaw rate")
    args = parser.parse_args()

    with args.csv.open(newline="", encoding="utf-8") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise SystemExit("log contains no samples")

    candidates = [
        (index, row) for index, row in enumerate(rows)
        if number(row, "brake") >= 0.5 and 120.0 <= number(row, "speed_kmh") <= 200.0
    ] or list(enumerate(rows))
    peak_index, peak = max(candidates, key=lambda item: abs(number(item[1], "yaw_rate_radps")))
    start = max(0, peak_index - args.window)
    end = min(len(rows), peak_index + args.window + 1)
    window = rows[start:end]

    print(f"file: {args.csv.resolve()}")
    print(f"samples: {len(rows)}; peak braking yaw sample: {peak_index} tick={peak.get('tick')}")
    print(
        f"peak state: speed={number(peak, 'speed_kmh'):.1f} km/h "
        f"yaw={number(peak, 'yaw_rate_radps'):.3f} rad/s "
        f"steer={number(peak, 'steer_deg'):.2f} deg brake={number(peak, 'brake'):.2f} "
        f"brake_direction={number(peak, 'brake_direction'):+.0f}"
    )

    print("\nwheel peaks in analysis window:")
    for wheel in WHEELS:
        max_demand = max(number(row, f"{wheel}_demand") for row in window)
        max_slip = max(window, key=lambda row: abs(number(row, f"{wheel}_slip_angle_deg")))
        min_load = min(number(row, f"{wheel}_load_n") for row in window)
        max_abs_cut = max(
            abs(number(row, f"{wheel}_raw_long_request_n"))
            - abs(number(row, f"{wheel}_assisted_long_request_n"))
            for row in window
        )
        print(
            f"  {wheel.upper()}: demand={max_demand:.3f} "
            f"slip={number(max_slip, f'{wheel}_slip_angle_deg'):+.2f} deg "
            f"min_load={min_load:.0f} N max_assist_cut={max_abs_cut:.0f} N"
        )

    print("\nwindow trace (every second sample):")
    print("tick speed yaw steer brake bdir load_xfer front_yaw rear_yaw front_load rear_load fl_dem fr_dem rl_dem rr_dem")
    for row in window[::2]:
        front_load = number(row, "fl_load_n") + number(row, "fr_load_n")
        rear_load = number(row, "rl_load_n") + number(row, "rr_load_n")
        print(
            f"{int(number(row, 'tick')):>6} {number(row, 'speed_kmh'):>5.1f} "
            f"{number(row, 'yaw_rate_radps'):>+6.3f} {number(row, 'steer_deg'):>+5.2f} "
            f"{number(row, 'brake'):>4.2f} {number(row, 'brake_direction'):>+4.0f} "
            f"{number(row, 'longitudinal_load_transfer_n'):>+9.0f} "
            f"{number(row, 'front_yaw_moment_nm'):>+9.0f} {number(row, 'rear_yaw_moment_nm'):>+9.0f} "
            f"{front_load:>9.0f} {rear_load:>9.0f} "
            + " ".join(f"{number(row, f'{wheel}_demand'):.2f}" for wheel in WHEELS)
        )


if __name__ == "__main__":
    main()
