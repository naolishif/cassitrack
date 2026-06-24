package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.ScheduledStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ScheduledStopRepository extends JpaRepository<ScheduledStop, Long> {

    List<ScheduledStop> findByTripId(String tripId);
    List<ScheduledStop> findByTripRouteIdOrderByStopSequenceAsc(String routeId);

    @Query("SELECT s.trip.route.id, MIN(s.arrivalSeconds), MAX(s.arrivalSeconds) " +
           "FROM ScheduledStop s GROUP BY s.trip.route.id")
    List<Object[]> findOperatingHoursByRoute();

    @Query("SELECT ss.trip.route.id, ss.trip.route.shortName, ss.trip.route.longName, " +
           "s.id, s.name, s.lat, s.lon, MIN(ss.stopSequence) " +
           "FROM ScheduledStop ss, Stop s " +
           "WHERE ss.stopId = s.id AND s.active = true AND ss.trip.route.active = true " +
           "GROUP BY ss.trip.route.id, ss.trip.route.shortName, ss.trip.route.longName, " +
           "s.id, s.name, s.lat, s.lon " +
           "ORDER BY ss.trip.route.id, MIN(ss.stopSequence)")
    List<Object[]> findStopsGroupedByRoute();
}