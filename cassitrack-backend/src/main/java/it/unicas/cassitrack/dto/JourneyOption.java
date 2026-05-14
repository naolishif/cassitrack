package it.unicas.cassitrack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One journey option returned by the planner.
 */
@Data
@Builder
public class JourneyOption {

    private String mode;

    @JsonProperty("mode_label")
    private String modeLabel;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @JsonProperty("distance_metres")
    private Double distanceMetres;

    @JsonProperty("cost_euros")
    private Double costEuros;

    @JsonProperty("green_index")
    private Integer greenIndex;

    @JsonProperty("co2_grams")
    private Double co2Grams;

    @JsonProperty("eta_minutes")
    private Integer etaMinutes;

    private String summary;

    /** Weather warning for this specific mode. Null if weather is fine. */
    @JsonProperty("weather_warning")
    private String weatherWarning;

    /** Overall weather situation shown with results. */
    @JsonProperty("weather_suggestion")
    private String weatherSuggestion;

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
