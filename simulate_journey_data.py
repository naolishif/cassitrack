#!/usr/bin/env python3
"""
OmniMove Journey Data Simulator
Generates 30 days of realistic journey_search events in InfluxDB
matching exactly the schema written by JourneyEventService.java
"""

import argparse
import random
import sys
from datetime import datetime, timedelta, timezone

try:
    from influxdb_client import InfluxDBClient, WriteOptions
    from influxdb_client.client.write_api import SYNCHRONOUS
except ImportError:
    print("ERROR: influxdb-client not installed. Run: pip install influxdb-client")
    sys.exit(1)

# ── Config defaults ────────────────────────────────────────────────────────────
DEFAULT_URL    = "http://localhost:8087"
DEFAULT_ORG    = "omnimove_org"
DEFAULT_BUCKET = "omnimove_bucket"

# ── Mode distribution (base weights) ──────────────────────────────────────────
BASE_WEIGHTS = {
    "BUS":     0.45,
    "WALK":    0.25,
    "BIKE":    0.20,
    "SCOOTER": 0.10,
}

# ── Hourly multipliers ─────────────────────────────────────────────────────────
def hour_multiplier(hour: int) -> float:
    if 7 <= hour <= 9:   return 4.0   # morning peak
    if 12 <= hour <= 13: return 2.0   # lunch
    if 17 <= hour <= 19: return 3.5   # evening peak
    if 22 <= hour or hour <= 6: return 0.1  # night
    return 1.0

# ── Weekend adjustments ────────────────────────────────────────────────────────
def weekend_weights(base: dict) -> dict:
    w = base.copy()
    w["BUS"]     *= 0.80
    w["BIKE"]    *= 1.15
    w["WALK"]    *= 1.10
    w["SCOOTER"] *= 1.05
    total = sum(w.values())
    return {k: v / total for k, v in w.items()}

def normalize(weights: dict) -> dict:
    total = sum(weights.values())
    return {k: v / total for k, v in weights.items()}

# ── Green index per mode ───────────────────────────────────────────────────────
def compute_green_index(mode: str, distance_km: float) -> int:
    """Replica exacta de GreenIndexService.java"""
    CO2 = {"BUS": 68.0, "BIKE": 0.0, "SCOOTER": 0.0, "WALK": 0.0, "CAR": 170.0}
    CO2_CAR = 170.0
    factor = CO2.get(mode.upper(), 68.0)
    co2 = factor * distance_km
    max_co2 = CO2_CAR * distance_km
    if max_co2 == 0:
        return 100
    index = 100.0 - (co2 / max_co2 * 100.0)
    return int(max(0, min(100, index)))

# ── Distance per mode (km) ─────────────────────────────────────────────────────
def random_distance(mode: str) -> float:
    ranges = {
        "BUS":     (1.5, 8.0),
        "BIKE":    (0.8, 5.0),
        "SCOOTER": (0.5, 4.0),
        "WALK":    (0.3, 2.5),
    }
    lo, hi = ranges[mode]
    return round(random.uniform(lo, hi), 2)

# ── Generate points ────────────────────────────────────────────────────────────
def generate_points(days: int):
    points = []
    now_utc = datetime.now(timezone.utc)
    base_daily = 95  # avg weekday events

    for day_offset in range(days, 0, -1):
        day_dt = now_utc - timedelta(days=day_offset)
        is_weekend = day_dt.weekday() >= 5

        daily_volume = int(base_daily * (0.4 if is_weekend else 1.0))
        daily_volume += random.randint(-10, 10)

        weights = normalize(weekend_weights(BASE_WEIGHTS) if is_weekend else BASE_WEIGHTS)
        modes   = list(weights.keys())
        probs   = list(weights.values())

        for _ in range(daily_volume):
            # Pick hour weighted by multipliers
            hour_weights = [hour_multiplier(h) for h in range(24)]
            total_hw = sum(hour_weights)
            hour_probs = [w / total_hw for w in hour_weights]
            hour = random.choices(range(24), weights=hour_probs, k=1)[0]

            minute  = random.randint(0, 59)
            second  = random.randint(0, 59)
            ts = day_dt.replace(hour=hour, minute=minute, second=second, microsecond=0)

            mode       = random.choices(modes, weights=probs, k=1)[0]
            distance   = random_distance(mode)
            green_idx = compute_green_index(mode, distance)
            day_name   = ts.strftime("%A").upper()

            points.append({
                "measurement": "journey_search",
                "tags": {
                    "mode":        mode,
                    "day_of_week": day_name,
                },
                "fields": {
                    "hour":        hour,
                    "green_index": green_idx,
                    "distance_km": distance,
                    "count":       1,
                },
                "time": ts,
            })

    return points

# ── Write to InfluxDB ──────────────────────────────────────────────────────────
def write_points(points, url, token, org, bucket, dry_run):
    if dry_run:
        print(f"[DRY RUN] Would write {len(points)} points to {bucket}")
        for p in points[:5]:
            print(" ", p)
        print("  ...")
        return

    print(f"Connecting to {url} ...")
    with InfluxDBClient(url=url, token=token, org=org) as client:
        write_api = client.write_api(write_options=SYNCHRONOUS)

        batch_size = 500
        total = len(points)
        for i in range(0, total, batch_size):
            batch = points[i:i + batch_size]
            records = []
            for p in batch:
                from influxdb_client.domain.write_precision import WritePrecision
                from influxdb_client import Point
                pt = (Point(p["measurement"])
                      .tag("mode",        p["tags"]["mode"])
                      .tag("day_of_week", p["tags"]["day_of_week"])
                      .field("hour",        p["fields"]["hour"])
                      .field("green_index", p["fields"]["green_index"])
                      .field("distance_km", p["fields"]["distance_km"])
                      .field("count",       p["fields"]["count"])
                      .time(p["time"], WritePrecision.MS))
                records.append(pt)
            write_api.write(bucket=bucket, org=org, record=records)
            print(f"  Written {min(i + batch_size, total)}/{total} points...")

    print(f"\nDone! {total} points written to bucket '{bucket}'")

# ── Main ───────────────────────────────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description="OmniMove journey data simulator")
    parser.add_argument("--days",    type=int,  default=30,                   help="Days of history to generate")
    parser.add_argument("--url",     type=str,  default=DEFAULT_URL,          help="InfluxDB URL")
    parser.add_argument("--token",   type=str,  default="",                   help="InfluxDB token")
    parser.add_argument("--org",     type=str,  default=DEFAULT_ORG,          help="InfluxDB org")
    parser.add_argument("--bucket",  type=str,  default=DEFAULT_BUCKET,       help="InfluxDB bucket")
    parser.add_argument("--dry-run", action="store_true",                     help="Print without writing")
    args = parser.parse_args()

    if not args.dry_run and not args.token:
        print("ERROR: --token is required (or use --dry-run)")
        print("Example: python simulate_journey_data.py --token YOUR_INFLUX_TOKEN")
        sys.exit(1)

    print(f"Generating {args.days} days of journey data...")
    points = generate_points(args.days)
    print(f"Generated {len(points)} points")

    write_points(points, args.url, args.token, args.org, args.bucket, args.dry_run)

if __name__ == "__main__":
    main()
