package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.VehicleStatusDTO;
import it.unicas.cassitrack.model.VehiclePosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for vehicle-related business logic.
 *
 * Translates raw VehiclePosition entities from the cache into
 * the rich VehicleStatusDTO format that the REST API returns.
 *
 * In future iterations, this service will also:
 *   - call ScheduleAdherenceService to compute delay_minutes
 *   - call ETAService to compute eta_seconds
 *   - call CrowdEstimationService to compute estimated_passengers
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleStateCache vehicleStateCache;

    /**
     * Returns current status of ALL active vehicles.
     * Used by: GET /api/v1/vehicles
     */
    public List<VehicleStatusDTO> getAllActiveVehicles() {
        return vehicleStateCache.getActive().stream()
            .map(this::toStatusDTO)
            .toList();
    }

    /**
     * Returns current status of a single vehicle.
     * Used by: GET /api/v1/vehicles/{id}
     */
    public Optional<VehicleStatusDTO> getVehicleById(String vehicleId) {
        return vehicleStateCache.get(vehicleId)
            .map(this::toStatusDTO);
    }

    /**
     * Converts a raw VehiclePosition entity to the API response DTO.
     * This is where we'll plug in schedule adherence and ETA
     * computation once those services are built.
     */
    private VehicleStatusDTO toStatusDTO(VehiclePosition pos) {
        boolean active = vehicleStateCache.isActive(pos.getVehicleId());

        // Crowd estimation: rough heuristic until calibration model is built
        Integer estimatedPassengers = null;
        String crowdingLevel = null;
        if (pos.getBleDeviceCount() != null) {
            // Very rough estimate: assume ~60% of BLE devices are passengers
            estimatedPassengers = (int) (pos.getBleDeviceCount() * 0.6);
            crowdingLevel = estimateCrowdingLevel(estimatedPassengers);
        }

        return VehicleStatusDTO.builder()
            .vehicleId(pos.getVehicleId())
            .lat(pos.getLat())
            .lon(pos.getLon())
            .speedKmh(pos.getSpeedKmh())
            .headingDeg(pos.getHeadingDeg())
            .routeId(pos.getMatchedRouteId())
            .routeName(resolveRouteName(pos.getMatchedRouteId()))
            .scheduleStatus(pos.getScheduleStatus())
            .delayMinutes(null)         // TODO: ScheduleAdherenceService
            .nextStopId(null)           // TODO: ETAService
            .nextStopName(null)         // TODO: ETAService
            .etaSeconds(null)           // TODO: ETAService
            .estimatedPassengers(estimatedPassengers)
            .crowdingLevel(crowdingLevel)
            .timestamp(pos.getTimestamp())
            .lastSeen(pos.getReceivedAt())
            .isActive(active)
            .build();
    }

    /**
     * Translates a route ID into a human-readable name.
     * Will be replaced by a DB lookup once routes are stored.
     */
    private String resolveRouteName(String routeId) {
        if (routeId == null) return null;
        return switch (routeId) {
            case "LINEA-16" -> "Linea 16 - Campus Folcara / Stazione";
            default -> routeId;
        };
    }

    /**
     * Rough crowding classification.
     * Will be replaced by the calibrated model from the university
     * measurement research group.
     */
    private String estimateCrowdingLevel(int passengers) {
        if (passengers < 10)  return "LOW";
        if (passengers < 25)  return "MEDIUM";
        if (passengers < 40)  return "HIGH";
        return "VERY_HIGH";
    }
}
