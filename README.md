# CASSITRACK + OMNIMOVE
### Real-Time Smart Mobility Platform for Cassino
**University of Cassino and Southern Lazio (UNICAS) — 2025/2026**
**CINI Smart City University Challenge — 10th Edition**

---

## What Is This?
Two interconnected systems built for Cassino's **Bus 16** (MAGNI Autoservizi),

CASSITRACK is a real-time bus fleet monitoring system built for **MAGNI Autoservizi Linea 16** 
— the bus that connects Cassino city centre to the UNICAS Campus via Via Folcara.

**OMNIMOVE** is the multimodal journey planning layer built on top of CASSITRACK, 
allowing passengers to compare Bus, Bike, Scooter, and Walking options with real-time data, 
cost estimates, and Green Index CO₂ scoring.

The motivation is personal and real: Bus 16 has **zero live tracking**.
Passengers stand at the stop with no idea if the bus is 2 minutes away or 20. This system fixes that.

CASSITRACK is the backend of the OMNIMOVE smart mobility platform.  
It receives GPS positions from buses (via MQTT), stores them, and  
exposes a REST API so OMNIMOVE and the fleet dashboard can show live bus locations and estimated arrival times.
---

| System | What it does | Port |
|---|---|---|
| **CASSITRACK** | Real-time fleet monitoring — live bus positions, ETA, schedule adherence | `8080` |
| **OMNIMOVE** | Multimodal journey planner — bus, walk, bike, scooter with Green Index CO₂ scoring | `8081` |



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

Make sure you have these installed before starting:

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (must be running)
- [Java 21 JDK](https://adoptium.net/) (not 17, not 25 — exactly **21**)
- [Python 3.x](https://www.python.org/downloads/) (install with "Add to PATH" checked)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community or Ultimate)
- Git

---
## Project Structure

```
cassitrack-complete-v2/
├── cassitrack-backend/        # Spring Boot — fleet monitoring API (port 8080)
├── omnimove-backend/          # Spring Boot — journey planner API (port 8081)
├── cassitrack-pwa/            # PWA for mobile (served on port 3000)
├── mosquitto/                 # MQTT broker config
├── gps_simulator.py           # Python GPS simulator for Bus 16
├── cassitrack-map-v4.html     # Main fleet map (open in browser)
├── omnimove-login.html        # OMNIMOVE login page
└── docker-compose.yml         # All infrastructure in one file
```


## How to Run — Step by Step

### ⚠️ Always follow this exact order
Clone the repository

```bash
git clone https://github.com/naolishif/cassitrack.git
cd cassitrack
```

### Step 1 — Start Docker Infrastructure

Open Docker Desktop first and wait for the whale icon to appear in the taskbar. Then in PowerShell:

```bash
cd cassitrack-backend
docker compose up -d postgres influxdb redis mosquitto
```

```bash
cd ..
cd omnimove-backend
docker compose up -d omnimove-postgres omnimove-influxdb omnimove-redis
```

Verify all containers are healthy:

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

1. Open IntelliJ → Run → **Edit Configurations**
2. Select **OmnimoveApplication**
3. Click **Modify options → Environment variables**
4. Add: `ANTHROPIC_API_KEY=your_key_here`
5. Click OK

 ```
   Name:  ANTHROPIC_API_KEY
   Value: sk-ant-api03-your-key-here
   ```

> Get a free API key at [console.anthropic.com](https://console.anthropic.com)


---

### Step 3 — Run the Backend in IntelliJ

1. File → Open → select the `cassitrack-complete-v2` folder
2. Wait for Maven to download dependencies (first time takes a few minutes)
3. Make sure the **Project SDK is set to Java 21**: File → Project Structure → Project → SDK

---

In IntelliJ, run both applications (in this order):

1. Run **CassitrackApplication** (port 8080)
2. Run **OmnimoveApplication** (port 8081)

Verify they started:
- CASSITRACK Swagger: http://localhost:8080/swagger-ui.html
- OMNIMOVE Swagger: http://localhost:8081/swagger-ui.html

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

### Step 5 — Open the frontend

Serve the HTML files with a local HTTP server (required — do not open directly as `file://`):

```powershell
python -m http.server 3000
```

Then open in browser:
- **CASSITRACK map**: http://localhost:3000/cassitrack-map-v4.html
- **OMNIMOVE**: http://localhost:3000/omnimove-login.html

---

## Startup Order (Important)

Always start in this exact order:

```
1. Docker Desktop
2. docker compose up -d
3. IntelliJ → CassitrackApplication
4. IntelliJ → OmnimoveApplication
5. python gps_simulator.py
6. python -m http.server 3000
7. Open browser
```

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

## Common Issues

| Problem | Fix |
|---|---|
| Spring Boot crashes on start | Docker must be running first — PostgreSQL is inside Docker |
| Map shows "Backend offline" | Check that CassitrackApplication is running on port 8080 |
| AI chat says "Sorry, I could not process your request" | Check ANTHROPIC_API_KEY is set in IntelliJ Run Configurations |
| Java compile error `Unexpected token 'class'` | You are using Java 25. Switch to Java 21 in Project Structure |
| No buses on map | Run `python gps_simulator.py` |
| Phone can't access the app on LAN | Run `ipconfig` in PowerShell, get your laptop IP, update `API` variable in `cassitrack-map-v4.html` and `CASSITRACK_API` in `cassitrack-pwa/app.js` |
| `ANTHROPIC_API_KEY` not found | Environment variables set in PowerShell are invisible to IntelliJ — set them in Run Configurations instead |

---

## Tech Stack

**Backend**
- Java 21 + Spring Boot 3.2.5
- PostgreSQL + PostGIS (spatial queries)
- InfluxDB (GPS time-series data)
- Redis (real-time cache)
- Eclipse Mosquitto (MQTT broker)
- Flyway (database migrations)

**Frontend**
- Leaflet.js (interactive maps)
- Progressive Web App (PWA) with service worker
- Vanilla HTML/CSS/JavaScript

**Data Standards**
- GTFS Realtime (Protocol Buffers)
- MQTT for GPS telemetry ingestion

**APIs**
- Anthropic Claude API (AI assistant)
- Open-Meteo (weather)
- Elerent (bike/scooter availability)

---

## API Endpoints
Both backends expose Swagger UI for interactive API exploration:

- CASSITRACK: http://localhost:8080/swagger-ui.html
- OMNIMOVE: http://localhost:8081/swagger-ui.html


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
 | Tomcat error on startup | Database miss-match | Run 'docker compose down -v' to reset all data, then start again. If necessary, in application.yml change ddl-auto:update to create-drop |

---

## Phase 2 — Planned Features

- Real ESP32 hardware installed on MAGNI buses
- Elerent real API integration (live vehicle locations)
- Driver Android app (voluntary GPS tracking)
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
