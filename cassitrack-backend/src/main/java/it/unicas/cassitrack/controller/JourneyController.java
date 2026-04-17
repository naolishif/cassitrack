package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.dto.JourneyRequest;
import it.unicas.cassitrack.dto.JourneyResponse;
import it.unicas.cassitrack.service.JourneyPlannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for multimodal journey planning.
 *
 * POST /api/v1/journeys/search
 *
 * This is the core OMNIMOVE endpoint.
 * Given an origin and destination, returns
 * all transport options ranked by speed,
 * each with cost and Green Index score.
 */
@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Journey Planner",
        description = "Multimodal journey planning — OMNIMOVE")
public class JourneyController {

    private final JourneyPlannerService plannerService;

    /**
     * POST /api/v1/journeys/search
     *
     * Example request body:
     * {
     *   "origin_lat": 41.5020,
     *   "origin_lon": 13.8200,
     *   "origin_name": "Via Folcara",
     *   "dest_lat": 41.4892,
     *   "dest_lon": 13.8282,
     *   "dest_name": "Cassino Stazione",
     *   "modes": ["BUS", "WALK", "BIKE"]
     * }
     */
    @PostMapping("/search")
    @Operation(
            summary = "Search journey options",
            description =
                    "Returns multimodal journey options " +
                            "between origin and destination. " +
                            "Each option includes duration, cost, " +
                            "and Green Index CO₂ score. " +
                            "Bus options use real-time CASSITRACK data."
    )
    public ResponseEntity<JourneyResponse> search(
            @RequestBody JourneyRequest request
    ) {
        if (request.getOriginLat() == null
                || request.getDestLat() == null) {
            return ResponseEntity.badRequest().build();
        }

        JourneyResponse response =
                plannerService.plan(request);
        return ResponseEntity.ok(response);
    }
}