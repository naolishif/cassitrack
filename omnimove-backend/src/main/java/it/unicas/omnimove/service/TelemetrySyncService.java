package it.unicas.omnimove.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import it.unicas.omnimove.dto.BusTelemetryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@Slf4j
public class TelemetrySyncService {

    private final RestClient restClient;

    @Value("${cassitrack.api.base-url}")
    private String cassitrackBaseUrl;

    @Value("${influx.url}")
    private String influxUrl;

    @Value("${influx.token}")
    private String token;

    // CAMBIATO IL NOME DA "org" A "influxOrg" PER EVITARE CONFLITTI CON I PACCHETTI JAVA
    @Value("${influx.org}")
    private String influxOrg;

    @Value("${influx.bucket}")
    private String bucket;

    /*---------- DEPRECATED ----------*/
    public TelemetrySyncService(@Value("${cassitrack.api.base-url}") String cassitrackBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(cassitrackBaseUrl)
                .build();
    }

    @Scheduled(fixedRate = 60000)
    public void fetchTelemetryFromCassitrack() {
        log.info("Avvio sincronizzazione telemetria da Cassitrack...");

        try {
            List<BusTelemetryDTO> data = restClient.get()
                    .uri("/telemetry/latest")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<BusTelemetryDTO>>() {});

            if (data != null && !data.isEmpty()) {
                log.info("Ricevuti {} record da Cassitrack. Scrittura su InfluxDB 8087...", data.size());

                // Usa influxOrg qui sotto
                try (InfluxDBClient influxClient = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket)) {

                    var writeApi = influxClient.getWriteApiBlocking();

                    for (BusTelemetryDTO bus : data) {
                        Point point = Point.measurement("bus_telemetry")
                                .addTag("bus_id", bus.getBusId())
                                .addField("latitude", bus.getLatitude())
                                .addField("longitude", bus.getLongitude())
                                .addField("speed", bus.getSpeed())
                                .addField("ble_device_count", bus.getBleDeviceCount())
                                .time(bus.getTimestamp(), WritePrecision.MS);

                        writeApi.writePoint(point);
                    }

                    log.info("Sincronizzazione completata con successo nel database di Omnimove!");
                }

            } else {
                log.warn("Nessun dato di telemetria ricevuto da Cassitrack.");
            }

        } catch (Exception e) {
            log.error("Errore durante il processo di sincronizzazione: {}", e.getMessage());
        }
    }



    //SSE Communication
    // Questo metodo parte DA SOLO una volta sola all'avvio di Omnimove (Handshake)
    @EventListener(ApplicationReadyEvent.class)
    public void startStreamingSubscription() {
        System.out.println("=== [OMNIMOVE] Avvio sottoscrizione allo stream SSE di Cassitrack... ===");

        // Creiamo il client reattivo puntato su Cassitrack
        WebClient webClient = WebClient.create(cassitrackBaseUrl);

        // Apriamo il flusso continuo verso l'endpoint /stream
        Flux<ServerSentEvent<List<BusTelemetryDTO>>> telemetryStream = webClient.get()
                .uri("/telemetry/stream")
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<List<BusTelemetryDTO>>>() {});

        // Ci mettiamo in ascolto passivo. Ogni volta che arriva un evento ("telemetry-update"), lo elaboriamo
        telemetryStream
                .filter(event -> "telemetry-update".equals(event.event())) // Filtra solo gli eventi corretti
                .subscribe(
                        event -> {
                            List<BusTelemetryDTO> data = event.data();
                            if (data != null && !data.isEmpty()) {
                                System.out.println("[OMNIMOVE] Ricevuto aggiornamento push in tempo reale: " + data.size() + " record.");
                                saveToInfluxDB(data);
                            }
                        },
                        error -> {
                            // Gestione degli errori (es. Cassitrack si spegne)
                            System.err.println("[OMNIMOVE] Errore nello stream: " + error.getMessage() + ". Tentativo di riconnessione tra 5 secondi...");
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            startStreamingSubscription(); // Riconnessione automatica!
                        },
                        () -> System.out.println("[OMNIMOVE] Stream completato (chiuso dal server).")
                );
    }

    // Il metodo di scrittura rimane praticamente identico a ieri, allineato ai campi del simulatore
    public void saveToInfluxDB(List<BusTelemetryDTO> telemetryList) {
        InfluxDBClient client = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket);
        WriteApiBlocking writeApi = client.getWriteApiBlocking();

        try {
            for (BusTelemetryDTO bus : telemetryList) {
                Point point = Point.measurement("vehicle_position")
                        .addTag("vehicle_id", bus.getBusId())
                        .addField("lat", bus.getLatitude())
                        .addField("lon", bus.getLongitude())
                        .addField("speed_kmh", bus.getSpeed())
                        .addField("ble_device_count", bus.getBleDeviceCount())
                        .time(bus.getTimestamp(), WritePrecision.MS);

                writeApi.writePoint(point);
            }
            System.out.println("[OMNIMOVE] Dati salvati con successo su InfluxDB local (Porta 8087)!");
        } catch (Exception e) {
            System.err.println("[OMNIMOVE] Errore durante la scrittura su InfluxDB: " + e.getMessage());
        } finally {
            client.close();
        }
    }
}

