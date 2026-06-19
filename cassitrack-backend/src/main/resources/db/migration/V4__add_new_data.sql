-- ============================================================
-- V4__add_new_data.sql
-- Linee reali Magni Autoservizi — Cassino
-- ============================================================
-- Linea A: Piazza San Benedetto ↔ Università Folcara
-- Linea B: Piazza San Benedetto ↔ Liceo Scientifico
-- Linea C: Piazza San Benedetto ↔ Ospedale
-- ============================================================

-- ============================================================
-- PULIZIA DATI FAKE PRECEDENTI
-- ============================================================

DELETE FROM scheduled_stops;
DELETE FROM trips;
DELETE FROM stops;
DELETE FROM routes;

-- ============================================================
-- ROUTES
-- ============================================================

INSERT INTO routes (id, short_name, long_name, description, color, text_color)
VALUES
    ('LINEA_A_OUT', 'A',
     'P.za San Benedetto → Università Folcara',
     'Linea A andata — verso Campus',
     '1976D2', 'FFFFFF'),

    ('LINEA_A_IN', 'A',
     'Università Folcara → P.za San Benedetto',
     'Linea A ritorno — da Campus',
     '1976D2', 'FFFFFF'),

    ('LINEA_B_OUT', 'B',
     'P.za San Benedetto → Liceo Scientifico',
     'Linea B andata',
     'E67E22', 'FFFFFF'),

    ('LINEA_B_IN', 'B',
     'Liceo Scientifico → P.za San Benedetto',
     'Linea B ritorno',
     'E67E22', 'FFFFFF'),

    ('LINEA_C_OUT', 'C',
     'P.za San Benedetto → Ospedale',
     'Linea C andata — verso Ospedale',
     '27AE60', 'FFFFFF'),

    ('LINEA_C_IN', 'C',
     'Ospedale → P.za San Benedetto',
     'Linea C ritorno — da Ospedale',
     '27AE60', 'FFFFFF')

    ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- STOPS
-- ============================================================

INSERT INTO stops (id, name, lat, lon)
VALUES
    ('PSB', 'Piazza San Benedetto',  41.4925, 13.8306),
    ('CRS', 'C.so Repubblica',       41.4912, 13.8321),
    ('VLE', 'V.le Europa',            41.4885, 13.8329),
    ('VGA', 'Via Garigliano',        41.4851, 13.8304),
    ('SFF', 'Staz. FF.SS.',          41.4845, 13.8320),
    ('VBO', 'Viale Bonomi',          41.4874, 13.8248),
    ('UNI', 'Università Folcara',    41.4748, 13.8294),
    ('LIC', 'Liceo Scientifico',     41.4883, 13.8219),
    ('LDA', 'Largo Dante',           41.4929, 13.8291),
    ('PLE', 'P.le Staz. FF.SS.',     41.4845, 13.8320),
    ('P14', 'P.zza 14 Febbraio',     41.4947, 13.8286),
    ('OSP', 'Ospedale',              41.4746, 13.8122)

    ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- TRIPS E SCHEDULED_STOPS
-- ============================================================

DO $$
DECLARE
dep INTEGER;
    tid TEXT;
    v_bus_id INTEGER;

    linea_a_deps INTEGER[] := ARRAY[
        28800,29700,30600,31500,32400,33300,34200,35100,
        36000,36900,37800,38700,39600,40500,41400,42300,
        43200,44100,45000,45900,46800,47700,68400
    ];

    linea_b_out_deps INTEGER[] := ARRAY[28900];
    linea_b_in_deps  INTEGER[] := ARRAY[32400, 36000, 45000, 48600, 59400];

    linea_c_deps INTEGER[] := ARRAY[
        28800,29700,30600,31500,32400,33300,34200,35100,
        36000,36900,37800,38700,39600,40500,41400,42300,
        43200,44100,45000,45900,46800,47700,68400
    ];

    i INTEGER;
BEGIN
    -- Recuperiamo un bus valido inserito in V2 per soddisfare il vincolo di NOT NULL
SELECT bus_id INTO v_bus_id FROM buses WHERE current_vehicle_id = 'MAGNI-001' LIMIT 1;

-- ----------------------------------------------------------------
-- LINEA A
-- ----------------------------------------------------------------
FOR i IN 1..array_length(linea_a_deps, 1) LOOP
        dep := linea_a_deps[i];

        -- Andata
        tid := 'A_OUT_' || dep;
