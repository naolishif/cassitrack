package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.JourneyRequest;
import it.unicas.omnimove.dto.JourneyResponse;
import it.unicas.omnimove.service.JourneyEventService;
import it.unicas.omnimove.service.JourneyPlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
@Tag(name="Journey Planner", description="Multimodal journey planning")
public class JourneyController {

    private final JourneyPlannerService plannerService;
    private final JourneyEventService journeyEventService; // ← añade esto

    @PostMapping("/search")
    public ResponseEntity<JourneyResponse> search(@RequestBody JourneyRequest request) {
        if (request.getOriginLat() == null || request.getDestLat() == null)
            return ResponseEntity.badRequest().build();

        JourneyResponse response = plannerService.plan(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/select")
    @Operation(summary = "Record a journey mode selection")
    public ResponseEntity<?> select(
            @RequestBody Map<String, Object> body) {

        String mode       = (String) body.get("mode");
        int greenIndex    = body.get("green_index") != null ? ((Number) body.get("green_index")).intValue() : 0;
        double distanceKm = body.get("distance_km") != null ? ((Number) body.get("distance_km")).doubleValue() : 0.0;

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        journeyEventService.recordJourneySearch(
                mode, now.getHour(), now.getDayOfWeek().toString(), greenIndex, distanceKm
        );

        return ResponseEntity.ok(Map.of("message", "Journey recorded"));
    }
}