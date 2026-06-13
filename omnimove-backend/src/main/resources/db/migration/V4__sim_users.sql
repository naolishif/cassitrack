-- =================================================================
-- V4__sim_users.sql
-- OMNIMOVE — Simulated test users for Smart Cities demo
-- All passwords: test1234
-- BCrypt $2a$ compatible with Spring Security BCryptPasswordEncoder
-- =================================================================

-- Clean up any previous sim users to allow re-runs
DELETE FROM users WHERE email LIKE '%@sim.omnimove.it';

-- BCrypt hash for "test1234" (cost 12, compatible with $2a$ prefix)
-- Generated with Spring BCryptPasswordEncoder

-- ── TRAVELLER users — Cassino/UNICAS profiles ─────────────────────
INSERT INTO users (name, email, password, role) VALUES
('Marco Esposito',    'marco.esposito@sim.omnimove.it',    '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Sofia Ricci',       'sofia.ricci@sim.omnimove.it',       '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Luca Ferrara',      'luca.ferrara@sim.omnimove.it',      '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Giulia Romano',     'giulia.romano@sim.omnimove.it',     '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Andrea Conti',      'andrea.conti@sim.omnimove.it',      '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Martina Bruno',     'martina.bruno@sim.omnimove.it',     '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Davide Marino',     'davide.marino@sim.omnimove.it',     '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Chiara Greco',      'chiara.greco@sim.omnimove.it',       '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Filippo Serra',     'filippo.serra@sim.omnimove.it',     '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Alessia Costa',     'alessia.costa@sim.omnimove.it',     '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Matteo Gallo',      'matteo.gallo@sim.omnimove.it',      '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Valentina Mancini', 'valentina.mancini@sim.omnimove.it', '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Simone De Luca',    'simone.deluca@sim.omnimove.it',     '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Eleonora Fontana',  'eleonora.fontana@sim.omnimove.it',  '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Lorenzo Caruso',    'lorenzo.caruso@sim.omnimove.it',    '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Francesca Villa',   'francesca.villa@sim.omnimove.it',   '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Riccardo Palumbo',  'riccardo.palumbo@sim.omnimove.it',  '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Giorgia Ferretti',  'giorgia.ferretti@sim.omnimove.it',  '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Nicola Santoro',    'nicola.santoro@sim.omnimove.it',    '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Aurora Pellegrini', 'aurora.pellegrini@sim.omnimove.it', '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Emanuele Fabbri',   'emanuele.fabbri@sim.omnimove.it',  '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Beatrice Monti',    'beatrice.monti@sim.omnimove.it',    '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Giacomo Cattaneo',  'giacomo.cattaneo@sim.omnimove.it',  '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Irene Lombardi',    'irene.lombardi@sim.omnimove.it',    '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Tommaso Gentile',   'tommaso.gentile@sim.omnimove.it',   '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Camilla Vitale',    'camilla.vitale@sim.omnimove.it',    '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Federico Marini',   'federico.marini@sim.omnimove.it',   '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Serena Basile',     'serena.basile@sim.omnimove.it',     '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Daniele Silvestri', 'daniele.silvestri@sim.omnimove.it', '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER'),
('Noemi Guerra',      'noemi.guerra@sim.omnimove.it',      '$2a$12$i1lXeuAFhlqYsZ0.F0GnjePUFB0VPzK30Ih4xSaXW2dU/7tSZFnhW', 'TRAVELLER');

-- ── Verify ────────────────────────────────────────────────────────
-- SELECT role, COUNT(*) FROM users GROUP BY role;
-- Expected: ADMIN=1, TRAVELLER=32 (2 original + 30 sim)
