package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class ScheduledStopPointDTO {
    @JacksonXmlProperty(isAttribute = true)
    private String id; // Es: STOP_43

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Longitude")
    private Double longitude;

    @JacksonXmlProperty(localName = "Latitude")
    private Double latitude;
}
