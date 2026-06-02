-- =================================================================
-- 1. TABELLE STATICHE (Graceful Degradation)
-- =================================================================

CREATE TABLE IF NOT EXISTS routes (
                                      id          VARCHAR(50)  PRIMARY KEY,
    short_name  VARCHAR(20)  NOT NULL,
    long_name   VARCHAR(150) NOT NULL,
    description TEXT
    );

CREATE TABLE IF NOT EXISTS trips (
                                     id            VARCHAR(50)  PRIMARY KEY,
    route_id      VARCHAR(50)  NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    service_type  VARCHAR(20)  NOT NULL DEFAULT 'WEEKDAY',
    headsign      VARCHAR(100)
    );

CREATE TABLE IF NOT EXISTS stops (
                                     id        VARCHAR(50)      PRIMARY KEY,
    name      VARCHAR(150)     NOT NULL,
    latitude  DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
    );

CREATE TABLE IF NOT EXISTS scheduled_stops (
                                               id               BIGSERIAL    PRIMARY KEY,
                                               trip_id          VARCHAR(50)  NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    stop_id          VARCHAR(50)  NOT NULL REFERENCES stops(id) ON DELETE CASCADE,
    stop_sequence    INTEGER      NOT NULL,
    arrival_seconds  INTEGER      NOT NULL,
    UNIQUE (trip_id, stop_sequence)
    );

-- Indici per ottimizzazione ricerche
CREATE INDEX IF NOT EXISTS idx_om_trips_route ON trips(route_id);
CREATE INDEX IF NOT EXISTS idx_om_ss_trip ON scheduled_stops(trip_id);

-- =================================================================
-- 2. TABELLE UTENTI E PREFERITI (Mappate sulla Entity Java)
-- =================================================================

CREATE TABLE IF NOT EXISTS users (
                                     id       BIGSERIAL    PRIMARY KEY, -- BIGSERIAL mappa perfettamente il Long con IDENTITY di Hibernate
                                     email    VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name     VARCHAR(100) NOT NULL,
    role     VARCHAR(20)  NOT NULL DEFAULT 'TRAVELLER' -- Default sincronizzato con Java
    );

-- Preferiti delle Fermate (Aggiornato il vincolo FK su users.id)
CREATE TABLE IF NOT EXISTS favorite_stops (
                                              id_favorite_stops INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                              user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stop_id           VARCHAR(50) NOT NULL REFERENCES stops(id) ON DELETE CASCADE,
    UNIQUE (user_id, stop_id)
    );

-- Preferiti delle Corse/Viaggi (Aggiornato il vincolo FK su users.id)
CREATE TABLE IF NOT EXISTS favorite_trips (
                                              id_favorite_trips INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                              user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trip_id           VARCHAR(50) NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    UNIQUE (user_id, trip_id)
    );

CREATE INDEX IF NOT EXISTS idx_fav_stops_user ON favorite_stops(user_id);
CREATE INDEX IF NOT EXISTS idx_fav_trips_user ON favorite_trips(user_id);