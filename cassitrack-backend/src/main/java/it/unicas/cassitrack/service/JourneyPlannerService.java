package it.unicas.cassitrack.service;

import it.unicas.cassitrack.dto.*;
import it.unicas.cassitrack.dto.JourneyOption.JourneyLeg;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The multimodal journey planner.
 *
 * Given an origin and destination, computes
 * all feasible journey options across multiple
 * transport modes and ranks them by duration.
 *
 * Each option includes:
 * - Travel time
 * - Distance
 * - Cost
 * - Green Index (CO₂ score)
 * - Detailed legs
 *
 * This is the core of OMNIMOVE — the
 * Mobility as a Service layer built on top
 * of CASSITRACK's real-time data.
 */
@Service
@RequiredArgsConstructor
public class JourneyPlannerService {

    private static final Logger log = LoggerFactory.getLogger(JourneyPlannerService.class);

    private final RouteMatchingService routeMatcher;
    private final GreenIndexService    greenIndex;
    private final ETAService           etaService;
    private final VehicleStateCache    vehicleCache;

    // Average speeds for each mode (km/h)
    private static final double SPEED_WALK    = 5.0;
    private static final double SPEED_BIKE    = 15.0;
    private static final double SPEED_SCOOTER = 20.0;
    private static final double SPEED_CAR     = 30.0;
    // Bus speed varies — we use real data

    // Costs per journey (flat rates for Cassino)
    // Bus — flat ticket price
    private static final double COST_BUS = 1.00;

    // Walk — always free
    private static final double COST_WALK = 0.0;

    // Car — fuel estimate
    private static final double COST_CAR = 3.50;

    // Elerent pricing (local provider in Cassino)
// Bike:    €0.50 unlock + €0.15 per minute
// Scooter: €1.00 unlock + €0.25 per minute
    private static final double ELERENT_BIKE_UNLOCK    = 0.50;
    private static final double ELERENT_BIKE_PER_MIN   = 0.15;
    private static final double ELERENT_SCOOTER_UNLOCK = 1.00;
    private static final double ELERENT_SCOOTER_PER_MIN = 0.25;
    /**
     * Plan all journey options between two points.
     */
    public JourneyResponse plan(JourneyRequest req) {
        log.info("Planning journey from [{},{}] to [{},{}]",
                req.getOriginLat(), req.getOriginLon(),
                req.getDestLat(), req.getDestLon());

        List<JourneyOption> options = new ArrayList<>();

        // Compute straight-line distance
        double distanceMetres = routeMatcher.haversineMetres(
                req.getOriginLat(), req.getOriginLon(),
                req.getDestLat(), req.getDestLon()
        );
        double distanceKm = distanceMetres / 1000.0;

        List<String> modes = req.getModes() != null
                && !req.getModes().isEmpty()
                ? req.getModes()
                : List.of("BUS", "WALK", "BIKE", "SCOOTER", "CAR");

        // Compute option for each requested mode
        for (String mode : modes) {
            try {
                JourneyOption option = switch (
                        mode.toUpperCase()) {
                    case "BUS"     -> planBus(
                            req, distanceKm);
                    case "WALK"    -> planWalk(
                            req, distanceMetres, distanceKm);
                    case "BIKE"    -> planBike(
                            req, distanceMetres, distanceKm);
                    case "SCOOTER" -> planScooter(
                            req, distanceMetres, distanceKm);
                    case "CAR"     -> planCar(
                            req, distanceMetres, distanceKm);
                    default -> null;
                };

                if (option != null) {
                    options.add(option);
                }
            } catch (Exception e) {
                log.warn("Failed to plan {} option: {}",
                        mode, e.getMessage());
            }
        }

        // Sort by duration (fastest first)
        options.sort(Comparator.comparing(
                JourneyOption::getDurationMinutes
        ));

        boolean realtimeAvailable =
                !vehicleCache.getActive().isEmpty();

        return JourneyResponse.builder()
                .options(options)
                .origin(req.getOriginName() != null
                        ? req.getOriginName()
                        : "Origin")
                .destination(req.getDestName() != null
                        ? req.getDestName()
                        : "Destination")
                .totalOptions(options.size())
                .realtimeAvailable(realtimeAvailable)
                .build();
    }

