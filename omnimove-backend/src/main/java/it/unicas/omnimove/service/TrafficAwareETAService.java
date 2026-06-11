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
 * Enriches bus arrival data (fetched from CASSITRACK via REST)
 * with real-time traffic information from Google Maps.
 *
 * Strategy:
 *   1. Get active bus arrivals from CASSITRACK via CassitrackClient
 *   2. For each arrival, call GoogleMapsService to get traffic-adjusted time
 *   3. Return enriched result with dataSource field indicating the source
 *
 * Used by JourneyPlannerService to give more accurate bus ETAs
 * in multimodal journey comparisons.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrafficAwareETAService {

    private final CassitrackClient  cassitrackClient;
    private final GoogleMapsService googleMapsService;

    private static final Map<String, double[]> STOP_COORDS = Map.of(
            "CASSINO-STAZIONE",  new double[]{41.4892, 13.8282},
            "CASSINO-CENTRO",    new double[]{41.4917, 13.8314},
            "CASSINO-OSPEDALE",  new double[]{41.4955, 13.8330},
            "FOLCARA-VIA",       new double[]{41.5020, 13.8200},
            "FOLCARA-CAMPUS",    new double[]{41.5041, 13.8189}
    );

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
     * Fetches base arrival data from CASSITRACK, then enriches
     * each entry with Google Maps real-time traffic if available.
     *
     * @param stopId  the stop to query (e.g. "FOLCARA-CAMPUS")
     * @param originLat  latitude of the bus / journey origin
     * @param originLon  longitude of the bus / journey origin
     * @return list of enriched ETA results
     */
    public List<TrafficEtaResult> getEnrichedArrivals(
            String stopId, double originLat, double originLon) {

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

        // Enrich each arrival with Google Maps traffic data
        return arrivals.stream()
                .map(arrival -> enrich(arrival, stopId, originLat, originLon, stopCoords))
                .toList();
    }

    /**
     * Enrich a single arrival with Google Maps traffic data.
     * Falls back to the original CASSITRACK ETA if Google Maps is unavailable.
     */
    private TrafficEtaResult enrich(
            StopArrivalDTO arrival, String stopId,
            double originLat, double originLon, double[] stopCoords) {

        Optional<GoogleMapsService.TrafficResult> trafficOpt =
                googleMapsService.getTravelTime(
                        originLat, originLon,
                        stopCoords[0], stopCoords[1]);

        if (trafficOpt.isPresent()) {
            GoogleMapsService.TrafficResult t = trafficOpt.get();
            long delay = t.durationInTrafficSeconds() - t.durationSeconds();

            // Adjust the arrival time from CASSITRACK with the traffic delta
            Instant adjusted = arrival.getEstimatedArrival().plusSeconds(delay);

            log.debug("Enriched stop {} with Google Maps: base={}s traffic={}s delay={}s",
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
                etaSec,
                etaSec,
                0L,
                0L,
                "CASSITRACK"
        );
    }
}
