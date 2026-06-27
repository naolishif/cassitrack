package it.unicas.omnimove.service;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unicas.omnimove.dto.netex.*;
import it.unicas.omnimove.model.*;
import it.unicas.omnimove.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.xml.stream.XMLInputFactory;
import java.util.List;

@Service
public class NetexImportService {

    @Value("${cassitrack.netex.url}")
    private String cassitrackNetexUrl;

    @Value("${cassitrack.api.token}")
    private String cassitrackApiToken;

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final ScheduledStopRepository scheduledStopRepository;
    private final BusRepository busRepository; // ← AGGIUNTO

    private final RestClient restClient;

    public NetexImportService(StopRepository stopRepository,
                              RouteRepository routeRepository,
                              TripRepository tripRepository,
                              ScheduledStopRepository scheduledStopRepository,
                              BusRepository busRepository) { // ← AGGIUNTO
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.tripRepository = tripRepository;
        this.scheduledStopRepository = scheduledStopRepository;
        this.busRepository = busRepository; // ← AGGIUNTO

        XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
        xmlFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        xmlFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        XmlMapper xmlMapper = new XmlMapper(xmlFactory);
        xmlMapper.registerModule(new JavaTimeModule());
        xmlMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.restClient = RestClient.builder()
                .messageConverters(converters -> {
                    converters.add(new org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter(xmlMapper));
                })
                .build();
    }

    /**
     * Estrae la parte locale da un ID NeTEx con namespace.
     * Es: "CASSITRACK:ScheduledStopPoint:PSB" → "PSB"
     *     "CASSITRACK:Line:LINEA_1"           → "LINEA_1"
     *     "PSB" (già locale)                  → "PSB"
     */
    private static String localId(String netexId) {
        if (netexId == null) return null;
        int colon = netexId.lastIndexOf(':');
        return colon >= 0 ? netexId.substring(colon + 1) : netexId;
    }

