package it.unicas.omnimove.service;

import it.unicas.omnimove.client.CassitrackClient;
import it.unicas.omnimove.dto.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OMNIMOVE Journey Planner.
 *
 * Gets live bus data from CASSITRACK via REST API.
 * Never accesses CASSITRACK database directly.
 * Weather-aware: each option gets a weather warning.
 */
@Service
@RequiredArgsConstructor
public class JourneyPlannerService {

    private static final Logger log =
        LoggerFactory.getLogger(JourneyPlannerService.class);

    private final CassitrackClient cassitrackClient;
    private final GreenIndexService greenIndex;
    private final WeatherService weatherService;

    private static final double SPEED_WALK    = 5.0;
    private static final double SPEED_BIKE    = 15.0;
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

    private static final String[] STOP_IDS = {
        "CASSINO-STAZIONE","CASSINO-CENTRO",
        "CASSINO-OSPEDALE","FOLCARA-VIA","FOLCARA-CAMPUS"
    };

    public JourneyResponse plan(JourneyRequest req) {
        log.info("Planning: {} → {}", req.getOriginName(), req.getDestName());

        WeatherService.WeatherData weather = weatherService.getCurrentWeather();
        boolean realtimeAvailable = cassitrackClient.isAvailable();

        double distMetres = haversineMetres(
            req.getOriginLat(), req.getOriginLon(),
            req.getDestLat(), req.getDestLon());
        double distKm = distMetres / 1000.0;

        List<String> modes = (req.getModes() != null && !req.getModes().isEmpty())
            ? req.getModes()
            : List.of("BUS","BIKE","SCOOTER","WALK");

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
        options.sort(Comparator.comparing(JourneyOption::getDurationMinutes));

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
        int waitMin = 5;
        try {
            var arrivals = cassitrackClient.getArrivalsAtStop(nearestStop);
            if (!arrivals.isEmpty()) {
                long etaSec = arrivals.get(0).getEstimatedArrival().getEpochSecond()
                    - System.currentTimeMillis() / 1000;
                waitMin = (int) Math.max(0, etaSec / 60);
            }
        } catch (Exception e) { log.debug("ETA unavailable"); }

        double walkMetres = haversineMetres(req.getOriginLat(), req.getOriginLon(),
            getStopLat(nearestStop), getStopLon(nearestStop));
        int walkMin = (int) Math.ceil(walkMetres / 1000.0 / SPEED_WALK * 60);
        int busMin  = (int) Math.ceil(distKm / 25.0 * 60);
        int total   = walkMin + waitMin + busMin;

        List<JourneyLeg> legs = new ArrayList<>();
        if (walkMin > 0) legs.add(JourneyLeg.builder().mode("WALK")
            .from(req.getOriginName()).to(fmtStop(nearestStop))
            .durationMinutes(walkMin).distanceMetres(walkMetres)
            .instruction("Walk to bus stop").build());
        legs.add(JourneyLeg.builder().mode("WAIT")
            .from(fmtStop(nearestStop)).to(fmtStop(nearestStop))
            .durationMinutes(waitMin).distanceMetres(0.0)
            .instruction("Wait " + waitMin + " min for Linea 16").build());
        legs.add(JourneyLeg.builder().mode("BUS")
            .from(fmtStop(nearestStop)).to(req.getDestName())
            .durationMinutes(busMin).distanceMetres(distKm * 1000)
            .instruction("Linea 16 — Magni Autoservizi").build());

        return JourneyOption.builder()
            .mode("BUS").modeLabel("Linea 16 — Magni Autoservizi")
            .durationMinutes(total).distanceMetres(distKm * 1000)
            .costEuros(COST_BUS)
            .greenIndex(greenIndex.computeGreenIndex("BUS", distKm))
            .co2Grams(greenIndex.computeCo2Grams("BUS", distKm))
            .etaMinutes(total)
            .summary("Take Bus 16 from " + fmtStop(nearestStop))
            .weatherWarning(weatherService.getModeWarning(weather.condition, "BUS"))
            .weatherSuggestion(weather.suggestion)
            .legs(legs).build();
    }

