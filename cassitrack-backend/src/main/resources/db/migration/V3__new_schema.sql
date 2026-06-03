-- =================================================================
-- 1. PULIZIA TABELLE OBSOLETE E VECCHIE STRUTTURE
-- =================================================================
DROP TABLE IF EXISTS alerts CASCADE;
DROP TABLE IF EXISTS route_stops CASCADE;
DROP TABLE IF EXISTS scheduled_stops CASCADE;
DROP TABLE IF EXISTS vehicle_positions CASCADE;
DROP TABLE IF EXISTS trips CASCADE;
DROP TABLE IF EXISTS buses CASCADE;

-- =================================================================
-- 2. STRUTTURA DELLE NUOVE TABELLE (DDL)
-- =================================================================

-- Tabella TRIPS (N:1 con la tabella routes esistente)
CREATE TABLE IF NOT EXISTS trips (
                                     id            VARCHAR(50)  PRIMARY KEY,  -- es. 'T-16-0800'
    route_id      VARCHAR(50)  NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    service_type  VARCHAR(20)  NOT NULL DEFAULT 'WEEKDAY',
    headsign      VARCHAR(100)               -- Destinazione (es. "Campus Folcara")
    );

CREATE INDEX IF NOT EXISTS idx_trips_route ON trips(route_id);

-- Tabella SCHEDULED_STOPS (Tabella di giunzione tra Trips e Stops con orari)
CREATE TABLE IF NOT EXISTS scheduled_stops (
                                               id               BIGSERIAL    PRIMARY KEY,
                                               trip_id          VARCHAR(50)  NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    stop_id          VARCHAR(50)  NOT NULL REFERENCES stops(id) ON DELETE CASCADE,
    stop_sequence    INTEGER      NOT NULL,
    arrival_seconds  INTEGER      NOT NULL,   -- Secondi passati dalla mezzanotte
    UNIQUE (trip_id, stop_sequence)           -- Impedisce doppioni di sequenza nella stessa corsa
    );

CREATE INDEX IF NOT EXISTS idx_ss_trip ON scheduled_stops(trip_id);

-- Tabella BUSES (Anagrafica della flotta dei veicoli)
CREATE TABLE IF NOT EXISTS buses (
                                     bus_id             INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                     targa              VARCHAR(20) NOT NULL UNIQUE,
    numero_posti       INT NOT NULL,
    posto_disabili     BOOLEAN NOT NULL DEFAULT FALSE,
    disponibile        BOOLEAN NOT NULL DEFAULT TRUE, -- 🔍 Colonna corretta
    current_vehicle_id VARCHAR(50) UNIQUE
    );

-- =================================================================
-- 3. POPOLAMENTO DATI INIZIALI (SEED DATA)
-- =================================================================

-- Inserimento della flotta Autobus
INSERT INTO buses (targa, numero_posti, posto_disabili, disponibile, current_vehicle_id) VALUES -- 🔍 Corretto anche qui!
                                                                                                ('AA123BB', 85, TRUE, TRUE, 'MAGNI-001'),
                                                                                                ('CC456DD', 85, TRUE, FALSE, NULL),
                                                                                                ('EE789FF', 52, TRUE, TRUE, 'MAGNI-002'),
                                                                                                ('GG012HH', 52, TRUE, TRUE, NULL),
                                                                                                ('JJ345KK', 22, FALSE, TRUE, NULL)
    ON CONFLICT (targa) DO NOTHING;

-- Inserimento delle Corse (Trips) per la Linea 16
INSERT INTO trips (id, route_id, service_type, headsign) VALUES
                                                             ('T-16-0800', 'LINEA-16', 'WEEKDAY', 'Campus Folcara'),
                                                             ('T-16-0830', 'LINEA-16', 'WEEKDAY', 'Campus Folcara'),
                                                             ('T-16-0900', 'LINEA-16', 'WEEKDAY', 'Campus Folcara')
    ON CONFLICT (id) DO NOTHING;

-- Inserimento degli orari pianificati (Scheduled Stops)
-- Corsa delle 08:00
INSERT INTO scheduled_stops (trip_id, stop_id, stop_sequence, arrival_seconds) VALUES
                                                                                   ('T-16-0800', 'CASSINO-STAZIONE',  1, 28800),
                                                                                   ('T-16-0800', 'CASSINO-CENTRO',    2, 28920),
                                                                                   ('T-16-0800', 'CASSINO-OSPEDALE',  3, 29100),
                                                                                   ('T-16-0800', 'FOLCARA-VIA',       4, 29520),
                                                                                   ('T-16-0800', 'FOLCARA-CAMPUS',    5, 29700)
    ON CONFLICT (trip_id, stop_sequence) DO NOTHING;

-- Corsa delle 08:30
INSERT INTO scheduled_stops (trip_id, stop_id, stop_sequence, arrival_seconds) VALUES
                                                                                   ('T-16-0830', 'CASSINO-STAZIONE',  1, 30600),
                                                                                   ('T-16-0830', 'CASSINO-CENTRO',    2, 30720),
                                                                                   ('T-16-0830', 'CASSINO-OSPEDALE',  3, 30900),
                                                                                   ('T-16-0830', 'FOLCARA-VIA',       4, 31320),
                                                                                   ('T-16-0830', 'FOLCARA-CAMPUS',    5, 31500)
    ON CONFLICT (trip_id, stop_sequence) DO NOTHING;