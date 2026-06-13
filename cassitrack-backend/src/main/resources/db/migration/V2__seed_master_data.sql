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
        'Stazione → Universita',
        'Linea principale verso Universita',
        '1976D2',
        'FFFFFF'
    ),

    (
        'RUTA_1_IN',
        'RUTA_1',
        'Universita → Stazione',
        'Linea principale ritorno',
        '1976D2',
        'FFFFFF'
    ),

    (
        'RUTA_2_OUT',
        'RUTA_2',
        'Stazione → Zona Industriale',
        'Linea industriale andata',
        'E67E22',
        'FFFFFF'
    ),

    (
        'RUTA_2_IN',
        'RUTA_2',
        'Zona Industriale → Stazione',
        'Linea industriale ritorno',
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

-- PARADA COMPARTIDA

(
    'STAZIONE_CENTRALE',
    'Stazione Centrale',
    41.4892,
    13.8282
),

-- RUTA 1

(
    'PIAZZA_GARIBALDI',
    'Piazza Garibaldi',
    41.4915,
    13.8310
),

(
    'OSPEDALE_CIVILE',
    'Ospedale Civile',
    41.4955,
    13.8330
),

(
    'PARCO_COMUNALE',
    'Parco Comunale',
    41.4995,
    13.8260
),

(
    'UNIVERSITA',
    'Universita di Cassino',
    41.5041,
    13.8189
),

-- RUTA 2

(
    'MUSEO_CITTADINO',
    'Museo Cittadino',
    41.4905,
    13.8350
),

(
    'QUARTIERE_NORD',
    'Quartiere Nord',
    41.4965,
    13.8410
),

(
    'CENTRO_COMMERCIALE',
    'Centro Commerciale',
    41.5015,
    13.8470
),

(
    'ZONA_INDUSTRIALE',
    'Zona Industriale',
    41.5070,
    13.8540
)

    ON CONFLICT (id) DO NOTHING;

-- ============================================================
-- BUSES
-- ============================================================

INSERT INTO buses (
    targa,
    numero_posti,
    posto_disabili,
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