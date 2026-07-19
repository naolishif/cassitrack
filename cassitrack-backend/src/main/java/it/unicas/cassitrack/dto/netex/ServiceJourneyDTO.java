package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

// NB: Extensions è definito nella EntityStructure di base NeTEx, quindi va in testa
//     (prima di LineRef); calls resta in coda.
@Data
@JsonPropertyOrder({ "extensions", "lineRef", "calls" })
public class ServiceJourneyDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id;

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    /**
     * Associazione veicolo–corsa. Non prevista nel core NeTEx (è runtime),
     * esposta come Extensions per trasferire l'info statica di configurazione.
     */
    @JacksonXmlProperty(localName = "Extensions")
    private ServiceJourneyExtensionsDTO extensions;

    @JacksonXmlProperty(localName = "LineRef")
    private RefDTO lineRef;

    @JacksonXmlElementWrapper(localName = "calls")
    @JacksonXmlProperty(localName = "Call")
    private List<CallDTO> calls;
}
