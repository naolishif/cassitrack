package it.unicas.cassitrack.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * What the passenger sends when asking
 * for journey options.
 *
 * Example:
 * {
 *   "origin_lat": 41.5020,
 *   "origin_lon": 13.8200,
 *   "origin_name": "Via Folcara",
 *   "dest_lat": 41.4892,
 *   "dest_lon": 13.8282,
 *   "dest_name": "Cassino Stazione",
 *   "modes": ["BUS", "WALK", "BIKE"]
 * }
 */
@Data
public class JourneyRequest {

    @JsonProperty("origin_lat")
    private Double originLat;

    @JsonProperty("origin_lon")
    private Double originLon;

    @JsonProperty("origin_name")
    private String originName;

    @JsonProperty("dest_lat")
    private Double destLat;

    @JsonProperty("dest_lon")
    private Double destLon;

    @JsonProperty("dest_name")
    private String destName;

    /**
     * Which transport modes to consider.
     * If null or empty, all modes are considered.
     * Options: BUS, WALK, BIKE, SCOOTER, CAR
     */
    private java.util.List<String> modes;
}