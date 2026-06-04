package it.unicas.cassitrack.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Driver App", description = "Web-based driver GPS publisher")
public class DriverController {

    private final ObjectMapper objectMapper;
    // Inject the outbound channel we just created in MqttConfig
    private final MessageChannel mqttOutboundChannel;

    @PostMapping("/location")
    @Operation(summary = "Publish driver location securely")
    public ResponseEntity<Map<String, Object>> publishLocation(
            @RequestBody Map<String, Object> body,
            Principal principal) {

        try {
            // 1. Identify the authenticated driver natively via Spring Security
            String driverEmail = principal != null ? principal.getName() : "UNKNOWN_DRIVER";

            // 2. Extract requested vehicle_id from payload.
            String vehicleId = (String) body.getOrDefault("vehicle_id", "UNKNOWN_VEHICLE");

            // TODO: Query your database here to verify this driverEmail is currently assigned to this vehicleId!
            // Example: if (!assignmentService.isDriverAssignedToBus(driverEmail, vehicleId)) throw new SecurityException();

            Double lat = Double.valueOf(body.getOrDefault("lat", 0.0).toString());
            Double lon = Double.valueOf(body.getOrDefault("lon", 0.0).toString());
            Double speed = Double.valueOf(body.getOrDefault("speed_kmh", 0.0).toString());

            // 3. Build the standardized payload
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("vehicle_id", vehicleId);
            payload.put("timestamp", Instant.now().toString());
            payload.put("lat", lat);
            payload.put("lon", lon);
            payload.put("speed_kmh", speed);
            payload.put("heading_deg", body.getOrDefault("heading_deg", 0.0));
            payload.put("battery_voltage", body.getOrDefault("battery_voltage", 100.0));

            String jsonString = objectMapper.writeValueAsString(payload);
            String topic = "cassitrack/" + vehicleId + "/position";

            // 4. Publish via Spring Integration (Reuses standard connection, No DoS vulnerability)
            Message<String> message = MessageBuilder
                    .withPayload(jsonString)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .build();

            mqttOutboundChannel.send(message);

            log.info("Driver {} securely published GPS for {} at [{}, {}]", driverEmail, vehicleId, lat, lon);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("vehicle_id", vehicleId);
            response.put("published_by", driverEmail);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Driver location publish failed: {}", e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}