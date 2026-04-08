package it.unicas.cassitrack.service;

import it.unicas.cassitrack.model.VehiclePosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache that holds the LATEST known position for each vehicle.
 *
 * Why not just query PostgreSQL or InfluxDB every time?
 * Because the fleet dashboard and OMNIMOVE poll positions every 30 seconds
 * and we don't want a DB round-trip on every request. This cache is updated
 * on every MQTT message arrival (potentially every 15 seconds per vehicle).
 *
 * A vehicle is considered "active" if its last message arrived within
 * ACTIVE_THRESHOLD_SECONDS (default: 5 minutes).
 *
 * For production, this could be moved to Redis so multiple backend
 * instances share the same state. For the prototype, an in-memory
 * ConcurrentHashMap is sufficient.
 */
@Component
@Slf4j
public class VehicleStateCache {

    /** A vehicle not seen for more than this many seconds is marked inactive */
    private static final long ACTIVE_THRESHOLD_SECONDS = 300;

    /** vehicleId → latest VehiclePosition */
    private final Map<String, VehiclePosition> cache = new ConcurrentHashMap<>();

    /**
     * Update the cache with the latest position for a vehicle.
     * Called by MqttMessageHandler after every valid message.
     */
    public void update(String vehicleId, VehiclePosition position) {
        cache.put(vehicleId, position);
        log.debug("Cache updated for vehicle {}: {}, {}", vehicleId, position.getLat(), position.getLon());
    }

    /**
     * Get the latest known position for a specific vehicle.
     */
    public Optional<VehiclePosition> get(String vehicleId) {
        return Optional.ofNullable(cache.get(vehicleId));
    }

    /**
     * Get all vehicles currently in the cache (active and inactive).
     */
    public Collection<VehiclePosition> getAll() {
        return cache.values();
    }

    /**
     * Get only active vehicles (seen within ACTIVE_THRESHOLD_SECONDS).
     */
    public Collection<VehiclePosition> getActive() {
        Instant cutoff = Instant.now().minusSeconds(ACTIVE_THRESHOLD_SECONDS);
        return cache.values().stream()
            .filter(v -> v.getReceivedAt() != null && v.getReceivedAt().isAfter(cutoff))
            .toList();
    }

    /**
     * Check if a specific vehicle is considered active.
     */
    public boolean isActive(String vehicleId) {
        return get(vehicleId)
            .map(v -> v.getReceivedAt() != null &&
                v.getReceivedAt().isAfter(Instant.now().minusSeconds(ACTIVE_THRESHOLD_SECONDS)))
            .orElse(false);
    }

    /**
     * How many vehicles are currently tracked.
     */
    public int size() {
        return cache.size();
    }
}