    // ─────────────────────────────────────────────
    // Bus option
    // ─────────────────────────────────────────────

    private JourneyOption planBus(
            JourneyRequest req, double distanceKm) {

        // Find nearest stop to origin
        String nearestOriginStop =
                routeMatcher.findNearestStopId(
                        req.getOriginLat(), req.getOriginLon()
                );

        // Find nearest stop to destination
        String nearestDestStop =
                routeMatcher.findNearestStopId(
                        req.getDestLat(), req.getDestLon()
                );

        // Walk time to the bus stop (minutes)
        double walkToStopMetres =
                routeMatcher.haversineMetres(
                        req.getOriginLat(), req.getOriginLon(),
                        getStopLat(nearestOriginStop),
                        getStopLon(nearestOriginStop)
                );
        int walkToStopMin = (int) Math.ceil(
                walkToStopMetres / 1000.0
                        / SPEED_WALK * 60
        );

        // Bus journey time (using distance at 25 km/h avg)
        int busJourneyMin = (int) Math.ceil(
                distanceKm / 25.0 * 60
        );

        // Wait time — from ETA service
        int waitMin = 5; // default
        try {
            var arrivals = etaService
                    .getArrivalsAtStop(nearestOriginStop);
            if (!arrivals.isEmpty()) {
                long etaSec = (
                        arrivals.get(0).getEstimatedArrival()
                                .getEpochSecond()
                                - System.currentTimeMillis() / 1000
                );
                waitMin = (int) Math.max(
                        0, etaSec / 60
                );
            }
        } catch (Exception e) {
            log.debug("ETA unavailable: {}", e.getMessage());
        }

        int totalMin = walkToStopMin
                + waitMin + busJourneyMin;

        double co2 = greenIndex.computeCo2Grams(
                "BUS", distanceKm
        );
        int gi = greenIndex.computeGreenIndex(
                "BUS", distanceKm
        );

        // Build legs
        List<JourneyLeg> legs = new ArrayList<>();

        if (walkToStopMin > 0) {
            legs.add(JourneyLeg.builder()
                    .mode("WALK")
                    .from(req.getOriginName() != null
                            ? req.getOriginName() : "Origin")
                    .to(formatStopName(nearestOriginStop))
                    .durationMinutes(walkToStopMin)
                    .distanceMetres(walkToStopMetres)
                    .instruction("Walk to bus stop")
                    .build());
        }

        legs.add(JourneyLeg.builder()
                .mode("WAIT")
                .from(formatStopName(nearestOriginStop))
                .to(formatStopName(nearestOriginStop))
                .durationMinutes(waitMin)
                .distanceMetres(0.0)
                .instruction("Wait " + waitMin
                        + " min for Linea 16")
                .build());

        legs.add(JourneyLeg.builder()
                .mode("BUS")
                .from(formatStopName(nearestOriginStop))
                .to(formatStopName(nearestDestStop))
                .durationMinutes(busJourneyMin)
                .distanceMetres(distanceKm * 1000)
                .instruction("Linea 16 — Magni Autoservizi")
                .build());

        return JourneyOption.builder()
                .mode("BUS")
                .modeLabel("Linea 16 — Magni Autoservizi")
                .durationMinutes(totalMin)
                .distanceMetres(distanceKm * 1000)
                .costEuros(COST_BUS)
                .greenIndex(gi)
                .co2Grams(co2)
                .etaMinutes(totalMin)
                .summary("Take Bus 16 from "
                        + formatStopName(nearestOriginStop)
                        + " — arrives in "
                        + totalMin + " min")
                .legs(legs)
                .build();
    }

