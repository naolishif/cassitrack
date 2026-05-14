package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.JourneyRequest;
import it.unicas.omnimove.dto.JourneyResponse;
import it.unicas.omnimove.service.JourneyPlannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
@Tag(name="Journey Planner", description="Multimodal journey planning with weather-aware suggestions")
public class JourneyController {
    private final JourneyPlannerService plannerService;

    @PostMapping("/search")
    @Operation(summary="Search journey options",
        description="Returns multimodal options with costs, Green Index, and weather warnings. Live bus data from CASSITRACK API.")
    public ResponseEntity<JourneyResponse> search(@RequestBody JourneyRequest request) {
        if (request.getOriginLat() == null || request.getDestLat() == null)
            return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(plannerService.plan(request));
    }
}
