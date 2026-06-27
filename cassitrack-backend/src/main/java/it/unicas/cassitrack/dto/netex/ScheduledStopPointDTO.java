package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class ScheduledStopPointDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id;

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    @JacksonXmlProperty(localName = "Name")
    private String name;

    // Le coordinate geografiche stanno in StopPlace (NeTEx standard), non qui
}
