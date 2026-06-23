-- =================================================================
-- V(N)__add_buses_and_trip_bus_ref.sql
-- OmniMove — Aggiunta tabella buses e riferimento in trips
-- =================================================================

-- 1. Creazione tabella buses
CREATE TABLE IF NOT EXISTS buses (
    bus_id                    SERIAL    PRIMARY KEY,
    license_plate             VARCHAR(20)  NOT NULL UNIQUE,
    number_seats              INTEGER      NOT NULL,
    place_disable_people      BOOLEAN      NOT NULL DEFAULT FALSE,
    available                 BOOLEAN      NOT NULL DEFAULT TRUE,
    current_vehicle_id        VARCHAR(50)  UNIQUE
    );

-- 2. Aggiunta colonna bus_id alla tabella trips
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS bus_id INTEGER REFERENCES buses(bus_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_om_trips_bus ON trips(bus_id);