package it.unicas.cassitrack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One journey option returned by the planner.
 *
 * Example:
 * {
 *   "mode": "BUS",
 *   "mode_label": "Linea 16 — Magni Autoservizi",
 *   "duration_minutes": 8,
 *   "distance_metres": 2100,
 *   "cost_euros": 1.30,
 *   "green_index": 85,
 *   "co2_grams": 42,
 *   "legs": [...],
 *   "summary": "Take Bus 16 from Via Folcara"
 * }
 */
@Data
@Builder
public class JourneyOption {

    /** Transport mode: BUS, WALK, BIKE, SCOOTER, CAR */
    private String mode;

    /** Human readable label */
    @JsonProperty("mode_label")
    private String modeLabel;

    /** Total travel time in minutes */
    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    /** Total distance in metres */
    @JsonProperty("distance_metres")
    private Double distanceMetres;

    /**
     * Estimated cost in euros.
     * 0.0 for walking and cycling.
     */
    @JsonProperty("cost_euros")
    private Double costEuros;

    /**
     * Green Index: 0-100 environmental score.
     * 100 = zero emissions (walk/bike)
     * 0   = worst possible (private car)
     *
     * Required by the CINI challenge spec.
     */
    @JsonProperty("green_index")
    private Integer greenIndex;

    /**
     * CO₂ emitted in grams for this journey.
     * Based on European Environment Agency
     * reference emission factors per mode.
     */
    @JsonProperty("co2_grams")
    private Double co2Grams;

    /**
     * Estimated arrival time in minutes from now.
     * Accounts for waiting time for the bus.
     */
    @JsonProperty("eta_minutes")
    private Integer etaMinutes;

    /** One-line human readable summary */
    private String summary;

    /** Detailed journey legs */
    private List<JourneyLeg> legs;

    @Data
    @Builder
    public static class JourneyLeg {
        private String mode;
        private String from;
        private String to;
        @JsonProperty("duration_minutes")
        private Integer durationMinutes;
        @JsonProperty("distance_metres")
        private Double distanceMetres;
        private String instruction;
    }
}