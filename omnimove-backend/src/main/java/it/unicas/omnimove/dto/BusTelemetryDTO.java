package it.unicas.omnimove.dto;

import lombok.Data;
import java.time.Instant;

@Data
//N.B.: no builder needed! Omimove deve solo andare a leggere i dati
public class BusTelemetryDTO {
    private String busId;
    private float latitude;
    private float longitude;
    private float speed;
    private int bleDeviceCount;
    private Instant timestamp;
}