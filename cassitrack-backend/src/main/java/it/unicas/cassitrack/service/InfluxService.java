package it.unicas.cassitrack.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import it.unicas.cassitrack.dto.BusTelemetryDTO;
import lombok.extern.slf4j.Slf4j; // <-- Solves the 'log' error
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

    public List<BusTelemetryDTO> getLatestTelemetry() {
        List<BusTelemetryDTO> telemetryList = new ArrayList<>();
        InfluxDBClient client = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket);

        // QUERY AGGIORNATA: Usa vehicle_position e compatta le colonne con pivot!
        try {
            String fluxQuery = String.format(
                    "from(bucket: \"%s\") " +
                            "|> range(start: -1h) " +
                            "|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") " +
                            "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")", bucket
            );


            List<FluxTable> tables = client.getQueryApi().query(fluxQuery);

            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    BusTelemetryDTO dto = BusTelemetryDTO.builder()
                            .busId(record.getValueByKey("vehicle_id") != null ? record.getValueByKey("vehicle_id").toString() : "UNKNOWN")
                            .latitude(record.getValueByKey("lat") != null ? ((Number) record.getValueByKey("lat")).floatValue() : 0.0f)
                            .longitude(record.getValueByKey("lon") != null ? ((Number) record.getValueByKey("lon")).floatValue() : 0.0f)
                            .speed(record.getValueByKey("speed_kmh") != null ? ((Number) record.getValueByKey("speed_kmh")).floatValue() : 0)
                            .bleDeviceCount(record.getValueByKey("ble_device_count") != null ? ((Number) record.getValueByKey("ble_device_count")).intValue() : 0)
                            .timestamp(record.getTime())
                            .numeroPosti(record.getValueByKey("numero_posti") != null ? ((Number) record.getValueByKey("numero_posti")).intValue() : 0)
                            .postoDisabili(record.getValueByKey("posto_disabili") != null ? (Boolean) record.getValueByKey("posto_disabili") : false)
                            .build();

                    telemetryList.add(dto);
                }
            }
        } catch (Exception e) {
            System.err.println("Errore durante la lettura da InfluxDB: " + e.getMessage());
            log.error("Error reading telemetry from InfluxDB: {}", e.getMessage());
        } finally {
            client.close();
        }

        return telemetryList;
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
                    point.put("count", record.getValue() != null ? Math.round((Double) record.getValue()) : 0);
                    hourlyData.add(point);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching busiest hours from InfluxDB: {}", e.getMessage());
        } finally {
            client.close();
        }

        return hourlyData;
    }
}