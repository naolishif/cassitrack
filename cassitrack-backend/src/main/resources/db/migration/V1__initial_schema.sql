-- ────────────────────────────────────────────────────────────────
-- CASSITRACK
-- V1__initial_schema.sql
-- ────────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================================
-- ROUTES
-- ============================================================

CREATE TABLE IF NOT EXISTS routes (
    id           VARCHAR(50) PRIMARY KEY,
    short_name   VARCHAR(20) NOT NULL,
    long_name    VARCHAR(200),
    description  TEXT,
    color        VARCHAR(6),
    text_color   VARCHAR(6),
    active       BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- STOPS
-- ============================================================

CREATE TABLE IF NOT EXISTS stops (
    id           VARCHAR(50) PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    lat          DOUBLE PRECISION NOT NULL,
    lon          DOUBLE PRECISION NOT NULL,
    location     GEOGRAPHY(POINT, 4326),
    description  TEXT,
    active       BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_stops_location
    ON stops USING GIST(location);

CREATE OR REPLACE FUNCTION update_stop_location()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location =
        ST_SetSRID(
            ST_MakePoint(NEW.lon, NEW.lat),
            4326
        )::GEOGRAPHY;

RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stop_location
    BEFORE INSERT OR UPDATE OF lat, lon
    ON stops
    FOR EACH ROW
EXECUTE FUNCTION update_stop_location();

-- ============================================================
-- BUSES
-- ============================================================

CREATE TABLE IF NOT EXISTS buses (
    bus_id              INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    targa               VARCHAR(20) NOT NULL UNIQUE,
    numero_posti        INTEGER NOT NULL,
    posto_disabili      BOOLEAN NOT NULL DEFAULT FALSE,
    disponibile         BOOLEAN NOT NULL DEFAULT TRUE,
    current_vehicle_id  VARCHAR(50) UNIQUE
);

-- ============================================================
-- TRIPS
-- ============================================================

CREATE TABLE IF NOT EXISTS trips (
    id           VARCHAR(50) PRIMARY KEY,

    route_id     VARCHAR(50) NOT NULL
        REFERENCES routes(id)
        ON DELETE CASCADE,

    bus_id       INTEGER NOT NULL
        REFERENCES buses(bus_id)
);

CREATE INDEX IF NOT EXISTS idx_trips_route
    ON trips(route_id);

CREATE INDEX IF NOT EXISTS idx_trips_bus
    ON trips(bus_id);

-- ============================================================
-- SCHEDULED STOPS
-- ============================================================

CREATE TABLE IF NOT EXISTS scheduled_stops (
    id               BIGSERIAL PRIMARY KEY,

    trip_id          VARCHAR(50) NOT NULL
        REFERENCES trips(id)
        ON DELETE CASCADE,

    stop_id          VARCHAR(50) NOT NULL
        REFERENCES stops(id)
        ON DELETE CASCADE,

    stop_sequence    INTEGER NOT NULL,

    arrival_seconds  INTEGER NOT NULL,

    UNIQUE (trip_id, stop_sequence)
);

CREATE INDEX IF NOT EXISTS idx_ss_trip
    ON scheduled_stops(trip_id);

-- ============================================================
-- USERS
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,

    tax_id        VARCHAR(50) NOT NULL UNIQUE,

    name          VARCHAR(100) NOT NULL,

    surname       VARCHAR(100) NOT NULL,

    email         VARCHAR(200) NOT NULL UNIQUE,

    password_hash VARCHAR(200) NOT NULL,

    role          VARCHAR(30) NOT NULL,

    telephone     VARCHAR(20) NOT NULL UNIQUE
);