package it.unicas.cassitrack.controller;

import it.unicas.cassitrack.dto.BusTelemetryDTO;
import it.unicas.cassitrack.service.InfluxService; // Assicurati che il nome della classe sia corretto (InfluxService o InfluxTelemetryService)
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/telemetry")
public class DataExportController {

    //@Autowired
    private InfluxService influxService;

    @Value("${sse.api-token}")
    private String expectedToken;

    // Lista speciale thread-safe per memorizzare i client connessi (es. Omnimove)
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public DataExportController(InfluxService influxService) {
        this.influxService = influxService;
    }


    /*---------- USE RESTAPI: DEPRECATED ----------*/
    @GetMapping("/latest")
    public ResponseEntity<List<BusTelemetryDTO>> getLatestTelemetry() {

        List<BusTelemetryDTO> data = influxService.getLatestTelemetry();

        return ResponseEntity.ok(data);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetry(@RequestHeader(value = "X-Api-Key", required = false) String receivedToken,
                                      HttpServletResponse response) {

        if (!MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                (receivedToken != null ? receivedToken : "").getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }

        // Creiamo un Emitter senza timeout (-1L) così la connessione resta aperta indefinitamente
        SseEmitter emitter = new SseEmitter(-1L);
        this.emitters.add(emitter);

        // Se Omnimove si disconnette o ci sono errori, facciamo pulizia rimuovendolo dalla lista
        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((e) -> this.emitters.remove(emitter));

        return emitter;
    }

    // 2. Il timer interno: ogni 60 secondi estrae i dati e li "spinge" nei tubi aperti
    @Scheduled(fixedRate = 60000)
    public void pushTelemetryData() {
        // Se Omnimove è spento, la lista è vuota: non facciamo query inutili al DB
        if (emitters.isEmpty()) {
            return;
        }

        // Recuperiamo i dati reali dal database (usando il metodo che abbiamo allineato ieri)
        List<BusTelemetryDTO> latestData = influxService.getLatestTelemetry();

        if (latestData != null && !latestData.isEmpty()) {
            // Spingiamo il pacchetto dati a tutti i client in ascolto
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("telemetry-update") // Nome dell'evento
                            .data(latestData));       // I dati veri e propri
                } catch (IOException e) {
                    // Se l'invio fallisce (es. Omnimove si è spento bruscamente), chiudiamo il tubo
                    emitter.complete();
                    emitters.remove(emitter);
                }
            }
        }
    }
}