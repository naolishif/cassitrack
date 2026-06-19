package it.unicas.cassitrack.dto.siri;

import it.unicas.cassitrack.dto.BusTelemetryDTO;
import java.util.ArrayList;
import java.util.List;

public class SiriMapper {

    public static Siri toSiri(List<BusTelemetryDTO> busList) {

        List<Siri.VehicleActivity> activities = new ArrayList<>();

        // Cicliamo su tutti gli autobus ricevuti da InfluxDB
        for (BusTelemetryDTO bus : busList) {
            Siri.VehicleLocation location = new Siri.VehicleLocation(bus.getLongitude(), bus.getLatitude());

            Siri.MonitoredVehicleJourney journey = new Siri.MonitoredVehicleJourney();
            journey.setVehicleRef(bus.getBusId());
            journey.setVehicleLocation(location);
            journey.setVelocity(bus.getSpeed());
            System.out.println(
                    "[SIRI] bus=" + bus.getBusId() +
                            " passengers=" + bus.getPassengers() +
                            " capacity=" + bus.getCapacity()
            );
            journey.setPassengers(bus.getPassengers());
            journey.setCapacity(bus.getCapacity());

            // 🚌 ── COSTRUIAMO I NUOVI DATI DA INVIARE A OMNIMOVE ──

            // 1. Gestione Accessibilità Disabili (Boolean -> Stringa standard SIRI)
            if (bus.getPostoDisabili() != null) {
                // Se true diventa "true", se false diventa "false"
                journey.setWheelchairAccessible(bus.getPostoDisabili() ? "true" : "false");
            } else {
                // Valore di ripiego previsto dallo standard SIRI se il dato manca
                journey.setWheelchairAccessible("unknown");
            }

            // 2. Passiamo il numero di posti totale
            journey.setNumberOfSeats(bus.getNumeroPosti());
            // 3. Delay in secondi
            if (bus.getDelay() != null) {
                journey.setDelay(bus.getDelay());
            }

            // 4. Ultima fermata registrata
            if (bus.getLastStopRegistered() != null) {
                journey.setLastStopRef(bus.getLastStopRegistered());
            }

            // 5. Trip ID corrente
            if (bus.getTripId() != null) {
                journey.setFramedVehicleJourneyRef(bus.getTripId());
            }

            Siri.VehicleActivity activity = new Siri.VehicleActivity();
            // Inseriamo il timestamp reale di quando il bus ha inviato il dato!
            if (bus.getTimestamp() != null) {
                activity.setRecordedAtTime(bus.getTimestamp().toString());
            }
            activity.setMonitoredVehicleJourney(journey);

            activities.add(activity);
        }

        Siri.VehicleMonitoringDelivery vmd = new Siri.VehicleMonitoringDelivery();
        vmd.setVehicleActivity(activities);

        Siri.ServiceDelivery sd = new Siri.ServiceDelivery();
        sd.setVehicleMonitoringDelivery(vmd);

        return new Siri(sd);
    }
}