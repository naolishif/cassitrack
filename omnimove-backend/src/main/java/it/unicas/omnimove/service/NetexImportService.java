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

    @Transactional
    public void importDataFromCassitrack() {
        System.out.println("Inizio scaricamento dati NeTEx da Cassitrack...");

        System.out.println("Pulizia dei vecchi dati prima dell'importazione...");
        scheduledStopRepository.deleteAll();
        tripRepository.deleteAll();
        routeRepository.deleteAll();
        stopRepository.deleteAll();
        busRepository.deleteAll(); // ← AGGIUNTO

        PublicationDeliveryDTO netexData = restClient.get()
                .uri(cassitrackNetexUrl)
                .accept(org.springframework.http.MediaType.APPLICATION_XML)
                .retrieve()
                .body(PublicationDeliveryDTO.class);

        if (netexData == null || netexData.getDataObjects() == null) {
            System.out.println("Nessun dato ricevuto o formato non valido.");
            return;
        }

        CompositeFrameDTO frame = netexData.getDataObjects().getCompositeFrame();

        // 2. IMPORTAZIONE DELLE FERMATE
        if (frame.getSiteFrames() != null) {
            for (SiteFrameDTO siteFrame : frame.getSiteFrames()) {
                if (siteFrame.getStopPoints() != null) {
                    for (ScheduledStopPointDTO stopDto : siteFrame.getStopPoints()) {
                        Stop stop = new Stop();
                        stop.setId(stopDto.getId());
                        stop.setName(stopDto.getName());
                        stop.setLat(stopDto.getLatitude());
                        stop.setLon(stopDto.getLongitude());
                        stop.setActive(true);
                        stopRepository.save(stop);
                    }
                }
            }
        }

        if (frame.getServiceFrames() != null) {
            for (ServiceFrameDTO serviceFrame : frame.getServiceFrames()) {

                // 3a. Salva le Linee
                if (serviceFrame.getLines() != null) {
                    for (LineDTO lineDto : serviceFrame.getLines()) {
                        Route route = new Route();
                        route.setId(lineDto.getId());
                        route.setLongName(lineDto.getName());
                        route.setShortName(lineDto.getShortName());
                        route.setActive(true);
                        routeRepository.save(route);
                    }
                }

                // 3b. Salva i Bus ← AGGIUNTO
                if (serviceFrame.getBuses() != null) {
                    for (BusDTO busDto : serviceFrame.getBuses()) {
                        Bus bus = new Bus();
                        bus.setLicensePlate(busDto.getTarga());
                        bus.setNumberSeats(busDto.getNumeroPosti());
                        bus.setPlaceDisablePeople(busDto.getPostoDisabili());
                        bus.setAvailable(busDto.getDisponibile());
                        bus.setCurrentVehicleId(busDto.getCurrentVehicleId());
                        busRepository.save(bus);
                    }
                }

                // 3c. Salva le Corse
                if (serviceFrame.getServiceJourneys() != null) {
                    for (ServiceJourneyDTO journeyDto : serviceFrame.getServiceJourneys()) {
                        Trip trip = new Trip();
                        trip.setId(journeyDto.getId());

                        String routeId = journeyDto.getLineRef().getRef();
                        Route associatedRoute = routeRepository.findById(routeId).orElse(null);
                        trip.setRoute(associatedRoute);

                        // Collegamento al Bus ← AGGIUNTO
                        if (journeyDto.getBusRef() != null) {
                            Integer busId = Integer.parseInt(journeyDto.getBusRef().getRef());
                            Bus associatedBus = busRepository.findById(busId).orElse(null);
                            trip.setBus(associatedBus);
                        }

                        //trip.setServiceType("WEEKDAY");
                        tripRepository.save(trip);

                        if (journeyDto.getCalls() != null) {
                            for (CallDTO callDto : journeyDto.getCalls()) {
                                ScheduledStop sStop = new ScheduledStop();
                                sStop.setTrip(trip);
                                sStop.setStopId(callDto.getScheduledStopPointRef().getRef());
                                sStop.setStopSequence(callDto.getOrder());
                                sStop.setArrivalSeconds(callDto.getArrivalSeconds());
                                scheduledStopRepository.save(sStop);
                            }
                        }
                    }
                }
            }
        }
        System.out.println("Importazione NeTEx completata con successo nel database di Omnimove!");
    }
}