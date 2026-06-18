-- ============================================================
-- V3__generate_trips_and_timetable.sql
-- ============================================================

DO $$
DECLARE
v_time TIME;
    trip_id TEXT;
    departure_seconds INTEGER;
    route_id TEXT;

    b1 INTEGER;
    b2 INTEGER;
    b3 INTEGER;
    b4 INTEGER;

BEGIN

SELECT bus_id INTO b1
FROM buses
WHERE current_vehicle_id = 'MAGNI-001';

SELECT bus_id INTO b2
FROM buses
WHERE current_vehicle_id = 'MAGNI-002';

SELECT bus_id INTO b3
FROM buses
WHERE current_vehicle_id = 'MAGNI-003';

SELECT bus_id INTO b4
FROM buses
WHERE current_vehicle_id = 'MAGNI-004';

v_time := TIME '06:00';

    WHILE v_time < TIME '22:00' LOOP

        departure_seconds :=
            EXTRACT(HOUR FROM v_time)::INTEGER * 3600 +
            EXTRACT(MINUTE FROM v_time)::INTEGER * 60;

        ------------------------------------------------------------------
        -- BUS 1
        ------------------------------------------------------------------

        IF ((departure_seconds - 21600) / 1800) % 2 = 0 THEN
            route_id := 'RUTA_1_OUT';
ELSE
            route_id := 'RUTA_1_IN';
END IF;

        trip_id := 'TRIP_B1_' || TO_CHAR(v_time,'HH24MI');

INSERT INTO trips(id, route_id, bus_id)
VALUES (trip_id, route_id, b1);

IF route_id = 'RUTA_1_OUT' THEN

            INSERT INTO scheduled_stops(trip_id,stop_id,stop_sequence,arrival_seconds)
            VALUES
            (trip_id,'STAZIONE_CENTRALE',1,departure_seconds),
            (trip_id,'PIAZZA_GARIBALDI',2,departure_seconds+450),
            (trip_id,'OSPEDALE_CIVILE',3,departure_seconds+900),
            (trip_id,'PARCO_COMUNALE',4,departure_seconds+1350),
            (trip_id,'UNIVERSITA',5,departure_seconds+1800);

ELSE

            INSERT INTO scheduled_stops(trip_id,stop_id,stop_sequence,arrival_seconds)
            VALUES
            (trip_id,'UNIVERSITA',1,departure_seconds),
            (trip_id,'PARCO_COMUNALE',2,departure_seconds+450),
            (trip_id,'OSPEDALE_CIVILE',3,departure_seconds+900),
            (trip_id,'PIAZZA_GARIBALDI',4,departure_seconds+1350),
            (trip_id,'STAZIONE_CENTRALE',5,departure_seconds+1800);

END IF;

        ------------------------------------------------------------------
        -- BUS 2
        ------------------------------------------------------------------

        IF ((departure_seconds - 21600) / 1800) % 2 = 0 THEN
            route_id := 'RUTA_1_IN';
ELSE
            route_id := 'RUTA_1_OUT';
END IF;

        trip_id := 'TRIP_B2_' || TO_CHAR(v_time,'HH24MI');

INSERT INTO trips(id, route_id, bus_id)
VALUES (trip_id, route_id, b2);

IF route_id = 'RUTA_1_OUT' THEN

            INSERT INTO scheduled_stops(trip_id,stop_id,stop_sequence,arrival_seconds)
            VALUES
            (trip_id,'STAZIONE_CENTRALE',1,departure_seconds),
            (trip_id,'PIAZZA_GARIBALDI',2,departure_seconds+450),
            (trip_id,'OSPEDALE_CIVILE',3,departure_seconds+900),
            (trip_id,'PARCO_COMUNALE',4,departure_seconds+1350),
            (trip_id,'UNIVERSITA',5,departure_seconds+1800);

