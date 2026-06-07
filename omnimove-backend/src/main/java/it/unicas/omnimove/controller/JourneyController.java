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

        // ── Record analytics event for each option returned ──
        if (response.getOptions() != null) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            int hour = now.getHour();
            String day = now.getDayOfWeek().toString(); // "MONDAY", "TUESDAY" etc

            response.getOptions().forEach(opt ->
                    journeyEventService.recordJourneySearch(
                            opt.getMode(),
                            hour,
                            day,
                            opt.getGreenIndex(),
                            opt.getDistanceMetres() / 1000.0
                    )
            );
        }

        return ResponseEntity.ok(response);
    }
}