package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Data;
import java.util.List;

@Data
public class ServiceJourneyDTO {
    @JacksonXmlProperty(isAttribute = true)
    private String id; // Mappa la colonna trips.id

    // Riferimento alla Linea (route_id)
    @JacksonXmlProperty(localName = "LineRef")
    private RefDTO lineRef;

    @XmlElement(name = "BusRef")
    private RefDTO busRef; // <- aggiunge il campo

    // La lista delle fermate per questo viaggio (scheduled_stops)
    @JacksonXmlElementWrapper(localName = "calls")
    @JacksonXmlProperty(localName = "Call")
    private List<CallDTO> calls;
}