    // ── helper: converti HH:mm:ss → secondi ────────────────────────────────
    private static Integer timeToSeconds(String time) {
        if (time == null || time.isBlank()) return null;
        String[] parts = time.split(":");
        if (parts.length != 3) return null;
        try {
            return Integer.parseInt(parts[0]) * 3600
                 + Integer.parseInt(parts[1]) * 60
                 + Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Transactional
    public void importDataFromCassitrack() {
        System.out.println("Inizio scaricamento dati NeTEx da Cassitrack...");

        System.out.println("Pulizia dei vecchi dati prima dell'importazione...");
        scheduledStopRepository.deleteAll();
        tripRepository.deleteAll();
        routeRepository.deleteAll();
        stopRepository.deleteAll();
        busRepository.deleteAll();

        PublicationDeliveryDTO netexData = restClient.get()
                .uri(cassitrackNetexUrl)
                .accept(org.springframework.http.MediaType.APPLICATION_XML)
                .header("X-Api-Key", cassitrackApiToken)
                .retrieve()
                .body(PublicationDeliveryDTO.class);

        if (netexData == null || netexData.getDataObjects() == null) {
            throw new RuntimeException("NeTEx import aborted: no data received from CassiTrack. Previous data preserved.");
        }

        CompositeFrameDTO compositeFrame = netexData.getDataObjects().getCompositeFrame();
        if (compositeFrame == null || compositeFrame.getFrames() == null) {
            throw new RuntimeException("NeTEx import aborted: CompositeFrame/frames assenti.");
        }

        FramesDTO frames = compositeFrame.getFrames();

        // ── 1. SITE FRAME → Fermate ─────────────────────────────────────────
        SiteFrameDTO siteFrame = frames.getSiteFrame();
        if (siteFrame != null && siteFrame.getStopPoints() != null) {
            List<Stop> stops = siteFrame.getStopPoints().stream().map(dto -> {
                Stop stop = new Stop();
                stop.setId(localId(dto.getId())); // "CASSITRACK:ScheduledStopPoint:PSB" → "PSB"
                stop.setName(dto.getName());
                if (dto.getLocation() != null) {
                    stop.setLat(dto.getLocation().getLatitude());
                    stop.setLon(dto.getLocation().getLongitude());
                }
                stop.setActive(true);
                return stop;
            }).collect(java.util.stream.Collectors.toList());
            stopRepository.saveAll(stops);
        }

        // ── 2. RESOURCE FRAME → Veicoli (Bus) ──────────────────────────────
        ResourceFrameDTO resourceFrame = frames.getResourceFrame();
        if (resourceFrame != null && resourceFrame.getVehicles() != null) {
            List<Bus> buses = resourceFrame.getVehicles().stream().map(dto -> {
                Bus bus = new Bus();
                bus.setCurrentVehicleId(dto.getPrivateCode());
                if (dto.getExtensions() != null) {
                    bus.setLicensePlate(dto.getExtensions().getTarga());
                    bus.setNumberSeats(dto.getExtensions().getNumeroPosti());
                    bus.setPlaceDisablePeople(dto.getExtensions().getWheelchairAccessible());
                    bus.setAvailable(dto.getExtensions().getDisponibile());
                }
                return bus;
            }).collect(java.util.stream.Collectors.toList());
            busRepository.saveAll(buses);
        }

        // ── 3. SERVICE FRAME → Linee e Corse ────────────────────────────────
        ServiceFrameDTO serviceFrame = frames.getServiceFrame();
        if (serviceFrame != null) {

            // 3a. Linee
            if (serviceFrame.getLines() != null) {
                List<Route> routes = serviceFrame.getLines().stream().map(dto -> {
                    Route route = new Route();
                    route.setId(localId(dto.getId())); // "CASSITRACK:Line:LINEA_1" → "LINEA_1"
                    route.setLongName(dto.getName());
                    route.setShortName(dto.getShortName());
                    route.setActive(true);
                    return route;
                }).collect(java.util.stream.Collectors.toList());
                routeRepository.saveAll(routes);
            }

            // 3b. Corse
            if (serviceFrame.getServiceJourneys() != null) {
                for (ServiceJourneyDTO journeyDto : serviceFrame.getServiceJourneys()) {
                    Trip trip = new Trip();
                    trip.setId(localId(journeyDto.getId())); // "CASSITRACK:ServiceJourney:LINEA_1_28800" → "LINEA_1_28800"

                    String routeId = journeyDto.getLineRef() != null
                            ? localId(journeyDto.getLineRef().getRef()) : null; // "CASSITRACK:Line:LINEA_1" → "LINEA_1"
                    Route associatedRoute = routeId != null ? routeRepository.findById(routeId).orElse(null) : null;
                    trip.setRoute(associatedRoute);

                    // VehicleRef nelle extensions → collegamento al Bus
                    if (journeyDto.getExtensions() != null && journeyDto.getExtensions().getVehicleRef() != null) {
                        try {
                            // "CASSITRACK:Vehicle:1" → "1" → 1
                            Integer busId = Integer.parseInt(localId(journeyDto.getExtensions().getVehicleRef()));
                            Bus associatedBus = busRepository.findById(busId).orElse(null);
                            trip.setBus(associatedBus);
                        } catch (NumberFormatException ignored) {}
                    }

                    tripRepository.save(trip);

                    // Fermate della corsa
                    if (journeyDto.getCalls() != null) {
                        List<ScheduledStop> stops = journeyDto.getCalls().stream().map(callDto -> {
                            ScheduledStop sStop = new ScheduledStop();
                            sStop.setTrip(trip);
                            sStop.setStopId(callDto.getScheduledStopPointRef() != null
                                    ? localId(callDto.getScheduledStopPointRef().getRef()) : null); // "CASSITRACK:ScheduledStopPoint:PSB" → "PSB"
                            sStop.setStopSequence(callDto.getOrder());
                            // Conversione HH:mm:ss → secondi
                            // Prima fermata ha solo Departure, ultima solo Arrival, mezzo entrambi
                            String time = null;
                            if (callDto.getArrival() != null) time = callDto.getArrival().getTime();
                            else if (callDto.getDeparture() != null) time = callDto.getDeparture().getTime();
                            sStop.setArrivalSeconds(timeToSeconds(time));
                            return sStop;
                        }).collect(java.util.stream.Collectors.toList());
                        scheduledStopRepository.saveAll(stops);
                    }
                }
            }
        }

        System.out.println("Importazione NeTEx completata con successo nel database di Omnimove!");
    }
}