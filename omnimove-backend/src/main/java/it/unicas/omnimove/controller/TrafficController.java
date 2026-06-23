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
 * No user coordinates required — Google Maps uses the route start
 * as origin and the requested stop as destination.
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
     * GET /api/v1/traffic/eta?stopId=UNI
     *
     * Returns predicted bus arrivals at a stop enriched with
     * real-time traffic data from Google Maps.
     *
     * Known stopIds:
     *   PSB (Piazza San Benedetto), SFF (Stazione FF.SS.), UNI (Università Folcara),
     *   OSP (Ospedale), LIC (Liceo Scientifico)
     */
    @GetMapping("/eta")
    @Operation(
        summary = "Get traffic-aware ETA for a stop",
        description = """
            Fetches bus arrivals from CASSITRACK and enriches them with
            Google Maps real-time traffic data.
            Falls back to CASSITRACK ETA if Google Maps is unavailable
            (dataSource will be "CASSITRACK").
            """
    )
    public ResponseEntity<List<TrafficAwareETAService.TrafficEtaResult>> getTrafficEta(
            @Parameter(description = "Stop ID, e.g. UNI for Università Folcara")
            @RequestParam String stopId
    ) {
        List<TrafficAwareETAService.TrafficEtaResult> results =
                trafficAwareETAService.getEnrichedArrivals(stopId);

        if (results.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(results);
    }
}
