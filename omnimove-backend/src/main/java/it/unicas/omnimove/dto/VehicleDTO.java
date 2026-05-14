package it.unicas.omnimove.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
@Data
public class VehicleDTO {
    @JsonProperty("vehicle_id") private String vehicleId;
    private Double lat;
    private Double lon;
    @JsonProperty("speed_kmh") private Double speedKmh;
    @JsonProperty("schedule_status") private String scheduleStatus;
    @JsonProperty("crowding_level") private String crowdingLevel;
    @JsonProperty("estimated_passengers") private Integer estimatedPassengers;
}
