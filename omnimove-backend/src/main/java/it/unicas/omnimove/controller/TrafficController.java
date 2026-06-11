package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.service.TrafficAwareETAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for traffic-aware ETA endpoints on OMNIMOVE.
 *
 * Base path: /api/v1/traffic
 *
 * Fetches bus arrival data from CASSITRACK and enriches it with
 * real-time traffic information from Google Maps Distance Matrix API.
 *
 * Swagger UI: http://localhost:8081/api/swagger-ui
 */
@RestController
@RequestMapping("/api/v1/traffic")
@RequiredArgsConstructor
@Tag(name = "Traffic", description = "Traffic-aware ETA for bus arrivals using Google Maps")
public class TrafficController {

    private final TrafficAwareETAService trafficAwareETAService;

    /**
     * GET /api/v1/traffic/eta?stopId=FOLCARA-CAMPUS&originLat=41.49&originLon=13.83
     *
     * Returns predicted bus arrivals at a stop enriched with
     * real-time traffic data from Google Maps.
     *
     * Known stopIds:
     *   CASSINO-STAZIONE, CASSINO-CENTRO, CASSINO-OSPEDALE,
     *   FOLCARA-VIA, FOLCARA-CAMPUS
     */
    @GetMapping("/eta")
    @Operation(
        summary = "Get traffic-aware ETA for a stop",
        description = """
            Fetches bus arrivals from CASSITRACK and enriches them with
            Google Maps real-time traffic data. Falls back to CASSITRACK
            ETA if Google Maps is unavailable (dataSource will be "CASSITRACK").
            """
    )
    public ResponseEntity<List<TrafficAwareETAService.TrafficEtaResult>> getTrafficEta(
            @Parameter(description = "Stop ID, e.g. FOLCARA-CAMPUS")
            @RequestParam String stopId,

            @Parameter(description = "Origin latitude (user or bus position)")
            @RequestParam double originLat,

            @Parameter(description = "Origin longitude (user or bus position)")
            @RequestParam double originLon
    ) {
        List<TrafficAwareETAService.TrafficEtaResult> results =
                trafficAwareETAService.getEnrichedArrivals(stopId, originLat, originLon);

        if (results.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(results);
    }
}
