package it.unicas.omnimove.service;

import it.unicas.omnimove.client.CassitrackClient;
import it.unicas.omnimove.dto.ChatResponse;
import it.unicas.omnimove.dto.StopArrivalDTO;
import it.unicas.omnimove.dto.VehicleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * AI chatbot for OMNIMOVE.
 * Fetches live data from CASSITRACK via REST API,
 * builds context, calls Claude API to answer questions.
 */
@Service
public class AiOrchestrationService {

    private static final Logger log =
            LoggerFactory.getLogger(AiOrchestrationService.class);

    private final CassitrackClient cassitrackClient;

    @Value("${anthropic.api.key}")
    private String apiKey;
    @Value("${anthropic.api.url}")
    private String apiUrl;
    @Value("${anthropic.api.model}")
    private String model;

    private static final String[] STOPS = {
            "CASSINO-STAZIONE", "CASSINO-CENTRO",
            "CASSINO-OSPEDALE", "FOLCARA-VIA", "FOLCARA-CAMPUS"
    };

    public AiOrchestrationService(CassitrackClient cassitrackClient) {
        this.cassitrackClient = cassitrackClient;
    }

    public ChatResponse answer(String question, String language) {
        try {
            String context = buildContext();
            String system = buildSystem(language, context);
            String answer = callClaude(system, question);
            return ChatResponse.builder().answer(answer).success(true).build();
        } catch (Exception e) {
            log.error("AI failed: {}", e.getMessage());
            return ChatResponse.builder().success(false)
                    .answer("Sorry, I could not process your request right now.").build();
        }
    }

    private String buildContext() {
        StringBuilder sb = new StringBuilder();
        String now = LocalDateTime.now(ZoneId.of("Europe/Rome"))
                .format(DateTimeFormatter.ofPattern("HH:mm:ss 'on' EEEE dd MMMM yyyy"));
        sb.append("=== CASSITRACK LIVE DATA ===\nTime in Cassino: ").append(now).append("\n\n");

        List<VehicleDTO> vehicles = cassitrackClient.getActiveVehicles();
        if (vehicles.isEmpty()) {
            sb.append("ACTIVE BUSES: None tracked.\n\n");
        } else {
            sb.append("ACTIVE BUSES (").append(vehicles.size()).append("):\n");
            vehicles.forEach(v -> sb
                    .append("\n  Bus: ").append(v.getVehicleId())
                    .append("\n    Position: lat=").append(String.format("%.5f", v.getLat()))
                    .append(", lon=").append(String.format("%.5f", v.getLon()))
                    .append("\n    Speed: ").append(v.getSpeedKmh() != null
                            ? String.format("%.1f", v.getSpeedKmh()) : "0.0").append(" km/h")
                    .append("\n    Schedule: ").append(v.getScheduleStatus())
                    .append("\n    Crowding: ").append(v.getCrowdingLevel()).append("\n"));
        }

        sb.append("\nETA AT STOPS:\n");
        for (String stopId : STOPS) {
            sb.append("  Stop: ").append(stopId).append("\n");
            try {
                List<StopArrivalDTO> arrivals = cassitrackClient.getArrivalsAtStop(stopId);
                if (arrivals.isEmpty()) {
                    sb.append("    No buses expected soon.\n");
                } else {
                    arrivals.forEach(a -> {
                        long etaMin = Math.max(0,
                                (a.getEstimatedArrival().getEpochSecond()
                                        - System.currentTimeMillis() / 1000) / 60);
                        sb.append("    - Bus ").append(a.getVehicleId())
                                .append(": arrives in ").append(etaMin > 0 ? etaMin + " min" : "<1 min")
                                .append(" (").append(a.getScheduleStatus()).append(")\n");
                    });
                }
            } catch (Exception e) {
                sb.append("    ETA unavailable.\n");
            }
        }
        sb.append("\nSTOPS: CASSINO-STAZIONE=Train Station, CASSINO-CENTRO=City Centre, ");
        sb.append("CASSINO-OSPEDALE=Hospital, FOLCARA-VIA=Via Folcara, FOLCARA-CAMPUS=UNICAS Campus\n");
        sb.append("=== END ===\n");
        return sb.toString();
    }

    private String buildSystem(String language, String context) {
        String lang = "it".equals(language) ? "Always respond in Italian." : "Always respond in English.";
        return "You are the OMNIMOVE assistant for Cassino, Italy. " +
                "Help passengers plan journeys using Bus, Bike, E-Scooter and Walk. " +
                "You have live real-time data from CASSITRACK fleet system. " +
                "Be concise, friendly and helpful. " + lang + "\n\nLive data:\n" + context;
    }

    @SuppressWarnings("unchecked")
    private String callClaude(String systemPrompt, String userMessage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);
        body.put("messages", List.of(Map.of("role", "user", "content", userMessage)));

        WebClient client = WebClient.builder().baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();

        Map response = client.post().bodyValue(body).retrieve()
                .bodyToMono(Map.class).block();
        if (response != null && response.containsKey("content")) {
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (!content.isEmpty()) return (String) content.get(0).get("text");
        }
        throw new RuntimeException("Unexpected Claude API response");
    }

}