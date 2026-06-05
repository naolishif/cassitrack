package it.unicas.cassitrack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Represents the JSON payload published by each ESP32 unit to:
 *   MQTT topic: cassitrack/{vehicle_id}/position
 *
 * This is what arrives from the bus (or from the GPS simulator
 * script during development).
 *
 * Example JSON:
 * {
 *   "vehicle_id": "MAGNI-001",
 *   "timestamp": "2026-04-04T08:30:00Z",
 *   "lat": 41.4917,
 *   "lon": 13.8314,
 *   "speed_kmh": 32.5,
 *   "heading_deg": 270.0,
 *   "ble_device_count": 12,
 *   "battery_voltage": 12.4,
 *   "firmware_version": "1.0.0"
 * }
 */
@Data
public class MqttPositionPayload {

    @JsonProperty("vehicle_id")
    private String vehicleId;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("lat")
    private Double lat;

    @JsonProperty("lon")
    private Double lon;

    @JsonProperty("speed_kmh")
    private Double speedKmh;

    @JsonProperty("heading_deg")
    private Double headingDeg;

    @JsonProperty("ble_device_count")
    private Integer bleDeviceCount;   // nullable — not all units have BLE

    @JsonProperty("battery_voltage")
    private Double batteryVoltage;

    @JsonProperty("firmware_version")
    private String firmwareVersion;

    @JsonProperty("trip_id")
    private String tripId;

    @JsonProperty("delay")
    private Integer delay;

    @JsonProperty("last_stop_registered")
    private String lastStopRegistered;
}
