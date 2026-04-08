package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.dto.StopArrivalDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for bus stop ETA queries.
 *
 * Base path: /api/v1/stops
 *
 * These endpoints are PUBLIC (no authentication required).
 * OMNIMOVE calls these when the user clicks on a stop on the map
 * to see when the next bus will arrive.
 */
@RestController
@RequestMapping("/api/v1/stops")
@RequiredArgsConstructor
@Tag(name = "Stops", description = "Bus stop ETAs and predicted arrivals")
public class StopController {

    /**
     * GET /api/v1/stops/{stopId}/arrivals
     *
     * Returns predicted arrival times at a specific stop.
     *
     * Example: GET /api/v1/stops/CASSINO-CENTRO/arrivals
     * → returns all buses expected at Cassino Centro stop,
     *   with scheduled vs. estimated arrival times.
     *
     * NOTE: This is currently a stub returning empty data.
     * It will be implemented once:
     *   1. GTFS schedule data is imported into the DB
     *   2. ETAService is built to compute predictions
     */
    @GetMapping("/{stopId}/arrivals")
    @Operation(
        summary = "Get predicted arrivals at a stop",
        description = "Returns predicted arrival times for all routes serving this stop. " +
                      "Uses real-time bus positions to compute ETAs, " +
                      "falling back to scheduled times if real-time data is unavailable."
    )
    public ResponseEntity<List<StopArrivalDTO>> getArrivalsAtStop(
        @Parameter(description = "Stop ID, e.g. CASSINO-CENTRO")
        @PathVariable String stopId
    ) {
        // TODO: implement ETA computation using ETAService
        // For now returns empty list — OMNIMOVE will show "no arrivals data"
        return ResponseEntity.ok(List.of());
    }
}
