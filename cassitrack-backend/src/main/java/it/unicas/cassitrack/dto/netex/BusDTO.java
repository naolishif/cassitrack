package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class BusDTO {

    @JacksonXmlProperty(isAttribute = true)
    private Integer id;

    @JacksonXmlProperty(localName = "Targa")
    private String targa;

    @JacksonXmlProperty(localName = "NumeroPosti")
    private Integer numeroPosti;

    @JacksonXmlProperty(localName = "PostoDisabili")
    private Boolean postoDisabili;

    @JacksonXmlProperty(localName = "Disponibile")
    private Boolean disponibile;

    @JacksonXmlProperty(localName = "CurrentVehicleId")
    private String currentVehicleId;
}
