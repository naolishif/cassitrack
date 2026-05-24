package it.unicas.cassitrack.controller;

import it.unicas.cassitrack.dto.BusTelemetryDTO;
import it.unicas.cassitrack.dto.siri.Siri;
import it.unicas.cassitrack.dto.siri.SiriMapper;
import it.unicas.cassitrack.service.InfluxService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/siri")
public class SiriController {

    private final InfluxService influxService;

    // Spring Boot inietterà automaticamente il tuo InfluxService qui
    public SiriController(InfluxService influxService) {
        this.influxService = influxService;
    }

    @GetMapping(value = "/vehicle-monitoring", produces = MediaType.APPLICATION_XML_VALUE)
    public Siri getRealTimeBusData() {

        // 1. Legge la lista di TUTTI gli autobus aggiornati dall'ultimo InfluxService
        List<BusTelemetryDTO> liveData = influxService.getLatestTelemetry();

        // 2. Mappa l'intera lista in un unico pacchetto XML Siri e lo spedisce
        return SiriMapper.toSiri(liveData);
    }
}