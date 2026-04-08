package it.unicas.cassitrack.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a single GPS position report received from a bus.
 * This is stored in PostgreSQL for route matching and metadata,
 * while the raw time-series data also goes to InfluxDB.
 *
 * MQTT topic: cassitrack/{vehicleId}/position
 * JSON payload example:
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
@Entity
@Table(name = "vehicle_positions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehiclePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique identifier of the bus (e.g. "MAGNI-001") */
    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    /** When the GPS fix was acquired on the bus */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /** Latitude in decimal degrees (WGS84) */
    @Column(nullable = false)
    private Double lat;

    /** Longitude in decimal degrees (WGS84) */
    @Column(nullable = false)
    private Double lon;

    /** Speed in km/h */
    @Column(name = "speed_kmh")
    private Double speedKmh;

    /** Heading in degrees (0=North, 90=East, 180=South, 270=West) */
    @Column(name = "heading_deg")
    private Double headingDeg;

    /**
     * Number of BLE devices detected nearby — used as a proxy
     * for passenger count (crowd estimation).
     * Nullable because not all units have BLE scanning enabled.
     */
    @Column(name = "ble_device_count")
    private Integer bleDeviceCount;

    /** Battery voltage of the ESP32 unit's power supply */
    @Column(name = "battery_voltage")
    private Double batteryVoltage;

    /** Firmware version running on the ESP32 unit */
    @Column(name = "firmware_version")
    private String firmwareVersion;

    // ── Fields added by server-side processing ────────────────

    /** The route this vehicle has been matched to (e.g. "LINEA-16") */
    @Column(name = "matched_route_id")
    private String matchedRouteId;

    /**
     * Schedule status computed by ScheduleAdherenceService.
     * ON_TIME, SLIGHTLY_LATE, SIGNIFICANTLY_LATE, EARLY, UNKNOWN
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_status")
    private ScheduleStatus scheduleStatus;

    /** When the server received and processed this message */
    @Column(name = "received_at")
    private Instant receivedAt;

    public enum ScheduleStatus {
        ON_TIME,
        SLIGHTLY_LATE,       // 1-5 minutes late
        SIGNIFICANTLY_LATE,  // >5 minutes late
        EARLY,
        UNKNOWN
    }
}
