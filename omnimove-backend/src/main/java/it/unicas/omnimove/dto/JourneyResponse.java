package it.unicas.omnimove.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;
@Data @Builder
public class JourneyResponse {
    private List<JourneyOption> options;
    private String origin;
    private String destination;
    @JsonProperty("total_options") private Integer totalOptions;
    @JsonProperty("realtime_available") private boolean realtimeAvailable;
    @JsonProperty("weather_summary") private String weatherSummary;
    @JsonProperty("temperature_celsius") private Double temperatureCelsius;
}
