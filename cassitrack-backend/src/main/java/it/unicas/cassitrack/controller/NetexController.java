package it.unicas.cassitrack.controller;

import it.unicas.cassitrack.dto.netex.*;
import it.unicas.cassitrack.model.*;
import it.unicas.cassitrack.model.Route;
import it.unicas.cassitrack.model.Stop;
import it.unicas.cassitrack.repository.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/static")
public class NetexController {

    @Value("${sse.api-token}")
    private String expectedToken;

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final ScheduledStopRepository scheduledStopRepository;
    private final BusRepository busRepository;

    public NetexController(StopRepository stopRepository,
                           RouteRepository routeRepository,
                           TripRepository tripRepository,
                           ScheduledStopRepository scheduledStopRepository,
                           BusRepository busRepository) {
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.tripRepository = tripRepository;
        this.scheduledStopRepository = scheduledStopRepository;
        this.busRepository = busRepository;
    }

    @GetMapping(value = "/netex", produces = MediaType.APPLICATION_XML_VALUE)
    public PublicationDeliveryDTO getNetexData(
            @RequestHeader(value = "X-Api-Key", required = false) String receivedToken,
            HttpServletResponse response) {

        if (!MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                (receivedToken != null ? receivedToken : "").getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return null;
        }

        // ==========================================
        // 1. SITE FRAME (Fermate)
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
        // 2. SERVICE FRAME (Linee, Bus e Corse)
        // ==========================================

        // 2a. Linee
        List<Route> dbRoutes = routeRepository.findAll();
        List<LineDTO> netexLines = dbRoutes.stream().map(route -> {
            LineDTO dto = new LineDTO();
            dto.setId(route.getId());
            dto.setName(route.getLongName());
            dto.setShortName(route.getShortName());
            return dto;
        }).collect(Collectors.toList());

        // 2b. Bus ← AGGIUNTO
        List<Bus> dbBuses = busRepository.findAll();
        List<BusDTO> netexBuses = dbBuses.stream().map(bus -> {
            BusDTO dto = new BusDTO();
            dto.setId(bus.getBusId());
            dto.setTarga(bus.getTarga());
            dto.setNumeroPosti(bus.getNumeroPosti());
            dto.setPostoDisabili(bus.getPostoDisabili());
            dto.setDisponibile(bus.getDisponibile());
            dto.setCurrentVehicleId(bus.getCurrentVehicleId());
            return dto;
        }).collect(Collectors.toList());

        // 2c. Corse
        List<Trip> dbTrips = tripRepository.findAll();
        List<ServiceJourneyDTO> netexJourneys = dbTrips.stream().map(trip -> {
            ServiceJourneyDTO journeyDto = new ServiceJourneyDTO();
            journeyDto.setId(trip.getId());
            journeyDto.setLineRef(new RefDTO(trip.getRoute().getId()));
            journeyDto.setBusRef(new RefDTO(String.valueOf(trip.getBus().getBusId()))); // ← AGGIUNTO

            List<ScheduledStop> stopsForThisTrip = scheduledStopRepository.findByTripId(trip.getId());
            if (!stopsForThisTrip.isEmpty()) {
                List<CallDTO> netexCalls = stopsForThisTrip.stream().map(sStop -> {
                    CallDTO callDto = new CallDTO();
                    callDto.setOrder(sStop.getStopSequence());
                    callDto.setScheduledStopPointRef(new RefDTO(sStop.getStopId()));
                    callDto.setArrivalSeconds(sStop.getArrivalSeconds());
                    return callDto;
                }).collect(Collectors.toList());
                journeyDto.setCalls(netexCalls);
            }

            return journeyDto;
        }).collect(Collectors.toList());

        // Assemblaggio ServiceFrame
        ServiceFrameDTO serviceFrame = new ServiceFrameDTO();
        serviceFrame.setLines(netexLines);
        serviceFrame.setBuses(netexBuses); // ← AGGIUNTO
        serviceFrame.setServiceJourneys(netexJourneys);

        // ==========================================
        // 3. ASSEMBLAGGIO FINALE
        // ==========================================
        CompositeFrameDTO compositeFrame = new CompositeFrameDTO();
        compositeFrame.setSiteFrames(List.of(siteFrame));
        compositeFrame.setServiceFrames(List.of(serviceFrame));

        DataObjects dataObjects = new DataObjects();
        dataObjects.setCompositeFrame(compositeFrame);

        PublicationDeliveryDTO publicationDelivery = new PublicationDeliveryDTO();
        publicationDelivery.setDataObjects(dataObjects);

        return publicationDelivery;
    }
}