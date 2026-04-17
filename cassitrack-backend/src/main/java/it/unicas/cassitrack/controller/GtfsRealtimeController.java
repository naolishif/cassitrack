package it.unicas.cassitrack.controller;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unicas.cassitrack.service.GtfsRealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes the GTFS Realtime feed.
 *
 * GET /api/v1/feed/gtfs-rt
 *
 * Returns Protocol Buffers binary data.
 * This is what Google Maps, Moovit, and the
 * Italian national NAP consume to show your
 * buses on their platforms.
 *
 * Also provides a human-readable JSON version
 * for debugging and development.
 */
@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "GTFS Realtime",
        description = "Standard transit data feed")
public class GtfsRealtimeController {

    private final GtfsRealtimeService gtfsService;

    /**
     * GET /api/v1/feed/gtfs-rt
     *
     * Returns the GTFS Realtime feed as
     * Protocol Buffers binary (the standard format).
     *
     * This endpoint can be registered with:
     * - Italy's national NAP (CCISS)
     * - Lazio Regional Access Point (RAP)
     * - Google Maps Transit API
     * - Any GTFS-RT compatible consumer
     */
    @GetMapping(
            value = "/gtfs-rt",
            produces = "application/x-protobuf"
    )
    @Operation(
            summary = "GTFS Realtime feed",
            description =
                    "Returns live vehicle positions and " +
                            "trip updates in GTFS Realtime format " +
                            "(Protocol Buffers). Compatible with " +
                            "Google Maps, Moovit, and the Italian NAP."
    )
    public ResponseEntity<byte[]> getGtfsRealtimeFeed() {
        try {
            FeedMessage feed = gtfsService.buildFeed();
            byte[] bytes = feed.toByteArray();

            log.debug("GTFS-RT feed generated: " +
                            "{} bytes, {} entities",
                    bytes.length,
                    feed.getEntityCount());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            "application/x-protobuf"
                    ))
                    .header("X-Entity-Count",
                            String.valueOf(feed.getEntityCount()))
                    .body(bytes);

        } catch (Exception e) {
            log.error("Failed to generate GTFS-RT feed: {}",
                    e.getMessage());
            return ResponseEntity.internalServerError()
                    .build();
        }
    }

    /**
     * GET /api/v1/feed/gtfs-rt/debug
     *
     * Human-readable version of the GTFS-RT feed.
     * Returns a summary as plain text — useful
     * for testing without a Protocol Buffers parser.
     */
    @GetMapping(
            value = "/gtfs-rt/debug",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    @Operation(
            summary = "GTFS Realtime feed (debug)",
            description =
                    "Human-readable summary of the feed. " +
                            "Use this to verify the feed is working " +
                            "without needing a Protobuf parser."
    )
    public ResponseEntity<String> getGtfsRealtimeDebug() {
        try {
            FeedMessage feed = gtfsService.buildFeed();

            StringBuilder sb = new StringBuilder();
            sb.append("=== CASSITRACK GTFS Realtime Feed ===\n");
            sb.append("Timestamp: ")
                    .append(feed.getHeader().getTimestamp())
                    .append("\n");
            sb.append("Version: ")
                    .append(feed.getHeader()
                            .getGtfsRealtimeVersion())
                    .append("\n");
            sb.append("Total entities: ")
                    .append(feed.getEntityCount())
                    .append("\n\n");

            feed.getEntityList().forEach(entity -> {
                sb.append("--- Entity: ")
                        .append(entity.getId())
                        .append(" ---\n");

                if (entity.hasVehicle()) {
                    var vp = entity.getVehicle();
                    sb.append("  Type: VehiclePosition\n");
                    sb.append("  Vehicle: ")
                            .append(vp.getVehicle().getId())
                            .append("\n");
                    sb.append("  Position: lat=")
                            .append(vp.getPosition().getLatitude())
                            .append(", lon=")
                            .append(vp.getPosition().getLongitude())
                            .append("\n");
                    sb.append("  Speed: ")
                            .append(String.format("%.1f",
                                    vp.getPosition().getSpeed() * 3.6f))
                            .append(" km/h\n");
                    sb.append("  Occupancy: ")
                            .append(vp.getOccupancyStatus())
                            .append("\n");
                }

                if (entity.hasTripUpdate()) {
                    var tu = entity.getTripUpdate();
                    sb.append("  Type: TripUpdate\n");
                    sb.append("  Vehicle: ")
                            .append(tu.getVehicle().getId())
                            .append("\n");
                    sb.append("  Stop updates: ")
                            .append(tu.getStopTimeUpdateCount())
                            .append("\n");
                    tu.getStopTimeUpdateList()
                            .forEach(stu -> {
                                sb.append("    Stop ")
                                        .append(stu.getStopId())
                                        .append(": arrives at ")
                                        .append(stu.getArrival()
                                                .getTime())
                                        .append(" (delay: ")
                                        .append(stu.getArrival()
                                                .getDelay())
                                        .append("s)\n");
                            });
                }
                sb.append("\n");
            });

            return ResponseEntity.ok(sb.toString());

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error: " + e.getMessage());
        }
    }
}