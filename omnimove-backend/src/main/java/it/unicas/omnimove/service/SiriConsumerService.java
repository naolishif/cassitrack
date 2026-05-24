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

    // Costruttore corretto (senza doppioni o graffe extra)
    public SiriConsumerService(TelemetrySyncService telemetrySyncService) {
        this.telemetrySyncService = telemetrySyncService;
    }

    @Scheduled(fixedRate = 5000)
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

                // Creiamo una lista temporanea di DTO da passare al tuo metodo di scrittura
                List<BusTelemetryDTO> dtoList = new ArrayList<>();

                for (Siri.VehicleActivity activity : activities) {
                    Siri.MonitoredVehicleJourney journey = activity.getMonitoredVehicleJourney();

                    // Estraiamo i dati dall'XML SIRI
                    String busId = journey.getVehicleRef();
                    double lat = journey.getVehicleLocation().getLatitude();
                    double lon = journey.getVehicleLocation().getLongitude();
                    double speed = journey.getVelocity();
                    String timestampStr = activity.getRecordedAtTime();

                    System.out.println("[SIRI-AUTOMATIC] Ricevuto e in elaborazione Bus: " + busId);

                    // Ricostruiamo il BusTelemetryDTO usando il Builder che hai già sul DTO
                    BusTelemetryDTO dto = BusTelemetryDTO.builder()
                            .busId(busId)
                            .latitude((float) lat)  // Cast a float richiesto dal tuo DTO
                            .longitude((float) lon) // Cast a float richiesto dal tuo DTO
                            .speed((float) speed)   // Cast a float richiesto dal tuo DTO
                            .timestamp(timestampStr != null ? Instant.parse(timestampStr) : Instant.now())
                            .bleDeviceCount(0) // SIRI non prevede i BLE device, lo impostiamo a 0 di default
                            .build();

                    dtoList.add(dto);
                }

                // Se abbiamo trovato dei bus, sfruttiamo il tuo metodo esistente per salvare su InfluxDB!
                if (!dtoList.isEmpty()) {
                    telemetrySyncService.saveToInfluxDB(dtoList);
                }
            }
        } catch (Exception e) {
            System.err.println("Impossibile recuperare dati SIRI da Cassitrack: " + e.getMessage());
        }
    }
}