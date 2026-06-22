package it.unicas.cassitrack.repository;

import org.springframework.data.repository.query.Param;
import it.unicas.cassitrack.model.ScheduledStop; // Controlla il package del tuo model
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScheduledStopRepository extends JpaRepository<ScheduledStop, Long> {

    // 💡 Sostituisci il vecchio metodo con questo:
    // Sfruttiamo il "Property Path" di Spring: TripRouteId (trip -> route -> id) e TripServiceType (trip -> serviceType)
    // List<ScheduledStop> findByTripRouteIdAndTripServiceTypeOrderByStopSequenceAsc(String routeId); // Second argument might not be needed
    List<ScheduledStop> findByTripId(String tripId); // This might be removed (put here just for the merge, came from an old version)
    List<ScheduledStop> findByTripRouteIdOrderByStopSequenceAsc(String routeId);
    @Query("SELECT ss FROM ScheduledStop ss " +
            "WHERE ss.trip.id = (SELECT MIN(t.id) FROM Trip t WHERE t.route.id = :routeId) " +
            "ORDER BY ss.stopSequence")
    List<ScheduledStop> findRepresentativeSequence(@Param("routeId") String routeId);

    List<ScheduledStop> findByTripIdOrderByStopSequenceAsc(String tripId);

    @Query("""
    SELECT ss.trip.id FROM ScheduledStop ss
    WHERE ss.trip.route.id = :routeId
    GROUP BY ss.trip.id
    HAVING MIN(ss.arrivalSeconds) <= :now AND MAX(ss.arrivalSeconds) >= :now
    ORDER BY MIN(ss.arrivalSeconds) DESC
""")
    List<String> findActiveTripIds(@Param("routeId") String routeId, @Param("now") int now);



}