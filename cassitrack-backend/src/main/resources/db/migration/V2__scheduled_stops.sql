-- ── scheduled_stops ───────────────────────────────────────────
-- The Bus 16 timetable from the paper schedule at the stop.
-- Times are stored as seconds after midnight for easy arithmetic.
-- Example: 08:45 = 8*3600 + 45*60 = 31500 seconds

CREATE TABLE IF NOT EXISTS scheduled_stops (
                                               id               BIGSERIAL PRIMARY KEY,
                                               route_id         VARCHAR(50)  NOT NULL,
    stop_id          VARCHAR(50)  NOT NULL,
    stop_sequence    INTEGER      NOT NULL,
    arrival_seconds  INTEGER      NOT NULL,
    service_type     VARCHAR(20)  NOT NULL DEFAULT 'WEEKDAY'
    );

CREATE INDEX IF NOT EXISTS idx_ss_route_service
    ON scheduled_stops (route_id, service_type);

-- ── Seed: Bus 16 weekday timetable ───────────────────────────
-- Trip 1: 08:00 departure from Stazione
INSERT INTO scheduled_stops
(route_id, stop_id, stop_sequence, arrival_seconds, service_type)
VALUES
    ('LINEA-16','CASSINO-STAZIONE',  1, 28800, 'WEEKDAY'),  -- 08:00
    ('LINEA-16','CASSINO-CENTRO',    2, 28920, 'WEEKDAY'),  -- 08:02
    ('LINEA-16','CASSINO-OSPEDALE',  3, 29100, 'WEEKDAY'),  -- 08:05
    ('LINEA-16','FOLCARA-VIA',       4, 29520, 'WEEKDAY'),  -- 08:12
    ('LINEA-16','FOLCARA-CAMPUS',    5, 29700, 'WEEKDAY');  -- 08:15

-- Trip 2: 08:30 departure
INSERT INTO scheduled_stops
(route_id, stop_id, stop_sequence, arrival_seconds, service_type)
VALUES
    ('LINEA-16','CASSINO-STAZIONE',  1, 30600, 'WEEKDAY'),  -- 08:30
    ('LINEA-16','CASSINO-CENTRO',    2, 30720, 'WEEKDAY'),  -- 08:32
    ('LINEA-16','CASSINO-OSPEDALE',  3, 30900, 'WEEKDAY'),  -- 08:35
    ('LINEA-16','FOLCARA-VIA',       4, 31320, 'WEEKDAY'),  -- 08:42
    ('LINEA-16','FOLCARA-CAMPUS',    5, 31500, 'WEEKDAY');  -- 08:45

-- Trip 3: 09:00 departure
INSERT INTO scheduled_stops
(route_id, stop_id, stop_sequence, arrival_seconds, service_type)
VALUES
    ('LINEA-16','CASSINO-STAZIONE',  1, 32400, 'WEEKDAY'),  -- 09:00
    ('LINEA-16','CASSINO-CENTRO',    2, 32520, 'WEEKDAY'),  -- 09:02
    ('LINEA-16','CASSINO-OSPEDALE',  3, 32700, 'WEEKDAY'),  -- 09:05
    ('LINEA-16','FOLCARA-VIA',       4, 33120, 'WEEKDAY'),  -- 09:12
    ('LINEA-16','FOLCARA-CAMPUS',    5, 33300, 'WEEKDAY');  -- 09:15

-- Trip 4: 13:00 departure
INSERT INTO scheduled_stops
(route_id, stop_id, stop_sequence, arrival_seconds, service_type)
VALUES
    ('LINEA-16','CASSINO-STAZIONE',  1, 46800, 'WEEKDAY'),  -- 13:00
    ('LINEA-16','CASSINO-CENTRO',    2, 46920, 'WEEKDAY'),  -- 13:02
    ('LINEA-16','CASSINO-OSPEDALE',  3, 47100, 'WEEKDAY'),  -- 13:05
    ('LINEA-16','FOLCARA-VIA',       4, 47520, 'WEEKDAY'),  -- 13:12
    ('LINEA-16','FOLCARA-CAMPUS',    5, 47700, 'WEEKDAY');  -- 13:15

-- Trip 5: 17:00 departure
INSERT INTO scheduled_stops
(route_id, stop_id, stop_sequence, arrival_seconds, service_type)
VALUES
    ('LINEA-16','CASSINO-STAZIONE',  1, 61200, 'WEEKDAY'),  -- 17:00
    ('LINEA-16','CASSINO-CENTRO',    2, 61320, 'WEEKDAY'),  -- 17:02
    ('LINEA-16','CASSINO-OSPEDALE',  3, 61500, 'WEEKDAY'),  -- 17:05
    ('LINEA-16','FOLCARA-VIA',       4, 61920, 'WEEKDAY'),  -- 17:12
    ('LINEA-16','FOLCARA-CAMPUS',    5, 62100, 'WEEKDAY');  -- 17:15