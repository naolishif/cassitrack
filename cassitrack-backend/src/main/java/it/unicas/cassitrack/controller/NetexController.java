package it.unicas.cassitrack.controller;

import it.unicas.cassitrack.dto.netex.*;
import it.unicas.cassitrack.model.*;
import it.unicas.cassitrack.model.Route;
import it.unicas.cassitrack.model.Stop;
import it.unicas.cassitrack.repository.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/static")
public class NetexController {

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final ScheduledStopRepository scheduledStopRepository;

    // Injection dei tuoi reali repository tramite costruttore
    public NetexController(StopRepository stopRepository,
                           RouteRepository routeRepository,
                           TripRepository tripRepository, ScheduledStopRepository scheduledStopRepository) {
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.tripRepository = tripRepository;
        this.scheduledStopRepository = scheduledStopRepository;
    }

    @GetMapping(value = "/netex", produces = MediaType.APPLICATION_XML_VALUE)
    public PublicationDeliveryDTO getNetexData() {

        // ==========================================
        // 1. POPOLIAMO IL SITE FRAME (Le Fermate)
        // ==========================================
        List<Stop> dbStops = stopRepository.findAll();
        List<ScheduledStopPointDTO> netexStops = dbStops.stream().map(stop -> {
            ScheduledStopPointDTO dto = new ScheduledStopPointDTO();
            dto.setId(stop.getId());
            dto.setName(stop.getName());
            dto.setLatitude(stop.getLat());
            dto.setLongitude(stop.getLon());
            return dto;
        }).collect(Collectors.toList());

        SiteFrameDTO siteFrame = new SiteFrameDTO();
        siteFrame.setStopPoints(netexStops);

        // ==========================================
        // 2. POPOLIAMO IL SERVICE FRAME (Linee e Corse)
        // ==========================================

        // 2a. Mappatura delle Linee (dalla tabella routes)
        List<Route> dbRoutes = routeRepository.findAll();
        List<LineDTO> netexLines = dbRoutes.stream().map(route -> {
            LineDTO dto = new LineDTO();
            dto.setId(route.getId());
            dto.setName(route.getLongName());      // o il campo che hai per il nome lungo
            dto.setShortName(route.getShortName());  // o il campo per il nome corto
            //dto.setColor(route.getColor());
            return dto;
        }).collect(Collectors.toList());

        // 2b. Mappatura delle Corse (dalla tabella trips) + Fermate intermedie
            List<Trip> dbTrips = tripRepository.findAll();
            List<ServiceJourneyDTO> netexJourneys = dbTrips.stream().map(trip -> {
                ServiceJourneyDTO journeyDto = new ServiceJourneyDTO();
                journeyDto.setId(trip.getId());

                // Attenzione: adatta il nome del getter (getRouteId o getRoute_id)
                // in base a come lo hai scritto in Trip.java
                journeyDto.setLineRef(new RefDTO(trip.getRoute().getId()));

                // Facciamo la query per prendere le fermate di questo specifico trip
                // Attenzione: adatta il nome del getter (getId o altro)
                List<ScheduledStop> stopsForThisTrip = scheduledStopRepository.findByTripId(trip.getId());

                if (!stopsForThisTrip.isEmpty()) {
                    List<CallDTO> netexCalls = stopsForThisTrip.stream().map(sStop -> {
                        CallDTO callDto = new CallDTO();

                        // Attenzione: adatta i getter (es. getStop_sequence() e getStop_id())
                        callDto.setOrder(sStop.getStopSequence());
                        callDto.setScheduledStopPointRef(new RefDTO(sStop.getStopId()));
                        callDto.setArrivalSeconds(sStop.getArrivalSeconds());

                        return callDto;
                    }).collect(Collectors.toList());

                    journeyDto.setCalls(netexCalls);
                }

                return journeyDto;
            }).collect(Collectors.toList());

        // Impacchettiamo tutto nel ServiceFrame
        ServiceFrameDTO serviceFrame = new ServiceFrameDTO();
        serviceFrame.setLines(netexLines);
        serviceFrame.setServiceJourneys(netexJourneys);

        // ==========================================
        // 3. ASSEMBLAGGIO FINALE DEL DOCUMENTO
        // ==========================================
        CompositeFrameDTO compositeFrame = new CompositeFrameDTO();
        compositeFrame.setSiteFrames(List.of(siteFrame));
        compositeFrame.setServiceFrames(List.of(serviceFrame));

        DataObjects dataObjects = new DataObjects();
        dataObjects.setCompositeFrame(compositeFrame);

        PublicationDeliveryDTO response = new PublicationDeliveryDTO();
        response.setDataObjects(dataObjects);

        return response;
    }
}