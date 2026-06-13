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
        List<VehicleStatusDTO> list = vehicleStateCache.getActive().stream()
                .map(this::toStatusDTO)
                .toList();
        return list;
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

        // Passengers: prefer direct count from simulator,
        // fall back to BLE estimate
        Integer estimatedPassengers = null;
        String  crowdingLevel       = null;
        if (pos.getPassengers() != null) {
            estimatedPassengers = pos.getPassengers();
            crowdingLevel = estimateCrowdingLevel(estimatedPassengers);
        } else if (pos.getBleDeviceCount() != null) {
            estimatedPassengers = (int)(pos.getBleDeviceCount() * 0.6);
            crowdingLevel = estimateCrowdingLevel(estimatedPassengers);
        }

        VehiclePosition.ScheduleStatus status =
                pos.getScheduleStatus() != null
                        ? pos.getScheduleStatus()
                        : VehiclePosition.ScheduleStatus.UNKNOWN;

        return VehicleStatusDTO.builder()
                .vehicleId(pos.getVehicleId())
                .busId(pos.getBusId())
                .numeroPosti(pos.getNumeroPosti())
                .postoDisabili(pos.getPostoDisabili())
                .lat(pos.getLat())
                .lon(pos.getLon())
                .speedKmh(pos.getSpeedKmh())
                .headingDeg(pos.getHeadingDeg())
                .routeId(pos.getRouteId() != null
                        ? pos.getRouteId() : "LINEA-16")
                .routeName(pos.getRouteName() != null
                        ? pos.getRouteName() : "Linea 16")
                .scheduleStatus(status)
                .delayMinutes(pos.getDelayMinutes())
                .nextStopId(pos.getNearestStopId())
                .nextStopName(pos.getNearestStop())
                .etaSeconds(null)
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
