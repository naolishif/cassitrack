package it.unicas.omnimove.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import it.unicas.omnimove.repository.JourneyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.*;

@Service
@Slf4j
public class AnalyticsService {

    @Autowired
    private JourneyLogRepository journeyLogRepository;

    @Value("${influx.url}")    private String influxUrl;
    @Value("${influx.token}")  private String token;
    @Value("${influx.org}")    private String influxOrg;
    @Value("${influx.bucket}") private String bucket;

    // ── CO₂ saved per km vs private car (120 g/km baseline) ─────────────────
    private static final Map<String, Double> CO2_SAVED_PER_KM = Map.of(
        "BUS",     52.0,   // 120 - 68
        "TRAIN",   79.0,   // 120 - 41
        "SCOOTER", 116.0,  // 120 - 4
        "BIKE",    120.0,  // 120 - 0
        "WALK",    120.0   // 120 - 0
    );

    // ── Range helpers ─────────────────────────────────────────────────────────
    private static final Map<String, String> RANGE_MAP = Map.of(
        "1W", "-7d", "1M", "-30d", "3M", "-90d", "6M", "-180d", "1Y", "-365d"
    );
    // Aggregation window for the Green Index trend line
    private static final Map<String, String> WINDOW_MAP = Map.of(
        "1W", "12h", "1M", "1d", "3M", "3d", "6M", "1w", "1Y", "2w"
    );

    private String influxRange(String range) {
        return RANGE_MAP.getOrDefault(range != null ? range.toUpperCase() : "1M", "-30d");
    }
    private String influxWindow(String range) {
        return WINDOW_MAP.getOrDefault(range != null ? range.toUpperCase() : "1M", "1d");
    }

    // ── Query 1: mode distribution ─────────────────────────────────────────
    public Map<String, Long> getModeDistribution(String range) {
        String start = influxRange(range);
        String flux = String.format("""
            from(bucket: "%s")
              |> range(start: %s)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "count")
              |> group(columns: ["mode"])
              |> sum()
            """, bucket, start);

        Map<String, Long> result = new LinkedHashMap<>();
        try (InfluxDBClient client = buildClient()) {
            for (FluxTable table : client.getQueryApi().query(flux, influxOrg)) {
                for (FluxRecord r : table.getRecords()) {
                    String mode = (String) r.getValueByKey("mode");
                    Number val  = (Number) r.getValue();
                    if (mode != null && val != null)
                        result.put(mode, val.longValue());
                }
            }
        } catch (Exception e) {
            log.error("getModeDistribution error: {}", e.getMessage());
        }
        return result;
    }

    // ── Query 2: mode × hour stacked bar ──────────────────────────────────
    public Map<String, long[]> getModeByHour(String range) {
        String start = influxRange(range);
        String[] modes = {"BUS", "BIKE", "SCOOTER", "WALK"};
        Map<String, long[]> result = new LinkedHashMap<>();
        for (String m : modes) result.put(m, new long[24]);

        String flux = String.format("""
            from(bucket: "%s")
              |> range(start: %s)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "hour")
              |> group(columns: ["mode"])
              |> keep(columns: ["_value", "mode"])
            """, bucket, start);

        try (InfluxDBClient client = buildClient()) {
            for (FluxTable table : client.getQueryApi().query(flux, influxOrg)) {
                for (FluxRecord r : table.getRecords()) {
                    String mode = (String) r.getValueByKey("mode");
                    Number hourVal = (Number) r.getValue();
                    if (mode == null || hourVal == null) continue;
                    int hour = hourVal.intValue();
                    if (hour < 0 || hour > 23) continue;
                    result.computeIfAbsent(mode.toUpperCase(), k -> new long[24])[hour]++;
                }
            }
        } catch (Exception e) {
            log.error("getModeByHour error: {}", e.getMessage());
        }
        return result;
    }

