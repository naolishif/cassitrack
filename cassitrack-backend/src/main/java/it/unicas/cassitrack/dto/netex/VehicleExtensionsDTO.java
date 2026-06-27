package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/** Campi operativi del veicolo non previsti dal core NeTEx */
@Data
public class VehicleExtensionsDTO {

    @JacksonXmlProperty(localName = "Targa")
    private String targa;

    @JacksonXmlProperty(localName = "NumeroPosti")
    private Integer numeroPosti;

    @JacksonXmlProperty(localName = "WheelchairAccessible")
    private Boolean wheelchairAccessible;

    @JacksonXmlProperty(localName = "Disponibile")
    private Boolean disponibile;
}
