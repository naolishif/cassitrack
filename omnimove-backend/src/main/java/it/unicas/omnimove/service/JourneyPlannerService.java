package it.unicas.omnimove.service;

import it.unicas.omnimove.client.CassitrackClient;
import it.unicas.omnimove.dto.*;
import it.unicas.omnimove.model.UserPreferences;
import it.unicas.omnimove.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * OMNIMOVE Journey Planner.
 *
 * Gets live bus data from CASSITRACK via REST API.
 * Never accesses CASSITRACK database directly.
 *
 * BUS option now uses Google Maps Distance Matrix API to compute
 * the real travel time from the bus current GPS position to the
 * destination stop, accounting for live traffic.
 *
 * Fallback chain:
 *   1. Google Maps with bus GPS position (best accuracy)
 *   2. Google Maps with nearest stop as origin (if no bus available)
 *   3. distKm / 25 km/h estimate (if Google Maps unavailable)
 */
@Service
@RequiredArgsConstructor
public class JourneyPlannerService {

    private static final Logger log =
        LoggerFactory.getLogger(JourneyPlannerService.class);

    private final CassitrackClient  cassitrackClient;
    private final GreenIndexService greenIndex;
    private final WeatherService    weatherService;
    private final GoogleMapsService googleMapsService;
    private final it.unicas.omnimove.repository.StopRepository stopRepository;
    private final it.unicas.omnimove.repository.ScheduledStopRepository scheduledStopRepository;
    private final it.unicas.omnimove.repository.UserPreferencesRepository preferencesRepository;

    private static final double SPEED_WALK    = 5.0;
    private static final double SPEED_SCOOTER = 20.0;
    private static final double COST_BUS      = 1.00;

    @Value("${elerent.bike.unlock:0.50}")
    private double bikeUnlock;
    @Value("${elerent.bike.per-minute:0.15}")
    private double bikePerMin;
    @Value("${elerent.scooter.unlock:1.00}")
    private double scooterUnlock;
    @Value("${elerent.scooter.per-minute:0.25}")
    private double scooterPerMin;

    public JourneyResponse plan(JourneyRequest req) {
        log.info("Planning: {} → {}", req.getOriginName(), req.getDestName());

        WeatherService.WeatherData weather = weatherService.getCurrentWeather();
        boolean realtimeAvailable = cassitrackClient.isAvailable();

        double distMetres = haversineMetres(
            req.getOriginLat(), req.getOriginLon(),
            req.getDestLat(), req.getDestLon());
        double distKm = distMetres / 1000.0;

        List<String> modes = new ArrayList<>(
                (req.getModes() != null && !req.getModes().isEmpty())
                        ? req.getModes()
                        : List.of("BUS","BIKE","SCOOTER","WALK")
        );

        boolean preferBike = false;
        if (req.getUserId() != null) {
            var prefsOpt = preferencesRepository.findByUserId(req.getUserId());
            if (prefsOpt.isPresent()) {
                var prefs = prefsOpt.get();
                if (Boolean.FALSE.equals(prefs.getShowWalking())) {
                    modes.remove("WALK");
                }
                preferBike = Boolean.TRUE.equals(prefs.getPreferBikeOverBus());
            }
        }

        boolean busDeferred = preferBike && modes.contains("BIKE") && modes.contains("BUS");
        if (busDeferred) modes.remove("BUS");

        List<JourneyOption> options = new ArrayList<>();
        for (String mode : modes) {
            try {
                JourneyOption opt = switch (mode.toUpperCase()) {
                    case "BUS"     -> planBus(req, distKm, weather);
                    case "BIKE"    -> planBike(req, distMetres, distKm, weather);
                    case "SCOOTER" -> planScooter(req, distMetres, distKm, weather);
                    case "WALK"    -> planWalk(req, distMetres, distKm, weather);
                    default -> null;
                };
                if (opt != null) options.add(opt);
            } catch (Exception e) {
                log.warn("Failed {} option: {}", mode, e.getMessage());
            }
        }
        if (busDeferred) {
            boolean bikeAvailable = options.stream().anyMatch(o -> "BIKE".equals(o.getMode()));
            if (!bikeAvailable) {
                // la bici non è disponibile → calcola il bus come riserva
                try {
                    JourneyOption bus = planBus(req, distKm, weather);
                    if (bus != null) options.add(bus);
                } catch (Exception e) {
                    log.warn("Failed BUS fallback option: {}", e.getMessage());
                }
            }

        }

        boolean raining =
                weather.condition == WeatherService.WeatherCondition.RAIN ||
                        weather.condition == WeatherService.WeatherCondition.HEAVY_RAIN;

        options.sort(
                Comparator.comparingInt((JourneyOption o) ->
                                raining && !"BUS".equals(o.getMode()) ? 1 : 0)   // bus prima se piove
                        .thenComparingInt(JourneyOption::getDurationMinutes));

        if (raining) options.removeIf(o -> "BIKE".equals(o.getMode()) || "SCOOTER".equals(o.getMode())|| "WALK".equals(o.getMode()));

        return JourneyResponse.builder()
            .options(options)
            .origin(req.getOriginName() != null ? req.getOriginName() : "Origin")
            .destination(req.getDestName() != null ? req.getDestName() : "Destination")
            .totalOptions(options.size())
            .realtimeAvailable(realtimeAvailable)
            .weatherSummary(weather.suggestion)
            .temperatureCelsius(weather.tempCelsius)
            .build();
    }


