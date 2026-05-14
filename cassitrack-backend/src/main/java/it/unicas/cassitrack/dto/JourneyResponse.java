package it.unicas.cassitrack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Full response from the journey planner.
 * Contains multiple ranked options plus weather information.
 */
@Data
@Builder
public class JourneyResponse {

    private List<JourneyOption> options;
    private String origin;
    private String destination;

    @JsonProperty("total_options")
    private Integer totalOptions;

    @JsonProperty("realtime_available")
    private boolean realtimeAvailable;

    /** Overall weather summary shown at top of results. */
    @JsonProperty("weather_summary")
    private String weatherSummary;

    /** Current temperature in Cassino. */
    @JsonProperty("temperature_celsius")
    private Double temperatureCelsius;
}
