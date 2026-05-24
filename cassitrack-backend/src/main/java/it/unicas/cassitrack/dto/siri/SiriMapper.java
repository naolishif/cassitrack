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