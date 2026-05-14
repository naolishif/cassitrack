package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.VehicleStatusDTO;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.VehiclePositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fleet analytics data for the manager dashboard.
 *
 * Three views:
 * 1. Summary     — active buses, on-time rate, reports today
 * 2. Adherence   — breakdown by schedule status + vehicle table
 * 3. Busiest hrs — GPS activity per hour over last 24 hours
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsService {

    private final VehiclePositionRepository positionRepo;
    private final VehicleService            vehicleService;

    // ── View 1: Summary ───────────────────────────────────────

    public Map<String, Object> getSummary() {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);

        List<VehicleStatusDTO> active = vehicleService.getAllActiveVehicles();
        int activeBuses = active.size();

        List<String> vehiclesToday =
            positionRepo.findActiveVehicleIdsSince(startOfDay);

        long totalReports = vehiclesToday.stream()
            .mapToLong(v -> positionRepo
                .countByVehicleIdAndReceivedAtAfter(v, startOfDay))
            .sum();

        long onTime = active.stream().filter(v ->
            v.getScheduleStatus() != null &&
            "ON_TIME".equals(v.getScheduleStatus().name())).count();
        long late = active.stream().filter(v ->
            v.getScheduleStatus() != null &&
            v.getScheduleStatus().name().contains("LATE")).count();
        long early = active.stream().filter(v ->
            v.getScheduleStatus() != null &&
            "EARLY".equals(v.getScheduleStatus().name())).count();

        int onTimePct = activeBuses > 0
            ? (int)(onTime * 100 / activeBuses) : 0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active_buses_now", activeBuses);
        out.put("buses_today", vehiclesToday.size());
        out.put("position_reports_today", totalReports);
        out.put("on_time_count", onTime);
        out.put("late_count", late);
        out.put("early_count", early);
        out.put("on_time_percentage", onTimePct);
        out.put("generated_at", Instant.now().toString());
        return out;
    }

    // ── View 2: Adherence breakdown ───────────────────────────

    public Map<String, Object> getAdherenceBreakdown() {
        List<VehicleStatusDTO> active = vehicleService.getAllActiveVehicles();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ON_TIME", 0L);
        counts.put("SLIGHTLY_LATE", 0L);
        counts.put("SIGNIFICANTLY_LATE", 0L);
        counts.put("EARLY", 0L);
        counts.put("UNKNOWN", 0L);

        active.forEach(v -> {
            String s = v.getScheduleStatus() != null
                ? v.getScheduleStatus().name() : "UNKNOWN";
            counts.merge(s, 1L, Long::sum);
        });

        List<Map<String, Object>> vehicles = active.stream()
            .map(v -> {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("vehicle_id", v.getVehicleId());
                info.put("status", v.getScheduleStatus() != null
                    ? v.getScheduleStatus().name() : "UNKNOWN");
                info.put("speed_kmh", v.getSpeedKmh());
                info.put("delay_minutes", v.getDelayMinutes());
                info.put("crowding", v.getCrowdingLevel());
                return info;
            })
            .collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status_counts", counts);
        out.put("vehicles", vehicles);
        out.put("total_active", active.size());
        return out;
    }

    // ── View 3: Busiest hours ─────────────────────────────────

    public Map<String, Object> getBusiestHours() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        Map<Integer, Long> hourCounts = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) hourCounts.put(h, 0L);

        List<String> vehicleIds =
            positionRepo.findActiveVehicleIdsSince(since);

        vehicleIds.forEach(vehicleId -> {
            List<VehiclePosition> positions =
                positionRepo.findByVehicleIdAndTimestampBetweenOrderByTimestampAsc(
                    vehicleId, since, Instant.now());
            positions.forEach(pos -> {
                int hour = pos.getTimestamp()
                    .atZone(ZoneId.of("Europe/Rome")).getHour();
                hourCounts.merge(hour, 1L, Long::sum);
            });
        });

        List<Map<String, Object>> hourlyData = new ArrayList<>();
        hourCounts.forEach((hour, count) -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("hour", String.format("%02d:00", hour));
            p.put("count", count);
            hourlyData.add(p);
        });

        String peakHour = hourCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(e -> String.format("%02d:00", e.getKey()))
            .orElse("N/A");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hourly_activity", hourlyData);
        out.put("peak_hour", peakHour);
        out.put("period_hours", 24);
        return out;
    }
}
