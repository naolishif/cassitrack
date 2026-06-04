package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.VehicleStatusDTO;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.VehiclePositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fleet analytics data for the manager dashboard.
 * * Aggiornato per architettura ibrida:
 * - Dati Live presi da Redis (tramite VehiclePositionRepository e VehicleService)
 * - Dati Storici in attesa di implementazione via InfluxDB
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsService {

    private final VehiclePositionRepository positionRepo;
    private final VehicleService            vehicleService;
    private final InfluxService             influxService;

    // ── View 1: Summary ───────────────────────────────────────

    public Map<String, Object> getSummary() {
        List<VehicleStatusDTO> active = vehicleService.getAllActiveVehicles();
        int activeBuses = active.size();

        // 🏎️ REDIS: Prendiamo tutti i bus attualmente salvati in memoria
        List<VehiclePosition> livePositions = positionRepo.findAll();

        // 📈 INFLUXDB TODO: Il conteggio totale dei report storici di oggi.
        // Per ora lo mettiamo a 0 per far compilare il progetto.
        long totalReports = 0L;

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
        out.put("buses_today", livePositions.size()); // Sostituito con Redis live data
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
        // Questo metodo è già perfetto perché usa il VehicleService
        // che gestisce la logica del tempo reale.
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
        // 🚀 We are no longer mocking! Fetching real 24h aggregated data from InfluxDB
        List<Map<String, Object>> hourlyData = influxService.getBusiestHours();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hourly_activity", hourlyData);
        out.put("message", "Data aggregated from InfluxDB stop_arrival events");
        return out;
    }

//    public Map<String, Object> getBusiestHours() {
//        // 📈 INFLUXDB TODO: Redis non conosce la storia passata.
//        // Tutta la logica di aggregazione temporale dovrà essere fatta
//        // tramite una Flux Query su InfluxDB.
//        // Per ora restituiamo una struttura vuota per non bloccare l'app.
//
//        List<Map<String, Object>> hourlyData = new ArrayList<>();
//        // Mock data temporaneo
//        for (int h = 0; h < 24; h++) {
//            Map<String, Object> p = new LinkedHashMap<>();
//            p.put("hour", String.format("%02d:00", h));
//            p.put("count", 0);
//            hourlyData.add(p);
//        }
//
//        Map<String, Object> out = new LinkedHashMap<>();
//        out.put("hourly_activity", hourlyData);
//        out.put("peak_hour", "N/A");
//        out.put("period_hours", 24);
//        out.put("note", "Historical data will be available once InfluxDB is integrated.");
//        return out;
//    }
}