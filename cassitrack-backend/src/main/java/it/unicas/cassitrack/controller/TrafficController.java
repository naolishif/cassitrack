package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.service.TrafficAwareETAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for traffic-aware ETA endpoints.
 *
 * Base path: /api/v1/traffic
 *
 * These endpoints enrich standard ETA data with real-time
 * traffic information from Google Maps Distance Matrix API.
 *
 * If the Google Maps API key is not configured, responses
 * fall back to haversine-based estimates and dataSource
 * will be "HAVERSINE" instead of "GOOGLE_MAPS".
 *
 * Swagger UI: http://localhost:8080/api/swagger-ui
 */
@RestController
@RequestMapping("/api/v1/traffic")
@RequiredArgsConstructor
@Tag(name = "Traffic", description = "Traffic-aware ETA for bus arrivals using Google Maps")
public class TrafficController {

    private final TrafficAwareETAService trafficAwareETAService;

    /**
     * GET /api/v1/traffic/eta?stopId=FOLCARA-CAMPUS
     * GET /api/v1/traffic/eta?stopId=FOLCARA-CAMPUS&vehicleId=MAGNI-001
     *
     * Returns predicted arrivals at a stop, enriched with real-time
     * traffic data from Google Maps.
     *
     * Response fields:
     *   - estimatedArrival         → ISO-8601 timestamp of predicted arrival
     *   - durationSeconds          → base travel time (no traffic)
     *   - trafficDurationSeconds   → travel time WITH current traffic
     *   - trafficDelaySeconds      → extra seconds caused by traffic
     *   - distanceMetres           → road distance (from Google) or straight-line
     *   - dataSource               → "GOOGLE_MAPS" or "HAVERSINE"
     *
     * Known stopIds:
     *   CASSINO-STAZIONE, CASSINO-CENTRO, CASSINO-OSPEDALE,
     *   FOLCARA-VIA, FOLCARA-CAMPUS
     */
    @GetMapping("/eta")
    @Operation(
        summary = "Get traffic-aware ETA for a stop",
        description = """
            Returns predicted bus arrivals at a stop using Google Maps
            real-time traffic data. Falls back to speed-based estimate
            if Google Maps is unavailable.
            """
    )
    public ResponseEntity<List<TrafficAwareETAService.TrafficEtaResult>> getTrafficEta(
            @Parameter(description = "Stop ID, e.g. FOLCARA-CAMPUS")
            @RequestParam String stopId,

            @Parameter(description = "Optional: filter by vehicle ID, e.g. MAGNI-001")
            @RequestParam(required = false) String vehicleId
    ) {
        List<TrafficAwareETAService.TrafficEtaResult> results =
                trafficAwareETAService.getTrafficAwareArrivals(stopId, vehicleId);

        if (results.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(results);
    }
}
