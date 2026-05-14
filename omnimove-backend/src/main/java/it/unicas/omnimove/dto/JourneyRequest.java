package it.unicas.omnimove.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
@Data
public class JourneyRequest {
    @JsonProperty("origin_lat") private Double originLat;
    @JsonProperty("origin_lon") private Double originLon;
    @JsonProperty("origin_name") private String originName;
    @JsonProperty("dest_lat") private Double destLat;
    @JsonProperty("dest_lon") private Double destLon;
    @JsonProperty("dest_name") private String destName;
    private List<String> modes;
}
