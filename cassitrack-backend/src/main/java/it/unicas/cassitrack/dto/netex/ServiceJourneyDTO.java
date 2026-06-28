package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

@Data
public class ServiceJourneyDTO {
    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlProperty(localName = "LineRef")
    private RefDTO lineRef;

    @JacksonXmlProperty(localName = "BusRef")
    private RefDTO busRef;

    @JacksonXmlElementWrapper(localName = "calls")
    @JacksonXmlProperty(localName = "Call")
    private List<CallDTO> calls;
}