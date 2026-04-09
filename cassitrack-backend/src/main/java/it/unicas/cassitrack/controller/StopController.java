package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.dto.StopArrivalDTO;
import it.unicas.cassitrack.service.ETAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for bus stop ETA queries.
 * GET /api/v1/stops/{stopId}/arrivals
 */
@RestController
@RequestMapping("/api/v1/stops")
@RequiredArgsConstructor
@Tag(name = "Stops",
        description = "Bus stop ETAs and predicted arrivals")
public class StopController {

    private final ETAService etaService;

    @GetMapping("/{stopId}/arrivals")
    @Operation(
            summary = "Get predicted arrivals at a stop",
            description =
                    "Returns predicted arrival times for all " +
                            "buses heading to this stop, computed from " +
                            "real-time GPS positions."
    )
    public ResponseEntity<List<StopArrivalDTO>>
    getArrivalsAtStop(
            @Parameter(description =
                    "Stop ID — one of: CASSINO-STAZIONE, " +
                            "CASSINO-CENTRO, CASSINO-OSPEDALE, " +
                            "FOLCARA-VIA, FOLCARA-CAMPUS")
            @PathVariable String stopId
    ) {
        List<StopArrivalDTO> arrivals =
                etaService.getArrivalsAtStop(stopId);
        return ResponseEntity.ok(arrivals);
    }
}