ELSE

            INSERT INTO scheduled_stops(trip_id,stop_id,stop_sequence,arrival_seconds)
            VALUES
            (trip_id,'UNIVERSITA',1,departure_seconds),
            (trip_id,'PARCO_COMUNALE',2,departure_seconds+450),
            (trip_id,'OSPEDALE_CIVILE',3,departure_seconds+900),
            (trip_id,'PIAZZA_GARIBALDI',4,departure_seconds+1350),
            (trip_id,'STAZIONE_CENTRALE',5,departure_seconds+1800);

END IF;

        ------------------------------------------------------------------
        -- BUS 3
        ------------------------------------------------------------------

        IF ((departure_seconds - 21600) / 1800) % 2 = 0 THEN
            route_id := 'RUTA_2_OUT';
ELSE
            route_id := 'RUTA_2_IN';
END IF;

        trip_id := 'TRIP_B3_' || TO_CHAR(v_time,'HH24MI');

INSERT INTO trips(id, route_id, bus_id)
VALUES (trip_id, route_id, b3);

IF route_id = 'RUTA_2_OUT' THEN

            INSERT INTO scheduled_stops(trip_id,stop_id,stop_sequence,arrival_seconds)
            VALUES
            (trip_id,'STAZIONE_CENTRALE',1,departure_seconds),
            (trip_id,'MUSEO_CITTADINO',2,departure_seconds+450),
            (trip_id,'QUARTIERE_NORD',3,departure_seconds+900),
            (trip_id,'CENTRO_COMMERCIALE',4,departure_seconds+1350),
            (trip_id,'ZONA_INDUSTRIALE',5,departure_seconds+1800);

ELSE

            INSERT INTO scheduled_stops(trip_id,stop_id,stop_sequence,arrival_seconds)
            VALUES
            (trip_id,'ZONA_INDUSTRIALE',1,departure_seconds),
            (trip_id,'CENTRO_COMMERCIALE',2,departure_seconds+450),
            (trip_id,'QUARTIERE_NORD',3,departure_seconds+900),
            (trip_id,'MUSEO_CITTADINO',4,departure_seconds+1350),
            (trip_id,'STAZIONE_CENTRALE',5,departure_seconds+1800);

END IF;

        ------------------------------------------------------------------
        -- BUS 4
        ------------------------------------------------------------------

        IF ((departure_seconds - 21600) / 1800) % 2 = 0 THEN
            route_id := 'RUTA_2_IN';
ELSE
            route_id := 'RUTA_2_OUT';
END IF;

        trip_id := 'TRIP_B4_' || TO_CHAR(v_time,'HH24MI');

INSERT INTO trips(id, route_id, bus_id)
VALUES (trip_id, route_id, b4);

IF route_id = 'RUTA_2_OUT' THEN

            INSERT INTO scheduled_stops(trip_id,stop_id,stop_sequence,arrival_seconds)
            VALUES
            (trip_id,'STAZIONE_CENTRALE',1,departure_seconds),
            (trip_id,'MUSEO_CITTADINO',2,departure_seconds+450),
            (trip_id,'QUARTIERE_NORD',3,departure_seconds+900),
            (trip_id,'CENTRO_COMMERCIALE',4,departure_seconds+1350),
            (trip_id,'ZONA_INDUSTRIALE',5,departure_seconds+1800);

ELSE

            INSERT INTO scheduled_stops(trip_id,stop_id,stop_sequence,arrival_seconds)
            VALUES
            (trip_id,'ZONA_INDUSTRIALE',1,departure_seconds),
            (trip_id,'CENTRO_COMMERCIALE',2,departure_seconds+450),
            (trip_id,'QUARTIERE_NORD',3,departure_seconds+900),
            (trip_id,'MUSEO_CITTADINO',4,departure_seconds+1350),
            (trip_id,'STAZIONE_CENTRALE',5,departure_seconds+1800);

END IF;

v_time := v_time + INTERVAL '30 minutes';

END LOOP;

END $$;