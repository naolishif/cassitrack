package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * NeTEx StopPlace — luogo fisico con coordinate geografiche.
 * Sta nel SiteFrame, separato dallo ScheduledStopPoint logico.
 */
@Data
public class StopPlaceDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id;

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Centroid")
    private CentroidDTO centroid;
}
