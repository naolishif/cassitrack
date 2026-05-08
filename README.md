# CASSITRACK + OMNIMOVE
### Real-Time Smart Mobility Platform for Cassino
**University of Cassino and Southern Lazio (UNICAS) — 2025/2026**
**CINI Smart City University Challenge — 10th Edition**

---

## What Is This?

CASSITRACK is a real-time bus fleet monitoring system built for **MAGNI Autoservizi Linea 16** 
— the bus that connects Cassino city centre to the UNICAS Engineering Campus via Via Folcara.

**OMNIMOVE** is the multimodal journey planning layer built on top of CASSITRACK, 
allowing passengers to compare Bus, Bike, Scooter, and Walking options with real-time data, 
cost estimates, and Green Index CO₂ scoring.

The motivation is personal and real: Bus 16 has **zero live tracking**.
Passengers stand at the stop with no idea if the bus is 2 minutes away or 20. This system fixes that.

CASSITRACK is the backend of the OMNIMOVE smart mobility platform.  
It receives GPS positions from buses (via MQTT), stores them, and  
exposes a REST API so OMNIMOVE and the fleet dashboard can show live bus locations and estimated arrival times.
---

## System Architecture

```
Bus (ESP32 GPS tracker)  ←→  GPS Simulator (Python)
         ↓  LoRa / MQTT
Eclipse Mosquitto Broker
         ↓
Spring Boot Backend (port 8080)
  ├── PostgreSQL + PostGIS   (routes, stops, schedules)
  ├── InfluxDB               (GPS time-series history)
  ├── Redis                  (live position cache)
  ├── Schedule Adherence     (on time / late detection)
  ├── ETA Service            (arrival predictions)
  ├── AI Orchestration       (Claude API + RAG pattern)
  ├── GTFS Realtime Feed     (national NAP standard)
  └── Journey Planner        (multimodal OMNIMOVE)
         ↓
REST API (OpenAPI 3.0 documented)
         ↓
├── cassitrack-map-v4.html   (desktop live map)
└── cassitrack-pwa/          (mobile PWA, installable)
```

---

## Features

| Feature | Description | Status |
|---|---|---|
| Live Bus Tracking | GPS positions every 15 seconds via MQTT | ✅ Working |
| Schedule Adherence | Green / Amber / Red bus status | ✅ Working |
| ETA Prediction | "Bus arrives in 4 min" at your stop | ✅ Working |
| Crowd Estimation | BLE device count → passenger density | ✅ Working |
| AI Chatbot | Natural language queries in EN + IT | ✅ Working |
| GTFS Realtime Feed | Standard feed for Italy national NAP | ✅ Working |
| Journey Planner | Bus / Bike / Scooter / Walk comparison | ✅ Working |
| Green Index | CO₂ score per journey option | ✅ Working |
| Elerent Integration | Local bike and e-scooter pricing | ✅ Working |
| Mobile PWA | Installable on iPhone via Safari | ✅ Working |
| REST API | OpenAPI 3.0 fully documented | ✅ Working |

---

## Prerequisites

| Tool | Version | Download |
|---|---|---|
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| Java JDK 17 | 17 LTS | https://adoptium.net |
| IntelliJ IDEA | Any | JetBrains student license |
| Python 3 | 3.9+ | https://python.org |

---

## How to Run — Step by Step

### ⚠️ Always follow this exact order

---

### Step 1 — Start Docker Infrastructure

Open Docker Desktop first and wait for the whale icon to appear in the taskbar. Then in PowerShell:

```bash
cd Desktop\cassitrack-fresh
docker compose up -d postgres influxdb redis mosquitto
```

Verify all 4 containers are healthy:

```bash
docker compose ps
```

Expected output:
```
cassitrack-postgres    running (healthy)
cassitrack-influxdb    running (healthy)
cassitrack-redis       running (healthy)
cassitrack-mosquitto   running (healthy)
```

| Service | Port | Credentials |
|---|---|---|
| PostgreSQL | 5432 | user: `cassitrack` / pass: `cassitrack_dev` / db: `cassitrack` |
| InfluxDB | 8086 | user: `cassitrack` / pass: `cassitrack_dev` / org: `unicas` |
| Redis | 6379 | password: `cassitrack_dev` |
| MQTT Broker | 1883 | no auth (local dev) |

---

### Step 2 — Set the Anthropic API Key in IntelliJ

The AI chatbot requires an Anthropic API key. Never put it in a file — always set it as an environment variable inside IntelliJ.

1. Go to **Run → Edit Configurations**
2. Select **CassitrackApplication**
3. Click **Modify options → Environment variables**
4. Click **+** and add:
   ```
   Name:  ANTHROPIC_API_KEY
   Value: sk-ant-api03-your-key-here
   ```
