package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class LineDTO {
    @JacksonXmlProperty(isAttribute = true)
    private String id; // Mappa la colonna routes.id

    @JacksonXmlProperty(localName = "Name")
    private String name; // Mappa la colonna routes.long_name

    @JacksonXmlProperty(localName = "ShortName")
    private String shortName; // Mappa la colonna routes.short_name

    @JacksonXmlProperty(localName = "Color")
    private String color; // Mappa la colonna routes.color
}