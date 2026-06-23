package it.unicas.omnimove.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
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

    /** Written when the user calls /search (counts raw queries). */
    public void recordJourneySearchQuery() {
        try (InfluxDBClient client = InfluxDBClientFactory.create(
                influxUrl, token.toCharArray(), influxOrg, bucket)) {

            Point point = Point.measurement("journey_search_query")
                    .addField("count", 1)
                    .time(Instant.now(), WritePrecision.MS);

            client.getWriteApiBlocking().writePoint(point);
            log.debug("Journey search query recorded");

        } catch (Exception e) {
            log.error("Failed to record search query to InfluxDB: {}", e.getMessage());
        }
    }

    /** Written when the user confirms/selects a journey option. */
    public void recordJourneySearch(String mode, int hour, String dayOfWeek,
                                    int greenIndex, double distanceKm) {
        try (InfluxDBClient client = InfluxDBClientFactory.create(
                influxUrl, token.toCharArray(), influxOrg, bucket)) {

            WriteApiBlocking writeApi = client.getWriteApiBlocking();

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