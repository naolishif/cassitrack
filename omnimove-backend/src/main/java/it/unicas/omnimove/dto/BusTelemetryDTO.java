package it.unicas.omnimove.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class BusTelemetryDTO {
    private String busId;
    private float latitude;
    private float longitude;
    private float speed;
    private int bleDeviceCount;
    private Instant timestamp;
    private Boolean postoDisabili;
    private Integer numeroPosti;
    private Integer delay;
    private String lastStopRegistered;
    private String tripId;
    private Integer passengers;
    private Integer capacity;
}