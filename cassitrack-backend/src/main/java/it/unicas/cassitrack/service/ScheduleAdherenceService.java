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
            if (pos.getLat() == null || pos.getLon() == null) {
                log.warn("Vehicle {} senza coordinate GPS.", pos.getVehicleId());
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }
            if (pos.getTripId() == null) {
                log.warn("Bus {} trasmette senza tripId assegnato.", pos.getVehicleId());
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }

            // Rileva se il bus è FISICAMENTE arrivato a una fermata (entro il raggio)
            var arrival = routeMatchingService.detectStopArrival(
                    pos.getTripId(), pos.getLat(), pos.getLon());

            if (arrival == null) {
                // Bus in transito tra due fermate: non c'è un arrivo da registrare.
                // Mantieni l'ultimo stato/ritardo calcolato, non ricalcolare nulla.
                vehicleStateCache.get(pos.getVehicleId()).ifPresent(prev -> {
                    pos.setDelayMinutes(prev.getDelayMinutes());
                    pos.setScheduleStatus(prev.getScheduleStatus() != null
                            ? prev.getScheduleStatus() : ScheduleStatus.UNKNOWN);
                    pos.setLastStopRegisteredId(prev.getLastStopRegisteredId());
                });
                if (pos.getScheduleStatus() == null) pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                return;
            }

            // Il bus è a una fermata. L'abbiamo già registrata in questo arrivo?
            String lastRegistered = vehicleStateCache.get(pos.getVehicleId())
                    .map(VehiclePosition::getLastStopRegisteredId)
                    .orElse(null);

            if (arrival.stopId().equals(lastRegistered)) {
                // Stesso arrivo già contabilizzato: mantieni il ritardo, non riscrivere.
                vehicleStateCache.get(pos.getVehicleId()).ifPresent(prev -> {
                    pos.setDelayMinutes(prev.getDelayMinutes());
                    pos.setScheduleStatus(prev.getScheduleStatus());
                });
                pos.setLastStopRegisteredId(arrival.stopId());
                return;
            }

            // NUOVO ARRIVO → calcola il ritardo: ora di arrivo − orario previsto
            if (arrival.scheduledSeconds() == null) {
                pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
                pos.setLastStopRegisteredId(arrival.stopId());
                return;
            }

            int nowSeconds   = LocalTime.now(ITALY_TZ).toSecondOfDay();
            int delayMinutes = (nowSeconds - arrival.scheduledSeconds()) / 60;

            pos.setDelayMinutes(delayMinutes);
            pos.setScheduleStatus(statusFromDelay(delayMinutes));
            pos.setLastStopRegisteredId(arrival.stopId());

            // Registra l'evento di arrivo (una volta sola per fermata) su InfluxDB
            String routeId = pos.getRouteId() != null ? pos.getRouteId()
                    : (pos.getMatchedRouteId() != null ? pos.getMatchedRouteId() : "UNKNOWN_ROUTE");
            int estimatedPassengers = pos.getBleDeviceCount() != null
                    ? (int)(pos.getBleDeviceCount() * 0.6) : 0;

            Point arrivalEvent = Point.measurement("stop_arrival")
                    .addTag("vehicle_id", pos.getVehicleId())
                    .addTag("stop_id",    arrival.stopId())
                    .addTag("route_id",   routeId)
                    .addField("bus_id",               pos.getBusId() != null ? pos.getBusId() : 0)
                    .addField("delay_minutes",         delayMinutes)
                    .addField("estimated_passengers",  estimatedPassengers)
                    .time(Instant.now(), WritePrecision.S);
            influxWriteApi.writePoint(arrivalEvent);

            log.info("Bus {} ARRIVATO a {} → ritardo {} min ({})",
                    pos.getVehicleId(), arrival.stopId(), delayMinutes, pos.getScheduleStatus());

        } catch (Exception e) {
            log.warn("Adherence non calcolabile per {}: {}", pos.getVehicleId(), e.getMessage());
            pos.setScheduleStatus(ScheduleStatus.UNKNOWN);
        }
    }

}