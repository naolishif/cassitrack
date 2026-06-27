package it.unicas.cassitrack.dto.siri;

import it.unicas.cassitrack.model.VehiclePosition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SiriMapper {

    /**
     * Costruisce un pacchetto SIRI standard dalla cache Redis (VehicleStateCache).
     * Unico punto di ingresso per la produzione di SIRI in CassiTrack.
     */
    public static Siri toSiriFromCache(Collection<VehiclePosition> vehicles) {
        List<Siri.VehicleActivity> activities = new ArrayList<>();
        String today = LocalDate.now().toString(); // "2026-06-26"

        for (VehiclePosition v : vehicles) {

            // ── VehicleLocation ───────────────────────────────────────────────
            Siri.VehicleLocation location = new Siri.VehicleLocation(
                    v.getLon() != null ? v.getLon() : 0.0,
                    v.getLat() != null ? v.getLat() : 0.0);

            // ── FramedVehicleJourneyRef ───────────────────────────────────────
            Siri.FramedVehicleJourneyRef journeyRef = null;
            if (v.getTripId() != null) {
                journeyRef = new Siri.FramedVehicleJourneyRef(today, v.getTripId());
            }

            // ── Occupancy (calcolato da passeggeri/capacità) ──────────────────
            String occupancy = computeOccupancy(v.getPassengers(), v.getCapacity());

            // ── Delay in formato ISO 8601 ─────────────────────────────────────
            String delay = null;
            if (v.getDelayMinutes() != null) {
                int d = v.getDelayMinutes();
                delay = d == 0 ? "PT0S" : (d > 0 ? "PT" + d + "M" : "-PT" + Math.abs(d) + "M");
            }

            // ── Accessibility ─────────────────────────────────────────────────
            Siri.Accessibility accessibility = null;
            if (v.getWheelchairAccessible() != null) {
                accessibility = new Siri.Accessibility(v.getWheelchairAccessible());
            }

            // ── MonitoredCall (prossima fermata) ──────────────────────────────
            Siri.MonitoredCall monitoredCall = null;
            if (v.getNextStop() != null) {
                monitoredCall = new Siri.MonitoredCall(v.getNextStop());
            }

            // ── PreviousCalls (ultima fermata registrata) ─────────────────────
            List<Siri.PreviousCall> previousCalls = null;
            if (v.getNearestStop() != null) {
                previousCalls = List.of(new Siri.PreviousCall(v.getNearestStop()));
            }

            // ── Extensions (campi non standard) ──────────────────────────────
            Siri.Extensions extensions = new Siri.Extensions();
            extensions.setVelocity(v.getSpeedKmh());
            extensions.setNumberOfSeats(v.getNumeroPosti());

            // ── MonitoredVehicleJourney ───────────────────────────────────────
            Siri.MonitoredVehicleJourney journey = new Siri.MonitoredVehicleJourney();
            journey.setVehicleRef(v.getVehicleId());
            journey.setFramedVehicleJourneyRef(journeyRef);
            journey.setVehicleLocation(location);
            journey.setBearing(v.getHeadingDeg());
            journey.setOccupancy(occupancy);
            journey.setDelay(delay);
            journey.setAccessibility(accessibility);
            journey.setMonitoredCall(monitoredCall);
            journey.setPreviousCalls(previousCalls);
            journey.setExtensions(extensions);

            // ── VehicleActivity ───────────────────────────────────────────────
            Siri.VehicleActivity activity = new Siri.VehicleActivity();
            if (v.getTimestamp() != null) activity.setRecordedAtTime(v.getTimestamp().toString());
            activity.setMonitoredVehicleJourney(journey);
            activities.add(activity);
        }

        Siri.VehicleMonitoringDelivery vmd = new Siri.VehicleMonitoringDelivery();
        vmd.setVehicleActivity(activities);

        Siri.ServiceDelivery sd = new Siri.ServiceDelivery();
        sd.setVehicleMonitoringDelivery(vmd);

        return new Siri(sd);
    }

    /**
     * Calcola l'OccupancyStatus SIRI dal rapporto passeggeri/capacità.
     * Valori standard: empty | manySeatsAvailable | seatsAvailable |
     *                  fewSeatsAvailable | standingAvailable | full
     */
    private static String computeOccupancy(Integer passengers, Integer capacity) {
        if (passengers == null || capacity == null || capacity == 0) return null;
        double ratio = (double) passengers / capacity;
        if (ratio < 0.20) return "empty";
        if (ratio < 0.50) return "manySeatsAvailable";
        if (ratio < 0.65) return "seatsAvailable";
        if (ratio < 0.80) return "fewSeatsAvailable";
        if (ratio < 0.95) return "standingAvailable";
        return "full";
    }
}
