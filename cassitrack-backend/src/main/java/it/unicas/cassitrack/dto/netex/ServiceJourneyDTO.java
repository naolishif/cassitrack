package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

@Data
public class ServiceJourneyDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id;

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    @JacksonXmlProperty(localName = "LineRef")
    private RefDTO lineRef;

    /**
     * Associazione veicolo–corsa. Non prevista nel core NeTEx (è runtime),
     * esposta come extensions per trasferire l'info statica di configurazione.
     */
    @JacksonXmlProperty(localName = "extensions")
    private ServiceJourneyExtensionsDTO extensions;

    @JacksonXmlElementWrapper(localName = "calls")
    @JacksonXmlProperty(localName = "Call")
    private List<CallDTO> calls;
}
