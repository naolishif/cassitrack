package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.ScheduledStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduledStopRepository
        extends JpaRepository<ScheduledStop, Long> {

    /**
     * Find all scheduled stops for a route,
     * ordered by stop sequence.
     */
    List<ScheduledStop> findByRouteIdAndServiceTypeOrderByStopSequenceAsc(
            String routeId, String serviceType
    );

    /**
     * Find the next scheduled trip after a
     * given time (in seconds after midnight).
     *
     * Example: "what trips stop at FOLCARA-CAMPUS
     * after 08:30 today?"
     */
    @Query("""
        SELECT s FROM ScheduledStop s
        WHERE s.stopId = :stopId
        AND s.serviceType = :serviceType
        AND s.arrivalSeconds >= :afterSeconds
        ORDER BY s.arrivalSeconds ASC
        """)
    List<ScheduledStop> findNextArrivalsAtStop(
            @Param("stopId")       String stopId,
            @Param("serviceType")  String serviceType,
            @Param("afterSeconds") int afterSeconds
    );
}