INSERT INTO trips(id, route_id, bus_id)
VALUES (tid, 'LINEA_A_OUT', v_bus_id);

INSERT INTO scheduled_stops(trip_id, stop_id, stop_sequence, arrival_seconds)
VALUES
    (tid, 'PSB', 1, dep),
    (tid, 'CRS', 2, dep + 120),
    (tid, 'VLE', 3, dep + 240),
    (tid, 'VGA', 4, dep + 420),
    (tid, 'SFF', 5, dep + 540),
    (tid, 'VBO', 6, dep + 840),
    (tid, 'UNI', 7, dep + 1320);

-- Ritorno (parte 5 min dopo l'arrivo a UNI)
dep := dep + 1320 + 300;
        tid := 'A_IN_' || dep;
INSERT INTO trips(id, route_id, bus_id)
VALUES (tid, 'LINEA_A_IN', v_bus_id);

INSERT INTO scheduled_stops(trip_id, stop_id, stop_sequence, arrival_seconds)
VALUES
    (tid, 'UNI', 1, dep),
    (tid, 'VBO', 2, dep + 480),
    (tid, 'SFF', 3, dep + 780),
    (tid, 'VGA', 4, dep + 900),
    (tid, 'VLE', 5, dep + 1080),
    (tid, 'CRS', 6, dep + 1200),
    (tid, 'PSB', 7, dep + 1320);

END LOOP;

    -- ----------------------------------------------------------------
    -- LINEA B ANDATA
    -- ----------------------------------------------------------------
    FOREACH dep IN ARRAY linea_b_out_deps LOOP
        tid := 'B_OUT_' || dep;
INSERT INTO trips(id, route_id, bus_id)
VALUES (tid, 'LINEA_B_OUT', v_bus_id);

INSERT INTO scheduled_stops(trip_id, stop_id, stop_sequence, arrival_seconds)
VALUES
    (tid, 'PSB', 1, dep),
    (tid, 'VLE', 2, dep + 180),
    (tid, 'VGA', 3, dep + 360),
    (tid, 'SFF', 4, dep + 480),
    (tid, 'LIC', 5, dep + 780);
END LOOP;

    -- ----------------------------------------------------------------
    -- LINEA B RITORNO
    -- ----------------------------------------------------------------
    FOREACH dep IN ARRAY linea_b_in_deps LOOP
        tid := 'B_IN_' || dep;
INSERT INTO trips(id, route_id, bus_id)
VALUES (tid, 'LINEA_B_IN', v_bus_id);

INSERT INTO scheduled_stops(trip_id, stop_id, stop_sequence, arrival_seconds)
VALUES
    (tid, 'LIC', 1, dep),
    (tid, 'SFF', 2, dep + 300),
    (tid, 'VGA', 3, dep + 420),
    (tid, 'VLE', 4, dep + 600),
    (tid, 'PSB', 5, dep + 780);
END LOOP;

    -- ----------------------------------------------------------------
    -- LINEA C
    -- ----------------------------------------------------------------
FOR i IN 1..array_length(linea_c_deps, 1) LOOP
        dep := linea_c_deps[i];

        -- Andata
        tid := 'C_OUT_' || dep;
INSERT INTO trips(id, route_id, bus_id)
VALUES (tid, 'LINEA_C_OUT', v_bus_id);

INSERT INTO scheduled_stops(trip_id, stop_id, stop_sequence, arrival_seconds)
VALUES
    (tid, 'PSB', 1, dep),
    (tid, 'LDA', 2, dep + 120),
    (tid, 'PLE', 3, dep + 480),
    (tid, 'VGA', 4, dep + 600),
    (tid, 'P14', 5, dep + 900),
    (tid, 'OSP', 6, dep + 1620);

-- Ritorno (parte 5 min dopo l'arrivo all'ospedale)
dep := dep + 1620 + 300;
        tid := 'C_IN_' || dep;
INSERT INTO trips(id, route_id, bus_id)
VALUES (tid, 'LINEA_C_IN', v_bus_id);

INSERT INTO scheduled_stops(trip_id, stop_id, stop_sequence, arrival_seconds)
VALUES
    (tid, 'OSP', 1, dep),
    (tid, 'P14', 2, dep + 720),
    (tid, 'VGA', 3, dep + 1020),
    (tid, 'PLE', 4, dep + 1140),
    (tid, 'LDA', 5, dep + 1500),
    (tid, 'PSB', 6, dep + 1620);

END LOOP;

END $$;