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