    private JourneyOption planBus(JourneyRequest req, double distKm,
            WeatherService.WeatherData weather) {

        String nearestStop = findNearestStopId(req.getOriginLat(), req.getOriginLon());
        String destStop    = findNearestStopId(req.getDestLat(), req.getDestLon());

        // --- Step 1: walk to bus stop ---
        boolean fromGps = Boolean.TRUE.equals(req.getOriginIsGps());

        double walkMetres = 0;
        int walkMin = 0;
        if (fromGps) {
            walkMetres = haversineMetres(req.getOriginLat(), req.getOriginLon(),
                    getStopLat(nearestStop), getStopLon(nearestStop));
            walkMin = (int) Math.ceil(walkMetres / 1000.0 / SPEED_WALK * 60);
        }
        // --- Step 2+3: la linea e la sua attesa si calcolano insieme ---
        var direct = scheduledStopRepository.findLinesConnecting(nearestStop, destStop);

        String lineLabel;
        int busMin;
        int waitMin = 5;                       // attesa iniziale, assegnata nei rami
        double busMetres = distKm * 1000;      // default per cambio/ripiego
        List<JourneyLeg> busLegs = new ArrayList<>();

        if (!direct.isEmpty()) {
            var line = direct.get(0);
            lineLabel = line.getShortName() + " — " + line.getLongName();

            SegTime seg = busTimeBySegments(line.getTripId(), nearestStop, destStop);
            if (seg == null) {
                log.warn("BUS: sequenza non risolvibile per trip {}", line.getTripId());
                return null;
            }
            busMin    = seg.minutes();
            busMetres = seg.metres();
            waitMin   = waitMinutesForLine(nearestStop, null, line.getShortName());

            String tripId = line.getTripId();
            busLegs.add(JourneyLeg.builder().mode("BUS")
                    .from(fmtStop(nearestStop)).to(req.getDestName())
                    .durationMinutes(busMin).distanceMetres(distKm * 1000)
                    .instruction(lineLabel)
                    .stopCoords(stopCoordsBetween(tripId, nearestStop, destStop))
                    .build());
        } else {
            Transfer t = findBestTransfer(nearestStop, destStop);
            if (t != null) {
                waitMin = waitMinutesForLine(nearestStop, t.l1RouteId(), t.l1Short());
                int changeWait = waitMinutesForLine(t.stop(), t.l2RouteId(), t.l2Short());

                SegTime s1 = busTimeBySegments(t.l1TripId(), nearestStop, t.stop());
                SegTime s2 = busTimeBySegments(t.l2TripId(), t.stop(), destStop);
                int    l1Min = (s1 != null) ? s1.minutes() : t.l1Min();   // ripiego: orario DB
                int    l2Min = (s2 != null) ? s2.minutes() : t.l2Min();
                double m1    = (s1 != null) ? s1.metres()  : 0.0;
                double m2    = (s2 != null) ? s2.metres()  : 0.0;

                lineLabel = t.l1Label() + " + " + t.l2Label();
                busMin    = l1Min + changeWait + l2Min;
                busMetres = m1 + m2;

                busLegs.add(JourneyLeg.builder().mode("BUS")
                        .from(fmtStop(nearestStop)).to(fmtStop(t.stop()))
                        .durationMinutes(l1Min).distanceMetres(m1)
                        .instruction(t.l1Label())
                        .stopCoords(stopCoordsBetween(t.l1TripId(), nearestStop, t.stop()))  // ← aggiunto
                        .build());
                busLegs.add(JourneyLeg.builder().mode("WAIT")
                        .from(fmtStop(t.stop())).to(fmtStop(t.stop()))
                        .durationMinutes(changeWait).distanceMetres(0.0)
                        .instruction("Cambia a " + fmtStop(t.stop()) + " · prendi " + t.l2Label()).build());
                busLegs.add(JourneyLeg.builder().mode("BUS")
                        .from(fmtStop(t.stop())).to(req.getDestName())
                        .durationMinutes(l2Min).distanceMetres(m2)
                        .instruction(t.l2Label())
                        .stopCoords(stopCoordsBetween(t.l2TripId(), t.stop(), destStop))     // ← aggiunto
                        .build());
            } else {
                lineLabel = "Bus";
                busMin = computeBusTravelTime(nearestStop, destStop, distKm);
                waitMin = waitMinutesAt(nearestStop);       // nessuna linea nota: prossimo bus qualunque
                busLegs.add(JourneyLeg.builder().mode("BUS")
                        .from(fmtStop(nearestStop)).to(req.getDestName())
                        .durationMinutes(busMin).distanceMetres(distKm * 1000)
                        .instruction(lineLabel).build());
            }
        }
        int total = walkMin + waitMin + busMin;
        List<JourneyLeg> legs = new ArrayList<>();
        if (walkMin > 0) legs.add(JourneyLeg.builder().mode("WALK")
                .from(req.getOriginName()).to(fmtStop(nearestStop))
                .durationMinutes(walkMin).distanceMetres(walkMetres)
                .instruction("Walk to bus stop").build());
        legs.add(JourneyLeg.builder().mode("WAIT")
                .from(fmtStop(nearestStop)).to(fmtStop(nearestStop))
                .durationMinutes(waitMin).distanceMetres(0.0)
                .instruction("Wait " + waitMin + " min for " + lineLabel).build());
        legs.addAll(busLegs);

        String occupancyWarning = null;
        if (req.getUserId() != null) {
            boolean avoid = preferencesRepository.findByUserId(req.getUserId())
                    .map(p -> Boolean.TRUE.equals(p.getAvoidHighOccupancy()))
                    .orElse(false);
            if (avoid) {
                boolean highOccupancy = cassitrackClient.getActiveVehicles().stream()
                        .anyMatch(v -> "HIGH".equalsIgnoreCase(v.getCrowdingLevel())
                                || "VERY_HIGH".equalsIgnoreCase(v.getCrowdingLevel()));
                if (highOccupancy) occupancyWarning = "⚠️ High occupancy";
            }
        }

        return JourneyOption.builder()
            .mode("BUS").modeLabel(lineLabel)
            .durationMinutes(total).distanceMetres(busMetres)
            .costEuros(COST_BUS)
            .greenIndex(greenIndex.computeGreenIndex("BUS", busMetres / 1000.0))
            .co2Grams(greenIndex.computeCo2Grams("BUS", busMetres / 1000.0))
            .etaMinutes(total)
            .summary("Take " + lineLabel + " from " + fmtStop(nearestStop))
            .weatherWarning(occupancyWarning != null ? occupancyWarning
                        : weatherService.getModeWarning(weather.condition, "BUS"))
            .weatherSuggestion(weather.suggestion)
            .legs(legs).build();
    }

