package it.unicas.cassitrack.service;

import com.google.transit.realtime.GtfsRealtime.*;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition.OccupancyStatus;
import it.unicas.cassitrack.model.VehiclePosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Generates a GTFS Realtime feed from live
 * CASSITRACK vehicle data.
 *
 * GTFS Realtime is THE international standard
 * for real-time public transport data.
 * It uses Google Protocol Buffers (binary format)
 * for efficient transmission.
 *
 * Two types of entities we produce:
 *
 * 1. VehiclePosition — where is the bus RIGHT NOW
 *    (lat, lon, speed, heading, occupancy)
 *
 * 2. TripUpdate — when will it arrive at each stop
 *    (stop_id, arrival_time, delay in seconds)
 *
 * Analogy: think of GTFS-RT like a standardised
 * radio protocol. Any receiver that speaks the
 * protocol can tune in and understand your data,
 * regardless of who built the transmitter.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GtfsRealtimeService {

    private final VehicleStateCache vehicleStateCache;
    private final ETAService        etaService;

    // GTFS route ID for Linea 16
    private static final String ROUTE_ID = "LINEA-16";

    // Known stop IDs in route order
    private static final String[] STOP_IDS = {
            "CASSINO-STAZIONE",
            "CASSINO-CENTRO",
            "CASSINO-OSPEDALE",
            "FOLCARA-VIA",
            "FOLCARA-CAMPUS"
    };

    /**
     * Build the complete GTFS Realtime feed.
     *
     * Returns a FeedMessage containing:
     * - One VehiclePosition entity per active bus
     * - One TripUpdate entity per active bus
     *   (with arrival predictions at each stop)
     *
     * This is what GET /api/v1/feed/gtfs-rt returns.
     */
    public FeedMessage buildFeed() {
        // FeedHeader — metadata about this feed
        FeedHeader header = FeedHeader.newBuilder()
                .setGtfsRealtimeVersion("2.0")
                .setIncrementality(
                        FeedHeader.Incrementality.FULL_DATASET
                )
                .setTimestamp(System.currentTimeMillis() / 1000)
                .build();

        FeedMessage.Builder feed =
                FeedMessage.newBuilder()
                        .setHeader(header);

        // Get all active vehicles from cache
        Collection<VehiclePosition> active =
                vehicleStateCache.getActive();

        log.debug("Building GTFS-RT feed for {} vehicles",
                active.size());

        active.forEach(vehicle -> {
            // Add VehiclePosition entity
            FeedEntity vpEntity =
                    buildVehiclePositionEntity(vehicle);
            feed.addEntity(vpEntity);

            // Add TripUpdate entity
            FeedEntity tuEntity =
                    buildTripUpdateEntity(vehicle);
            feed.addEntity(tuEntity);
        });

        return feed.build();
    }

    // ─────────────────────────────────────────────────────
    // VehiclePosition entity
    // ─────────────────────────────────────────────────────

    /**
     * Builds a VehiclePosition entity for one bus.
     *
     * This tells consumers: "Bus MAGNI-001 is at
     * lat=41.4917, lon=13.8314, moving at 32 km/h,
     * heading North-East, with MANY_SEATS_AVAILABLE"
     */
    private FeedEntity buildVehiclePositionEntity(
            VehiclePosition v) {

        // Convert our schedule status to GTFS occupancy
        OccupancyStatus occupancy = toOccupancyStatus(
                v.getBleDeviceCount()
        );

        // Build the Position object
        Position.Builder position = Position.newBuilder()
                .setLatitude(v.getLat().floatValue())
                .setLongitude(v.getLon().floatValue());

        if (v.getSpeedKmh() != null) {
            // GTFS speed is in metres per second
            float speedMs = v.getSpeedKmh().floatValue()
                    / 3.6f;
            position.setSpeed(speedMs);
        }

        if (v.getHeadingDeg() != null) {
            position.setBearing(
                    v.getHeadingDeg().floatValue()
            );
        }

        // Build the trip descriptor
        TripDescriptor trip = TripDescriptor.newBuilder()
                .setRouteId(ROUTE_ID)
                .setScheduleRelationship(
                        TripDescriptor.ScheduleRelationship.SCHEDULED
                )
                .build();

        // Build vehicle descriptor
        VehicleDescriptor vehicleDesc =
                VehicleDescriptor.newBuilder()
                        .setId(v.getVehicleId())
                        .setLabel(v.getVehicleId())
                        .build();

        // Combine into VehiclePosition
        com.google.transit.realtime.GtfsRealtime
                .VehiclePosition vp =
                com.google.transit.realtime.GtfsRealtime
                        .VehiclePosition.newBuilder()
                        .setTrip(trip)
                        .setVehicle(vehicleDesc)
                        .setPosition(position.build())
                        .setTimestamp(
                                v.getTimestamp() != null
                                        ? v.getTimestamp().getEpochSecond()
                                        : System.currentTimeMillis() / 1000
                        )
                        .setOccupancyStatus(occupancy)
                        .build();

        return FeedEntity.newBuilder()
                .setId("VP-" + v.getVehicleId())
                .setVehicle(vp)
                .build();
    }

    // ─────────────────────────────────────────────────────
    // TripUpdate entity
    // ─────────────────────────────────────────────────────

    /**
     * Builds a TripUpdate entity for one bus.
     *
     * This tells consumers: "Bus MAGNI-001 will
     * arrive at CASSINO-CENTRO in 120 seconds
     * (2 minutes late), at FOLCARA-VIA in 480
     * seconds, at FOLCARA-CAMPUS in 600 seconds"
     */
    private FeedEntity buildTripUpdateEntity(
            VehiclePosition v) {

        TripDescriptor trip = TripDescriptor.newBuilder()
                .setRouteId(ROUTE_ID)
                .setScheduleRelationship(
                        TripDescriptor.ScheduleRelationship.SCHEDULED
                )
                .build();

        VehicleDescriptor vehicleDesc =
                VehicleDescriptor.newBuilder()
                        .setId(v.getVehicleId())
                        .build();

        TripUpdate.Builder tripUpdate =
                TripUpdate.newBuilder()
                        .setTrip(trip)
                        .setVehicle(vehicleDesc)
                        .setTimestamp(
                                System.currentTimeMillis() / 1000
                        );

        // Add a StopTimeUpdate for each stop
        for (int i = 0; i < STOP_IDS.length; i++) {
            String stopId = STOP_IDS[i];

            try {
                // Get ETA at this stop
                var arrivals =
                        etaService.getArrivalsAtStop(stopId);

                // Find the arrival for this vehicle
                var arrival = arrivals.stream()
                        .filter(a -> a.getVehicleId()
                                .equals(v.getVehicleId()))
                        .findFirst();

                if (arrival.isPresent()) {
                    long arrivalTime = arrival.get()
                            .getEstimatedArrival()
                            .getEpochSecond();

                    long scheduledTime = arrival.get()
                            .getScheduledArrival()
                            .getEpochSecond();

                    // Delay in seconds
                    // (positive = late, negative = early)
                    int delay = (int)(arrivalTime
                            - scheduledTime);

                    TripUpdate.StopTimeEvent arrivalEvent =
                            TripUpdate.StopTimeEvent
                                    .newBuilder()
                                    .setTime(arrivalTime)
                                    .setDelay(delay)
                                    .build();

                    TripUpdate.StopTimeUpdate stu =
                            TripUpdate.StopTimeUpdate
                                    .newBuilder()
                                    .setStopSequence(i + 1)
                                    .setStopId(stopId)
                                    .setArrival(arrivalEvent)
                                    .setScheduleRelationship(
                                            TripUpdate.StopTimeUpdate
                                                    .ScheduleRelationship
                                                    .SCHEDULED
                                    )
                                    .build();

                    tripUpdate.addStopTimeUpdate(stu);
                }

            } catch (Exception e) {
                log.debug("Could not compute ETA for " +
                                "stop {} vehicle {}: {}",
                        stopId, v.getVehicleId(),
                        e.getMessage());
            }
        }

        return FeedEntity.newBuilder()
                .setId("TU-" + v.getVehicleId())
                .setTripUpdate(tripUpdate.build())
                .build();
    }

    // ─────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────

    /**
     * Convert BLE device count to GTFS occupancy status.
     *
     * GTFS defines standard occupancy levels that
     * passenger apps can display as crowding icons.
     */
    private OccupancyStatus toOccupancyStatus(
            Integer bleCount) {
        if (bleCount == null) {
            return OccupancyStatus.EMPTY;
        }
        // Rough estimate: ~60% of BLE devices
        // are passengers
        int passengers = (int)(bleCount * 0.6);
        if (passengers < 10) {
            return OccupancyStatus.MANY_SEATS_AVAILABLE;
        } else if (passengers < 25) {
            return OccupancyStatus.FEW_SEATS_AVAILABLE;
        } else if (passengers < 40) {
            return OccupancyStatus.STANDING_ROOM_ONLY;
        } else {
            return OccupancyStatus.FULL;
        }
    }
}