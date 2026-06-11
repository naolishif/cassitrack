package it.unicas.omnimove.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

/**
 * Client for the Google Maps Distance Matrix API.
 *
 * Given an origin and a destination, returns the travel duration
 * accounting for REAL-TIME traffic (departure_time=now).
 *
 * If the API key is missing or the call fails, returns Optional.empty()
 * so the caller can fall back to haversine-based estimates.
 */
@Service
@Slf4j
public class GoogleMapsService {

    private static final String BASE_URL =
            "https://maps.googleapis.com/maps/api/distancematrix/json";

    @Value("${google.maps.api-key:}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GoogleMapsService(WebClient.Builder webClientBuilder,
                             ObjectMapper objectMapper) {
        this.webClient    = webClientBuilder.baseUrl(BASE_URL).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Result from the Distance Matrix API.
     *
     * @param durationSeconds          travel time WITHOUT traffic (baseline)
     * @param durationInTrafficSeconds travel time WITH real-time traffic
     * @param distanceMetres           road distance in metres
     */
    public record TrafficResult(
            long durationSeconds,
            long durationInTrafficSeconds,
            long distanceMetres
    ) {}

    /**
     * Query Google Maps for travel time between two points with live traffic.
     *
     * @param originLat  origin latitude
     * @param originLon  origin longitude
     * @param destLat    destination latitude
     * @param destLon    destination longitude
     * @return Optional with traffic data, or empty if unavailable
     */
    public Optional<TrafficResult> getTravelTime(
            double originLat, double originLon,
            double destLat,   double destLon) {

        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Google Maps API key not configured — skipping traffic query");
            return Optional.empty();
        }

        try {
            String origins      = originLat + "," + originLon;
            String destinations = destLat   + "," + destLon;

            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("origins",        origins)
                            .queryParam("destinations",   destinations)
                            .queryParam("departure_time", "now")
                            .queryParam("traffic_model",  "best_guess")
                            .queryParam("mode",           "driving")
                            .queryParam("key",            apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response);

        } catch (Exception e) {
            log.warn("Google Maps API call failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TrafficResult> parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String topStatus = root.path("status").asText();
            if (!"OK".equals(topStatus)) {
                log.warn("Google Maps API returned status: {}", topStatus);
                return Optional.empty();
            }

            JsonNode element = root
                    .path("rows").get(0)
                    .path("elements").get(0);

            String elementStatus = element.path("status").asText();
            if (!"OK".equals(elementStatus)) {
                log.warn("Distance Matrix element status: {}", elementStatus);
                return Optional.empty();
            }

            long duration       = element.path("duration").path("value").asLong();
            long distanceMetres = element.path("distance").path("value").asLong();

            JsonNode trafficNode = element.path("duration_in_traffic");
            long durationInTraffic = trafficNode.isMissingNode()
                    ? duration
                    : trafficNode.path("value").asLong();

            log.debug("Google Maps: dist={}m, base={}s, traffic={}s",
                    distanceMetres, duration, durationInTraffic);

            return Optional.of(new TrafficResult(
                    duration, durationInTraffic, distanceMetres));

        } catch (Exception e) {
            log.error("Failed to parse Google Maps response: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
