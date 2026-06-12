package it.unicas.omnimove.service;

import it.unicas.omnimove.client.CassitrackClient;
import it.unicas.omnimove.dto.StopArrivalDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traffic-aware ETA service for OMNIMOVE journey planning.
 *
 * Fetches bus arrival data from CASSITRACK via REST, then enriches
 * each arrival with real-time traffic data from Google Maps.
 *
 * The origin used for the Google Maps query is the first stop of the
 * route (CASSINO-STAZIONE), so no user coordinates are needed.
 * Google Maps computes the traffic along the bus route itself.
 *
 * dataSource values:
 *   "GOOGLE_MAPS" → real-time traffic data available
 *   "CASSITRACK"  → fallback, using CASSITRACK ETA as-is
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrafficAwareETAService {

    private final CassitrackClient  cassitrackClient;
    private final GoogleMapsService googleMapsService;

    // Coordinates of all known stops
    private static final Map<String, double[]> STOP_COORDS = Map.of(
            "CASSINO-STAZIONE",  new double[]{41.4892, 13.8282},
            "CASSINO-CENTRO",    new double[]{41.4917, 13.8314},
            "CASSINO-OSPEDALE",  new double[]{41.4955, 13.8330},
            "FOLCARA-VIA",       new double[]{41.5020, 13.8200},
            "FOLCARA-CAMPUS",    new double[]{41.5041, 13.8189}
    );

    // Route start: used as origin for Google Maps when no position available
    private static final double ROUTE_START_LAT = 41.4892;
    private static final double ROUTE_START_LON = 13.8282;

    /**
     * Result enriched with Google Maps traffic data.
     */
    public record TrafficEtaResult(
            String  vehicleId,
            String  stopId,
            Instant estimatedArrival,
            long    durationSeconds,
            long    trafficDurationSeconds,
            long    trafficDelaySeconds,
            long    distanceMetres,
            String  dataSource
    ) {}

    /**
     * Get traffic-enriched ETA for buses arriving at a stop.
     *
     * No user coordinates needed — Google Maps query uses
     * the route start as origin and the requested stop as destination.
     *
     * @param stopId  the stop to query (e.g. "FOLCARA-CAMPUS")
     * @return list of enriched ETA results, sorted by estimated arrival
     */
    public List<TrafficEtaResult> getEnrichedArrivals(String stopId) {

        double[] stopCoords = STOP_COORDS.get(stopId);
        if (stopCoords == null) {
            log.warn("Unknown stopId: {}", stopId);
            return List.of();
        }

        // Fetch base arrivals from CASSITRACK
        List<StopArrivalDTO> arrivals;
        try {
            arrivals = cassitrackClient.getArrivalsAtStop(stopId);
        } catch (Exception e) {
            log.warn("Could not fetch arrivals from CASSITRACK: {}", e.getMessage());
            return List.of();
        }

        if (arrivals == null || arrivals.isEmpty()) {
            return List.of();
        }

        // Query Google Maps once for this stop (route start → stop destination)
        Optional<GoogleMapsService.TrafficResult> trafficOpt =
                googleMapsService.getTravelTime(
                        ROUTE_START_LAT, ROUTE_START_LON,
                        stopCoords[0], stopCoords[1]);

        // Enrich each arrival with the same traffic delta
        return arrivals.stream()
                .map(arrival -> enrich(arrival, stopId, trafficOpt))
                .sorted((a, b) -> a.estimatedArrival().compareTo(b.estimatedArrival()))
                .toList();
    }

    /**
     * Enrich a single arrival with Google Maps traffic data.
     * Falls back to CASSITRACK ETA if Google Maps is unavailable.
     */
    private TrafficEtaResult enrich(
            StopArrivalDTO arrival,
            String stopId,
            Optional<GoogleMapsService.TrafficResult> trafficOpt) {

        if (trafficOpt.isPresent()) {
            GoogleMapsService.TrafficResult t = trafficOpt.get();
            long delay = t.durationInTrafficSeconds() - t.durationSeconds();

            // Adjust the CASSITRACK arrival time with the traffic delta
            Instant adjusted = arrival.getEstimatedArrival().plusSeconds(delay);

            log.debug("Enriched {} with Google Maps: base={}s traffic={}s delay={}s",
                    stopId, t.durationSeconds(), t.durationInTrafficSeconds(), delay);

            return new TrafficEtaResult(
                    arrival.getVehicleId(),
                    stopId,
                    adjusted,
                    t.durationSeconds(),
                    t.durationInTrafficSeconds(),
                    delay,
                    t.distanceMetres(),
                    "GOOGLE_MAPS"
            );
        }

        // Fallback: use CASSITRACK ETA as-is
        long etaSec = arrival.getEstimatedArrival().getEpochSecond()
                - Instant.now().getEpochSecond();

        return new TrafficEtaResult(
                arrival.getVehicleId(),
                stopId,
                arrival.getEstimatedArrival(),
                Math.max(0, etaSec),
                Math.max(0, etaSec),
                0L,
                0L,
                "CASSITRACK"
        );
    }
}
