package it.unicas.cassitrack.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import it.unicas.cassitrack.dto.VehicleStatusDTO;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.VehiclePositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import it.unicas.cassitrack.model.Trip;
import it.unicas.cassitrack.repository.TripRepository;

/**
 * Fleet analytics data for the manager dashboard.
 * Sistema híbrido real: Datos instantáneos desde Redis y agregaciones históricas desde InfluxDB.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsService {

    private final VehiclePositionRepository positionRepo;
    private final VehicleService            vehicleService;
    private final InfluxDBClient            influxDBClient; // Conexión directa a InfluxDB
    private final TripRepository            tripRepository;

    @Value("${spring.influx.bucket:vehicle_telemetry}")
    private String bucket;

    // ── View 1: Summary (GET /api/v1/analytics/summary) ───────────────────────

    public Map<String, Object> getSummary() {
        List<VehicleStatusDTO> active = vehicleService.getAllActiveVehicles();
        int activeBuses = active.size();

        // REDIS: Autobuses en memoria ahora mismo
        List<VehiclePosition> livePositions = positionRepo.findAll();

        // 📈 INFLUXDB 1: Contamos todos los reportes de posición recibidos hoy
        long totalReports = 0L;
        String fluxCount = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: today()) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"delay\") " +
                        "|> count()", bucket
        );

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxCount);
            if (!tables.isEmpty() && !tables.get(0).getRecords().isEmpty()) {
                Number val = (Number) tables.get(0).getRecords().get(0).getValue();
                if (val != null) totalReports = val.longValue();
            }
        } catch (Exception e) {
            log.error("Error al consultar conteo de reportes en InfluxDB", e);
        }

        // 📈 INFLUXDB 2: Calculamos el Average Delay global de la red en la última hora
        double globalAverageDelay = 0.0;
        String fluxGlobalDelay = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -1h) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"delay\") " +
                        "|> mean()", bucket
        );

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxGlobalDelay);
            if (!tables.isEmpty() && !tables.get(0).getRecords().isEmpty()) {
                Number val = (Number) tables.get(0).getRecords().get(0).getValue();
                if (val != null) {
                    globalAverageDelay = Math.round(val.doubleValue() * 10.0) / 10.0;
                }
            }
        } catch (Exception e) {
            log.error("Error al consultar retraso promedio global en InfluxDB", e);
        }

        // Cálculos de la lógica estacional de puntualidad
        long onTime = active.stream().filter(v ->
                v.getScheduleStatus() != null &&
                        "ON_TIME".equals(v.getScheduleStatus().name())).count();
        long late = active.stream().filter(v ->
                v.getScheduleStatus() != null &&
                        v.getScheduleStatus().name().contains("LATE")).count();
        long early = active.stream().filter(v ->
                v.getScheduleStatus() != null &&
                        "EARLY".equals(v.getScheduleStatus().name())).count();

        int onTimePct = activeBuses > 0 ? (int)(onTime * 100 / activeBuses) : 0;

        // Construimos la respuesta exacta que tu cuadro de mandos va a devorar
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active_buses_now", activeBuses);
        out.put("buses_today", livePositions.size());
        out.put("position_reports_today", totalReports);
        out.put("average_delay_minutes", globalAverageDelay); // ¡Aquí metemos tu métrica limpia!
        out.put("on_time_count", onTime);
        out.put("late_count", late);
        out.put("early_count", early);
        out.put("on_time_percentage", onTimePct);
        out.put("generated_at", Instant.now().toString());
        return out;
    }

    // ── View 2: Adherence breakdown (GET /api/v1/analytics/adherence) ───────

    public Map<String, Object> getAdherenceBreakdown() {
        List<VehicleStatusDTO> active = vehicleService.getAllActiveVehicles();

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("ON_TIME", 0L);
        counts.put("SLIGHTLY_LATE", 0L);
        counts.put("SIGNIFICANTLY_LATE", 0L);
        counts.put("EARLY", 0L);
        counts.put("UNKNOWN", 0L);

        active.forEach(v -> {
            String s = v.getScheduleStatus() != null ? v.getScheduleStatus().name() : "UNKNOWN";
            counts.merge(s, 1L, Long::sum);
        });

        // INFLUXDB: Sacamos la media del delay de la última hora para cada autobús de forma individual
        Map<String, Double> avgDelaysByBus = new HashMap<>();
        String fluxDelayMean = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -1h) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"delay\") " +
                        "|> mean()", bucket
        );

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxDelayMean);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    String vId = (String) record.getValueByKey("vehicle_id");
                    Number val = (Number) record.getValue();
                    if (vId != null && val != null) {
                        avgDelaysByBus.put(vId, Math.round(val.doubleValue() * 10.0) / 10.0);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error al consultar medias de retraso individuales en InfluxDB", e);
        }

        List<Map<String, Object>> vehicles = active.stream()
                .map(v -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("vehicle_id", v.getVehicleId());
                    info.put("status", v.getScheduleStatus() != null ? v.getScheduleStatus().name() : "UNKNOWN");
                    info.put("speed_kmh", v.getSpeedKmh());
                    // Inyectamos la media calculada por InfluxDB
                    info.put("delay_minutes", avgDelaysByBus.getOrDefault(v.getVehicleId(), (double) v.getDelayMinutes()));
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

    // ── View 3: Busiest hours (GET /api/v1/analytics/busiest-hours) ───────

    public Map<String, Object> getBusiestHours() {
        List<Map<String, Object>> hourlyData = new ArrayList<>();

        // Estructura limpia inicial de 24 horas para prevenir gráficas vacías
        for (int h = 0; h < 24; h++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("hour", String.format("%02d:00", h));
            p.put("count", 0);
            hourlyData.add(p);
        }

        // INFLUXDB: Historial de pasajeros agregados por tramos horarios
        String fluxBusiest = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: today()) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"ble_device_count\") " +
                        "|> aggregateWindow(every: 1h, fn: mean, createEmpty: false)", bucket
        );

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxBusiest);
            if (!tables.isEmpty()) {
                for (FluxRecord record : tables.get(0).getRecords()) {
                    Instant time = record.getTime();
                    Number val = (Number) record.getValue();
                    if (time != null && val != null) {
                        ZonedDateTime zdt = time.atZone(ZoneId.systemDefault());
                        int hour = zdt.getHour();
                        if (hour >= 0 && hour < 24) {
                            hourlyData.get(hour).put("count", val.intValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error al calcular tramos horarios de ocupación en InfluxDB", e);
        }

        String peakHour = "N/A";
        int maxCount = -1;
        for (Map<String, Object> data : hourlyData) {
            int currentCount = (int) data.get("count");
            if (currentCount > maxCount && currentCount > 0) {
                maxCount = currentCount;
                peakHour = (String) data.get("hour");
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hourly_activity", hourlyData);
        out.put("peak_hour", peakHour);
        out.put("period_hours", 24);
        out.put("message", "Data aggregated live from InfluxDB Bluetooth telemetry");
        return out;
    }

    private Map<String, Object> getMetricByRouteAndHour(String fieldName) {
        Map<String, Object> result = new LinkedHashMap<>();

        String fluxQuery = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: today()) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") " +
                        "|> filter(fn: (r) => r[\"_field\"] == \"%s\")",
                bucket, fieldName
        );

        Map<String, List<Double>> valuesByTrip = new LinkedHashMap<>();
        Map<String, Instant> firstSeenByTrip = new LinkedHashMap<>();

        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(fluxQuery);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    String tripId = record.getValueByKey("trip_id") != null
                            ? record.getValueByKey("trip_id").toString() : null;
                    Number val = record.getValue() != null ? (Number) record.getValue() : null;
                    Instant time = record.getTime();
                    if (tripId == null || val == null || time == null) continue;

                    valuesByTrip.computeIfAbsent(tripId, k -> new ArrayList<>()).add(val.doubleValue());
                    firstSeenByTrip.merge(tripId, time, (a, b) -> a.isBefore(b) ? a : b);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching '{}' by route and hour: {}", fieldName, e.getMessage());
            return result;
        }

        if (valuesByTrip.isEmpty()) return result;

        Map<String, Trip> tripsById = tripRepository.findAllByIdInWithRouteAndBus(new ArrayList<>(valuesByTrip.keySet()))
                .stream()
                .collect(Collectors.toMap(Trip::getId, t -> t));

        Map<String, Map<String, List<Double>>> grouped = new LinkedHashMap<>();

        for (String tripId : valuesByTrip.keySet()) {
            Trip trip = tripsById.get(tripId);
            if (trip == null) continue;

            double tripAvg = valuesByTrip.get(tripId).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            ZonedDateTime zdt = firstSeenByTrip.get(tripId).atZone(ZoneId.systemDefault());
            int hour = zdt.getHour();
            if (hour < 6 || hour >= 22) continue;

            String slotLabel = String.format("%02d:00", hour);
            String routeKey = trip.getRoute().getId();

            grouped
                    .computeIfAbsent(routeKey, k -> new LinkedHashMap<>())
                    .computeIfAbsent(slotLabel, k -> new ArrayList<>())
                    .add(tripAvg);
        }

        for (Map.Entry<String, Map<String, List<Double>>> routeEntry : grouped.entrySet()) {
            Map<String, Double> bySlot = new LinkedHashMap<>();
            for (Map.Entry<String, List<Double>> slotEntry : routeEntry.getValue().entrySet()) {
                double avg = slotEntry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                bySlot.put(slotEntry.getKey(), Math.round(avg * 10) / 10.0);
            }
            result.put(routeEntry.getKey(), bySlot);
        }

        return result;
    }

    public Map<String, Object> getPassengersByRouteAndHour() {
        return getMetricByRouteAndHour("passengers");
    }

    public Map<String, Object> getDelayByRouteAndHour() {
        return getMetricByRouteAndHour("delay");
    }
}