package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.StopArrivalDTO;
import it.unicas.cassitrack.model.Route;
import it.unicas.cassitrack.model.Stop;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.RouteRepository;
import it.unicas.cassitrack.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Predicts when buses will arrive at a specific stop.
 *
 * How it works:
 *   1. Look at every active bus
 *   2. For each bus, compute how far it is
 *      from the requested stop
 *   3. Estimate travel time based on current speed
 *      and distance
 *   4. Combine with schedule data for accuracy
 *   5. Return a sorted list of predicted arrivals
 *
 * This is what powers the "arrives in 4 minutes"
 * display on the passenger app.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ETAService {

    private final VehicleStateCache     vehicleStateCache;
    private final RouteMatchingService  routeMatchingService;
    private final StopRepository        stopRepository;
    private final RouteRepository       routeRepository;

    private static final ZoneId ITALY_TZ =
            ZoneId.of("Europe/Rome");

    // Average bus speed assumption when stopped
    // or speed data is missing
    private static final double DEFAULT_SPEED_KMH = 25.0;

    /**
     * Get predicted arrivals at a specific stop.
     * Called by StopController for the API endpoint:
     *   GET /api/v1/stops/{stopId}/arrivals
     */
    public List<StopArrivalDTO> getArrivalsAtStop(
            String stopId) {

        // Load all routes once up front — avoids N DB hits inside the loop.
        Map<String, Route> routeMap = routeRepository.findAll().stream()
                .collect(Collectors.toMap(Route::getId, r -> r, (a, b) -> a));

        List<StopArrivalDTO> arrivals = new ArrayList<>();

        // Look at every active bus
        for (VehiclePosition bus
                : vehicleStateCache.getActive()) {

            StopArrivalDTO arrival =
                    computeArrival(bus, stopId, routeMap);

            if (arrival != null) {
                arrivals.add(arrival);
            }
        }

        // Sort by estimated arrival time
        arrivals.sort((a, b) ->
                a.getEstimatedArrival()
                        .compareTo(b.getEstimatedArrival())
        );

        return arrivals;
    }

    /**
     * Compute when one specific bus will reach
     * a specific stop.
     */
    private StopArrivalDTO computeArrival(
            VehiclePosition bus, String targetStopId,
            Map<String, Route> routeMap) {
        try {
            // Distance from bus to the target stop
            double[] stopCoords =
                    getStopCoords(targetStopId);

            if (stopCoords == null) return null;

            double distMetres =
                    routeMatchingService.haversineMetres(
                            bus.getLat(), bus.getLon(),
                            stopCoords[0], stopCoords[1]
                    );

            // If bus is already past the stop
            // (more than 8km behind), skip it
            if (distMetres > 8000) return null;

            // Estimate travel time
            double speedKmh = (bus.getSpeedKmh() != null
                    && bus.getSpeedKmh() > 2)
                    ? bus.getSpeedKmh()
                    : DEFAULT_SPEED_KMH;

            double speedMs      = speedKmh / 3.6;
            long   etaSeconds   = (long)(distMetres / speedMs);

            // Cap at 30 minutes — if longer,
            // bus is probably not heading to this stop
            if (etaSeconds > 1800) return null;

            Instant estimatedArrival =
                    Instant.now().plusSeconds(etaSeconds);

            // Prefer routeId from the simulator (set from MQTT), fall back to server-matched
            String routeId = bus.getRouteId() != null
                    ? bus.getRouteId()
                    : bus.getMatchedRouteId();
            Route route = routeId != null ? routeMap.get(routeId) : null;

            // Route display name: DB long name → DB short name → simulator name → null
            String routeName = null;
            String routeShortName = null;
            if (route != null) {
                routeName      = route.getLongName() != null ? route.getLongName() : route.getShortName();
                routeShortName = route.getShortName();
            } else if (bus.getRouteName() != null) {
                routeName = bus.getRouteName();
            }

            // Schedule lookup: try matchedRouteId first (more precise), fall back to routeId
            int nowSeconds = LocalTime.now(ITALY_TZ).toSecondOfDay();
            String scheduleRouteId = bus.getMatchedRouteId() != null
                    ? bus.getMatchedRouteId()
                    : routeId;
            int scheduledSec = scheduleRouteId != null
                    ? routeMatchingService.getScheduledArrival(
                            scheduleRouteId, targetStopId, nowSeconds)
                    : -1;

            Instant scheduledArrival = scheduledSec > 0
                    ? Instant.now().plusSeconds(
                    scheduledSec - nowSeconds)
                    : estimatedArrival;

            int delayMinutes = scheduledSec > 0
                    ? (int)((estimatedArrival.getEpochSecond()
                             - scheduledArrival.getEpochSecond()) / 60)
                    : 0;

            return StopArrivalDTO.builder()
                    .vehicleId(bus.getVehicleId())
                    .routeId(routeId)
                    .routeName(routeName)
                    .routeShortName(routeShortName)
                    .scheduledArrival(scheduledArrival)
                    .estimatedArrival(estimatedArrival)
                    .delayMinutes(delayMinutes)
                    .scheduleStatus(
                            bus.getScheduleStatus() != null
                                    ? bus.getScheduleStatus().name()
                                    : "UNKNOWN"
                    )
                    .build();

        } catch (Exception e) {
            log.warn("ETA computation failed for " +
                            "bus {} to stop {}: {}",
                    bus.getVehicleId(), targetStopId,
                    e.getMessage());
            return null;
        }
    }

    /**
     * Returns the coordinates of a known stop.
     * Returns null if the stop ID is not recognised.
     */
    private double[] getStopCoords(String stopId) {
        return stopRepository.findById(stopId)
                .filter(s -> s.getLat() != null && s.getLon() != null)
                .map(s -> new double[]{s.getLat(), s.getLon()})
                .orElse(null);
    }
}