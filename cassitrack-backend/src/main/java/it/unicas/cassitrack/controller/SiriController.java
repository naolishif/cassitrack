package it.unicas.cassitrack.controller;

import it.unicas.cassitrack.dto.siri.Siri;
import it.unicas.cassitrack.dto.siri.SiriMapper;
import it.unicas.cassitrack.model.VehiclePosition;
import it.unicas.cassitrack.service.VehicleStateCache;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/siri")
public class SiriController {

    private final VehicleStateCache vehicleStateCache;

    public SiriController(VehicleStateCache vehicleStateCache) {
        this.vehicleStateCache = vehicleStateCache;
    }

    @GetMapping(value = "/vehicle-monitoring", produces = MediaType.APPLICATION_XML_VALUE)
    public Siri getRealTimeBusData(@RequestParam(value = "route_id", required = false) String routeId) {

        Collection<VehiclePosition> vehicles = vehicleStateCache.getActive();

        // Filtro opzionale per route_id
        if (routeId != null && !routeId.isBlank()) {
            vehicles = vehicles.stream()
                    .filter(v -> routeId.equals(v.getRouteId()))
                    .collect(Collectors.toList());
        }

        return SiriMapper.toSiriFromCache(vehicles);
    }
}