package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.StopArrivalDTO;
import it.unicas.cassitrack.model.Stop;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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

        List<StopArrivalDTO> arrivals = new ArrayList<>();

        // Look at every active bus
        for (VehiclePosition bus
                : vehicleStateCache.getActive()) {

            StopArrivalDTO arrival =
                    computeArrival(bus, stopId);

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
            VehiclePosition bus, String targetStopId) {
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
            // (more than 500m behind), skip it
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

            // Get the scheduled arrival for comparison
            int nowSeconds = LocalTime.now(ITALY_TZ)
                    .toSecondOfDay();
            int scheduledSec =
                    routeMatchingService.getScheduledArrival(
                            bus.getMatchedRouteId() != null
                                    ? bus.getMatchedRouteId()
                                    : "LINEA-16",
                            targetStopId,
                            nowSeconds
                    );

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
                    .routeId(bus.getMatchedRouteId() != null
                            ? bus.getMatchedRouteId()
                            : "LINEA-16")
                    .routeName("Linea 16 — Campus Folcara")
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