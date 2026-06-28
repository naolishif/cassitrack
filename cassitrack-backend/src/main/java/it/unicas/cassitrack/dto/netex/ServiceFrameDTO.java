package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

@Data
public class ServiceFrameDTO {

    @JacksonXmlProperty(isAttribute = true)
    private String id = "Cassitrack:ServiceFrame:1";

    @JacksonXmlElementWrapper(localName = "lines")
    @JacksonXmlProperty(localName = "Line")
    private List<LineDTO> lines;

    @JacksonXmlElementWrapper(localName = "serviceJourneys")
    @JacksonXmlProperty(localName = "ServiceJourney")
    private List<ServiceJourneyDTO> serviceJourneys;

    @JacksonXmlElementWrapper(localName = "Buses")
    @JacksonXmlProperty(localName = "Bus")
    private List<BusDTO> buses;
}