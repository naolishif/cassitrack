package it.unicas.cassitrack.service;

import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.model.VehiclePosition.ScheduleStatus;
import it.unicas.cassitrack.repository.ScheduledStopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;

/**
 * Computes whether each bus is running on time.
 *
 * How it works:
 *   1. Every 30 seconds, look at every active bus
 *   2. Find the nearest bus stop to its current position
 *   3. Check what time the schedule says it should
 *      be at that stop
 *   4. Compare with the current time
 *   5. Mark it: ON_TIME, SLIGHTLY_LATE,
 *      SIGNIFICANTLY_LATE, or EARLY
 *
 * Analogy: like a train controller watching
 * the board and comparing actual vs planned times.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScheduleAdherenceService {

    private final VehicleStateCache vehicleStateCache;
    private final RouteMatchingService routeMatchingService;

    // Thresholds for late classification
    private static final int SLIGHTLY_LATE_MINUTES     = 2;
    private static final int SIGNIFICANTLY_LATE_MINUTES = 5;

    // Rome/Italy timezone
    private static final ZoneId ITALY_TZ =
            ZoneId.of("Europe/Rome");

    /**
     * Runs every 30 seconds automatically.
     * Updates the schedule status of every active bus.
     *
     * The @Scheduled annotation is why we have
     * @EnableScheduling in CassitrackApplication.
     */
    @Scheduled(fixedDelay = 30000)
    public void updateAllVehicleAdherence() {
        Collection<VehiclePosition> active =
                vehicleStateCache.getActive();

        if (active.isEmpty()) return;

        log.debug("Updating schedule adherence " +
                "for {} vehicles", active.size());

        active.forEach(this::updateVehicleAdherence);
    }

    /**
     * Compute and update schedule status
     * for one specific vehicle.
     */
    public void updateVehicleAdherence(VehiclePosition pos) {
        try {
            // What time is it now in Italy?
            LocalTime now = LocalTime.now(ITALY_TZ);
            int nowSeconds = now.toSecondOfDay();

            // Find the nearest stop to this bus
            String nearestStopId =
                    routeMatchingService.findNearestStopId(
                            pos.getLat(), pos.getLon()
                    );

            if (nearestStopId == null) {
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }

            // Find what time the schedule says
            // the bus should be at that stop
            int scheduledSeconds =
                    routeMatchingService.getScheduledArrival(
                            pos.getMatchedRouteId() != null
                                    ? pos.getMatchedRouteId()
                                    : "LINEA-16",
                            nearestStopId,
                            nowSeconds
                    );

            if (scheduledSeconds < 0) {
                // No scheduled trip found near this time
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }

            // How many minutes late is the bus?
            int delaySeconds  = nowSeconds - scheduledSeconds;
            int delayMinutes  = delaySeconds / 60;

            ScheduleStatus status;
            if (delayMinutes < -1) {
                status = ScheduleStatus.EARLY;
            } else if (delayMinutes <= SLIGHTLY_LATE_MINUTES) {
                status = ScheduleStatus.ON_TIME;
            } else if (delayMinutes <= SIGNIFICANTLY_LATE_MINUTES) {
                status = ScheduleStatus.SLIGHTLY_LATE;
            } else {
                status = ScheduleStatus.SIGNIFICANTLY_LATE;
            }

            pos.setScheduleStatus(status);
            pos.setMatchedRouteId("LINEA-16");

            log.debug("Vehicle {} at stop {}: " +
                            "{} minutes delay → {}",
                    pos.getVehicleId(), nearestStopId,
                    delayMinutes, status);

        } catch (Exception e) {
            log.warn("Could not compute adherence " +
                            "for {}: {}",
                    pos.getVehicleId(), e.getMessage());
            pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
        }
    }
}