5. Click **OK → Apply → OK**

Get a key from: https://console.anthropic.com

---

### Step 3 — Run the Backend in IntelliJ

1. Open IntelliJ IDEA
2. **File → Open** → select the `cassitrack-backend` folder
3. Wait for Maven to download dependencies
4. Run `CassitrackApplication.java` (green ▶ button)

On first startup, Flyway automatically creates all database tables and seeds the Bus 16 route, stops, and timetable data.

Verify it is running:
```
http://localhost:8080/api/swagger-ui
```

You should see the full Swagger UI with all documented endpoints.

---

### Step 4 — Run the GPS Simulator

Open a **new PowerShell window** and run:

```bash
# Install dependency (first time only)
pip install paho-mqtt

# Simulate 2 buses, publish every 15 seconds
cd Desktop\cassitrack-fresh
python gps_simulator.py --buses 2 --interval 15
```

Expected output:
```
✅ Connected to MQTT broker at localhost:1883
🚌 Simulating 2 buses on Linea 16 — Cassino

📤 MAGNI-001 | lat=41.4917 | speed=32.5 km/h | BLE=12
📤 MAGNI-002 | lat=41.5020 | speed=0.0 km/h  | BLE=7
```

Leave this window open while developing.

---

### Step 5 — Open the Map

**Desktop browser:**
Open `cassitrack-map-v4.html` directly in Chrome. You should see:
- 🟢 Green dot in header — backend connected
- Bus icons on the Cassino map moving in real time
- 4 tabs: Fleet, Journey Planner, ETA, AI Chat

---

### Step 6 — Run the Mobile PWA (Optional)

Open a **third PowerShell window**:

```bash
cd Desktop\cassitrack-fresh\cassitrack-pwa
python -m http.server 3000
```

Find your laptop IP address:
```bash
ipconfig
# Look for IPv4 Address under Wi-Fi adapter
```

Update the API URL in `cassitrack-pwa/app.js` line 1:
```javascript
const API = 'http://YOUR-IP-HERE:8080/api/v1';
```

Also add your IP to the CORS allowed origins in `SecurityConfig.java`:
```java
config.setAllowedOrigins(List.of(
    "http://localhost:3000",
    "null",
    "http://YOUR-IP-HERE:3000"
));
```

Restart Spring Boot, then on your iPhone open Safari and go to:
```
http://YOUR-IP-HERE:3000
```

To install: **Share button → Add to Home Screen → Add**

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/vehicles` | All active bus positions |
| GET | `/api/v1/vehicles/{id}` | Single vehicle detail |
| GET | `/api/v1/stops/{id}/arrivals` | ETA at a specific stop |
| POST | `/api/v1/ai/chat` | AI chatbot query |
| POST | `/api/v1/journeys/search` | Multimodal journey planning |
| GET | `/api/v1/feed/gtfs-rt` | GTFS Realtime feed (Protobuf) |
| GET | `/api/v1/feed/gtfs-rt/debug` | GTFS Realtime (human readable) |
| GET | `/api/swagger-ui` | Full API documentation |

### AI Chat Example

```bash
curl -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "When does the next bus reach Campus?", "language": "en"}'
```

### Journey Planner Example

```bash
curl -X POST http://localhost:8080/api/v1/journeys/search \
  -H "Content-Type: application/json" \
  -d '{
    "origin_lat": 41.5020,
    "origin_lon": 13.8200,
    "origin_name": "Via Folcara",
    "dest_lat": 41.4892,
    "dest_lon": 13.8282,
    "dest_name": "Cassino Stazione FS"
  }'
