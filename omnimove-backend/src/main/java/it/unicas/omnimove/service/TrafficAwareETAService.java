package it.unicas.omnimove.service;

import it.unicas.omnimove.client.CassitrackClient;
import it.unicas.omnimove.dto.StopArrivalDTO;
import it.unicas.omnimove.model.Stop;
import it.unicas.omnimove.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Traffic-aware ETA service for OMNIMOVE journey planning.
 *
 * Fetches bus arrival data from CASSITRACK via REST, then enriches
 * each arrival with real-time traffic data from Google Maps.
 *
 * The origin used for the Google Maps query is the start stop of the
 * lines (Piazza San Benedetto), read from the DB, so no user coordinates
 * are needed. Google Maps computes the traffic along the bus route itself.
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
    private final StopRepository    stopRepository;

    // ID of the stop used as origin for the Google Maps traffic query
    // (start of the lines — Piazza San Benedetto in the real Cassino network).
    // Falls back gracefully if this stop is not present in the DB.
    private static final String ROUTE_START_STOP_ID = "PSB";

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
     * @param stopId  the stop to query (e.g. "UNI" for Università Folcara)
     * @return list of enriched ETA results, sorted by estimated arrival
     */
    public List<TrafficEtaResult> getEnrichedArrivals(String stopId) {

        // Destination stop coordinates from DB
        Stop destStop = stopRepository.findById(stopId).orElse(null);
        if (destStop == null || destStop.getLat() == null || destStop.getLon() == null) {
            log.warn("Unknown or incomplete stopId: {}", stopId);
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

        // Origin = route start stop (from DB), fallback to destination if absent
        Stop originStop = stopRepository.findById(ROUTE_START_STOP_ID)
                .filter(s -> s.getLat() != null && s.getLon() != null)
                .orElse(destStop);

        // Query Google Maps once for this stop (route start → stop destination)
        Optional<GoogleMapsService.TrafficResult> trafficOpt =
                googleMapsService.getTravelTime(
                        originStop.getLat(), originStop.getLon(),
                        destStop.getLat(),   destStop.getLon());

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
