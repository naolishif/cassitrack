package it.unicas.omnimove.service;

import it.unicas.omnimove.dto.netex.*;
import it.unicas.omnimove.model.*;
import it.unicas.omnimove.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class NetexImportService {

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final ScheduledStopRepository scheduledStopRepository;

    private final RestClient restClient;

    public NetexImportService(StopRepository stopRepository,
                              RouteRepository routeRepository,
                              TripRepository tripRepository,
                              ScheduledStopRepository scheduledStopRepository) {
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.tripRepository = tripRepository;
        this.scheduledStopRepository = scheduledStopRepository;

        // Inizializziamo il client HTTP di Spring
        this.restClient = RestClient.builder()
                .messageConverters(converters -> {
                    converters.add(new org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter());
                })
                .build();
    }

    @Transactional
    public void importDataFromCassitrack() {
        String cassitrackUrl = "http://localhost:8080/api/static/netex"; // Modifica la porta se necessario

        System.out.println("Inizio scaricamento dati NeTEx da Cassitrack...");

        System.out.println("Pulizia dei vecchi dati prima dell'importazione...");
        scheduledStopRepository.deleteAll();
        tripRepository.deleteAll();
        routeRepository.deleteAll();
        stopRepository.deleteAll();

        // 1. Chiamata HTTP e conversione automatica da XML a DTO Java grazie a Jackson
        PublicationDeliveryDTO netexData = restClient.get()
                .uri(cassitrackUrl)
                .accept(org.springframework.http.MediaType.APPLICATION_XML)
                .retrieve()
                .body(PublicationDeliveryDTO.class);

        if (netexData == null || netexData.getDataObjects() == null) {
            System.out.println("Nessun dato ricevuto o formato non valido.");
            return;
        }

        CompositeFrameDTO frame = netexData.getDataObjects().getCompositeFrame();

        // 2. IMPORTAZIONE DELLE FERMATE (Stops)
        if (frame.getSiteFrames() != null) {
            for (SiteFrameDTO siteFrame : frame.getSiteFrames()) {
                if (siteFrame.getStopPoints() != null) {
                    for (ScheduledStopPointDTO stopDto : siteFrame.getStopPoints()) {
                        Stop stop = new Stop();
                        stop.setId(stopDto.getId());
                        stop.setName(stopDto.getName());
                        stop.setLat(stopDto.getLatitude());
                        stop.setLon(stopDto.getLongitude());
                        stop.setActive(true); // Impostiamo di default ad attivo

                        stopRepository.save(stop); // Salva o aggiorna nel DB di Omnimove
                    }
                }
            }
        }

        // 3. IMPORTAZIONE DELLE LINEE (Routes) E DELLE CORSE (Trips)
        if (frame.getServiceFrames() != null) {
            for (ServiceFrameDTO serviceFrame : frame.getServiceFrames()) {

                // 3a. Salva le Linee
                if (serviceFrame.getLines() != null) {
                    for (LineDTO lineDto : serviceFrame.getLines()) {
                        Route route = new Route();
                        route.setId(lineDto.getId());
                        route.setLongName(lineDto.getName());
                        route.setShortName(lineDto.getShortName());
                        //route.setColor(lineDto.getColor());
                        route.setActive(true);

                        routeRepository.save(route);
                    }
                }

                // 3b. Salva le Corse e i loro passaggi orari
                if (serviceFrame.getServiceJourneys() != null) {
                    for (ServiceJourneyDTO journeyDto : serviceFrame.getServiceJourneys()) {
                        Trip trip = new Trip();
                        trip.setId(journeyDto.getId());

                        // Recuperiamo la rotta salvata un attimo fa per collegarla al Trip
                        String routeId = journeyDto.getLineRef().getRef();
                        Route associatedRoute = routeRepository.findById(routeId).orElse(null);
                        trip.setRoute(associatedRoute);

                        trip.setServiceType("WEEKDAY"); // Valore di esempio o mappato se presente
                        tripRepository.save(trip);

                        // Salva la sequenza delle fermate per questo Trip
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