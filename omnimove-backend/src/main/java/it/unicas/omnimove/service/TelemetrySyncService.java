package it.unicas.omnimove.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import it.unicas.omnimove.dto.BusTelemetryDTO;
import it.unicas.omnimove.dto.siri.Siri;
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
import reactor.core.publisher.Hooks;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TelemetrySyncService {

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper = new XmlMapper();

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

    @Value("${cassitrack.api.token}") // Added this token to authenticate in the SSE connection with Cassitrack
    private String cassitrackApiToken;

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
                                // Removed ble_device_count and numero_posti as per cleanup request
                                .addField("wheelchair_accessible", bus.getWheelchairAccessible() != null ? bus.getWheelchairAccessible() : false)
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

        // Sopprime il rumore di un bug noto in reactor-netty: quando un SSE stream
        // si chiude normalmente, Netty può tentare di liberare un buffer già rilasciato.
        // L'eccezione viene droppata (non propagata) ma genera log spuri. La filtriamo qui.
        Hooks.onErrorDropped(e -> {
            if (!(e instanceof io.netty.util.IllegalReferenceCountException)) {
                log.error("[OMNIMOVE] onErrorDropped inatteso: {}", e.getMessage());
            }
        });

        WebClient webClient = WebClient.create(cassitrackBaseUrl);

        webClient.get()
                .uri("/telemetry/stream")
                .header("X-Api-Key", cassitrackApiToken)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .filter(event -> "telemetry-update".equals(event.event()))
                .retryWhen(Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(5))
                        .doBeforeRetry(signal -> System.out.println(
                                "[OMNIMOVE] Stream interrotto (" + signal.failure().getMessage() +
                                "), riconnessione in 5 secondi... (tentativo " + (signal.totalRetries() + 1) + ")")))
                .subscribe(
                        event -> {
                            String xml = event.data();
                            if (xml == null || xml.isBlank()) return;
                            try {
                                Siri siri = xmlMapper.readValue(xml, Siri.class);
                                List<BusTelemetryDTO> data = siriToDto(siri);
                                if (!data.isEmpty()) {
                                    System.out.println("[OMNIMOVE] Ricevuto pacchetto SIRI XML via SSE: " + data.size() + " attività.");
                                    saveToRedis(data);
                                    saveToInfluxDB(data);

                                }
                            } catch (Exception e) {
                                System.err.println("[OMNIMOVE] Errore deserializzazione SIRI XML: " + e.getMessage());
                            }
                        },
                        error -> System.err.println("[OMNIMOVE] Errore fatale nello stream (non recuperabile): " + error.getMessage()),
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
                        // Removed ble_device_count and numero_posti fields from Omnimove Influx writes
                        .addField("wheelchair_accessible", bus.getWheelchairAccessible() != null ? bus.getWheelchairAccessible() : false)
                        .addField("delay", bus.getDelay() != null ? bus.getDelay() : 0)
                        .addField("last_stop_registered", bus.getLastStopRegistered() != null ? bus.getLastStopRegistered() : "")
                        .addField("trip_id", bus.getTripId() != null ? bus.getTripId() : "")
                        .addField("passengers", bus.getPassengers() != null ? bus.getPassengers() : 0)
                        .addField("capacity", bus.getCapacity() != null ? bus.getCapacity() : 0)
                        .time(bus.getTimestamp(), WritePrecision.MS);

                if (bus.getNextStop() != null) point.addField("next_stop", bus.getNextStop());

                writeApi.writePoint(point);
            }
            System.out.println("[OMNIMOVE] Dati salvati con successo su InfluxDB local (Porta 8087) con info Bus aggiuntive!");
        } catch (Exception e) {
            System.err.println("[OMNIMOVE] Errore durante la scrittura su InfluxDB: " + e.getMessage());
        } finally {
            client.close();
        }
    }

    private List<BusTelemetryDTO> siriToDto(Siri siri) {
        List<BusTelemetryDTO> result = new ArrayList<>();
        if (siri.getServiceDelivery() == null
                || siri.getServiceDelivery().getVehicleMonitoringDelivery() == null) return result;

        List<Siri.VehicleActivity> activities = siri.getServiceDelivery()
                .getVehicleMonitoringDelivery().getVehicleActivity();
        if (activities == null) return result;

        for (Siri.VehicleActivity activity : activities) {
            Siri.MonitoredVehicleJourney journey = activity.getMonitoredVehicleJourney();
            if (journey == null) continue;

            // Wheelchair: ora da Extensions/WheelchairAccess; fallback all'elemento
            // legacy <Accessibility> per retro-compatibilità con pacchetti vecchi.
            Boolean wheelchair = null;
            if (activity.getExtensions() != null && activity.getExtensions().getWheelchairAccess() != null) {
                wheelchair = activity.getExtensions().getWheelchairAccess();
            } else if (journey.getAccessibility() != null) {
                wheelchair = journey.getAccessibility().getWheelchairAccess();
            }

            // TripId: da FramedVehicleJourneyRef.datedVehicleJourneyRef
            String tripId = journey.getFramedVehicleJourneyRef() != null
                    ? journey.getFramedVehicleJourneyRef().getDatedVehicleJourneyRef() : null;

            // NextStop: da MonitoredCall.stopPointName
            String nextStop = journey.getMonitoredCall() != null
                    ? journey.getMonitoredCall().getStopPointName() : null;

            // LastStop: da PreviousCalls (prima voce)
            String lastStop = (journey.getPreviousCalls() != null && !journey.getPreviousCalls().isEmpty())
                    ? journey.getPreviousCalls().get(0).getStopPointName() : null;

            // Delay: da stringa ISO 8601 a minuti interi (es. "PT2M" → 2)
            Integer delayMinutes = parseDelayMinutes(journey.getDelay());

            // Velocity e NumberOfSeats: da Extensions
            float speed = 0f;
            Integer numeroPosti = null;
            if (activity.getExtensions() != null) {
                speed = activity.getExtensions().getVelocity() != null
                        ? activity.getExtensions().getVelocity().floatValue() : 0f;
                numeroPosti = activity.getExtensions().getNumberOfSeats();
            }

            result.add(BusTelemetryDTO.builder()
                    .busId(journey.getVehicleRef())
                    .latitude(journey.getVehicleLocation() != null ? (float) journey.getVehicleLocation().getLatitude()  : 0f)
                    .longitude(journey.getVehicleLocation() != null ? (float) journey.getVehicleLocation().getLongitude() : 0f)
                    .speed(speed)
                    .timestamp(activity.getRecordedAtTime() != null ? Instant.parse(activity.getRecordedAtTime()) : Instant.now())
                    .wheelchairAccessible(wheelchair)
                    .numeroPosti(numeroPosti)
                    .capacity(numeroPosti)
                    .delay(delayMinutes)
                    .lastStopRegistered(lastStop)
                    .tripId(tripId)
                    .nextStop(nextStop)
                    .passengers(activity.getExtensions() != null ? activity.getExtensions().getPassengers() : null)
                    .build());
        }
        return result;
    }

    /** Converte durata ISO 8601 in minuti interi. Supporta PT0S, PT2M, -PT1M. */
    private Integer parseDelayMinutes(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            boolean negative = iso.startsWith("-");
            String s = iso.replace("-", "");          // rimuovi segno
            s = s.replace("PT", "").replace("M", "").replace("S", "");
            int minutes = s.isBlank() ? 0 : Integer.parseInt(s);
            return negative ? -minutes : minutes;
        } catch (Exception e) {
            return null;
        }
    }

    // Salva lo stato istantaneo su Redis (Porta 6380)
    public void saveToRedis(List<BusTelemetryDTO> telemetryList) {
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