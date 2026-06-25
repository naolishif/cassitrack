package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.StopArrivalDTO;
import it.unicas.cassitrack.model.Stop;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import it.unicas.cassitrack.model.ScheduledStop;
import it.unicas.cassitrack.repository.ScheduledStopRepository;

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
    private final ScheduledStopRepository scheduledStopRepository;

    private static final ZoneId ITALY_TZ =
            ZoneId.of("Europe/Rome");


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
    private StopArrivalDTO computeArrival(VehiclePosition bus, String targetStopId) {
        try {
            Long seqEta = computeSequenceEta(bus, targetStopId);
            // null = questo bus NON e' un arrivo valido per questa fermata:
            // non la serve, oppure l'ha gia' superata in questo giro. Lo escludiamo.
            if (seqEta == null) return null;
            long etaSeconds = seqEta;

            if (etaSeconds > 1800) return null;   // filtro display: non mostriamo arrivi oltre 30 min
            Instant estimatedArrival = Instant.now().plusSeconds(etaSeconds);

            // Confronto con l'orario di tabella per calcolare il ritardo.
            // String routeId = bus.getMatchedRouteId() != null
            //        ? bus.getMatchedRouteId() : bus.getRouteId();
            // matchedRouteId non e' implementato (vale "UNKNOWN_ROUTE"): usiamo la
            // linea reale pubblicata dal simulatore. matchedRouteId solo come ripiego.
            String routeId = bus.getRouteId() != null
                    ? bus.getRouteId() : bus.getMatchedRouteId();
            int nowSeconds = LocalTime.now(ITALY_TZ).toSecondOfDay();
            int scheduledSec = routeId != null
                    ? routeMatchingService.getScheduledArrival(routeId, targetStopId, nowSeconds)
                    : -1;

            Instant scheduledArrival = scheduledSec > 0
                    ? Instant.now().plusSeconds(scheduledSec - nowSeconds)
                    : estimatedArrival;
            int delayMinutes = scheduledSec > 0
                    ? (int)((estimatedArrival.getEpochSecond()
                    - scheduledArrival.getEpochSecond()) / 60)
                    : 0;

            return StopArrivalDTO.builder()
                    .vehicleId(bus.getVehicleId())
                    .routeId(routeId)
                    .routeName(bus.getRouteName() != null ? bus.getRouteName() : routeId)
                    .scheduledArrival(scheduledArrival)
                    .estimatedArrival(estimatedArrival)
                    .delayMinutes(delayMinutes)
                    .scheduleStatus(ScheduleAdherenceService.statusFromDelay(delayMinutes).name())
                    .build();

        } catch (Exception e) {
            log.warn("ETA computation failed for bus {} to stop {}: {}",
                    bus.getVehicleId(), targetStopId, e.getMessage());
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

    /** ETA in secondi sommando i tratti dal DB, o null se non calcolabile. */
    private Long computeSequenceEta(VehiclePosition bus, String targetStopId) {
        // NOTA: non usiamo bus.getMatchedRouteId() per recuperare la sequenza.
        // Quel campo dovrebbe contenere la linea DEDOTTA dal GPS (route matching),
        // ma al momento non e' implementato: nessuno lo valorizza davvero e finisce
        // sempre a "UNKNOWN_ROUTE". Ci appoggiamo quindi a tripId/routeId, che il
        // simulatore pubblica leggendoli dal DB e sono affidabili.
        // TODO: quando il route matching sara' reale, valutare se preferire
        //       matchedRouteId come fonte primaria della linea.
        List<ScheduledStop> seq;
        if (bus.getTripId() != null) {
            seq = scheduledStopRepository.findByTripIdOrderByStopSequenceAsc(bus.getTripId());
        } else {
            String routeId = bus.getRouteId() != null ? bus.getRouteId() : bus.getMatchedRouteId();
            if (routeId == null) return null;
            seq = scheduledStopRepository.findRepresentativeSequence(routeId);
        }
        if (seq.isEmpty()) return null;

        String anchorStop = (bus.getNearestStopId() != null)
                ? bus.getNearestStopId()
                : (bus.getTripId() != null && bus.getLat() != null && bus.getLon() != null
                ? routeMatchingService.findNearestStopOnTrip(bus.getTripId(), bus.getLat(), bus.getLon())
                : null);
        if (anchorStop != null && targetStopId.equals(anchorStop)) return 0L;

        int anchorIdx = indexOfStop(seq, anchorStop, 0);
        if (anchorIdx < 0) return null;
        int targetIdx = indexOfStop(seq, targetStopId, anchorIdx + 1);   // prossima occorrenza: gestisce gli anelli
        if (targetIdx < 0) return null;

        // L'ETA e' la differenza tra gli orari di tabella della fermata target
        // e di quella attuale (ancora). Gli arrival_seconds includono gia' le
        // soste, quindi questa differenza conta sia i tragitti sia le fermate
        // intermedie. NB: include anche la sosta alla fermata attuale (bus
        // considerato ancora fermo li'). Per assumerlo invece in uscita,
        // sottrarre una sosta (-60).
        long eta = (long) seq.get(targetIdx).getArrivalSeconds()
                - seq.get(anchorIdx).getArrivalSeconds();
        return eta > 0 ? eta : null;
    }

    private int indexOfStop(List<ScheduledStop> seq, String stopId, int from) {
        if (stopId == null) return -1;
        for (int i = Math.max(0, from); i < seq.size(); i++) {
            if (seq.get(i).getStopId().equals(stopId)) return i;
        }
        return -1;
    }

}