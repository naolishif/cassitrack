package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.ScheduledStop; // Controlla il package del tuo model
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScheduledStopRepository extends JpaRepository<ScheduledStop, Long> {

    // 💡 Sostituisci il vecchio metodo con questo:
    // Sfruttiamo il "Property Path" di Spring: TripRouteId (trip -> route -> id) e TripServiceType (trip -> serviceType)
    // List<ScheduledStop> findByTripRouteIdAndTripServiceTypeOrderByStopSequenceAsc(String routeId); // Second argument might not be needed
    List<ScheduledStop> findByTripId(String tripId); // This might be removed (put here just for the merge, came from an old version)
    List<ScheduledStop> findByTripRouteIdOrderByStopSequenceAsc(String routeId);
}