#!/usr/bin/env python3
"""
CASSITRACK — GPS Bus Simulator (With Analytics Support)
University of Cassino and Southern Lazio

Simulates one or more MAGNI buses moving along the Bus 16 route
and publishes their positions to the MQTT broker every N seconds.
"""

import argparse
import json
import math
import random
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

# ── Bus 16 Route: stop coordinates in order ──────────────────────
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
    Generate a list of waypoints and keep track of which stop names
    they correspond to.
    """
    waypoints_data = []

    for i in range(len(stops) - 1):
        a = stops[i]
        b = stops[i + 1]
        for step in range(steps_between):
            t = step / steps_between
            lat = a["lat"] + (b["lat"] - a["lat"]) * t
            lon = a["lon"] + (b["lon"] - a["lon"]) * t
            # The current stop is 'a' until we reach 'b'
            waypoints_data.append(((lat, lon), a["name"]))

    # Add last stop
    waypoints_data.append(((stops[-1]["lat"], stops[-1]["lon"]), stops[-1]["name"]))

    # Return trip (reverse)
    return_trip = list(reversed(waypoints_data[:-1]))
    waypoints_data += return_trip

    return waypoints_data


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
    """Simulates a single bus moving along the route with analytical data."""

    def __init__(self, vehicle_id: str, offset: int = 0):
        self.vehicle_id = vehicle_id
        self.waypoints_data = interpolate_route(ROUTE_STOPS, STEPS_BETWEEN_STOPS)
        self.position_index = offset % len(self.waypoints_data)
        self.speed_kmh = random.uniform(20, 45)

        # ── Analytics Simulated States ──
        self.trip_id = f"TRIP_L16_{vehicle_id.replace('-', '_')}"
        # Empezamos con un retraso aleatorio entre 0 y 5 minutos enteros
        self.simulated_delay_min = random.randint(0, 5)

    def next_position(self):
        """Advance the bus one step and return a position payload with analytics."""
        current_data = self.waypoints_data[self.position_index]
        current_coords = current_data[0]
        last_stop_name = current_data[1]

        next_idx = (self.position_index + 1) % len(self.waypoints_data)
        next_coords = self.waypoints_data[next_idx][0]

        lat, lon = add_gps_noise(current_coords[0], current_coords[1])
        heading = compute_heading(current_coords[0], current_coords[1], next_coords[0], next_coords[1])

        # Simulate realistic speed variation
        self.speed_kmh += random.uniform(-3, 3)
        self.speed_kmh = max(0, min(60, self.speed_kmh))

        # Simulate occasional bus stop (speed drops to 0)
        at_stop = self.position_index % STEPS_BETWEEN_STOPS == 0
        if at_stop:
            self.speed_kmh = 0

        # Fluctuating delay en minutos enteros (máximo 30 minutos)
        self.simulated_delay_min += random.choice([-1, 0, 1])
        self.simulated_delay_min = max(0, min(30, self.simulated_delay_min))

        self.position_index = next_idx

        return {
            "vehicle_id": self.vehicle_id,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "lat": round(lat, 6),
            "lon": round(lon, 6),
            "speed_kmh": round(self.speed_kmh, 1),
            "heading_deg": round(heading, 1),
            "ble_device_count": random.randint(3, 35),
            "battery_voltage": round(random.uniform(12.0, 12.8), 2),
            "firmware_version": "1.0.0-sim",

            # ─── ADDED FOR THE ANALYTICS SPRINT ───
            "trip_id": self.trip_id,
            "delay": self.simulated_delay_min,  # Enviamos los minutos limpios
            "last_stop_registered": last_stop_name
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
    parser.add_argument("--interval", type=int, default=10, help="Publish interval in seconds")
    args = parser.parse_args()

    total_waypoints = len(interpolate_route(ROUTE_STOPS, STEPS_BETWEEN_STOPS))
    buses = [
        BusSimulator(
            vehicle_id=f"MAGNI-{str(i+1).zfill(3)}",
            offset=(i * total_waypoints) // args.buses
        )
        for i in range(args.buses)
    ]

    client = mqtt.Client(userdata={"broker": args.broker, "port": args.port})
    client.on_connect = on_connect
    client.connect(args.broker, args.port, keepalive=60)
    client.loop_start()

    time.sleep(1)

    print(f"\n🚌 Simulating {args.buses} bus(es) on Linea 16 — Cassino")
    print(f"📡 Publishing to MQTT every {args.interval}s")
    print(f"📈 Analytics Enabled: sending [trip_id, delay, last_stop_registered]")
    print(f"\nPress Ctrl+C to stop.\n")

    try:
        while True:
            for bus in buses:
                payload = bus.next_position()
                topic = f"cassitrack/{payload['vehicle_id']}/position"
                client.publish(topic, json.dumps(payload), qos=1)
                print(
                    f"📤 {payload['vehicle_id']} | "
                    f"Pos: ({payload['lat']}, {payload['lon']}) | "
                    f"Speed: {payload['speed_kmh']} km/h | "
                    f"Stop: {payload['last_stop_registered']} | "
                    f"Delay: {payload['delay']}m | "  # Cambiado a 'm' para ver minutos en consola
                    f"Trip: {payload['trip_id']}"
                )
            print()
            time.sleep(args.interval)

    except KeyboardInterrupt:
        print("\n🛑 Simulator stopped.")
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()