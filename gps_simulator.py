#!/usr/bin/env python3
"""
CASSITRACK — GPS Bus Simulator
University of Cassino and Southern Lazio

Simulates one or more MAGNI buses moving along the Bus 16 route
and publishes their positions to the MQTT broker every N seconds.

This lets you develop and test the entire backend without
any real hardware (ESP32 / GPS tracker on a bus).

Usage:
    pip install paho-mqtt
    python3 gps_simulator.py                    # 1 bus, default settings
    python3 gps_simulator.py --buses 3          # 3 buses on the same route
    python3 gps_simulator.py --interval 15      # publish every 15 seconds
    python3 gps_simulator.py --broker localhost --port 1883

Topics published:
    cassitrack/MAGNI-001/position
    cassitrack/MAGNI-002/position
    ...
"""

import argparse
import json
import math
import random
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

# ── Bus 16 Route: stop coordinates in order ──────────────────────
# These are the actual approximate coordinates of stops on the route
# from Cassino Stazione to Campus Folcara.
ROUTE_STOPS = [
    {"id": "CASSINO-STAZIONE",  "name": "Cassino Stazione FS",     "lat": 41.4892, "lon": 13.8282},
    {"id": "CASSINO-CENTRO",    "name": "Cassino Centro",           "lat": 41.4917, "lon": 13.8314},
    {"id": "CASSINO-OSPEDALE",  "name": "Ospedale Santa Scolastica","lat": 41.4955, "lon": 13.8330},
    {"id": "FOLCARA-VIA",       "name": "Via Folcara",              "lat": 41.5020, "lon": 13.8200},
    {"id": "FOLCARA-CAMPUS",    "name": "Campus UNICAS Folcara",    "lat": 41.5041, "lon": 13.8189},
]

# Interpolate intermediate points between stops for smoother movement
STEPS_BETWEEN_STOPS = 10


def interpolate_route(stops, steps_between):
    """
    Generate a list of (lat, lon) waypoints by interpolating
    linearly between each pair of consecutive stops.
    At the end of the route, the bus reverses back to the start.
    """
    waypoints = []
    for i in range(len(stops) - 1):
        a = stops[i]
        b = stops[i + 1]
        for step in range(steps_between):
            t = step / steps_between
            lat = a["lat"] + (b["lat"] - a["lat"]) * t
            lon = a["lon"] + (b["lon"] - a["lon"]) * t
            waypoints.append((lat, lon))
    # Add last stop
    waypoints.append((stops[-1]["lat"], stops[-1]["lon"]))
    # Return trip (reverse)
    waypoints += list(reversed(waypoints[:-1]))
    return waypoints


def compute_heading(lat1, lon1, lat2, lon2):
    """Compute compass heading from point 1 to point 2."""
    delta_lon = math.radians(lon2 - lon1)
    lat1_r = math.radians(lat1)
    lat2_r = math.radians(lat2)
    x = math.sin(delta_lon) * math.cos(lat2_r)
    y = (math.cos(lat1_r) * math.sin(lat2_r)
         - math.sin(lat1_r) * math.cos(lat2_r) * math.cos(delta_lon))
    heading = math.degrees(math.atan2(x, y))
    return (heading + 360) % 360


def add_gps_noise(lat, lon, meters=5):
    """Add small random noise to simulate GPS inaccuracy (~±5m)."""
    delta_lat = (random.uniform(-meters, meters)) / 111320
    delta_lon = (random.uniform(-meters, meters)) / (111320 * math.cos(math.radians(lat)))
    return lat + delta_lat, lon + delta_lon


class BusSimulator:
    """Simulates a single bus moving along the route."""

    def __init__(self, vehicle_id: str, offset: int = 0):
        self.vehicle_id = vehicle_id
        self.waypoints = interpolate_route(ROUTE_STOPS, STEPS_BETWEEN_STOPS)
        # Offset different buses so they're not all at the same position
        self.position_index = offset % len(self.waypoints)
        self.speed_kmh = random.uniform(20, 45)

    def next_position(self):
        """Advance the bus one step and return a position payload."""
        current = self.waypoints[self.position_index]
        next_idx = (self.position_index + 1) % len(self.waypoints)
        next_wp = self.waypoints[next_idx]

        lat, lon = add_gps_noise(current[0], current[1])
        heading = compute_heading(current[0], current[1], next_wp[0], next_wp[1])

        # Simulate realistic speed variation
        self.speed_kmh += random.uniform(-3, 3)
        self.speed_kmh = max(0, min(60, self.speed_kmh))

        # Simulate occasional bus stop (speed drops to 0)
        at_stop = self.position_index % STEPS_BETWEEN_STOPS == 0
        if at_stop:
            self.speed_kmh = 0

        self.position_index = next_idx

        return {
            "vehicle_id": self.vehicle_id,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "lat": round(lat, 6),
            "lon": round(lon, 6),
            "speed_kmh": round(self.speed_kmh, 1),
            "heading_deg": round(heading, 1),
            "ble_device_count": random.randint(3, 35),   # simulated passengers
            "battery_voltage": round(random.uniform(12.0, 12.8), 2),
            "firmware_version": "1.0.0-sim"
        }


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"✅ Connected to MQTT broker at {userdata['broker']}:{userdata['port']}")
    else:
        print(f"❌ Connection failed with code {rc}")


def main():
    parser = argparse.ArgumentParser(description="CASSITRACK GPS Bus Simulator")
    parser.add_argument("--broker",   default="localhost",  help="MQTT broker host")
    parser.add_argument("--port",     type=int, default=1883, help="MQTT broker port")
    parser.add_argument("--buses",    type=int, default=1,  help="Number of buses to simulate")
    parser.add_argument("--interval", type=int, default=30, help="Publish interval in seconds")
    args = parser.parse_args()

    # Create bus simulators, spaced evenly along the route
    total_waypoints = len(interpolate_route(ROUTE_STOPS, STEPS_BETWEEN_STOPS))
    buses = [
        BusSimulator(
            vehicle_id=f"MAGNI-{str(i+1).zfill(3)}",
            offset=(i * total_waypoints) // args.buses
        )
        for i in range(args.buses)
    ]

    # Connect to MQTT broker
    client = mqtt.Client(userdata={"broker": args.broker, "port": args.port})
    client.on_connect = on_connect
    client.connect(args.broker, args.port, keepalive=60)
    client.loop_start()

    time.sleep(1)  # wait for connection

    print(f"\n🚌 Simulating {args.buses} bus(es) on Linea 16 — Cassino")
    print(f"📡 Publishing to MQTT every {args.interval}s")
    print(f"📍 Route: Cassino Stazione → Campus Folcara → (return)")
    print(f"🔗 Topics: cassitrack/MAGNI-XXX/position")
    print(f"\nPress Ctrl+C to stop.\n")

    try:
        while True:
            for bus in buses:
                payload = bus.next_position()
                topic = f"cassitrack/{payload['vehicle_id']}/position"
                client.publish(topic, json.dumps(payload), qos=1)
                print(
                    f"📤 {payload['vehicle_id']} | "
                    f"lat={payload['lat']}, lon={payload['lon']} | "
                    f"speed={payload['speed_kmh']} km/h | "
                    f"BLE devices={payload['ble_device_count']}"
                )
            print()
            time.sleep(args.interval)

    except KeyboardInterrupt:
        print("\n🛑 Simulator stopped.")
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
