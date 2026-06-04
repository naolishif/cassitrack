-- ─────────────────────────────────────────────────────────────────
-- CASSITRACK — Initial Database Schema
-- Migration: V1__initial_schema.sql
-- Flyway runs this automatically on first startup.
-- ─────────────────────────────────────────────────────────────────

-- Enable PostGIS extension (already available in the postgis/postgis image)
CREATE EXTENSION IF NOT EXISTS postgis;

-- ── vehicle_positions ─────────────────────────────────────────────
-- Stores every GPS report received from a bus.
-- Raw time-series data also goes to InfluxDB, but we keep
-- a copy here for route matching and relational queries.
CREATE TABLE IF NOT EXISTS vehicle_positions (
    id                BIGSERIAL PRIMARY KEY,
    vehicle_id        VARCHAR(50)  NOT NULL,
    timestamp         TIMESTAMPTZ  NOT NULL,
    lat               DOUBLE PRECISION NOT NULL,
    lon               DOUBLE PRECISION NOT NULL,
    speed_kmh         DOUBLE PRECISION,
    heading_deg       DOUBLE PRECISION,
    ble_device_count  INTEGER,
    battery_voltage   DOUBLE PRECISION,
    firmware_version  VARCHAR(20),
    matched_route_id  VARCHAR(50),
    schedule_status   VARCHAR(30)  DEFAULT 'UNKNOWN',
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for fast lookups by vehicle + time (most common query pattern)
CREATE INDEX IF NOT EXISTS idx_vp_vehicle_time
    ON vehicle_positions (vehicle_id, timestamp DESC);

-- Index for finding recent positions across all vehicles
CREATE INDEX IF NOT EXISTS idx_vp_received_at
    ON vehicle_positions (received_at DESC);


-- ── routes ────────────────────────────────────────────────────────
-- The bus routes operated by MAGNI Autoservizi in Cassino.
-- Will be populated from GTFS data import.
CREATE TABLE IF NOT EXISTS routes (
    id           VARCHAR(50)  PRIMARY KEY,  -- e.g. "LINEA-16"
    short_name   VARCHAR(20)  NOT NULL,     -- e.g. "16"
    long_name    VARCHAR(200),              -- e.g. "Linea 16 - Campus Folcara"
    description  TEXT,
    color        VARCHAR(6),               -- hex color for map display
    text_color   VARCHAR(6),
    active       BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- Seed the one route we know about: Bus 16 Magni
INSERT INTO routes (id, short_name, long_name, color, text_color)
VALUES ('LINEA-16', '16', 'Linea 16 - Campus Folcara / Stazione', '1E88E5', 'FFFFFF')
ON CONFLICT (id) DO NOTHING;


-- ── stops ─────────────────────────────────────────────────────────
-- Bus stop locations. Will be enriched from GTFS import.
-- Uses PostGIS geometry for spatial queries.
CREATE TABLE IF NOT EXISTS stops (
    id           VARCHAR(50)  PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    lat          DOUBLE PRECISION NOT NULL,
    lon          DOUBLE PRECISION NOT NULL,
    location     GEOGRAPHY(POINT, 4326),   -- PostGIS column for spatial queries
    description  TEXT,
    active       BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- PostGIS spatial index for "find stops near this point" queries
CREATE INDEX IF NOT EXISTS idx_stops_location
    ON stops USING GIST (location);

-- Trigger to auto-populate the PostGIS geometry from lat/lon
CREATE OR REPLACE FUNCTION update_stop_location()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location = ST_SetSRID(ST_MakePoint(NEW.lon, NEW.lat), 4326)::GEOGRAPHY;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stop_location
    BEFORE INSERT OR UPDATE OF lat, lon ON stops
    FOR EACH ROW EXECUTE FUNCTION update_stop_location();

-- Seed known stops on the Bus 16 route (approximate coordinates)
-- These will be replaced by the official GTFS data from Magni
INSERT INTO stops (id, name, lat, lon) VALUES
    ('CASSINO-STAZIONE',   'Cassino Stazione FS',            41.4892, 13.8282),
    ('CASSINO-CENTRO',     'Cassino Centro',                  41.4917, 13.8314),
    ('FOLCARA-CAMPUS',     'Campus UNICAS Folcara',           41.5041, 13.8189),
    ('FOLCARA-VIA',        'Via Folcara',                     41.5020, 13.8200),
    ('CASSINO-OSPEDALE',   'Ospedale Santa Scolastica',       41.4955, 13.8330)
ON CONFLICT (id) DO NOTHING;


-- ── route_stops ───────────────────────────────────────────────────
-- Maps which stops belong to which route and in what order.
CREATE TABLE IF NOT EXISTS route_stops (
    id           BIGSERIAL    PRIMARY KEY,
    route_id     VARCHAR(50)  NOT NULL REFERENCES routes(id),
    stop_id      VARCHAR(50)  NOT NULL REFERENCES stops(id),
    stop_sequence INTEGER      NOT NULL,   -- order of stop on the route
    arrival_offset_sec INTEGER,           -- seconds after trip start (from GTFS)
    UNIQUE (route_id, stop_id, stop_sequence)
);

-- Seed stop sequence for Linea 16
INSERT INTO route_stops (route_id, stop_id, stop_sequence) VALUES
    ('LINEA-16', 'CASSINO-STAZIONE',  1),
    ('LINEA-16', 'CASSINO-CENTRO',    2),
    ('LINEA-16', 'CASSINO-OSPEDALE',  3),
    ('LINEA-16', 'FOLCARA-VIA',       4),
    ('LINEA-16', 'FOLCARA-CAMPUS',    5)
ON CONFLICT DO NOTHING;


-- ── users ─────────────────────────────────────────────────────────
-- Application users (fleet managers, drivers).
-- Passengers using OMNIMOVE are anonymous or registered separately.
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL    PRIMARY KEY,
    tax_id        VARCHAR(50)  NOT NULL UNIQUE, -- National Identity Number / Codice Fiscale
    name          VARCHAR(100) NOT NULL,        -- First name
    surname       VARCHAR(100) NOT NULL,        -- Last name
    email         VARCHAR(200) NOT NULL UNIQUE, -- Login email identifier
    password_hash VARCHAR(200) NOT NULL,        -- bcrypt hash
    role          VARCHAR(20)  NOT NULL DEFAULT 'DRIVER', -- Can be FLEET_MANAGER or DRIVER
    telephone     VARCHAR(20)  NOT NULL UNIQUE  -- Contact telephone number
);

-- Seed a default fleet manager user (password: "admin123" — CHANGE IN PRODUCTION)
-- bcrypt hash for "admin123" is: $2a$12$LQv3c1yqBwEHXMKFNlqLXeB8cjvtWdFxkOl7A6C6GcH.bFvg5JMuO
INSERT INTO users (tax_id, name, surname, email, password_hash, role, telephone)
SELECT 'TAXID123456', 'AdminName', 'AdminSurname', 'admin@unicas.it',
       '$2a$12$CGnvVCYJ52qUzTH7oTvt5eBeHLhZ/ZOXE6eKg3hkiBHRw8odm6XLa',
       'FLEET_MANAGER',
       '444444444'
    WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE tax_id = 'TAXID123456' OR email = 'admin@unicas.it'
);


-- ── alerts ────────────────────────────────────────────────────────
-- Fleet alerts generated by the AlertService.
CREATE TABLE IF NOT EXISTS alerts (
    id           BIGSERIAL    PRIMARY KEY,
    vehicle_id   VARCHAR(50),
    route_id     VARCHAR(50),
    alert_type   VARCHAR(50)  NOT NULL,  -- ROUTE_DEVIATION, COMM_LOSS, etc.
    severity     VARCHAR(20)  NOT NULL DEFAULT 'WARNING',
    message      TEXT         NOT NULL,
    resolved     BOOLEAN DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_alerts_vehicle
    ON alerts (vehicle_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_unresolved
    ON alerts (resolved, created_at DESC) WHERE resolved = FALSE;
