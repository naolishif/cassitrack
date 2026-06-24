package it.unicas.omnimove.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StopArrivalDTO {
    @JsonProperty("vehicle_id")        private String  vehicleId;
    @JsonProperty("route_id")          private String  routeId;
    @JsonProperty("route_name")        private String  routeName;
    @JsonProperty("route_short_name")  private String  routeShortName;
    @JsonProperty("estimated_arrival") private Instant estimatedArrival;
    @JsonProperty("scheduled_arrival") private Instant scheduledArrival;
    @JsonProperty("delay_minutes")     private Integer delayMinutes;
    @JsonProperty("schedule_status")   private String  scheduleStatus;
    @JsonProperty("crowding_level")    private String  crowdingLevel;
}
