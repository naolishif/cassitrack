package it.unicas.cassitrack.service;

import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.model.VehiclePosition.ScheduleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    // Injects the InfluxDB writer to keep historical records of delays and crowding
    private final WriteApiBlocking influxWriteApi;

    private static final int SLIGHTLY_LATE_MINUTES = 3;
    private static final int SIGNIFICANTLY_LATE_MINUTES = 10;
    private static final ZoneId ITALY_TZ = ZoneId.of("Europe/Rome");

    /** Unica fonte di verità: da minuti di ritardo a stato di puntualità. */
    public static ScheduleStatus statusFromDelay(Integer delayMinutes) {
        if (delayMinutes == null)                       return ScheduleStatus.UNKNOWN;
        if (delayMinutes < -1)                          return ScheduleStatus.EARLY;
        if (delayMinutes <= SLIGHTLY_LATE_MINUTES)      return ScheduleStatus.ON_TIME;
        if (delayMinutes <= SIGNIFICANTLY_LATE_MINUTES) return ScheduleStatus.SLIGHTLY_LATE;
        return ScheduleStatus.SIGNIFICANTLY_LATE;
    }

    @Scheduled(fixedRate = 30000)
    public void checkAdherence() {
        Collection<VehiclePosition> activeBuses = vehicleStateCache.getActive();
        for (VehiclePosition pos : activeBuses) {
            processBusAdherence(pos);
            vehicleStateCache.update(pos.getVehicleId(), pos);
        }
    }

    public void processBusAdherence(VehiclePosition pos) {
        try {
            /*
             * GOOGLE MAPS API INTEGRATION POINT:
             * Currently, `findNearestStop` likely uses the Haversine formula (straight-line distance) inside RouteMatchingService.
             * * DUMMY / FUTURE UPDATE:
             * When you implement Google Maps, RouteMatchingService.findNearestStop() should be updated
             * to use the Google Maps Roads API (Snap to Roads) or Distance Matrix to determine exactly
             * which stop the bus is closest to along the actual road network, rather than a straight line.
             * * This service doesn't need to call GMaps directly; it just trusts RouteMatchingService.
             */

            // SAFE BY DESIGN GUARD CLAUSE: Validate inputs before processing
            if (pos.getLat() == null || pos.getLon() == null) {
                log.warn("Vehicle {} has no GPS coordinates. Cannot compute adherence.", pos.getVehicleId());
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }

            if (pos.getTripId() == null) {
                log.warn("Bus {} sta trasmettendo posizione [{},{}] ma non ha tripId — " +
                                "bus attivo senza corsa assegnata. Verificare assegnazione.",
                        pos.getVehicleId(), pos.getLat(), pos.getLon());
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }
            String nearestStopId = routeMatchingService.findNearestStopOnTrip(
                    pos.getTripId(), pos.getLat(), pos.getLon());

            if (nearestStopId == null) {
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }

            int nowSeconds = LocalTime.now(ITALY_TZ).toSecondOfDay();

            String routeId = pos.getRouteId() != null ? pos.getRouteId()
                    : (pos.getMatchedRouteId() != null ? pos.getMatchedRouteId() : "UNKNOWN_ROUTE");

            int scheduledSeconds = routeMatchingService.getScheduledArrival(routeId, nearestStopId, nowSeconds);

            if (scheduledSeconds < 0) {
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }

            int delaySeconds = nowSeconds - scheduledSeconds;
            int delayMinutes = delaySeconds / 60;

            pos.setDelayMinutes(delayMinutes);
            pos.setScheduleStatus(statusFromDelay(delayMinutes));

            // Write historical "Stop Arrival" event to InfluxDB for the Analytics Dashboard
            int estimatedPassengers = pos.getBleDeviceCount() != null ? (int)(pos.getBleDeviceCount() * 0.6) : 0;

            Point arrivalEvent = Point.measurement("stop_arrival")
                    .addTag("vehicle_id", pos.getVehicleId())
                    .addTag("stop_id", nearestStopId)
                    .addTag("route_id", routeId)
                    .addField("bus_id", pos.getBusId() != null ? pos.getBusId() : 0)
                    .addField("delay_minutes", delayMinutes)
                    .addField("estimated_passengers", estimatedPassengers)
                    .time(Instant.now(), WritePrecision.S);

            influxWriteApi.writePoint(arrivalEvent);

            log.debug("Vehicle {} at stop {}: {} minutes delay → {}",
                    pos.getVehicleId(), nearestStopId, delayMinutes, pos.getScheduleStatus());

        } catch (Exception e) {
            log.warn("Could not compute adherence for {}: {}", pos.getVehicleId(), e.getMessage());
            pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
        }
    }

}