    // ── Query 3: Green Index daily/windowed trend ──────────────────────────
    public List<Map<String, Object>> getGreenIndexTrend(String range) {
        String start  = influxRange(range);
        String window = influxWindow(range);
        String flux = String.format("""
            from(bucket: "%s")
              |> range(start: %s)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "green_index")
              |> aggregateWindow(every: %s, fn: mean, createEmpty: false)
              |> yield(name: "avg")
            """, bucket, start, window);

        List<Map<String, Object>> result = new ArrayList<>();
        try (InfluxDBClient client = buildClient()) {
            for (FluxTable table : client.getQueryApi().query(flux, influxOrg)) {
                for (FluxRecord r : table.getRecords()) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("time", r.getTime() != null ? r.getTime().toString().substring(0, 10) : "");
                    Number val = (Number) r.getValue();
                    point.put("value", val != null ? Math.round(val.doubleValue() * 10.0) / 10.0 : 0);
                    result.add(point);
                }
            }
        } catch (Exception e) {
            log.error("getGreenIndexTrend error: {}", e.getMessage());
        }
        return result;
    }

    // ── Query 4: KPI summary ───────────────────────────────────────────────
    public Map<String, Object> getSummaryKpis(String range) {
        String start = influxRange(range);

        // Journey selections (written by JourneyEventService on /select)
        String fluxSelections = String.format("""
            from(bucket: "%s")
              |> range(start: %s)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "count")
              |> sum()
            """, bucket, start);

        // Journey searches (written by JourneyEventService on /search, new)
        String fluxSearches = String.format("""
            from(bucket: "%s")
              |> range(start: %s)
              |> filter(fn: (r) => r._measurement == "journey_search_query")
              |> filter(fn: (r) => r._field == "count")
              |> sum()
            """, bucket, start);

        // Avg Green Index
        String fluxAvgGI = String.format("""
            from(bucket: "%s")
              |> range(start: %s)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "green_index")
              |> mean()
            """, bucket, start);

        // Distance per mode (for CO₂ saved)
        String fluxCo2 = String.format("""
            from(bucket: "%s")
              |> range(start: %s)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "distance_km")
              |> group(columns: ["mode"])
              |> sum()
            """, bucket, start);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalSearches",  0L);
        kpis.put("totalSelections", 0L);
        kpis.put("selectionRate",  null);
        kpis.put("avgGreenIndex",  0.0);
        kpis.put("co2SavedKg",     0.0);

        try (InfluxDBClient client = buildClient()) {
            QueryApi q = client.getQueryApi();

            // selections
            long selections = sumFirst(q.query(fluxSelections, influxOrg));
            kpis.put("totalSelections", selections);

            // searches
            long searches = sumFirst(q.query(fluxSearches, influxOrg));
            kpis.put("totalSearches", searches);

            // selection rate
            if (searches > 0)
                kpis.put("selectionRate", Math.round(selections * 1000.0 / searches) / 10.0);

            // avg Green Index
            for (FluxTable t : q.query(fluxAvgGI, influxOrg))
                for (FluxRecord r : t.getRecords()) {
                    Number v = (Number) r.getValue();
                    if (v != null) kpis.put("avgGreenIndex", Math.round(v.doubleValue() * 10.0) / 10.0);
                }

            // CO₂ saved (kg)
            double co2Grams = 0.0;
            for (FluxTable t : q.query(fluxCo2, influxOrg)) {
                for (FluxRecord r : t.getRecords()) {
                    String mode = (String) r.getValueByKey("mode");
                    Number dist = (Number) r.getValue();
                    if (mode == null || dist == null) continue;
                    double factor = CO2_SAVED_PER_KM.getOrDefault(mode.toUpperCase(), 0.0);
                    co2Grams += dist.doubleValue() * factor;
                }
            }
            kpis.put("co2SavedKg", Math.round(co2Grams / 100.0) / 10.0); // g → kg, 1 decimal

        } catch (Exception e) {
            log.error("getSummaryKpis error: {}", e.getMessage());
        }
        return kpis;
    }

    // ── Query 5: trips by day of week (InfluxDB tag day_of_week) ─────────
    public Map<String, Long> getModeByDayOfWeek(String range) {
        String start = influxRange(range);
        String flux = String.format("""
            from(bucket: "%s")
              |> range(start: %s)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "count")
              |> group(columns: ["day_of_week"])
              |> sum()
            """, bucket, start);

        // Keep canonical day order
        Map<String, Long> result = new LinkedHashMap<>();
        List<String> order = List.of("MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY");
        order.forEach(d -> result.put(d, 0L));

        try (InfluxDBClient client = buildClient()) {
            for (FluxTable table : client.getQueryApi().query(flux, influxOrg)) {
                for (FluxRecord r : table.getRecords()) {
                    String day = (String) r.getValueByKey("day_of_week");
                    Number val = (Number) r.getValue();
                    if (day != null && val != null)
                        result.put(day.toUpperCase(), val.longValue());
                }
            }
        } catch (Exception e) {
            log.error("getModeByDayOfWeek error: {}", e.getMessage());
        }
        return result;
    }

    // ── Query 6: top routes from SQL journey_log ──────────────────────────
    public List<Map<String, Object>> getTopRoutes(String range) {
        ZonedDateTime since = switch (range != null ? range.toUpperCase() : "1M") {
            case "1W" -> ZonedDateTime.now().minusWeeks(1);
            case "3M" -> ZonedDateTime.now().minusMonths(3);
            case "6M" -> ZonedDateTime.now().minusMonths(6);
            case "1Y" -> ZonedDateTime.now().minusYears(1);
            default   -> ZonedDateTime.now().minusMonths(1);
        };

        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<Object[]> rows = journeyLogRepository.findTopRoutes(since, PageRequest.of(0, 8));
            for (Object[] row : rows) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("origin",   row[0]);
                entry.put("dest",     row[1]);
                entry.put("uses",     ((Number) row[2]).longValue());
                double avgGi = row[3] != null ? ((Number) row[3]).doubleValue() : 0;
                entry.put("avgGreenIndex", Math.round(avgGi * 10.0) / 10.0);
                result.add(entry);
            }
        } catch (Exception e) {
            log.error("getTopRoutes error: {}", e.getMessage());
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private long sumFirst(List<FluxTable> tables) {
        long total = 0;
        for (FluxTable t : tables)
            for (FluxRecord r : t.getRecords()) {
                Number v = (Number) r.getValue();
                if (v != null) total += v.longValue();
            }
        return total;
    }

    private InfluxDBClient buildClient() {
        return InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket);
    }
}
