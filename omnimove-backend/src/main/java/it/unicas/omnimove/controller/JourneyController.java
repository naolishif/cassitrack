package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.dto.JourneyRequest;
import it.unicas.omnimove.dto.JourneyResponse;
import it.unicas.omnimove.model.JourneyLog;
import it.unicas.omnimove.model.Stop;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.JourneyLogRepository;
import it.unicas.omnimove.repository.StopRepository;
import it.unicas.omnimove.repository.UserRepository;
import it.unicas.omnimove.service.GreenIndexService;
import it.unicas.omnimove.service.JourneyEventService;
import it.unicas.omnimove.service.JourneyPlannerService;
import it.unicas.omnimove.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
@Tag(name="Journey Planner", description="Multimodal journey planning")
public class JourneyController {

    private final JourneyPlannerService plannerService;
    private final JourneyEventService journeyEventService;
    private final StopRepository stopRepository;
    private final JourneyLogRepository journeyLogRepository;
    private final UserRepository userRepo;
    private final GreenIndexService greenIndexService;
    private final RateLimiterService rateLimiter;

    @GetMapping("/stops")
    @Operation(summary = "List active stops for origin/destination pickers")
    public ResponseEntity<List<Map<String, Object>>> stops() {
        List<Map<String, Object>> result = stopRepository.findByActiveTrue().stream()
                .filter(s -> s.getLat() != null && s.getLon() != null)
                .sorted(Comparator.comparing(Stop::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(500)
                .<Map<String, Object>>map(s -> Map.of(
                        "id",   s.getId(),
                        "name", s.getName() != null ? s.getName() : s.getId(),
                        "lat",  s.getLat(),
                        "lon",  s.getLon()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/search")
    public ResponseEntity<JourneyResponse> search(
            @RequestBody JourneyRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        if (request.getOriginLat() == null || request.getDestLat() == null)
            return ResponseEntity.badRequest().build();

        if (principal != null && !rateLimiter.allowJourneySearch(principal.getUsername()))
            return ResponseEntity.status(429).build();

        journeyEventService.recordJourneySearchQuery(); // FR-OM-009: count raw searches
        JourneyResponse response = plannerService.plan(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/select")
    @Operation(summary = "Record a journey mode selection")
    public ResponseEntity<?> select(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails principal) {

        if (principal == null)
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));

        User user = userRepo.findByEmail(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String mode      = (String) body.get("mode");
        String originName = (String) body.get("origin_name");
        String destName   = (String) body.get("dest_name");

        if (mode == null || !java.util.Set.of("BUS", "WALK", "BIKE", "SCOOTER").contains(mode.toUpperCase()))
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid transport mode"));

        double distanceKm = body.get("distance_km") != null ? ((Number) body.get("distance_km")).doubleValue() : 0.0;
        if (distanceKm < 0 || distanceKm > 200)
            return ResponseEntity.badRequest().body(Map.of("message", "Distance out of valid range (0–200 km)"));

        double costEuros = body.get("cost_euros") != null ? ((Number) body.get("cost_euros")).doubleValue() : 0.0;
        if (costEuros < 0 || costEuros > 50)
            return ResponseEntity.badRequest().body(Map.of("message", "Cost out of valid range (0–50 €)"));

        // Always compute server-side — never trust the client-supplied green_index
        int greenIndex  = greenIndexService.computeGreenIndex(mode, distanceKm);
        double co2Grams = greenIndexService.computeCo2Grams(mode, distanceKm);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        journeyEventService.recordJourneySearch(
                mode, now.getHour(), now.getDayOfWeek().toString(), greenIndex, distanceKm
        );

        journeyLogRepository.save(JourneyLog.builder()
                .userId(user.getId())
                .mode(mode)
                .distanceKm(distanceKm)
                .costEuros(costEuros)
                .co2Grams(co2Grams)
                .greenIndex(greenIndex)
                .originName(originName)
                .destName(destName)
                .createdAt(java.time.ZonedDateTime.now())
                .build());

        return ResponseEntity.ok(Map.of("message", "Journey recorded"));
    }
}