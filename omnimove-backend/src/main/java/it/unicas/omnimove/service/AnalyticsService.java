package it.unicas.omnimove.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class AnalyticsService {

    @Value("${influx.url}")    private String influxUrl;
    @Value("${influx.token}")  private String token;
    @Value("${influx.org}")    private String influxOrg;
    @Value("${influx.bucket}") private String bucket;

    // Query 1: counting by mode (last 30 days)
    public Map<String, Long> getModeDistribution() {
        String flux = String.format("""
            from(bucket: "%s")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "count")
              |> group(columns: ["mode"])
              |> sum()
            """, bucket);

        Map<String, Long> result = new LinkedHashMap<>();
        try (InfluxDBClient client = buildClient()) {
            QueryApi q = client.getQueryApi();
            for (FluxTable table : q.query(flux, influxOrg)) {
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

    // Query 2: counting by mode and hour (stacked bar 0–23)
    public Map<String, long[]> getModeByHour() {
        String flux = String.format("""
            from(bucket: "%s")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "hour")
              |> group(columns: ["mode"])
              |> map(fn: (r) => ({ r with _value: r._value }))
            """, bucket);

        // I<Initializes 4 modes x 24 hours
        String[] modes = {"BUS", "BIKE", "SCOOTER", "WALK"};
        Map<String, long[]> result = new LinkedHashMap<>();
        for (String m : modes) result.put(m, new long[24]);

        String fluxHistogram = String.format("""
            from(bucket: "%s")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "count")
              |> group(columns: ["mode", "hour"])
              |> sum()
            """, bucket);

        // Reconstructs hour from the tag hour
        String fluxByHour = String.format("""
            from(bucket: "%s")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "hour")
              |> group(columns: ["mode"])
              |> keep(columns: ["_value", "mode"])
            """, bucket);

        try (InfluxDBClient client = buildClient()) {
            QueryApi q = client.getQueryApi();
            // Counted directly from the "hour" field
            for (FluxTable table : q.query(fluxByHour, influxOrg)) {
                for (FluxRecord r : table.getRecords()) {
                    String mode = (String) r.getValueByKey("mode");
                    Number hourVal = (Number) r.getValue();
                    if (mode == null || hourVal == null) continue;
                    int hour = hourVal.intValue();
                    if (hour < 0 || hour > 23) continue;
                    String key = mode.toUpperCase();
                    result.computeIfAbsent(key, k -> new long[24])[hour]++;
                }
            }
        } catch (Exception e) {
            log.error("getModeByHour error: {}", e.getMessage());
        }
        return result;
    }

    // Query 3: Green Index average per day (last 30 days)
    public List<Map<String, Object>> getGreenIndexTrend() {
        String flux = String.format("""
            from(bucket: "%s")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "green_index")
              |> aggregateWindow(every: 1d, fn: mean, createEmpty: false)
              |> yield(name: "daily_avg")
            """, bucket);

        List<Map<String, Object>> result = new ArrayList<>();
        try (InfluxDBClient client = buildClient()) {
            QueryApi q = client.getQueryApi();
            for (FluxTable table : q.query(flux, influxOrg)) {
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

    // Query 4: KPIs summary
    public Map<String, Object> getSummaryKpis() {
        String fluxCount = String.format("""
            from(bucket: "%s")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "count")
              |> sum()
            """, bucket);

        String fluxAvgGI = String.format("""
            from(bucket: "%s")
              |> range(start: -30d)
              |> filter(fn: (r) => r._measurement == "journey_search")
              |> filter(fn: (r) => r._field == "green_index")
              |> mean()
            """, bucket);

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalSearches", 0L);
        kpis.put("avgGreenIndex", 0.0);

        try (InfluxDBClient client = buildClient()) {
            QueryApi q = client.getQueryApi();

            List<FluxTable> countTables = q.query(fluxCount, influxOrg);
            long total = 0;
            for (FluxTable t : countTables)
                for (FluxRecord r : t.getRecords()) {
                    Number v = (Number) r.getValue();
                    if (v != null) total += v.longValue();
                }
            kpis.put("totalSearches", total);

            List<FluxTable> giTables = q.query(fluxAvgGI, influxOrg);
            for (FluxTable t : giTables)
                for (FluxRecord r : t.getRecords()) {
                    Number v = (Number) r.getValue();
                    if (v != null) kpis.put("avgGreenIndex", Math.round(v.doubleValue() * 10.0) / 10.0);
                }
        } catch (Exception e) {
            log.error("getSummaryKpis error: {}", e.getMessage());
        }
        return kpis;
    }

    private InfluxDBClient buildClient() {
        return InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket);
    }

    // Query 5: counting per weekday and hour (heatmap)
    public Map<String, long[]> getModeByDayAndHour() {
        String flux = String.format("""
        from(bucket: "%s")
          |> range(start: -30d)
          |> filter(fn: (r) => r._measurement == "journey_search")
          |> filter(fn: (r) => r._field == "count")
          |> group(columns: ["day_of_week", "hour"])
          |> sum()
        """, bucket);

        // día → array de 24 horas
        String[] days = {"MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"};
        Map<String, long[]> result = new LinkedHashMap<>();
        for (String d : days) result.put(d, new long[24]);

        // To reconstruct the hour we use the tag + field hour separated
        String fluxRaw = String.format("""
        from(bucket: "%s")
          |> range(start: -30d)
          |> filter(fn: (r) => r._measurement == "journey_search")
          |> filter(fn: (r) => r._field == "hour")
          |> keep(columns: ["_value", "day_of_week"])
        """, bucket);

        try (InfluxDBClient client = buildClient()) {
            QueryApi q = client.getQueryApi();
            for (FluxTable table : q.query(fluxRaw, influxOrg)) {
                for (FluxRecord r : table.getRecords()) {
                    String day = (String) r.getValueByKey("day_of_week");
                    Number hourVal = (Number) r.getValue();
                    if (day == null || hourVal == null) continue;
                    int hour = hourVal.intValue();
                    if (hour < 0 || hour > 23) continue;
                    String key = day.toUpperCase();
                    if (result.containsKey(key))
                        result.get(key)[hour]++;
                }
            }
        } catch (Exception e) {
            log.error("getModeByDayAndHour error: {}", e.getMessage());
        }
        return result;
    }

}