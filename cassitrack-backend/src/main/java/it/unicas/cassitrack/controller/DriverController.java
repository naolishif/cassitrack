package it.unicas.cassitrack.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Driver Web App endpoint.
 *
 * Receives GPS from browser Geolocation API and
 * publishes it to MQTT — same format as ESP32 hardware.
 *
 * This replaces the Android app for demo purposes.
 * The driver opens driver-app.html in their phone browser,
 * grants location permission, and their GPS is published
 * to the CASSITRACK system in real time.
 *
 * POST /api/v1/driver/location
 */
@RestController
@RequestMapping("/api/v1/driver")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Driver App", description = "Web-based driver GPS tracking")
public class DriverController {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    private final ObjectMapper objectMapper;

    @PostMapping("/location")
    @Operation(summary = "Publish driver GPS location",
        description = "Receives GPS from driver's browser and publishes to MQTT. " +
            "Simulates what the Android driver app would do.")
    public ResponseEntity<Map<String, Object>> publishLocation(
            @RequestBody Map<String, Object> body) {

        try {
            String vehicleId = (String) body.getOrDefault(
                "vehicle_id", "DRIVER-001");
            double lat = Double.parseDouble(
                body.getOrDefault("lat", 41.4917).toString());
            double lon = Double.parseDouble(
                body.getOrDefault("lon", 13.8314).toString());
            double speed = Double.parseDouble(
                body.getOrDefault("speed_kmh", 0.0).toString());
            double heading = Double.parseDouble(
                body.getOrDefault("heading_deg", 0.0).toString());

            // Build MQTT payload — same format as ESP32
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("vehicle_id", vehicleId);
            payload.put("timestamp", Instant.now().toString());
            payload.put("lat", lat);
            payload.put("lon", lon);
            payload.put("speed_kmh", speed);
            payload.put("heading_deg", heading);
            payload.put("ble_device_count", 0);
            payload.put("battery_voltage", 100.0);
            payload.put("firmware_version", "driver-web-1.0");

            String json = objectMapper.writeValueAsString(payload);
            String topic = "cassitrack/" + vehicleId + "/position";

            // Publish to MQTT
            MqttClient client = new MqttClient(
                brokerUrl,
                "driver-web-" + vehicleId + "-" + System.currentTimeMillis()
            );
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setConnectionTimeout(5);
            opts.setCleanSession(true);
            client.connect(opts);
            MqttMessage msg = new MqttMessage(json.getBytes());
            msg.setQos(1);
            client.publish(topic, msg);
            client.disconnect();
            client.close();

            log.info("Driver GPS published: {} at [{}, {}] {}km/h",
                vehicleId, lat, lon, speed);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("vehicle_id", vehicleId);
            response.put("topic", topic);
            response.put("timestamp", Instant.now().toString());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Driver location publish failed: {}", e.getMessage());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/ping")
    @Operation(summary = "Check driver API is reachable")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Driver API ready",
            "timestamp", Instant.now().toString()
        ));
    }
}
