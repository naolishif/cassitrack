package it.unicas.cassitrack.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * The full response from the journey planner.
 * Contains multiple ranked options for the user
 * to choose from.
 */
@Data
@Builder
public class JourneyResponse {

    /** All journey options, sorted by duration */
    private List<JourneyOption> options;

    /** Origin name for display */
    private String origin;

    /** Destination name for display */
    private String destination;

    /** Total options found */
    private Integer totalOptions;

    /** True if real-time bus data was available */
    private boolean realtimeAvailable;
}