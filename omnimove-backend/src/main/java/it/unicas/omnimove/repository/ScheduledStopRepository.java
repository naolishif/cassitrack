package it.unicas.omnimove.repository;

import it.unicas.omnimove.model.ScheduledStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduledStopRepository extends JpaRepository<ScheduledStop, Long> {

    // 💡 Sostituisci il vecchio metodo con questo:
    // Sfruttiamo il "Property Path" di Spring: TripRouteId (trip -> route -> id) e TripServiceType (trip -> serviceType)
    //List<ScheduledStop> findByTripRouteIdAndTripServiceTypeOrderByStopSequenceAsc(String routeId, String serviceType);
    List<ScheduledStop> findByTripId(String tripId);
}