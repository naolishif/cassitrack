package it.unicas.cassitrack.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the InfluxDB client bean used to write GPS telemetry
 * time-series data.
 *
 * InfluxDB is used INSTEAD of PostgreSQL for raw GPS positions
 * because it is optimised for high-write, time-indexed data with
 * built-in downsampling and retention policies.
 */
@Configuration
public class InfluxConfig {

    @Value("${influx.url}")
    private String url;

    @Value("${influx.token}")
    private String token;

    @Value("${influx.org}")
    private String org;

    @Value("${influx.bucket}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }

    /**
     * Blocking write API — simpler to use than the async API
     * for the current prototype stage.
     * For production with 40 buses reporting every 15s,
     * switch to the non-blocking WriteApi.
     */
    @Bean
    public WriteApiBlocking influxWriteApi(InfluxDBClient client) {
        return client.getWriteApiBlocking();
    }
}
