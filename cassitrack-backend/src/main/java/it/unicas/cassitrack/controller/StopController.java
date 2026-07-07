package it.unicas.cassitrack.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.dto.StopArrivalDTO;
import it.unicas.cassitrack.model.Route;
import it.unicas.cassitrack.model.ScheduledStop;
import it.unicas.cassitrack.repository.RouteRepository;
import it.unicas.cassitrack.repository.ScheduledStopRepository;
import it.unicas.cassitrack.service.ETAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    private static final Pattern STOP_ID_RE = Pattern.compile("^[A-Za-z0-9\\-_]{1,50}$");
    private static final ZoneId  ITALY_TZ   = ZoneId.of("Europe/Rome");

    private final ETAService              etaService;
    private final ScheduledStopRepository scheduledStopRepository;
    private final RouteRepository         routeRepository;

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
                    "Stop ID — e.g. PSB (Piazza San Benedetto), " +
                            "SFF (Stazione FF.SS.), UNI (Università Folcara), " +
                            "OSP (Ospedale), LIC (Liceo Scientifico)")
            @PathVariable String stopId
    ) {
        List<StopArrivalDTO> arrivals =
                etaService.getArrivalsAtStop(stopId);
        return ResponseEntity.ok(arrivals);
    }

    @GetMapping("/{stopId}/schedule")
    @Operation(summary = "Today's remaining scheduled arrivals at a stop (static timetable)")
    public ResponseEntity<List<StopArrivalDTO>> getScheduleAtStop(
            @PathVariable String stopId) {

        if (!STOP_ID_RE.matcher(stopId).matches())
            return ResponseEntity.badRequest().build();

        int nowSeconds = LocalTime.now(ITALY_TZ).toSecondOfDay();
        Instant todayMidnight = LocalDate.now(ITALY_TZ).atStartOfDay(ITALY_TZ).toInstant();

        Map<String, Route> routeMap = routeRepository.findAll().stream()
                .collect(Collectors.toMap(Route::getId, r -> r, (a, b) -> a));

        List<StopArrivalDTO> schedule = scheduledStopRepository
                .findUpcomingByStopId(stopId, nowSeconds)
                .stream()
                .map(ss -> {
                    Route r     = routeMap.get(ss.getTrip().getRoute().getId());
                    Instant arr = todayMidnight.plusSeconds(ss.getArrivalSeconds());
                    return StopArrivalDTO.builder()
                            .tripId(ss.getTrip().getId())
                            .routeId(ss.getTrip().getRoute().getId())
                            .routeName(r != null && r.getLongName() != null
                                    ? r.getLongName() : (r != null ? r.getShortName() : null))
                            .routeShortName(r != null ? r.getShortName() : null)
                            .scheduledArrival(arr)
                            .estimatedArrival(arr)
                            .delayMinutes(0)
                            .scheduleStatus("SCHEDULED")
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(schedule);
    }
}