package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

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
