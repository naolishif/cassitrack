package it.unicas.omnimove.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class JourneyEventService {

    @Value("${influx.url}")
    private String influxUrl;

    @Value("${influx.token}")
    private String token;

    @Value("${influx.org}")
    private String influxOrg;

    @Value("${influx.bucket}")
    private String bucket;

    // Singleton client — created once at startup, reused for all writes
    private InfluxDBClient client;
    private WriteApiBlocking writeApi;

    @PostConstruct
    public void init() {
        client = InfluxDBClientFactory.create(influxUrl, token.toCharArray(), influxOrg, bucket);
        writeApi = client.getWriteApiBlocking();
        log.info("InfluxDB client initialized → {}", influxUrl);
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
            log.info("InfluxDB client closed.");
        }
    }

    /**
     * Records a journey search event to InfluxDB.
     * Called on every user search — each call writes one point with the current timestamp,
     * so the data is as live as your user traffic.
     *
     * Dashboard auto-refresh (e.g. Grafana every 1 min) controls how often the UI updates —
     * not this method.
     */
    public void recordJourneySearch(String mode, int hour, String dayOfWeek,
                                    int greenIndex, double distanceKm) {
        try {
            Point point = Point.measurement("journey_search")
                    .addTag("mode", mode.toUpperCase())
                    .addTag("day_of_week", dayOfWeek)
                    .addField("hour", hour)
                    .addField("green_index", greenIndex)
                    .addField("distance_km", distanceKm)
                    .addField("count", 1)
                    .time(Instant.now(), WritePrecision.MS);

            writeApi.writePoint(point);
            log.debug("Journey event recorded: mode={} hour={} day={}", mode, hour, dayOfWeek);

        } catch (Exception e) {
            log.error("Failed to record journey event to InfluxDB: {}", e.getMessage());
        }
    }
}