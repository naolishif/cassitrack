package it.unicas.cassitrack.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import it.unicas.cassitrack.dto.BusTelemetryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class InfluxService {

    @Value("${influx.url}")
    private String influxUrl;

    @Value("${influx.token}")
    private String token;

    @Value("${influx.org}")
    private String influxOrg;

    @Value("${influx.bucket}")
    private String bucket;

    /**
     * Recupera l'ultima telemetria. Se routeId è non-null e non vuoto, applica anche il filtro per route_id.
     */
    public List<BusTelemetryDTO> getLatestTelemetry(String routeId) {
        List<BusTelemetryDTO> telemetryList = new ArrayList<>();
        InfluxDBClient client = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket);

        try {
            // Costruisco dinamicamente la query Flux aggiungendo il filtro su route_id quando richiesto
            StringBuilder flux = new StringBuilder();
            flux.append(String.format("from(bucket: \"%s\") ", bucket));
            flux.append("|> range(start: -1h) ");
            flux.append("|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") ");

            if (routeId != null && !routeId.trim().isEmpty()) {
                // aggiunge il filtro route_id (stringa)
                flux.append(String.format("|> filter(fn: (r) => r[\"route_id\"] == \"%s\") ", routeId));
            }

            flux.append("|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")");

            String fluxQuery = flux.toString();

            List<FluxTable> tables = client.getQueryApi().query(fluxQuery);

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {

                    // Estrazione dei nuovi campi analitici con controlli di sicurezza per i valori nulli
                    BusTelemetryDTO dto = BusTelemetryDTO.builder()
                            .busId(record.getValueByKey("vehicle_id") != null ? record.getValueByKey("vehicle_id").toString() : "UNKNOWN")
                            .latitude(record.getValueByKey("lat") != null ? ((Number) record.getValueByKey("lat")).floatValue() : 0.0f)
                            .longitude(record.getValueByKey("lon") != null ? ((Number) record.getValueByKey("lon")).floatValue() : 0.0f)
                            .speed(record.getValueByKey("speed_kmh") != null ? ((Number) record.getValueByKey("speed_kmh")).floatValue() : 0)
                            .bleDeviceCount(record.getValueByKey("ble_device_count") != null ? ((Number) record.getValueByKey("ble_device_count")).intValue() : 0)

                            // ─── NEW ───
                            .tripId(record.getValueByKey("trip_id") != null ? record.getValueByKey("trip_id").toString() : "UNKNOWN")
                            .delay(record.getValueByKey("delay") != null ? ((Number) record.getValueByKey("delay")).intValue() : 0)
                            .lastStopRegistered(record.getValueByKey("last_stop_registered") != null ? record.getValueByKey("last_stop_registered").toString() : "-")

                            .timestamp(record.getTime())
                            .numeroPosti(record.getValueByKey("numero_posti") != null ? ((Number) record.getValueByKey("numero_posti")).intValue() : 0)
                            .wheelchairAccessible(record.getValueByKey("wheelchair_accessible") != null ? (Boolean) record.getValueByKey("wheelchair_accessible") : false)
                            .capacity(record.getValueByKey("capacity") != null ? ((Number) record.getValueByKey("capacity")).intValue() : 0)
                            .passengers(record.getValueByKey("passengers") != null ? ((Number) record.getValueByKey("passengers")).intValue() : 0)
                            .nextStop(record.getValueByKey("next_stop") != null ? record.getValueByKey("next_stop").toString() : null)
                            .build();

                    telemetryList.add(dto);
                }
            }
        } catch (Exception e) {
            System.err.println("Errore durante la lettura da InfluxDB: " + e.getMessage());
            log.error("Error reading telemetry from InfluxDB: {}", e.getMessage());
        } finally {
            if (client != null) {
                client.close();
            }
        }

        return telemetryList;
    }

    // Compatibilità: overload senza parametri
    public List<BusTelemetryDTO> getLatestTelemetry() {
        return getLatestTelemetry(null);
    }

    public List<Map<String, Object>> getBusiestHours() {
        List<Map<String, Object>> hourlyData = new ArrayList<>();
        InfluxDBClient client = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket);

        try {
            String fluxQuery = String.format(
                    "from(bucket: \"%s\") " +
                            "|> range(start: -24h) " +
                            "|> filter(fn: (r) => r[\"_measurement\"] == \"stop_arrival\") " +
                            "|> filter(fn: (r) => r[\"_field\"] == \"estimated_passengers\") " +
                            "|> aggregateWindow(every: 1h, fn: mean, createEmpty: true) " +
                            "|> yield(name: \"mean\")", bucket
            );

            List<FluxTable> tables = client.getQueryApi().query(fluxQuery);

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    Map<String, Object> point = new LinkedHashMap<>();

                    String timeStr = record.getTime().toString();
                    String hourLabel = timeStr.substring(11, 13) + ":00";

                    point.put("hour", hourLabel);
                    point.put("count", record.getValue() != null ? Math.round(((Number) record.getValue()).doubleValue()) : 0);
                    hourlyData.add(point);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching busiest hours from InfluxDB: {}", e.getMessage());
        } finally {
            if (client != null) {
                client.close();
            }
        }

        return hourlyData;
    }

    public Map<String, Integer> getPassengersByRoute() {
        Map<String, Integer> result = new LinkedHashMap<>();
        InfluxDBClient client = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket);

        try {
            String fluxQuery = String.format(
                    "from(bucket: \"%s\") " +
                            "|> range(start: -24h) " +
                            "|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") " +
                            "|> filter(fn: (r) => r[\"_field\"] == \"passengers\") " +
                            "|> group(columns: [\"route_id\"]) " +
                            "|> sum()",
                    bucket
            );

            List<FluxTable> tables = client.getQueryApi().query(fluxQuery);
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    String routeId = record.getValueByKey("route_id") != null
                            ? record.getValueByKey("route_id").toString()
                            : "UNKNOWN";
                    int total = record.getValue() != null
                            ? ((Number) record.getValue()).intValue()
                            : 0;
                    result.put(routeId, total);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching passengers by route: {}", e.getMessage());
        } finally {
            client.close();
        }

        return result;
    }
}