    // ─────────────────────────────────────────────
    // Walk option
    // ─────────────────────────────────────────────

    private JourneyOption planWalk(
            JourneyRequest req,
            double distanceMetres,
            double distanceKm) {

        int durationMin = (int) Math.ceil(
                distanceKm / SPEED_WALK * 60
        );

        return JourneyOption.builder()
                .mode("WALK")
                .modeLabel("Walking")
                .durationMinutes(durationMin)
                .distanceMetres(distanceMetres)
                .costEuros(COST_WALK)
                .greenIndex(100)
                .co2Grams(0.0)
                .etaMinutes(durationMin)
                .summary("🚶 Have a car free day! Walk " + formatDistance(distanceMetres)
                        + " — " + durationMin + " min")
                .legs(List.of(JourneyLeg.builder()
                        .mode("WALK")
                        .from(req.getOriginName() != null
                                ? req.getOriginName() : "Origin")
                        .to(req.getDestName() != null
                                ? req.getDestName() : "Destination")
                        .durationMinutes(durationMin)
                        .distanceMetres(distanceMetres)
                        .instruction("Walk the entire route")
                        .build()))
                .build();
    }

    // ─────────────────────────────────────────────
    // Bike option
    // ─────────────────────────────────────────────

    private JourneyOption planBike(
            JourneyRequest req,
            double distanceMetres,
            double distanceKm) {

        int durationMin = (int) Math.ceil(
                distanceKm / SPEED_BIKE * 60
        );

        // Elerent pricing: unlock + per-minute rate
        double cost = ELERENT_BIKE_UNLOCK
                + (durationMin * ELERENT_BIKE_PER_MIN);
        cost = Math.round(cost * 100.0) / 100.0;

        int gi = greenIndex.computeGreenIndex(
                "BIKE", distanceKm
        );

        return JourneyOption.builder()
                .mode("BIKE")
                .modeLabel("Elerent Bike Share")
                .durationMinutes(durationMin)
                .distanceMetres(distanceMetres)
                .costEuros(cost)
                .greenIndex(gi)
                .co2Grams(0.0)
                .etaMinutes(durationMin)
                .summary("Elerent bike "
                        + formatDistance(distanceMetres)
                        + " — " + durationMin + " min"
                        + " (~€" + String.format("%.2f", cost) + ")")
                .legs(List.of(JourneyLeg.builder()
                        .mode("BIKE")
                        .from(req.getOriginName() != null
                                ? req.getOriginName() : "Origin")
                        .to(req.getDestName() != null
                                ? req.getDestName() : "Destination")
                        .durationMinutes(durationMin)
                        .distanceMetres(distanceMetres)
                        .instruction(
                                "Elerent bike share · Unlock €"
                                        + ELERENT_BIKE_UNLOCK
                                        + " + €" + ELERENT_BIKE_PER_MIN
                                        + "/min · elerent.it"
                        )
                        .build()))
                .build();
    }

    // ─────────────────────────────────────────────
    // E-Scooter option
    // ─────────────────────────────────────────────

    private JourneyOption planScooter(
            JourneyRequest req,
            double distanceMetres,
            double distanceKm) {

        int durationMin = (int) Math.ceil(
                distanceKm / SPEED_SCOOTER * 60
        );

        // Elerent pricing: unlock + per-minute rate
        double cost = ELERENT_SCOOTER_UNLOCK
                + (durationMin * ELERENT_SCOOTER_PER_MIN);
        cost = Math.round(cost * 100.0) / 100.0;

        double co2 = greenIndex.computeCo2Grams(
                "SCOOTER", distanceKm
        );
        int gi = greenIndex.computeGreenIndex(
                "SCOOTER", distanceKm
        );

        return JourneyOption.builder()
                .mode("SCOOTER")
                .modeLabel("Elerent E-Scooter")
                .durationMinutes(durationMin)
                .distanceMetres(distanceMetres)
                .costEuros(cost)
                .greenIndex(gi)
                .co2Grams(co2)
                .etaMinutes(durationMin)
                .summary("🛴 Have a car free day! E-scooter "
                         + formatDistance(distanceMetres)
                         + " — " + durationMin + " min"
                         + " (~€" + String.format("%.2f", cost) + ")")
                .legs(List.of(JourneyLeg.builder()
                        .mode("SCOOTER")
                        .from(req.getOriginName() != null
                                ? req.getOriginName() : "Origin")
                        .to(req.getDestName() != null
                                ? req.getDestName() : "Destination")
                        .durationMinutes(durationMin)
                        .distanceMetres(distanceMetres)
                        .instruction(
                                "Elerent e-scooter · Unlock €"
                                        + ELERENT_SCOOTER_UNLOCK
                                        + " + €" + ELERENT_SCOOTER_PER_MIN
                                        + "/min · elerent.it"
                                        + " · API integration pending"
                        )
                        .build()))
                .build();
    }

