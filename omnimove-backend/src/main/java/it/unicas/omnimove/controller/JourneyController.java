package it.unicas.omnimove.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.omnimove.client.CassitrackClient;
import it.unicas.omnimove.dto.JourneyRequest;
import it.unicas.omnimove.dto.JourneyResponse;
import it.unicas.omnimove.dto.StopArrivalDTO;
import it.unicas.omnimove.dto.VehicleDTO;
import it.unicas.omnimove.model.JourneyLog;
import it.unicas.omnimove.model.ScheduledStop;
import it.unicas.omnimove.model.Stop;
import it.unicas.omnimove.model.User;
import it.unicas.omnimove.repository.JourneyLogRepository;
import it.unicas.omnimove.repository.ScheduledStopRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/journeys")
@RequiredArgsConstructor
@Tag(name="Journey Planner", description="Multimodal journey planning")
public class JourneyController {

    private static final Pattern STOP_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9\\-_]{1,50}$");
    private static final int MAX_ARRIVALS_LIMIT   = 10;
    private static final ZoneId ITALY_TZ          = ZoneId.of("Europe/Rome");

    private final JourneyPlannerService  plannerService;
    private final JourneyEventService    journeyEventService;
    private final StopRepository         stopRepository;
    private final ScheduledStopRepository scheduledStopRepository;
    private final JourneyLogRepository   journeyLogRepository;
    private final UserRepository         userRepo;
    private final GreenIndexService      greenIndexService;
    private final RateLimiterService     rateLimiter;
    private final CassitrackClient       cassitrackClient;

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

    @GetMapping("/stops/{stopId}/arrivals")
    @Operation(summary = "Next buses arriving at a stop (real-time + scheduled, max 10)")
    public ResponseEntity<?> getStopArrivals(
            @PathVariable String stopId,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserDetails principal) {

        // Reject stop IDs that don't match the expected format.
        // Prevents path traversal, SSRF via forwarded stopId, and log injection.
        if (!STOP_ID_PATTERN.matcher(stopId).matches()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid stop ID format."));
        }

        // Hard-cap limit regardless of client input (min 1, max 10).
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_ARRIVALS_LIMIT);

        // Rate-limit per authenticated user (60 lookups / hour via Redis).
        if (principal != null
                && !rateLimiter.allowStopArrivalsLookup(principal.getUsername())) {
            return ResponseEntity.status(429)
                    .body(Map.of("message", "Too many requests. Please try again later."));
        }

        // ── 1. Real-time arrivals from CASSITRACK ────────────────────────
        List<StopArrivalDTO> realTime = cassitrackClient.getArrivalsAtStop(stopId);
        Set<String> coveredVehicleIds = realTime.stream()
                .map(StopArrivalDTO::getVehicleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // ── 1b. Fetch vehicle crowding levels from CASSITRACK ─────────────
        // Build vehicleId → crowdingLevel map; merge function keeps first on dup keys.
        Map<String, String> vehicleCrowding = cassitrackClient.getActiveVehicles().stream()
                .filter(v -> v.getVehicleId() != null && v.getCrowdingLevel() != null)
                .collect(Collectors.toMap(
                        VehicleDTO::getVehicleId,
                        VehicleDTO::getCrowdingLevel,
                        (existing, replacement) -> existing));

        // Enrich real-time arrivals with crowding level (setter — list elements are mutable).
        realTime.forEach(a -> {
            if (a.getVehicleId() != null) {
                a.setCrowdingLevel(vehicleCrowding.get(a.getVehicleId()));
            }
        });

        // ── 2. Scheduled arrivals from Omnimove DB (rest of today) ───────
        int nowSeconds    = LocalTime.now(ITALY_TZ).toSecondOfDay();
        int windowSeconds = 86400; // search through end of day
        List<ScheduledStop> scheduled =
                scheduledStopRepository.findUpcomingByStopId(stopId, nowSeconds, windowSeconds);

        Instant todayMidnight = LocalDate.now(ITALY_TZ)
                .atStartOfDay(ITALY_TZ)
                .toInstant();

        List<StopArrivalDTO> scheduledDTOs = scheduled.stream()
                .filter(ss -> {
                    // Skip if a real-time entry already covers this vehicle.
                    String vid = ss.getTrip().getBus().getCurrentVehicleId();
                    return vid == null || !coveredVehicleIds.contains(vid);
                })
                .map(ss -> {
                    String vehicleId = ss.getTrip().getBus().getCurrentVehicleId();
                    Instant arrival  = todayMidnight.plusSeconds(ss.getArrivalSeconds());
                    return StopArrivalDTO.builder()
                            .vehicleId(vehicleId)
                            .routeId(ss.getTrip().getRoute().getId())
                            .routeShortName(ss.getTrip().getRoute().getShortName())
                            .routeName(ss.getTrip().getRoute().getLongName() != null
                                    ? ss.getTrip().getRoute().getLongName()
                                    : ss.getTrip().getRoute().getShortName())
                            .estimatedArrival(arrival)
                            .scheduledArrival(arrival)
                            .delayMinutes(0)
                            .scheduleStatus("SCHEDULED")
                            .crowdingLevel(vehicleId != null
                                    ? vehicleCrowding.get(vehicleId) : null)
                            .build();
                })
                .collect(Collectors.toList());

        // ── 3. Merge, sort by estimated arrival, cap at effectiveLimit ────
        List<StopArrivalDTO> result = Stream
                .concat(realTime.stream(), scheduledDTOs.stream())
                .filter(a -> a.getEstimatedArrival() != null)
                .sorted(Comparator.comparing(StopArrivalDTO::getEstimatedArrival))
                .limit(effectiveLimit)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}