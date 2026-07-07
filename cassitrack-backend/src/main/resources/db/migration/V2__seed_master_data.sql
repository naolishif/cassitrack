-- ────────────────────────────────────────────────────────────────
-- CASSITRACK
-- V2__seed_master_data.sql
-- Datos maestros de demostración
-- ────────────────────────────────────────────────────────────────

-- ============================================================
-- ROUTES
-- ============================================================

INSERT INTO routes (
    id,
    short_name,
    long_name,
    description,
    color,
    text_color
)
VALUES

    (
        'RUTA_1_OUT',
        'RUTA_1',
        'Piazza Corte → Universita',
        'Linea principale: centro storico verso Universita',
        '1976D2',
        'FFFFFF'
    ),

    (
        'RUTA_1_IN',
        'RUTA_1',
        'Universita → Piazza Corte',
        'Linea principale ritorno',
        '1976D2',
        'FFFFFF'
    ),

    (
        'RUTA_2_OUT',
        'RUTA_2',
        'Villa Comunale → Centro Commerciale',
        'Linea commerciale: parco verso centro commerciale',
        'E67E22',
        'FFFFFF'
    ),

    (
        'RUTA_2_IN',
        'RUTA_2',
        'Centro Commerciale → Villa Comunale',
        'Linea commerciale ritorno',
        'E67E22',
        'FFFFFF'
    )

    ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- STOPS
-- ============================================================

INSERT INTO stops (
    id,
    name,
    lat,
    lon
)
VALUES

-- PARADA COMPARTIDA (cruce de las dos rutas)

(
    'STAZIONE_CENTRALE',
    'Stazione Centrale',
    41.4845,
    13.8320
),

-- RUTA 1 (Piazza Corte ↔ Universita)

(
    'PIAZZA_CORTE',
    'Piazza Corte (Duomo)',
    41.4937,
    13.8281
),

(
    'CORSO_REPUBBLICA',
    'Corso della Repubblica',
    41.4910,
    13.8312
),

(
    'VIA_SANTANGELO',
    'Via Sant''Angelo',
    41.4795,
    13.8305
),

(
    'UNIVERSITA',
    'Universita di Cassino',
    41.4719,
    13.8283
),

-- RUTA 2 (Villa Comunale ↔ Centro Commerciale)

(
    'VILLA_COMUNALE',
    'Villa Comunale',
    41.4895,
    13.8262
),

(
    'LARGO_TESCIONE',
    'Largo Tescione',
    41.4870,
    13.8290
),

(
    'VIA_CASILINA_EST',
    'Via Casilina Est',
    41.4835,
    13.8420
),

(
    'CENTRO_COMMERCIALE',
    'Centro Commerciale',
    41.4829,
    13.8559
)

    ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- BUSES
-- ============================================================

INSERT INTO buses (
    targa,
    numero_posti,
    wheelchair_accessible,
    disponibile,
    current_vehicle_id
)
VALUES

    (
        'AA123BB',
        85,
        TRUE,
        TRUE,
        'MAGNI-001'
    ),

    (
        'CC456DD',
        85,
        TRUE,
        TRUE,
        'MAGNI-002'
    ),

    (
        'EE789FF',
        52,
        TRUE,
        TRUE,
        'MAGNI-003'
    ),

    (
        'GG012HH',
        52,
        TRUE,
        TRUE,
        'MAGNI-004'
    )

    ON CONFLICT (targa) DO NOTHING;

-- ============================================================
-- USERS
-- ============================================================
-- ── SEEDING SECURE USERS ──────────────────────────────────────────
-- Plain text passwords comply with rules: Min 8 chars, Uppercase, Lowercase, Number, and Symbol.
-- Note: For local development use only. These credentials must be rotated in production.

-- 1. SYSTEM ADMINISTRATOR
-- Plain text password: "Admin_Cassitrack2026!"
INSERT INTO users (tax_id, name, surname, email, password_hash, role, telephone)
SELECT 'TAXID_ADMIN_01', 'Alessandro', 'Rossi', 'admin@cassitrack.it',
       '$2a$12$HUEm.U1eBmDDQYFFZPXBU.T9OPWGwgX4THTN1wDUWfvZc65qtHnm2',
       'ADMIN',
       '+393331111111'
    WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE tax_id = 'TAXID_ADMIN_01'
       OR email = 'admin@cassitrack.it'
       OR telephone = '+393331111111'
);

-- 2. FLEET MANAGER
-- Plain text password: "Manager_Magni2026*"
INSERT INTO users (tax_id, name, surname, email, password_hash, role, telephone)
    SELECT 'TAXID_MNG_02', 'Francesca', 'Bianchi', 'manager@cassitrack.it',
       '$2a$12$Q.jCq9QvD2BFmkFQdwpWpu15PsRtT0npz6RoGmZXZlk/ctxsw17nC',
       'FLEET_MANAGER',
       '+393332222222'
    WHERE NOT EXISTS (
    SELECT 1 FROM users
    WHERE tax_id = 'TAXID_MNG_02'
       OR email = 'manager@cassitrack.it'
       OR telephone = '+393332222222'
);