    private JourneyOption planBike(JourneyRequest req, double distMetres,
            double distKm, WeatherService.WeatherData weather) {
        int dur = (int) Math.ceil(distKm / SPEED_BIKE * 60);
        double cost = Math.round((bikeUnlock + dur * bikePerMin) * 100) / 100.0;
        return JourneyOption.builder()
            .mode("BIKE").modeLabel("Elerent Bike Share")
            .durationMinutes(dur).distanceMetres(distMetres)
            .costEuros(cost).greenIndex(100).co2Grams(0.0).etaMinutes(dur)
            .summary("Elerent bike " + fmtDist(distMetres) + " — " + dur
                + " min (~€" + String.format("%.2f", cost) + ")")
            .weatherWarning(weatherService.getModeWarning(weather.condition, "BIKE"))
            .weatherSuggestion(weather.suggestion)
            .legs(List.of(JourneyLeg.builder().mode("BIKE")
                .from(req.getOriginName()).to(req.getDestName())
                .durationMinutes(dur).distanceMetres(distMetres)
                .instruction("Elerent bike · Unlock €" + bikeUnlock
                    + " + €" + bikePerMin + "/min · elerent.it").build()))
            .build();
    }

    private JourneyOption planScooter(JourneyRequest req, double distMetres,
            double distKm, WeatherService.WeatherData weather) {
        int dur = (int) Math.ceil(distKm / SPEED_SCOOTER * 60);
        double cost = Math.round((scooterUnlock + dur * scooterPerMin) * 100) / 100.0;
        return JourneyOption.builder()
            .mode("SCOOTER").modeLabel("Elerent E-Scooter")
            .durationMinutes(dur).distanceMetres(distMetres)
            .costEuros(cost).greenIndex(100).co2Grams(0.0).etaMinutes(dur)
            .summary("🛴 Elerent e-scooter " + fmtDist(distMetres) + " — " + dur
                + " min (~€" + String.format("%.2f", cost) + ")")
            .weatherWarning(weatherService.getModeWarning(weather.condition, "SCOOTER"))
            .weatherSuggestion(weather.suggestion)
            .legs(List.of(JourneyLeg.builder().mode("SCOOTER")
                .from(req.getOriginName()).to(req.getDestName())
                .durationMinutes(dur).distanceMetres(distMetres)
                .instruction("Elerent e-scooter · Unlock €" + scooterUnlock
                    + " + €" + scooterPerMin + "/min · elerent.it").build()))
            .build();
    }

    private JourneyOption planWalk(JourneyRequest req, double distMetres,
            double distKm, WeatherService.WeatherData weather) {
        int dur = (int) Math.ceil(distKm / SPEED_WALK * 60);
        return JourneyOption.builder()
            .mode("WALK").modeLabel("Walking")
            .durationMinutes(dur).distanceMetres(distMetres)
            .costEuros(0.0).greenIndex(100).co2Grams(0.0).etaMinutes(dur)
            .summary("🌿 Have a car free day! Walk " + fmtDist(distMetres) + " — " + dur + " min")
            .weatherWarning(weatherService.getModeWarning(weather.condition, "WALK"))
            .weatherSuggestion(weather.suggestion)
            .legs(List.of(JourneyLeg.builder().mode("WALK")
                .from(req.getOriginName()).to(req.getDestName())
                .durationMinutes(dur).distanceMetres(distMetres)
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

    private String findNearestStopId(double lat, double lon) {
        double min = Double.MAX_VALUE; String nearest = "CASSINO-STAZIONE";
        for (String id : STOP_IDS) {
            double d = haversineMetres(lat, lon, getStopLat(id), getStopLon(id));
            if (d < min) { min = d; nearest = id; }
        }
        return nearest;
    }

    private double getStopLat(String id) { return switch(id) {
        case "CASSINO-STAZIONE"->41.4892; case "CASSINO-CENTRO"->41.4917;
        case "CASSINO-OSPEDALE"->41.4955; case "FOLCARA-VIA"->41.5020;
        case "FOLCARA-CAMPUS"->41.5041; default->41.4917; }; }

    private double getStopLon(String id) { return switch(id) {
        case "CASSINO-STAZIONE"->13.8282; case "CASSINO-CENTRO"->13.8314;
        case "CASSINO-OSPEDALE"->13.8330; case "FOLCARA-VIA"->13.8200;
        case "FOLCARA-CAMPUS"->13.8189; default->13.8314; }; }

    private String fmtStop(String id) { return switch(id) {
        case "CASSINO-STAZIONE"->"Cassino Stazione FS"; case "CASSINO-CENTRO"->"Cassino Centro";
        case "CASSINO-OSPEDALE"->"Ospedale S. Scolastica"; case "FOLCARA-VIA"->"Via Folcara";
        case "FOLCARA-CAMPUS"->"Campus UNICAS Folcara"; default->id; }; }

    private String fmtDist(double m) {
        return m<1000 ? (int)m+"m" : String.format("%.1f km", m/1000); }
}
