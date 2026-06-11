package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import it.unicas.omnimove.dto.netex.ServiceJourneyDTO;
import lombok.Data;

import java.util.List;

@Data
public class ServiceFrameDTO {

    @JacksonXmlProperty(isAttribute = true)
    private String id = "Cassitrack:ServiceFrame:1";

    // Qui inseriremo i dati della tabella "routes"
    @JacksonXmlElementWrapper(localName = "lines")
    @JacksonXmlProperty(localName = "Line")
    private List<LineDTO> lines;

    // Qui inseriremo i dati delle tabelle "trips" + "scheduled_stops"
    @JacksonXmlElementWrapper(localName = "serviceJourneys")
    @JacksonXmlProperty(localName = "ServiceJourney")
    private List<ServiceJourneyDTO> serviceJourneys;
}