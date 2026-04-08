package it.unicas.cassitrack.repository;

import it.unicas.cassitrack.model.VehiclePosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for VehiclePosition.
 *
 * Spring automatically generates the SQL for all these methods —
 * you don't need to write any SQL yourself for the basic queries.
 */
@Repository
public interface VehiclePositionRepository extends JpaRepository<VehiclePosition, Long> {

    /**
     * Get the most recent position for a specific vehicle.
     * Used by the schedule adherence service.
     */
    Optional<VehiclePosition> findTopByVehicleIdOrderByTimestampDesc(String vehicleId);

    /**
     * Get position history for a vehicle within a time range.
     * Used by: GET /api/v1/vehicles/{id}/history
     */
    List<VehiclePosition> findByVehicleIdAndTimestampBetweenOrderByTimestampAsc(
        String vehicleId,
        Instant from,
        Instant to
    );

    /**
     * Get all distinct vehicle IDs seen since a given time.
     * Used to identify which buses have been active today.
     */
    @Query("SELECT DISTINCT v.vehicleId FROM VehiclePosition v WHERE v.receivedAt > :since")
    List<String> findActiveVehicleIdsSince(@Param("since") Instant since);

    /**
     * Count how many position reports we've received for a vehicle today.
     * Useful for monitoring and detecting stuck/offline units.
     */
    long countByVehicleIdAndReceivedAtAfter(String vehicleId, Instant since);
}