    /**
     * Compute bus travel time from the bus current position to the destination stop.
     *
     * Strategy:
     *   1. Get active vehicles from CASSITRACK
     *   2. Find the nearest bus to the origin stop (most likely to serve the user)
     *   3. Call Google Maps with bus GPS coordinates → destination stop
     *   4. Fallback: Google Maps from origin stop → destination stop
     *   5. Fallback: distKm / 25 km/h estimate
     */
    private int computeBusTravelTime(String originStop, String destStop, double distKm) {

        double destLat = getStopLat(destStop);
        double destLon = getStopLon(destStop);

        // Try to find nearest active bus GPS position
        try {
            List<VehicleDTO> vehicles = cassitrackClient.getActiveVehicles();
            if (!vehicles.isEmpty()) {
                // Find the bus closest to the origin stop
                double stopLat = getStopLat(originStop);
                double stopLon = getStopLon(originStop);

                VehicleDTO nearestBus = vehicles.stream()
                    .filter(v -> v.getLat() != null && v.getLon() != null)
                    .min(Comparator.comparingDouble(v ->
                        haversineMetres(v.getLat(), v.getLon(), stopLat, stopLon)))
                    .orElse(null);

                if (nearestBus != null) {
                    // Google Maps: bus current position → destination stop
                    Optional<GoogleMapsService.TrafficResult> trafficOpt =
                        googleMapsService.getTravelTime(
                            nearestBus.getLat(), nearestBus.getLon(),
                            destLat, destLon);

                    if (trafficOpt.isPresent()) {
                        int busMin = (int) Math.ceil(
                            trafficOpt.get().durationInTrafficSeconds() / 60.0);
                        log.info("Bus travel time (Google Maps, bus GPS [{},{}] → {}): {} min",
                            nearestBus.getLat(), nearestBus.getLon(), destStop, busMin);
                        return busMin;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not get vehicles from CASSITRACK: {}", e.getMessage());
        }

        // Fallback: Google Maps from origin stop → destination stop
        Optional<GoogleMapsService.TrafficResult> fallbackOpt =
            googleMapsService.getTravelTime(
                getStopLat(originStop), getStopLon(originStop),
                destLat, destLon);

        if (fallbackOpt.isPresent()) {
            int busMin = (int) Math.ceil(
                fallbackOpt.get().durationInTrafficSeconds() / 60.0);
            log.info("Bus travel time (Google Maps, stop → stop): {} min", busMin);
            return busMin;
        }

        // Final fallback: simple speed estimate
        int busMin = (int) Math.ceil(distKm / 25.0 * 60);
        log.debug("Bus travel time (fallback estimate): {} min", busMin);
        return busMin;
    }

    private JourneyOption planBike(JourneyRequest req, double distMetres,
        double distKm, WeatherService.WeatherData weather) {
        var r = route(req, "bicycling");
        if (r.isEmpty()) {
            log.warn("BIKE: percorso reale non disponibile da Google — opzione esclusa");
            return null;
        }
        double roadM = r.get().distanceMetres();
        int dur = (int) Math.ceil(r.get().durationSeconds() / 60.0);
        double cost = Math.round((bikeUnlock + dur * bikePerMin) * 100) / 100.0;
        return JourneyOption.builder()
                .mode("BIKE").modeLabel("Elerent Bike Share")
                .durationMinutes(dur).distanceMetres(roadM)            // era distMetres
                .costEuros(cost).greenIndex(100).co2Grams(0.0).etaMinutes(dur)
                .summary("Elerent bike " + fmtDist(roadM) + " — " + dur   // era distMetres
                        + " min (~€" + String.format("%.2f", cost) + ")")
                .weatherWarning(weatherService.getModeWarning(weather.condition, "BIKE"))
                .weatherSuggestion(weather.suggestion)
                .legs(List.of(JourneyLeg.builder().mode("BIKE")
                        .from(req.getOriginName()).to(req.getDestName())
                        .durationMinutes(dur).distanceMetres(roadM)         // era distMetres
                        .instruction("Elerent bike · Unlock €" + bikeUnlock
                                + " + €" + bikePerMin + "/min · elerent.it").build()))
                .build();
    }

    private JourneyOption planScooter(JourneyRequest req, double distMetres,
                                      double distKm, WeatherService.WeatherData weather) {
        var r = route(req, "bicycling");
        if (r.isEmpty()) {
            log.warn("SCOOTER: percorso reale non disponibile da Google — opzione esclusa");
            return null;
        }
        double roadM = r.get().distanceMetres();
        int dur = (int) Math.ceil(roadM / 1000.0 / SPEED_SCOOTER * 60);
        double cost = Math.round((scooterUnlock + dur * scooterPerMin) * 100) / 100.0;
        return JourneyOption.builder()
            .mode("SCOOTER").modeLabel("Elerent E-Scooter")
            .durationMinutes(dur).distanceMetres(roadM)
            .costEuros(cost).greenIndex(100).co2Grams(0.0).etaMinutes(dur)
            .summary("🛴 Elerent e-scooter " + fmtDist(roadM) + " — " + dur
                + " min (~€" + String.format("%.2f", cost) + ")")
            .weatherWarning(weatherService.getModeWarning(weather.condition, "SCOOTER"))
            .weatherSuggestion(weather.suggestion)
            .legs(List.of(JourneyLeg.builder().mode("SCOOTER")
                .from(req.getOriginName()).to(req.getDestName())
                .durationMinutes(dur).distanceMetres(roadM)
                .instruction("Elerent e-scooter · Unlock €" + scooterUnlock
                    + " + €" + scooterPerMin + "/min · elerent.it").build()))
            .build();
    }

    private JourneyOption planWalk(JourneyRequest req, double distMetres,
                                   double distKm, WeatherService.WeatherData weather) {
        var r = route(req, "walking");
        if (r.isEmpty()) {
            log.warn("WALK: percorso reale non disponibile da Google — opzione esclusa");
            return null;                       // niente fallback in linea d'aria
        }
        double roadM = r.get().distanceMetres();
        int dur = (int) Math.ceil(r.get().durationSeconds() / 60.0);
        return JourneyOption.builder()
            .mode("WALK").modeLabel("Walking")
            .durationMinutes(dur).distanceMetres(roadM)
            .costEuros(0.0).greenIndex(100).co2Grams(0.0).etaMinutes(dur)
            .summary("🌿 Have a car free day! Walk " + fmtDist(roadM) + " — " + dur + " min")
            .weatherWarning(weatherService.getModeWarning(weather.condition, "WALK"))
            .weatherSuggestion(weather.suggestion)
            .legs(List.of(JourneyLeg.builder().mode("WALK")
                .from(req.getOriginName()).to(req.getDestName())
                .durationMinutes(dur).distanceMetres(roadM)
                .instruction("Walk the entire route").build()))
            .build();
    }

    private double haversineMetres(double lat1,double lon1,double lat2,double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
            + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }

    /** Minuti di attesa a una fermata, dall'ETA reale di cassitrack (default 5). */
    private int waitMinutesAt(String stopId) {
        int waitMin = 5;
        try {
            var arrivals = cassitrackClient.getArrivalsAtStop(stopId);
            if (!arrivals.isEmpty()) {
                long etaSec = arrivals.get(0).getEstimatedArrival().getEpochSecond()
                        - System.currentTimeMillis() / 1000;
                waitMin = (int) Math.max(0, etaSec / 60);
            }
        } catch (Exception e) {
            log.debug("ETA non disponibile a {}: {}", stopId, e.getMessage());
        }
        return waitMin;
    }

    /** Percorso reale su strada per la modalità Google data (walking/bicycling/driving). */
    private Optional<GoogleMapsService.TrafficResult> route(JourneyRequest req, String mode) {
        return googleMapsService.getTravelTime(
                req.getOriginLat(), req.getOriginLon(),
                req.getDestLat(),   req.getDestLon(), mode);
    }

    private String findNearestStopId(double lat, double lon) {
        double min = Double.MAX_VALUE;
        String nearest = null;
        for (it.unicas.omnimove.model.Stop stop : stopRepository.findAll()) {
            if (stop.getLat() == null || stop.getLon() == null) continue;
            double d = haversineMetres(lat, lon, stop.getLat(), stop.getLon());
            if (d < min) { min = d; nearest = stop.getId(); }
        }
        return nearest;
    }

    private double getStopLat(String id) {
        if (id == null) return 41.4925;
        return stopRepository.findById(id)
                .filter(s -> s.getLat() != null)
                .map(it.unicas.omnimove.model.Stop::getLat)
                .orElse(41.4925);
    }

    private double getStopLon(String id) {
        if (id == null) return 13.8306;
        return stopRepository.findById(id)
                .filter(s -> s.getLon() != null)
                .map(it.unicas.omnimove.model.Stop::getLon)
                .orElse(13.8306);
    }

    private String fmtStop(String id) {
        if (id == null) return "Unknown stop";
        return stopRepository.findById(id)
                .map(it.unicas.omnimove.model.Stop::getName)
                .orElse(id);
    }

    private List<double[]> stopCoordsBetween(String tripId, String originStopId, String destStopId) {
        if (tripId == null) return List.of();

        var seq = new ArrayList<>(scheduledStopRepository.findByTripId(tripId));
        seq.sort(Comparator.comparingInt(it.unicas.omnimove.model.ScheduledStop::getStopSequence));

        // posizioni (indici nella sequenza ordinata) di origine e cambio/destinazione
        int oi = -1, di = -1;
        for (int i = 0; i < seq.size(); i++) {
            String sid = seq.get(i).getStopId();
            if (oi < 0 && sid.equals(originStopId)) oi = i;
            else if (oi >= 0 && di < 0 && sid.equals(destStopId)) { di = i; break; }
        }
        // se non le ho trovate in quest'ordine, riprovo senza vincolo di precedenza
        if (oi < 0 || di < 0) {
            oi = indexOfStop(seq, originStopId);
            di = indexOfStop(seq, destStopId);
        }
        if (oi < 0 || di < 0) return List.of();          // sequenza incoerente

        int from = Math.min(oi, di);
        int to   = Math.max(oi, di);

        List<double[]> coords = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            String sid = seq.get(i).getStopId();
            coords.add(new double[]{ getStopLat(sid), getStopLon(sid) });
        }
        return coords;
    }

    private int indexOfStop(List<it.unicas.omnimove.model.ScheduledStop> seq, String stopId) {
        for (int i = 0; i < seq.size(); i++)
            if (seq.get(i).getStopId().equals(stopId)) return i;
        return -1;
    }
    private record SegTime(int minutes, double metres) {}

    /** Tempo e distanza del bus lungo la sequenza reale delle fermate:
     *  per ogni tratta consecutiva una chiamata Google (traffico live),
     *  con ripiego sull'orario del DB se Google non risponde. */
    private SegTime busTimeBySegments(String tripId, String originStop, String destStop) {
        var seq = new ArrayList<>(scheduledStopRepository.findByTripId(tripId));
        seq.sort(Comparator.comparingInt(it.unicas.omnimove.model.ScheduledStop::getStopSequence));

        int oi = indexOfStop(seq, originStop);
        int di = indexOfStop(seq, destStop);
        if (oi < 0 || di < 0) return null;

        int from = Math.min(oi, di);
        int to   = Math.max(oi, di);

        int totalSec = 0;
        double totalM = 0;
        for (int i = from; i < to; i++) {
            var a = seq.get(i);
            var b = seq.get(i + 1);
            var g = googleMapsService.getTravelTime(
                    getStopLat(a.getStopId()), getStopLon(a.getStopId()),
                    getStopLat(b.getStopId()), getStopLon(b.getStopId()), "driving");
            if (g.isPresent()) {
                totalSec += (int) g.get().durationInTrafficSeconds();
                totalM   += g.get().distanceMetres();
            } else {
                totalSec += Math.abs(b.getArrivalSeconds() - a.getArrivalSeconds());  // ripiego DB
            }
        }
        return new SegTime((int) Math.ceil(totalSec / 60.0), totalM);
    }

    private String fmtDist(double m) {
        return m<1000 ? (int)m+"m" : String.format("%.1f km", m/1000); }

    private record Transfer(String stop, String l1Label, int l1Min,
                            String l2Label, int l2Min, int totalMin,
                            String l2RouteId, String l2Short,
                            String l1RouteId, String l1Short,
                            String l1TripId,  String l2TripId) {}

    /** Cerca il miglior percorso con UN cambio: origine → X → destinazione. */
    private Transfer findBestTransfer(String origin, String dest) {
        var rows = scheduledStopRepository.findBestTransfer(origin, dest);
        if (rows.isEmpty()) return null;
        var x = rows.get(0);
        int m1 = (int) Math.ceil(x.getL1Sec() / 60.0);
        int m2 = (int) Math.ceil(x.getL2Sec() / 60.0);
        return new Transfer(
                x.getTransferStop(),
                x.getL1Short() + " — " + x.getL1Long(), m1,
                x.getL2Short() + " — " + x.getL2Long(), m2,
                m1 + m2,
                x.getL2RouteId(), x.getL2Short(),
                x.getL1RouteId(), x.getL1Short(),
                x.getL1TripId(),  x.getL2TripId());
    }

    /** Attesa per UNA linea specifica a una fermata; ripiega sul prossimo bus, poi su 5. */
    private int waitMinutesForLine(String stopId, String routeId, String routeShort) {
        try {
            var arrivals = cassitrackClient.getArrivalsAtStop(stopId);
            var match = arrivals.stream()
                    .filter(a -> (routeShort != null && routeShort.equals(a.getRouteName()))
                            || (routeId != null && routeId.equals(a.getRouteId())))
                    .findFirst()
                    .orElse(arrivals.isEmpty() ? null : arrivals.get(0));   // fallback: prossimo bus
            if (match != null && match.getEstimatedArrival() != null) {
                long etaSec = match.getEstimatedArrival().getEpochSecond()
                        - System.currentTimeMillis() / 1000;
                return (int) Math.max(0, etaSec / 60);
            }
        } catch (Exception e) {
            log.debug("ETA per linea non disponibile a {}: {}", stopId, e.getMessage());
        }
        return 5;
    }

}
