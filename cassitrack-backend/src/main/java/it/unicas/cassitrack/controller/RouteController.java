package it.unicas.cassitrack.controller;

import it.unicas.cassitrack.model.Route;
import it.unicas.cassitrack.model.Stop;
import it.unicas.cassitrack.repository.RouteRepository;
import it.unicas.cassitrack.repository.ScheduledStopRepository;
import it.unicas.cassitrack.repository.StopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteRepository routeRepository;
    private final ScheduledStopRepository scheduledStopRepository;
    private final StopRepository stopRepository;

    public record StopPoint(String id, String name, double lat, double lon) {}
    public record RouteGeometry(String id, String name, String longName,
                                String color, List<StopPoint> stops) {}

    @GetMapping
    public List<RouteGeometry> getRoutes() {
        Map<String, Stop> stops = new HashMap<>();
        for (Stop s : stopRepository.findAll()) stops.put(s.getId(), s);

        List<RouteGeometry> out = new ArrayList<>();
        for (Route r : routeRepository.findAll()) {
            if (!r.isActive()) continue;;
            List<StopPoint> pts = new ArrayList<>();
            for (var ss : scheduledStopRepository.findRepresentativeSequence(r.getId())) {
                Stop s = stops.get(ss.getStopId());
                if (s != null && s.getLat() != null)
                    pts.add(new StopPoint(s.getId(), s.getName(), s.getLat(), s.getLon()));
            }
            if (pts.size() >= 2)
                out.add(new RouteGeometry(r.getId(), r.getShortName(),
                        r.getLongName(), r.getColor(), pts));
        }
        return out;
    }
}