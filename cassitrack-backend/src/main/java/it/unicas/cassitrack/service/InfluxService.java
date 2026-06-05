package it.unicas.cassitrack.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import it.unicas.cassitrack.dto.BusTelemetryDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class InfluxService {

    @Value("${influx.url}")
    private String influxUrl;

    @Value("${influx.token}")
    private String token;

    @Value("${influx.org}")
    private String org;

    @Value("${influx.bucket}")
    private String bucket;

    public List<BusTelemetryDTO> getLatestTelemetry() {
        List<BusTelemetryDTO> telemetryList = new ArrayList<>();

        InfluxDBClient client = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), org, bucket);

        // QUERY AGGIORNATA: Estrae vehicle_position con la funzione pivot per l'analisi dei dati
        String fluxQuery = String.format(
                "from(bucket: \"%s\") " +
                        "|> range(start: -1h) " +
                        "|> filter(fn: (r) => r[\"_measurement\"] == \"vehicle_position\") " +
                        "|> pivot(rowKey:[\"_time\"], columnKey: [\"_field\"], valueColumn: \"_value\")",
                bucket
        );

        try {
            List<FluxTable> tables = client.getQueryApi().query(fluxQuery, org);

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
                            .build();

                    telemetryList.add(dto);
                }
            }
        } catch (Exception e) {
            System.err.println("Errore durante la lettura da InfluxDB: " + e.getMessage());
        } finally {
            client.close();
        }

        return telemetryList;
    }
}