package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * NeTEx PassengerStopAssignment — collega un ScheduledStopPoint logico
 * al corrispondente StopPlace fisico.
 * Sta nel ServiceFrame > stopAssignments.
 */
@Data
public class PassengerStopAssignmentDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id;

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    @JacksonXmlProperty(isAttribute = true, localName = "order")
    private Integer order;

    @JacksonXmlProperty(localName = "ScheduledStopPointRef")
    private RefDTO scheduledStopPointRef;

    @JacksonXmlProperty(localName = "StopPlaceRef")
    private RefDTO stopPlaceRef;
}
