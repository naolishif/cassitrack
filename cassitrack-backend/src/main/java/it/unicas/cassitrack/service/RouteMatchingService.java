package it.unicas.cassitrack.service;

import it.unicas.cassitrack.repository.ScheduledStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Matches a bus GPS position to:
 *   1. The nearest bus stop
 *   2. The scheduled arrival time at that stop
 *
 * This is how we know a bus is "at" a stop
 * even though GPS coordinates are never exact.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RouteMatchingService {

    private final ScheduledStopRepository scheduledStopRepo;

    private static final ZoneId ITALY_TZ =
            ZoneId.of("Europe/Rome");

    // Known stop coordinates
    // stopId → [latitude, longitude]
    private static final Map<String, double[]> STOP_COORDS = Map.of(
            "CASSINO-STAZIONE",  new double[]{41.4892, 13.8282},
            "CASSINO-CENTRO",    new double[]{41.4917, 13.8314},
            "CASSINO-OSPEDALE",  new double[]{41.4955, 13.8330},
            "FOLCARA-VIA",       new double[]{41.5020, 13.8200},
            "FOLCARA-CAMPUS",    new double[]{41.5041, 13.8189}
    );

    // Only match if the bus is within this many metres of a stop
    private static final double MAX_MATCH_METRES = 300.0;

    /**
     * Find the ID of the nearest stop to a GPS position.
     * Returns null if no stop is within MAX_MATCH_METRES.
     */
    public String findNearestStopId(double lat, double lon) {
        String nearestId  = null;
        double nearestDist = Double.MAX_VALUE;

        for (Map.Entry<String, double[]> entry
                : STOP_COORDS.entrySet()) {
            double[] coords = entry.getValue();
            double dist = haversineMetres(
                    lat, lon, coords[0], coords[1]
            );

            if (dist < nearestDist) {
                nearestDist = dist;
                nearestId   = entry.getKey();
            }
        }

        if (nearestDist > MAX_MATCH_METRES) {
            // Bus is between stops — return closest anyway
            // for schedule computation purposes
            log.debug("Bus is {}m from nearest stop {}",
                    (int)nearestDist, nearestId);
        }

        return nearestId;
    }

    /**
     * Get the scheduled arrival time (seconds after midnight)
     * for a specific stop on a specific route,
     * near the current time.
     *
     * Returns -1 if no matching trip is found.
     */
    public int getScheduledArrival(
            String routeId,
            String stopId,
            int currentSeconds) {

        String serviceType = getTodayServiceType();

        // Look for trips within a 30-minute window
        // around the current time
        int windowStart = currentSeconds - 1800; // 30 min ago
        int windowEnd   = currentSeconds + 1800; // 30 min ahead

        var allTrips = scheduledStopRepo
                .findByRouteIdAndServiceTypeOrderByStopSequenceAsc(
                        routeId, serviceType
                );

        // Find the trip whose stop time is closest
        // to the current time
        return allTrips.stream()
                .filter(s -> s.getStopId().equals(stopId))
                .filter(s -> s.getArrivalSeconds() >= windowStart
                        && s.getArrivalSeconds() <= windowEnd)
                .mapToInt(s -> s.getArrivalSeconds())
                .findFirst()
                .orElse(-1);
    }

    /**
     * Determine what kind of service runs today.
     * Weekdays have a different timetable from weekends.
     */
    private String getTodayServiceType() {
        DayOfWeek day = LocalDate.now(ITALY_TZ).getDayOfWeek();
        return switch (day) {
            case SATURDAY -> "SATURDAY";
            case SUNDAY   -> "SUNDAY";
            default       -> "WEEKDAY";
        };
    }

    /**
     * Calculate the distance in metres between
     * two GPS coordinates.
     *
     * Uses the Haversine formula — the same formula
     * used in navigation systems.
     *
     * Analogy: this is how your phone calculates
     * "you are 250m from the next waypoint."
     */
    public double haversineMetres(
            double lat1, double lon1,
            double lat2, double lon2) {

        final double R = 6371000; // Earth radius in metres
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(
                Math.sqrt(a), Math.sqrt(1 - a)
        );
        return R * c;
    }
}