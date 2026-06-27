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

    // ── helper: converti secondi in formato NeTEx HH:mm:ss ──────────────────
    private static String secondsToTime(Integer seconds) {
        if (seconds == null) return null;
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
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

        // ── 1. SITE FRAME (Fermate) ──────────────────────────────────────────
        List<Stop> dbStops = stopRepository.findAll();
        List<ScheduledStopPointDTO> netexStops = dbStops.stream().map(stop -> {
            ScheduledStopPointDTO dto = new ScheduledStopPointDTO();
            dto.setId("CASSITRACK:ScheduledStopPoint:" + stop.getId());
            dto.setName(stop.getName());
            dto.setLocation(new LocationDTO(stop.getLon(), stop.getLat()));
            return dto;
        }).collect(Collectors.toList());

        SiteFrameDTO siteFrame = new SiteFrameDTO();
        siteFrame.setStopPoints(netexStops);

        // ── 2. SERVICE FRAME (Linee e Corse) ────────────────────────────────
        List<Route> dbRoutes = routeRepository.findAll();
        List<LineDTO> netexLines = dbRoutes.stream().map(route -> {
            LineDTO dto = new LineDTO();
            dto.setId("CASSITRACK:Line:" + route.getId());
            dto.setName(route.getLongName());
            dto.setShortName(route.getShortName());
            // transportMode è già "bus" per default
            return dto;
        }).collect(Collectors.toList());

        List<Trip> dbTrips = tripRepository.findAll();
        List<ServiceJourneyDTO> netexJourneys = dbTrips.stream().map(trip -> {
            ServiceJourneyDTO journeyDto = new ServiceJourneyDTO();
            journeyDto.setId("CASSITRACK:ServiceJourney:" + trip.getId());
            journeyDto.setLineRef(new RefDTO("CASSITRACK:Line:" + trip.getRoute().getId()));

            // VehicleRef in extensions (associazione non standard nel core NeTEx)
            if (trip.getBus() != null) {
                journeyDto.setExtensions(
                        new ServiceJourneyExtensionsDTO("CASSITRACK:Vehicle:" + trip.getBus().getBusId()));
            }

            List<ScheduledStop> stopsForThisTrip = scheduledStopRepository.findByTripId(trip.getId());
            if (!stopsForThisTrip.isEmpty()) {
                int totalStops = stopsForThisTrip.size();
                List<CallDTO> netexCalls = stopsForThisTrip.stream().map(sStop -> {
                    CallDTO callDto = new CallDTO();
                    callDto.setOrder(sStop.getStopSequence());
                    callDto.setScheduledStopPointRef(
                            new RefDTO("CASSITRACK:ScheduledStopPoint:" + sStop.getStopId()));
                    String time = secondsToTime(sStop.getArrivalSeconds());
                    if (time != null) {
                        int pos = sStop.getStopSequence();
                        boolean isLast = (pos == totalStops);
                        // Arrival: sempre presente (richiesto dall'import; prima fermata ha stesso
                        //          orario di partenza dato che il DB ha un solo campo)
                        // Departure: tutte le fermate tranne l'ultima (il bus riparte)
                        callDto.setArrival(new ArrivalDTO(time));
                        if (!isLast) callDto.setDeparture(new DepartureDTO(time));
                    }
                    return callDto;
                }).collect(Collectors.toList());
                journeyDto.setCalls(netexCalls);
            }

            return journeyDto;
        }).collect(Collectors.toList());

        ServiceFrameDTO serviceFrame = new ServiceFrameDTO();
        serviceFrame.setLines(netexLines);
        serviceFrame.setServiceJourneys(netexJourneys);

        // ── 3. RESOURCE FRAME (Veicoli) ─────────────────────────────────────
        List<Bus> dbBuses = busRepository.findAll();
        List<VehicleDTO> netexVehicles = dbBuses.stream().map(bus -> {
            VehicleDTO dto = new VehicleDTO();
            dto.setId("CASSITRACK:Vehicle:" + bus.getBusId());
            dto.setPrivateCode(bus.getCurrentVehicleId()); // ID MQTT (es. MAGNI-001)
            VehicleExtensionsDTO ext = new VehicleExtensionsDTO();
            ext.setTarga(bus.getTarga());
            ext.setNumeroPosti(bus.getNumeroPosti());
            ext.setWheelchairAccessible(bus.getWheelchairAccessible());
            ext.setDisponibile(bus.getDisponibile());
            dto.setExtensions(ext);
            return dto;
        }).collect(Collectors.toList());

        ResourceFrameDTO resourceFrame = new ResourceFrameDTO();
        resourceFrame.setVehicles(netexVehicles);

        // ── 4. ASSEMBLAGGIO FINALE ───────────────────────────────────────────
        FramesDTO frames = new FramesDTO();
        frames.setSiteFrame(siteFrame);
        frames.setServiceFrame(serviceFrame);
        frames.setResourceFrame(resourceFrame);

        CompositeFrameDTO compositeFrame = new CompositeFrameDTO();
        compositeFrame.setFrames(frames);

        DataObjects dataObjects = new DataObjects();
        dataObjects.setCompositeFrame(compositeFrame);

        PublicationDeliveryDTO publicationDelivery = new PublicationDeliveryDTO();
        publicationDelivery.setDataObjects(dataObjects);

        return publicationDelivery;
    }
}