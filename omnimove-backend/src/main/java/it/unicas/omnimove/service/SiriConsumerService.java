package it.unicas.omnimove.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import it.unicas.omnimove.dto.siri.Siri;
import it.unicas.omnimove.dto.BusTelemetryDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SiriConsumerService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final TelemetrySyncService telemetrySyncService;

    public SiriConsumerService(TelemetrySyncService telemetrySyncService) {
        this.telemetrySyncService = telemetrySyncService;
    }

    // Disabilitato: i dati arrivano ora tramite SSE stream da CassiTrack (TelemetrySyncService)
    // @Scheduled(fixedRate = 5000)
    public void fetchAndProcessSiriData() {
        String url = "http://localhost:8080/api/v1/siri/vehicle-monitoring";

        try {
            Siri siriResponse = restTemplate.getForObject(url, Siri.class);

            if (siriResponse != null &&
                    siriResponse.getServiceDelivery() != null &&
                    siriResponse.getServiceDelivery().getVehicleMonitoringDelivery() != null) {

                List<Siri.VehicleActivity> activities = siriResponse.getServiceDelivery()
                        .getVehicleMonitoringDelivery()
                        .getVehicleActivity();

                List<BusTelemetryDTO> dtoList = new ArrayList<>();

                System.out.println("[SIRI-AUTOMATIC] Ricevuto pacchetto SIRI con " + activities.size() + " attività. Inizio elaborazione...");
                for (Siri.VehicleActivity activity : activities) {
                    Siri.MonitoredVehicleJourney journey = activity.getMonitoredVehicleJourney();

                    // Accessibility → Boolean
                    Boolean wheelchairAccessible = journey.getAccessibility() != null
                            ? journey.getAccessibility().getWheelchairAccess() : null;

                    // FramedVehicleJourneyRef → tripId
                    String tripId = journey.getFramedVehicleJourneyRef() != null
                            ? journey.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef() : null;

                    // MonitoredCall → nextStop
                    String nextStop = journey.getMonitoredCall() != null
                            ? journey.getMonitoredCall().getStopPointName() : null;

                    // PreviousCalls → lastStop
                    String lastStop = (journey.getPreviousCalls() != null && !journey.getPreviousCalls().isEmpty())
                            ? journey.getPreviousCalls().get(0).getStopPointName() : null;

                    // Extensions → velocity, numberOfSeats
                    float speed = 0f;
                    Integer numeroPosti = null;
                    if (journey.getExtensions() != null) {
                        speed = journey.getExtensions().getVelocity() != null
                                ? journey.getExtensions().getVelocity().floatValue() : 0f;
                        numeroPosti = journey.getExtensions().getNumberOfSeats();
                    }

                    BusTelemetryDTO dto = BusTelemetryDTO.builder()
                            .busId(journey.getVehicleRef())
                            .latitude(journey.getVehicleLocation() != null ? (float) journey.getVehicleLocation().getLatitude()  : 0f)
                            .longitude(journey.getVehicleLocation() != null ? (float) journey.getVehicleLocation().getLongitude() : 0f)
                            .speed(speed)
                            .timestamp(activity.getRecordedAtTime() != null ? Instant.parse(activity.getRecordedAtTime()) : Instant.now())
                            .wheelchairAccessible(wheelchairAccessible)
                            .numeroPosti(numeroPosti)
                            .lastStopRegistered(lastStop)
                            .tripId(tripId)
                            .nextStop(nextStop)
                            .build();

                    dtoList.add(dto);
                }

                if (!dtoList.isEmpty()) {
                    telemetrySyncService.saveToInfluxDB(dtoList);
                    telemetrySyncService.saveToRedis(dtoList);
                }
            }
        } catch (Exception e) {
            System.err.println("Impossibile recuperare dati SIRI da Cassitrack: " + e.getMessage());
        }
    }
}