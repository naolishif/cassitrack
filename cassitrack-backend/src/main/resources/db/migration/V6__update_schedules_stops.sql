-- ============================================================
-- V6__drop_travel_seconds.sql
-- Rimuove scheduled_stops.travel_seconds_from_prev.
--
-- Motivo: il valore e' ridondante. Il tempo tra due fermate e'
-- ricavabile dalla differenza dei rispettivi arrival_seconds
-- (al netto della sosta costante di 60s):
--     travel[k] = arrival[k] - arrival[k-1] - 60
-- E l'ETA non ha nemmeno bisogno del travel "puro": usa
-- direttamente la differenza degli orari di tabella
--     eta = arrival[target] - arrival[ancora]
-- che include gia' tragitti e soste. Quindi la colonna e' inutile.
--
-- Nota: la V5 la crea e la popola; questa V6 la elimina. Su un DB
-- nuovo Flyway esegue V5 poi V6 in ordine (creata e poi rimossa):
-- innocuo. NON modificare la V5 (gia' applicata -> checksum mismatch).
-- ============================================================

ALTER TABLE scheduled_stops
DROP COLUMN IF EXISTS travel_seconds_from_prev;