    // ─────────────────────────────────────────────
    // Car option
    // ─────────────────────────────────────────────

    private JourneyOption planCar(
            JourneyRequest req,
            double distanceMetres,
            double distanceKm) {

        int durationMin = (int) Math.ceil(
                distanceKm / SPEED_CAR * 60
        );

        double co2 = greenIndex.computeCo2Grams(
                "CAR", distanceKm
        );
        int gi = greenIndex.computeGreenIndex(
                "CAR", distanceKm
        );

        return JourneyOption.builder()
                .mode("CAR")
                .modeLabel("Private Car")
                .durationMinutes(durationMin)
                .distanceMetres(distanceMetres)
                .costEuros(COST_CAR)
                .greenIndex(gi)
                .co2Grams(co2)
                .etaMinutes(durationMin)
                .summary("Drive "
                        + formatDistance(distanceMetres)
                        + " — " + durationMin + " min")
                .legs(List.of(JourneyLeg.builder()
                        .mode("CAR")
                        .from(req.getOriginName() != null
                                ? req.getOriginName() : "Origin")
                        .to(req.getDestName() != null
                                ? req.getDestName() : "Destination")
                        .durationMinutes(durationMin)
                        .distanceMetres(distanceMetres)
                        .instruction("Drive by private car")
                        .build()))
                .build();
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private String formatDistance(double metres) {
        if (metres < 1000) {
            return (int) metres + "m";
        }
        return String.format("%.1f km", metres / 1000);
    }

    private String formatStopName(String stopId) {
        if (stopId == null) return "Unknown stop";
        return switch (stopId) {
            case "CASSINO-STAZIONE"  ->
                    "Cassino Stazione FS";
            case "CASSINO-CENTRO"    ->
                    "Cassino Centro";
            case "CASSINO-OSPEDALE"  ->
                    "Ospedale S. Scolastica";
            case "FOLCARA-VIA"       ->
                    "Via Folcara";
            case "FOLCARA-CAMPUS"    ->
                    "Campus UNICAS Folcara";
            default -> stopId;
        };
    }

    private double getStopLat(String stopId) {
        if (stopId == null) return 41.4917;
        return switch (stopId) {
            case "CASSINO-STAZIONE"  -> 41.4892;
            case "CASSINO-CENTRO"    -> 41.4917;
            case "CASSINO-OSPEDALE"  -> 41.4955;
            case "FOLCARA-VIA"       -> 41.5020;
            case "FOLCARA-CAMPUS"    -> 41.5041;
            default -> 41.4917;
        };
    }

    private double getStopLon(String stopId) {
        if (stopId == null) return 13.8314;
        return switch (stopId) {
            case "CASSINO-STAZIONE"  -> 13.8282;
            case "CASSINO-CENTRO"    -> 13.8314;
            case "CASSINO-OSPEDALE"  -> 13.8330;
            case "FOLCARA-VIA"       -> 13.8200;
            case "FOLCARA-CAMPUS"    -> 13.8189;
            default -> 13.8314;
        };
    }
}