```

---

## Known Bus Stops — Linea 16

| Stop ID | Name | Coordinates |
|---|---|---|
| CASSINO-STAZIONE | Cassino Stazione FS | 41.4892, 13.8282 |
| CASSINO-CENTRO | Cassino Centro | 41.4917, 13.8314 |
| CASSINO-OSPEDALE | Ospedale S. Scolastica | 41.4955, 13.8330 |
| FOLCARA-VIA | Via Folcara | 41.5020, 13.8200 |
| FOLCARA-CAMPUS | Campus UNICAS Folcara | 41.5041, 13.8189 |

---

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
Full API docs:
- **Swagger UI:** http://localhost:8080/api/swagger-ui
- **OpenAPI JSON:** http://localhost:8080/api/docs
---
## Project Structure

```
cassitrack-fresh/
├── docker-compose.yml                    ← Infrastructure
├── gps_simulator.py                      ← Bus GPS simulator
├── mosquitto/config/mosquitto.conf       ← MQTT broker config
├── cassitrack-map-v4.html                ← Desktop live map
├── cassitrack-pwa/                       ← Mobile PWA
│   ├── index.html                        ← App shell
│   ├── app.js                            ← All app logic
│   ├── style.css                         ← Mobile-first styles
│   ├── manifest.json                     ← PWA install config
│   └── sw.js                             ← Service worker (offline)
└── cassitrack-backend/                   ← Spring Boot backend
    ├── pom.xml                           ← Maven dependencies
    └── src/main/java/it/unicas/cassitrack/
        ├── config/
        │   ├── MqttConfig.java           ← MQTT connection
        │   ├── InfluxConfig.java         ← InfluxDB client
        │   └── SecurityConfig.java       ← JWT + CORS
        ├── controller/
        │   ├── VehicleController.java    ← /api/v1/vehicles
        │   ├── StopController.java       ← /api/v1/stops
        │   ├── AiController.java         ← /api/v1/ai/chat
        │   ├── JourneyController.java    ← /api/v1/journeys
        │   ├── GtfsRealtimeController.java ← /api/v1/feed
        │   └── AuthController.java       ← /api/v1/auth
        ├── service/
        │   ├── VehicleService.java       ← Vehicle business logic
        │   ├── VehicleStateCache.java    ← Redis live cache
        │   ├── ETAService.java           ← Arrival prediction
        │   ├── ScheduleAdherenceService.java ← Late detection
        │   ├── AiOrchestrationService.java   ← Claude API + RAG
        │   ├── GtfsRealtimeService.java  ← GTFS-RT feed builder
        │   ├── JourneyPlannerService.java ← Multimodal planner
        │   ├── GreenIndexService.java    ← CO₂ scoring
        │   └── RouteMatchingService.java ← Haversine distance
        ├── model/
        │   ├── VehiclePosition.java      ← JPA entity
        │   ├── ScheduledStop.java        ← Stop entity
        │   └── User.java                 ← Auth entity
        ├── dto/                          ← API request/response shapes
        ├── mqtt/
        │   └── MqttMessageHandler.java   ← Processes incoming GPS
        └── resources/
            ├── application.yml           ← All configuration
            └── db/migration/
                ├── V1__initial_schema.sql ← Tables
                └── V2__scheduled_stops.sql ← Bus 16 timetable
```

---

## Journey Planner — Transport Modes

| Mode | Provider | Pricing | Green Index |
|---|---|---|-------------|
| 🚌 Bus | Magni Autoservizi | €1.00 flat | ~85/100     |
| 🚲 Bike | Elerent (Cassino) | €0.50 unlock + €0.15/min | 100/100     |
| 🛴 E-Scooter | Elerent (Cassino) | €1.00 unlock + €0.25/min | 0/100       |
| 🚶 Walk | — | Free | 100/100     |

Green Index based on EEA CO₂ emission factors per passenger-km.

---

## Useful Commands

```bash
# View logs of all services
docker compose logs -f

# View only a specific service
docker compose logs -f mosquitto

# Stop everything (keeps data)
docker compose down

# Stop everything AND delete all data (fresh start)
docker compose down -v

# Connect to PostgreSQL directly
docker exec -it cassitrack-postgres psql -U cassitrack -d cassitrack

# See live bus positions in the database
SELECT vehicle_id, lat, lon, speed_kmh, received_at
FROM vehicle_positions
ORDER BY received_at DESC LIMIT 10;

# Check GTFS Realtime feed (human readable)
curl http://localhost:8080/api/v1/feed/gtfs-rt/debug
```

---

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `Connection to localhost:5432 refused` | Docker not running | Start Docker Desktop first |
| `Backend offline` on phone | Wrong IP in app.js | Run `ipconfig` and update `app.js` |
| AI says "could not process" | API key not set | Add `ANTHROPIC_API_KEY` in IntelliJ Run Configurations |
| No buses on map | Simulator not running | Run `python gps_simulator.py --buses 2` |
| Phone cannot reach backend | CORS not configured | Add your IP to `SecurityConfig.java` allowed origins |
| Container name conflict | Old containers still registered | Run `docker compose down` then `docker compose up -d` |

---

## Phase 2 — Planned Features

- Real ESP32 hardware installed on MAGNI buses
- Elerent real API integration (live vehicle locations)
- Driver Android app (voluntary GPS tracking)
- Weather-aware journey suggestions
- SIRI Vehicle Monitoring for Lazio RAP
- React PWA upgrade with TypeScript
- Contact Magni Autoservizi for official GTFS data

---

## Team

University of Cassino and Southern Lazio (UNICAS)
Telecommunications Engineering — 2025/2026
Distributed Programming Course

**Competition:** CINI Smart City University Challenge, 10th Edition
**Conference:** I-CiTies 2026, University of Brescia + Polytechnic University of Milan

---

## Repository

```
https://github.com/naolishif/cassitrack
```
