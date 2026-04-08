# CASSITRACK — Setup & Run Guide
**University of Cassino and Southern Lazio — 2025/2026**

Real-time bus fleet monitoring for MAGNI Autoservizi, Cassino.

---

## What Is This?

CASSITRACK is the backend of the OMNIMOVE smart mobility platform.
It receives GPS positions from buses (via MQTT), stores them, and
exposes a REST API so OMNIMOVE and the fleet dashboard can show
live bus locations and estimated arrival times.

```
Bus (ESP32 GPS tracker)
        ↓  MQTT
Mosquitto broker
        ↓
CASSITRACK Spring Boot backend
  ├── validates & stores in InfluxDB (time-series)
  ├── stores route info in PostgreSQL
  └── exposes REST API on :8080
        ↓
OMNIMOVE app  +  Fleet dashboard
```

---

## Prerequisites

Install these before starting:

| Tool | Version | Download |
|------|---------|----------|
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| Java JDK 17 | 17+ | https://adoptium.net |
| IntelliJ IDEA | Any | Your JetBrains student license |
| Python 3 | 3.9+ | https://python.org (for the simulator) |

---

## Step 1 — Start the Infrastructure

This starts PostgreSQL, InfluxDB, Redis, and Mosquitto (MQTT broker).
You do NOT need to start the Spring Boot app from Docker — run it from IntelliJ.

```bash
# Go to the project folder
cd cassitrack-project

# Start infrastructure only (no Spring Boot app)
docker compose up -d postgres influxdb redis mosquitto

# Check everything is running
docker compose ps

# You should see 4 containers all with status "healthy"
```

### What just started:

| Service | URL / Port | Credentials |
|---------|-----------|-------------|
| PostgreSQL | localhost:5432 | user: `cassitrack` / pass: `cassitrack_dev` / db: `cassitrack` |
| InfluxDB UI | http://localhost:8086 | user: `cassitrack` / pass: `cassitrack_dev` |
| Redis | localhost:6379 | password: `cassitrack_dev` |
| MQTT broker | localhost:1883 | no auth (local dev) |

---

## Step 2 — Open the Backend in IntelliJ

1. Open IntelliJ IDEA
2. **File → Open** → select the `cassitrack-backend` folder
3. IntelliJ will detect the `pom.xml` and import the Maven project
4. Wait for dependencies to download (first time takes a few minutes)
5. Run `CassitrackApplication.java` (green play button)

On first startup, Flyway automatically:
- Creates all tables in PostgreSQL
- Seeds the Bus 16 route and stop data

### Verify it's running:
Open http://localhost:8080/api/swagger-ui in your browser.
You should see the Swagger UI with all API endpoints documented.

---

## Step 3 — Run the GPS Simulator

This simulates buses moving along the Cassino route and publishes
positions to the MQTT broker. Run this in a separate terminal.

```bash
# Install the MQTT library (one time only)
pip install paho-mqtt

# Simulate 1 bus, publish every 30 seconds
python3 gps_simulator.py

# Simulate 3 buses, publish every 15 seconds
python3 gps_simulator.py --buses 3 --interval 15
```

You should see output like:
```
✅ Connected to MQTT broker at localhost:1883
🚌 Simulating 1 bus on Linea 16 — Cassino

📤 MAGNI-001 | lat=41.4917, lon=13.8314 | speed=32.5 km/h | BLE devices=12
📤 MAGNI-001 | lat=41.4938, lon=13.8298 | speed=28.1 km/h | BLE devices=9
```

---

## Step 4 — Test the API

With the simulator running, test these endpoints in your browser
or with curl:

```bash
# Get all active vehicles (the one being simulated)
curl http://localhost:8080/api/v1/vehicles

# Get a specific vehicle
curl http://localhost:8080/api/v1/vehicles/MAGNI-001

# Count active vehicles
curl http://localhost:8080/api/v1/vehicles/count

# Get stop arrivals (stub for now, returns empty list)
curl http://localhost:8080/api/v1/stops/CASSINO-CENTRO/arrivals
```

### Full API docs:
- **Swagger UI:** http://localhost:8080/api/swagger-ui
- **OpenAPI JSON:** http://localhost:8080/api/docs

---

## Project Structure

```
cassitrack-project/
│
├── docker-compose.yml              ← infrastructure (DB, MQTT, Redis)
├── mosquitto/config/mosquitto.conf ← MQTT broker config
├── gps_simulator.py                ← simulates buses (run this for dev)
│
└── cassitrack-backend/             ← Spring Boot project (open in IntelliJ)
    ├── pom.xml                     ← Maven dependencies
    ├── Dockerfile                  ← for Docker deployment
    └── src/main/
        ├── java/it/unicas/cassitrack/
        │   ├── CassitrackApplication.java   ← entry point (run this)
        │   ├── config/
        │   │   ├── MqttConfig.java          ← MQTT broker connection
        │   │   ├── InfluxConfig.java        ← InfluxDB client
        │   │   └── SecurityConfig.java      ← JWT security, CORS
        │   ├── controller/
        │   │   ├── VehicleController.java   ← GET /api/v1/vehicles
        │   │   └── StopController.java      ← GET /api/v1/stops/{id}/arrivals
        │   ├── dto/
        │   │   ├── VehicleStatusDTO.java    ← API response shape
        │   │   ├── StopArrivalDTO.java      ← ETA response shape
        │   │   └── MqttPositionPayload.java ← what the bus sends
        │   ├── model/
        │   │   └── VehiclePosition.java     ← JPA entity (DB table)
        │   ├── mqtt/
        │   │   └── MqttMessageHandler.java  ← receives + stores bus data
        │   ├── repository/
        │   │   └── VehiclePositionRepository.java
        │   └── service/
        │       ├── VehicleService.java      ← business logic
        │       └── VehicleStateCache.java   ← fast in-memory latest positions
        └── resources/
            ├── application.yml              ← all config (DB, MQTT, etc.)
            └── db/migration/
                └── V1__initial_schema.sql   ← DB tables (Flyway runs this)
```

---

## Team Work Allocation

| Sub-team | Files to work on |
|----------|-----------------|
| **Backend — ingestion** | `MqttMessageHandler`, `VehiclePositionRepository`, `InfluxConfig` |
| **Backend — API** | `VehicleController`, `StopController`, `VehicleService` |
| **Backend — ETA/Schedule** | New: `ScheduleAdherenceService`, `ETAService` |
| **Frontend** | New React project consuming `GET /api/v1/vehicles` |
| **Hardware** | ESP32 firmware (separate repo) |
| **Driver app** | Android app publishing to MQTT |

---

## Next Steps (After This MVP)

1. **Import GTFS data** — get the actual Cassino bus schedule from Magni Autoservizi
2. **Build ETAService** — compute real arrival time predictions
3. **Build ScheduleAdherenceService** — detect when buses are late
4. **Build the React map** — show live bus positions on Leaflet.js
5. **Add WebSocket push** — dashboard receives positions in real-time
6. **Contact Magni Autoservizi** — get permission to install ESP32 on the bus

---

## Useful Commands

```bash
# View logs of all services
docker compose logs -f

# View only MQTT broker logs
docker compose logs -f mosquitto

# Stop everything (keeps data)
docker compose down

# Stop everything AND delete all data (fresh start)
docker compose down -v

# Connect to PostgreSQL directly
docker exec -it cassitrack-postgres psql -U cassitrack -d cassitrack

# List tables
\dt

# See recorded positions
SELECT vehicle_id, lat, lon, speed_kmh, received_at
FROM vehicle_positions
ORDER BY received_at DESC LIMIT 10;
```
