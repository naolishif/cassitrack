package it.unicas.omnimove.client;

import it.unicas.omnimove.dto.StopArrivalDTO;
import it.unicas.omnimove.dto.VehicleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client that calls CASSITRACK REST API.
 *
 * This is the ONLY way OMNIMOVE accesses fleet data.
 * No shared database, no direct imports from cassitrack-backend.
 * Pure REST API contract — clean microservices separation.
 *
 * If CASSITRACK is offline, returns empty lists so
 * OMNIMOVE still works (without live bus data).
 */
@Component
public class CassitrackClient {

    private static final Logger log =
        LoggerFactory.getLogger(CassitrackClient.class);

    private final WebClient webClient;

    public CassitrackClient(
            @Value("${cassitrack.api.base-url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        log.info("CassitrackClient → {}", baseUrl);
    }

    public List<VehicleDTO> getActiveVehicles() {
        try {
            VehicleDTO[] vehicles = webClient.get()
                .uri("/vehicles")
                .retrieve()
                .bodyToMono(VehicleDTO[].class)
                .block();
            if (vehicles == null) return Collections.emptyList();
            log.debug("CASSITRACK: {} active vehicles", vehicles.length);
            return Arrays.asList(vehicles);
        } catch (Exception e) {
            log.warn("CASSITRACK /vehicles unreachable: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<StopArrivalDTO> getArrivalsAtStop(String stopId) {
        try {
            StopArrivalDTO[] arrivals = webClient.get()
                .uri("/stops/{stopId}/arrivals", stopId)
                .retrieve()
                .bodyToMono(StopArrivalDTO[].class)
                .block();
            if (arrivals == null) return Collections.emptyList();
            return Arrays.asList(arrivals);
        } catch (Exception e) {
            log.warn("CASSITRACK /stops/{}/arrivals unreachable: {}",
                stopId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean isAvailable() {
        try {
            webClient.get().uri("/vehicles")
                .retrieve().bodyToMono(String.class).block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
