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

    /**
     * Returns upcoming scheduled stops for a given stop within a time window,
     * eagerly joining trip → route and trip → bus so callers can access
     * route name and vehicle ID without triggering lazy-load exceptions.
     *
     * @param stopId      the stop to query (e.g. "CASSINO-STAZIONE")
     * @param fromSeconds seconds-since-midnight lower bound (inclusive)
     * @param toSeconds   seconds-since-midnight upper bound (exclusive)
     */
    @Query("""
        SELECT ss FROM ScheduledStop ss
        JOIN FETCH ss.trip t
        JOIN FETCH t.route r
        JOIN FETCH t.bus b
        WHERE ss.stopId      = :stopId
          AND ss.arrivalSeconds >= :fromSeconds
          AND ss.arrivalSeconds  < :toSeconds
        ORDER BY ss.arrivalSeconds ASC
        """)
    List<ScheduledStop> findUpcomingByStopId(
            @Param("stopId")      String stopId,
            @Param("fromSeconds") int    fromSeconds,
            @Param("toSeconds")   int    toSeconds);

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
}