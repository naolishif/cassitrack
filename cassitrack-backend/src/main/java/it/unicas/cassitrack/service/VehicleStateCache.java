package it.unicas.cassitrack.service;

import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.repository.VehiclePositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * Production-ready cache backed by REDIS that holds the LATEST known position for each vehicle.
 * Disables the local ConcurrentHashMap and delegates persistence directly to Redis.
 */
@Component
@Slf4j
@RequiredArgsConstructor // 🧠 Genera in automatico il costruttore per iniettare il repository
public class VehicleStateCache {

    /** A vehicle not seen for more than this many seconds is marked inactive */
    private static final long ACTIVE_THRESHOLD_SECONDS = 300;

    // 🏎️ Iniettiamo il repository di Redis che abbiamo configurato prima
    private final VehiclePositionRepository positionRepo;

    /**
     * Update Redis with the latest position for a vehicle.
     * Called by MqttMessageHandler after every valid message.
     */
    public void update(String vehicleId, VehiclePosition position) {
        // Ci assicuriamo che il vehicleId sia impostato correttamente come chiave
        position.setVehicleId(vehicleId);

        // 💾 Salva (o sovrascrive) il record direttamente in Redis
        positionRepo.save(position);

        log.debug("Redis cache updated for vehicle {}: {}, {}", vehicleId, position.getLat(), position.getLon());
    }

    /**
     * Get the latest known position from Redis.
     */
    public Optional<VehiclePosition> get(String vehicleId) {
        return positionRepo.findById(vehicleId);
    }

    /**
     * Get all vehicles currently tracked in Redis.
     */
    public Collection<VehiclePosition> getAll() {
        return positionRepo.findAll();
    }

    /**
     * Get only active vehicles from Redis (seen within ACTIVE_THRESHOLD_SECONDS).
     */
    public Collection<VehiclePosition> getActive() {
        Instant cutoff = Instant.now().minusSeconds(ACTIVE_THRESHOLD_SECONDS);
        return positionRepo.findAll().stream()
                .filter(v -> v.getReceivedAt() != null && v.getReceivedAt().isAfter(cutoff))
                .toList();
    }

    /**
     * Check if a specific vehicle is considered active in Redis.
     */
    public boolean isActive(String vehicleId) {
        return get(vehicleId)
                .map(v -> v.getReceivedAt() != null &&
                        v.getReceivedAt().isAfter(Instant.now().minusSeconds(ACTIVE_THRESHOLD_SECONDS)))
                .orElse(false);
    }

    /**
     * How many vehicles are currently tracked in Redis.
     */
    public int size() {
        // Sfrutta il conteggio nativo dei record di Redis
        return (int) positionRepo.count();
    }
}