package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * Fleet analytics endpoints for the CASSITRACK dashboard.
 *
 * Three views:
 *   GET /api/v1/analytics/summary       — today's overview
 *   GET /api/v1/analytics/adherence     — on-time breakdown
 *   GET /api/v1/analytics/busiest-hours — activity by hour
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Fleet manager dashboard data")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Today's fleet summary",
        description = "Active buses, trips today, on-time percentage")
    public ResponseEntity<Map<String, Object>> getSummary() {
        return ResponseEntity.ok(analyticsService.getSummary());
    }

    @GetMapping("/adherence")
    @Operation(summary = "Schedule adherence breakdown",
        description = "On-time / late / early counts per bus")
    public ResponseEntity<Map<String, Object>> getAdherence() {
        return ResponseEntity.ok(analyticsService.getAdherenceBreakdown());
    }

    @GetMapping("/busiest-hours")
    @Operation(summary = "Busiest hours of the day",
        description = "Bus activity count per hour over last 24h")
    public ResponseEntity<Map<String, Object>> getBusiestHours() {
        return ResponseEntity.ok(analyticsService.getBusiestHours());
    }

    @GetMapping("/passengers-by-route")
    @Operation(summary = "Passengers per route per hour (last 24h)")
    public ResponseEntity<Map<String, Object>> getPassengersByRoute() {
        return ResponseEntity.ok(analyticsService.getPassengersByRouteAndHour());
    }

    @GetMapping("/delay-by-route")
    @Operation(summary = "Average delay per route per hour (last 24h)")
    public ResponseEntity<Map<String, Object>> getDelayByRoute() {
        return ResponseEntity.ok(analyticsService.getDelayByRouteAndHour());
    }
}
