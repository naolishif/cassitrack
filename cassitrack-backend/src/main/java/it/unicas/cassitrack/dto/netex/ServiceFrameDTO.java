package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

// Ordine NeTEx nel ServiceFrame: lines PRIMA di scheduledStopPoints, poi stopAssignments.
@Data
@JsonPropertyOrder({ "lines", "scheduledStopPoints", "stopAssignments" })
public class ServiceFrameDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id = "CASSITRACK:ServiceFrame:1";

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    @JacksonXmlElementWrapper(localName = "lines")
    @JacksonXmlProperty(localName = "Line")
    private List<LineDTO> lines;

    @JacksonXmlElementWrapper(localName = "scheduledStopPoints")
    @JacksonXmlProperty(localName = "ScheduledStopPoint")
    private List<ScheduledStopPointDTO> scheduledStopPoints;

    @JacksonXmlElementWrapper(localName = "stopAssignments")
    @JacksonXmlProperty(localName = "PassengerStopAssignment")
    private List<PassengerStopAssignmentDTO> stopAssignments;

    // NB: le ServiceJourney sono state spostate nel TimetableFrame (standard NeTEx).
}
