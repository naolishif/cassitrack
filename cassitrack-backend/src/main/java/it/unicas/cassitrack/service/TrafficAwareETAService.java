package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.StopArrivalDTO;
import it.unicas.cassitrack.model.VehiclePosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traffic-aware ETA service for bus arrivals.
 *
 * Upgrade over the basic ETAService:
 *   - First tries Google Maps Distance Matrix API (real traffic data)
 *   - Falls back to haversine + speed estimate if Google Maps is unavailable
 *
 * The result exposed via the API includes:
 *   - estimatedArrival  → arrival time adjusted for current traffic
 *   - durationSeconds   → base travel time (no traffic)
 *   - trafficDurationSeconds → travel time WITH traffic
 *   - trafficDelaySeconds → extra delay caused by traffic
 *   - dataSource        → "GOOGLE_MAPS" or "HAVERSINE" so the client knows
 *
 * Integration point:
 *   This service is called by TrafficController at:
 *   GET /api/v1/traffic/eta?stopId=FOLCARA-CAMPUS
 *   GET /api/v1/traffic/eta?stopId=FOLCARA-CAMPUS&vehicleId=MAGNI-001
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrafficAwareETAService {

    private final VehicleStateCache    vehicleStateCache;
    private final RouteMatchingService routeMatchingService;
    private final GoogleMapsService    googleMapsService;

    private static final ZoneId   ITALY_TZ          = ZoneId.of("Europe/Rome");
    private static final double   DEFAULT_SPEED_KMH = 25.0;
    private static final long     MAX_ETA_SECONDS   = 1800; // 30 min

    // Known stop coordinates — same as RouteMatchingService
    private static final Map<String, double[]> STOP_COORDS = Map.of(
            "CASSINO-STAZIONE",  new double[]{41.4892, 13.8282},
            "CASSINO-CENTRO",    new double[]{41.4917, 13.8314},
            "CASSINO-OSPEDALE",  new double[]{41.4955, 13.8330},
            "FOLCARA-VIA",       new double[]{41.5020, 13.8200},
            "FOLCARA-CAMPUS",    new double[]{41.5041, 13.8189}
    );

    /**
     * DTO for the traffic-enriched ETA response.
     */
    public record TrafficEtaResult(
            String   vehicleId,
            String   routeId,
            String   routeName,
            String   stopId,
            Instant  estimatedArrival,
            Instant  scheduledArrival,
            int      delayMinutes,
            long     durationSeconds,
            long     trafficDurationSeconds,
            long     trafficDelaySeconds,
            long     distanceMetres,
            String   dataSource,
            String   scheduleStatus
    ) {}

    /**
     * Get traffic-aware ETAs for all active buses heading to a stop.
     * Optionally filter by vehicleId.
     */
    public List<TrafficEtaResult> getTrafficAwareArrivals(
            String stopId, String vehicleIdFilter) {

        double[] stopCoords = STOP_COORDS.get(stopId);
        if (stopCoords == null) {
            log.warn("Unknown stop: {}", stopId);
            return List.of();
        }

        List<TrafficEtaResult> results = new ArrayList<>();

        for (VehiclePosition bus : vehicleStateCache.getActive()) {

            // Apply optional vehicle filter
            if (vehicleIdFilter != null && !vehicleIdFilter.isBlank()
                    && !vehicleIdFilter.equals(bus.getVehicleId())) {
                continue;
            }

            TrafficEtaResult result = computeTrafficEta(bus, stopId, stopCoords);
            if (result != null) {
                results.add(result);
            }
        }

        // Sort by estimated arrival (soonest first)
        results.sort((a, b) ->
                a.estimatedArrival().compareTo(b.estimatedArrival()));

        return results;
    }

    /**
     * Compute traffic-aware ETA for one bus to one stop.
     *
     * Strategy:
     *   1. Try Google Maps Distance Matrix with departure_time=now
     *   2. If unavailable, fall back to haversine + current speed
     */
    private TrafficEtaResult computeTrafficEta(
            VehiclePosition bus, String stopId, double[] stopCoords) {
        try {
            long etaSeconds;
            long baseDurationSeconds;
            long trafficDurationSeconds;
            long distanceMetres;
            String dataSource;

            // --- Try Google Maps first ---
            Optional<GoogleMapsService.TrafficResult> trafficOpt =
                    googleMapsService.getTravelTime(
                            bus.getLat(), bus.getLon(),
                            stopCoords[0], stopCoords[1]
                    );

            if (trafficOpt.isPresent()) {
                GoogleMapsService.TrafficResult traffic = trafficOpt.get();
                etaSeconds              = traffic.durationInTrafficSeconds();
                baseDurationSeconds     = traffic.durationSeconds();
                trafficDurationSeconds  = traffic.durationInTrafficSeconds();
                distanceMetres          = traffic.distanceMetres();
                dataSource              = "GOOGLE_MAPS";

                log.debug("Bus {} → stop {}: Google Maps ETA {}s (traffic {}s, base {}s)",
                        bus.getVehicleId(), stopId,
                        etaSeconds, trafficDurationSeconds, baseDurationSeconds);

            } else {
                // --- Fallback: haversine + speed ---
                double distM = routeMatchingService.haversineMetres(
                        bus.getLat(), bus.getLon(),
                        stopCoords[0], stopCoords[1]);

                double speedKmh = (bus.getSpeedKmh() != null && bus.getSpeedKmh() > 2)
                        ? bus.getSpeedKmh()
                        : DEFAULT_SPEED_KMH;

                etaSeconds             = (long)(distM / (speedKmh / 3.6));
                baseDurationSeconds    = etaSeconds;
                trafficDurationSeconds = etaSeconds;
                distanceMetres         = (long) distM;
                dataSource             = "HAVERSINE";

                log.debug("Bus {} → stop {}: Haversine ETA {}s ({}m @ {}km/h)",
                        bus.getVehicleId(), stopId,
                        etaSeconds, (long)distM, (int)speedKmh);
            }

            // Filter out buses too far or heading elsewhere
            if (etaSeconds > MAX_ETA_SECONDS) return null;

            Instant estimatedArrival = Instant.now().plusSeconds(etaSeconds);
            long trafficDelay = trafficDurationSeconds - baseDurationSeconds;

            // Compute scheduled arrival for delay calculation
            int nowSec = LocalTime.now(ITALY_TZ).toSecondOfDay();
            int scheduledSec = routeMatchingService.getScheduledArrival(
                    bus.getMatchedRouteId() != null
                            ? bus.getMatchedRouteId() : "LINEA-16",
                    stopId, nowSec);

            Instant scheduledArrival = scheduledSec > 0
                    ? Instant.now().plusSeconds(scheduledSec - nowSec)
                    : estimatedArrival;

            int delayMinutes = scheduledSec > 0
                    ? (int)((estimatedArrival.getEpochSecond()
                             - scheduledArrival.getEpochSecond()) / 60)
                    : 0;

            return new TrafficEtaResult(
                    bus.getVehicleId(),
                    bus.getMatchedRouteId() != null
                            ? bus.getMatchedRouteId() : "LINEA-16",
                    "Linea 16 — Campus Folcara",
                    stopId,
                    estimatedArrival,
                    scheduledArrival,
                    delayMinutes,
                    baseDurationSeconds,
                    trafficDurationSeconds,
                    trafficDelay,
                    distanceMetres,
                    dataSource,
                    bus.getScheduleStatus() != null
                            ? bus.getScheduleStatus().name() : "UNKNOWN"
            );

        } catch (Exception e) {
            log.warn("Traffic ETA failed for bus {} to stop {}: {}",
                    bus.getVehicleId(), stopId, e.getMessage());
            return null;
        }
    }
}
