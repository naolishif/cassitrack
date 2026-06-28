package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class ServiceFrameDTO {

    @JacksonXmlProperty(isAttribute = true)
    private String id;

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