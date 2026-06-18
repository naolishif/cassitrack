package it.unicas.omnimove.service;

import com.fasterxml.jackson.databind.ObjectMapper; // 👈 Nuovo import per convertire in JSON
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
import org.springframework.data.redis.core.StringRedisTemplate; // 👈 Nuovo import per Redis
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
    private final StringRedisTemplate redisTemplate; // 👈 Field per Redis
    private final ObjectMapper objectMapper;         // 👈 Field per JSON

    @Value("${cassitrack.api.base-url}")
    private String cassitrackBaseUrl;

    @Value("${influx.url}")
    private String influxUrl;

    @Value("${influx.token}")
    private String token;

    @Value("${influx.org}")
    private String influxOrg;

    @Value("${influx.bucket}")
    private String bucket;

    // 🛠️ Costruttore aggiornato per iniettare i componenti di Redis e Jackson
    public TelemetrySyncService(
            @Value("${cassitrack.api.base-url}") String cassitrackBaseUrl,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(cassitrackBaseUrl)
                .build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // TO FIX: this
    /*
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

                try (InfluxDBClient influxClient = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket)) {
                    var writeApi = influxClient.getWriteApiBlocking();

                    for (BusTelemetryDTO bus : data) {
                        Point point = Point.measurement("bus_telemetry")
                                .addTag("bus_id", bus.getBusId())
                                .addField("latitude", bus.getLatitude())
                                .addField("longitude", bus.getLongitude())
                                .addField("speed", bus.getSpeed())
                                .addField("ble_device_count", bus.getBleDeviceCount())
                                .addField("numero_posti", bus.getNumeroPosti() != null ? bus.getNumeroPosti() : 0)
                                .addField("posto_disabili", bus.getPostoDisabili() != null ? bus.getPostoDisabili() : false)
                                .time(bus.getTimestamp(), WritePrecision.MS);

                        writeApi.writePoint(point);
                    }
                    log.info("Sincronizzazione completata con successo nel database di Omnimove!");

                    // 🚀 INTERCETTAZIONE: Copiamo i dati anche su Redis
                    saveToRedis(data);
                }
            } else {
                log.warn("Nessun dato di telemetria ricevuto da Cassitrack.");
            }
        } catch (Exception e) {
            log.error("Errore durante il processo di sincronizzazione: {}", e.getMessage());
        }
    }*/

    @EventListener(ApplicationReadyEvent.class)
    public void startStreamingSubscription() {
        System.out.println("=== [OMNIMOVE] Avvio sottoscrizione allo stream SSE di Cassitrack... ===");

        WebClient webClient = WebClient.create(cassitrackBaseUrl);

        Flux<ServerSentEvent<List<BusTelemetryDTO>>> telemetryStream = webClient.get()
                .uri("/telemetry/stream")
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<List<BusTelemetryDTO>>>() {});

        telemetryStream
                .filter(event -> "telemetry-update".equals(event.event()))
                .subscribe(
                        event -> {
                            List<BusTelemetryDTO> data = event.data();
                            if (data != null && !data.isEmpty()) {
                                System.out.println("[OMNIMOVE] Ricevuto aggiornamento push in tempo reale: " + data.size() + " record.");
                                saveToInfluxDB(data);

                                // 🚀 INTERCETTAZIONE: Salviamo lo streaming in tempo reale su Redis
                                saveToRedis(data);
                            }
                        },
                        error -> {
                            System.err.println("[OMNIMOVE] Errore nello stream: " + error.getMessage() + ". Tentativo di riconnessione tra 5 secondi...");
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            startStreamingSubscription();
                        },
                        () -> System.out.println("[OMNIMOVE] Stream completato (chiuso dal server).")
                );
    }

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
                        .addField("numero_posti", bus.getNumeroPosti() != null ? bus.getNumeroPosti() : 0)
                        .addField("posto_disabili", bus.getPostoDisabili() != null ? bus.getPostoDisabili() : false)
                        .addField("delay", bus.getDelay() != null ? bus.getDelay() : 0)
                        .addField("last_stop_registered", bus.getLastStopRegistered() != null ? bus.getLastStopRegistered() : "")
                        .addField("trip_id", bus.getTripId() != null ? bus.getTripId() : "")
                        .addField("passengers", bus.getPassengers() != null ? bus.getPassengers() : 0)
                        .addField("capacity", bus.getCapacity() != null ? bus.getCapacity() : 0)
                        .time(bus.getTimestamp(), WritePrecision.MS);

                writeApi.writePoint(point);
            }
            System.out.println("[OMNIMOVE] Dati salvati con successo su InfluxDB local (Porta 8087) con info Bus aggiuntive!");
        } catch (Exception e) {
            System.err.println("[OMNIMOVE] Errore durante la scrittura su InfluxDB: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    // 🟢 NUOVO METODO: Salva lo stato istantaneo su Redis (Porta 6380)
    private void saveToRedis(List<BusTelemetryDTO> telemetryList) {
        log.info("saveToRedis() chiamato con {} record", telemetryList.size());

        try {
            for (BusTelemetryDTO bus : telemetryList) {
                String redisKey = "bus:latest:" + bus.getBusId();
                String jsonValue = objectMapper.writeValueAsString(bus);

                redisTemplate.opsForValue().set(redisKey, jsonValue);

                log.info("Salvato su Redis: {}", redisKey);
            }

            log.info("Completata scrittura Redis");
        } catch (Exception e) {
            log.error("Errore Redis", e);
        }
    }
}