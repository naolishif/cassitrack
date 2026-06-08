package it.unicas.cassitrack.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.Instant;

/**
 * Represents the CURRENT live position of a vehicle, stored in Redis.
 * The historical tracking will be handled separately by InfluxDB.
 */
@RedisHash("vehicle_positions") // 🏎️ Dice a Spring che questo oggetto va in Redis
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehiclePosition {

    @Id // 🔑 Il vehicleId diventa la CHIAVE del record (es. vehicle_positions:MAGNI-001)
    private String vehicleId;

    private Integer busId; // 🚌

    private Integer numeroPosti;
    private Boolean postoDisabili;

    private Instant timestamp;
    private Double lat;
    private Double lon;
    private Double speedKmh;
    private Double headingDeg;
    private Integer bleDeviceCount;
    private Double batteryVoltage;
    private String firmwareVersion;
    private Integer passengers;
    private Integer capacity;
    private Integer delayMinutes;
    private String  nearestStop;
    private String  nearestStopId;
    private String  tripId;
    private String  routeId;
    private String  routeName;        // from simulator

    // Server-side processing fields
    private String matchedRouteId;
    private ScheduleStatus scheduleStatus;
    private Instant receivedAt;

    //Pending: no more alarm table (SHOULD do later on)
    public enum ScheduleStatus {
        ON_TIME,
        SLIGHTLY_LATE,
        SIGNIFICANTLY_LATE,
        EARLY,
        UNKNOWN
    }
}