package it.unicas.omnimove.repository;

import it.unicas.omnimove.model.ScheduledStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduledStopRepository extends JpaRepository<ScheduledStop, Long> {

    List<ScheduledStop> findByTripId(String tripId);

    // ── Linea diretta origine → dest ───────────────────────────────
    @Query("""
        SELECT r.shortName AS shortName, r.longName AS longName,
               so.arrivalSeconds AS originSec, sd.arrivalSeconds AS destSec, t.id AS tripId
        FROM ScheduledStop so
             JOIN so.trip t
             JOIN t.route r,
             ScheduledStop sd
        WHERE sd.trip = t
          AND so.stopId = :origin
          AND sd.stopId = :dest
          AND so.stopSequence < sd.stopSequence
          AND r.active = true
        ORDER BY (sd.arrivalSeconds - so.arrivalSeconds)
        """)
    List<ConnectingLine> findLinesConnecting(@Param("origin") String origin,
                                             @Param("dest") String dest);

    interface ConnectingLine {
        String getShortName();
        String getLongName();
        Integer getOriginSec();
        Integer getDestSec();
        String getTripId();
    }

    // ── Soluzione con un cambio: origine → X → dest ────────────────
    @Query(value = """
        SELECT
            ss_x1.stop_id AS "transferStop",
            r1.short_name AS "l1Short",
            r1.long_name  AS "l1Long",
            r1.id         AS "l1RouteId",
            ss_o.trip_id  AS "l1TripId",
            (ss_x1.arrival_seconds - ss_o.arrival_seconds) AS "l1Sec",
            r2.short_name AS "l2Short",
            r2.long_name  AS "l2Long",
            r2.id         AS "l2RouteId",
            ss_x2.trip_id AS "l2TripId",
            (ss_d.arrival_seconds - ss_x2.arrival_seconds) AS "l2Sec"
        FROM scheduled_stops ss_o
        JOIN trips  t1 ON t1.id = ss_o.trip_id
        JOIN routes r1 ON r1.id = t1.route_id
        JOIN scheduled_stops ss_x1 ON ss_x1.trip_id = ss_o.trip_id
                                  AND ss_x1.stop_sequence > ss_o.stop_sequence
        JOIN scheduled_stops ss_x2 ON ss_x2.stop_id = ss_x1.stop_id
        JOIN trips  t2 ON t2.id = ss_x2.trip_id
        JOIN routes r2 ON r2.id = t2.route_id
        JOIN scheduled_stops ss_d ON ss_d.trip_id = ss_x2.trip_id
                                 AND ss_d.stop_sequence > ss_x2.stop_sequence
        WHERE ss_o.stop_id = :origin
          AND ss_d.stop_id = :dest
          AND ss_x1.stop_id <> :origin
          AND ss_x1.stop_id <> :dest
          AND t1.route_id <> t2.route_id
        ORDER BY (ss_x1.arrival_seconds - ss_o.arrival_seconds)
               + (ss_d.arrival_seconds - ss_x2.arrival_seconds)
        LIMIT 1
        """, nativeQuery = true)
    List<TransferRoute> findBestTransfer(@Param("origin") String origin,
                                         @Param("dest")   String dest);

    interface TransferRoute {
        String  getTransferStop();
        String  getL1Short();
        String  getL1Long();
        Integer getL1Sec();
        String  getL1RouteId();
        String  getL2Short();
        String  getL2Long();
        Integer getL2Sec();
        String  getL2RouteId();
        String  getL1TripId();
        String  getL2TripId();
    }
}