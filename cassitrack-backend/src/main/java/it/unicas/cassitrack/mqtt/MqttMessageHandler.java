package it.unicas.cassitrack.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import it.unicas.cassitrack.dto.MqttPositionPayload;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.VehiclePositionRepository;
import it.unicas.cassitrack.service.VehicleStateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Processes every incoming MQTT message from a bus.
 *
 * Pipeline:
 *   1. Parse the JSON payload
 *   2. Validate (coordinates in bounds, timestamp fresh)
 *   3. Write to InfluxDB (time-series store)
 *   4. Write to PostgreSQL (for route matching and relational queries)
 *   5. Update Redis cache (latest position per vehicle)
 *   6. Broadcast via WebSocket to the fleet dashboard
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MqttMessageHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final WriteApiBlocking influxWriteApi;
    private final VehiclePositionRepository positionRepository;
    private final VehicleStateCache vehicleStateCache;

    // Cassino bounding box — reject obviously wrong coordinates
    private static final double LAT_MIN = 41.40;
    private static final double LAT_MAX = 41.60;
    private static final double LON_MIN = 13.70;
    private static final double LON_MAX = 14.00;

    // Reject messages older than 5 minutes (stale buffered data, etc.)
    private static final long MAX_AGE_SECONDS = 300;

    @Override
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = (String) message.getPayload();

        log.debug("MQTT message received on topic [{}]: {}", topic, payload);

        try {
            // ── Step 1: Parse ─────────────────────────────────────
            MqttPositionPayload pos = objectMapper.readValue(payload, MqttPositionPayload.class);

            // ── Step 2: Validate ──────────────────────────────────
            if (!isValid(pos)) {
                log.warn("Invalid MQTT payload from topic [{}], discarding: {}", topic, payload);
                return;
            }

            // ── Step 3: Write to InfluxDB (optional — skip if unavailable) ─
            try {
                writeToInflux(pos);
            } catch (Exception influxError) {
                log.warn("InfluxDB unavailable, skipping time-series write for vehicle {}: {}",
                        pos.getVehicleId(), influxError.getMessage());
            }

// ── Step 4: Write to PostgreSQL ───────────────────────────────
            VehiclePosition entity = toEntity(pos);
            positionRepository.save(entity);

// ── Step 5: Update in-memory cache ────────────────────────────
            vehicleStateCache.update(pos.getVehicleId(), entity);

            log.info("Position processed for vehicle [{}]: lat={}, lon={}, speed={}km/h",
                pos.getVehicleId(), pos.getLat(), pos.getLon(), pos.getSpeedKmh());

        } catch (Exception e) {
            log.error("Failed to process MQTT message from topic [{}]: {}", topic, e.getMessage(), e);
            // Do NOT rethrow — we don't want one bad message to crash the listener
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    private boolean isValid(MqttPositionPayload pos) {
        if (pos.getVehicleId() == null || pos.getVehicleId().isBlank()) {
            log.warn("Missing vehicle_id");
            return false;
        }
        if (pos.getLat() == null || pos.getLon() == null) {
            log.warn("Missing coordinates for vehicle {}", pos.getVehicleId());
            return false;
        }
        if (pos.getLat() < LAT_MIN || pos.getLat() > LAT_MAX ||
            pos.getLon() < LON_MIN || pos.getLon() > LON_MAX) {
            log.warn("Coordinates out of Cassino bounds for vehicle {}: {}, {}",
                pos.getVehicleId(), pos.getLat(), pos.getLon());
            return false;
        }
        if (pos.getTimestamp() == null) {
            log.warn("Missing timestamp for vehicle {}", pos.getVehicleId());
            return false;
        }
        long ageSeconds = Instant.now().getEpochSecond() - pos.getTimestamp().getEpochSecond();
        if (ageSeconds > MAX_AGE_SECONDS) {
            log.warn("Stale message for vehicle {} (age: {}s)", pos.getVehicleId(), ageSeconds);
            return false;
        }
        return true;
    }

    private void writeToInflux(MqttPositionPayload pos) {
        Point point = Point
            .measurement("vehicle_position")
            .addTag("vehicle_id", pos.getVehicleId())
            .addField("lat", pos.getLat())
            .addField("lon", pos.getLon())
            .addField("speed_kmh", pos.getSpeedKmh() != null ? pos.getSpeedKmh() : 0.0)
            .addField("heading_deg", pos.getHeadingDeg() != null ? pos.getHeadingDeg() : 0.0)
            .time(pos.getTimestamp(), WritePrecision.S);

        if (pos.getBleDeviceCount() != null) {
            point.addField("ble_device_count", pos.getBleDeviceCount());
        }
        if (pos.getBatteryVoltage() != null) {
            point.addField("battery_voltage", pos.getBatteryVoltage());
        }

        influxWriteApi.writePoint(point);
    }

    private VehiclePosition toEntity(MqttPositionPayload pos) {
        return VehiclePosition.builder()
            .vehicleId(pos.getVehicleId())
            .timestamp(pos.getTimestamp())
            .lat(pos.getLat())
            .lon(pos.getLon())
            .speedKmh(pos.getSpeedKmh())
            .headingDeg(pos.getHeadingDeg())
            .bleDeviceCount(pos.getBleDeviceCount())
            .batteryVoltage(pos.getBatteryVoltage())
            .firmwareVersion(pos.getFirmwareVersion())
            .scheduleStatus(VehiclePosition.ScheduleStatus.UNKNOWN) // computed later
            .receivedAt(Instant.now())
            .build();
    }
}
