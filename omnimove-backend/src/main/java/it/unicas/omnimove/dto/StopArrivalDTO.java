package it.unicas.omnimove.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.Instant;
@Data
public class StopArrivalDTO {
    @JsonProperty("vehicle_id") private String vehicleId;
    @JsonProperty("stop_id") private String stopId;
    @JsonProperty("estimated_arrival") private Instant estimatedArrival;
    @JsonProperty("scheduled_arrival") private Instant scheduledArrival;
    @JsonProperty("schedule_status") private String scheduleStatus;
    @JsonProperty("route_id")   private String routeId;
    @JsonProperty("route_name") private String routeName;
    @JsonProperty("delay_minutes") private Integer delayMinutes;
}
