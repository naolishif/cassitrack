package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "order")
    private Integer order;

    @JacksonXmlProperty(localName = "ScheduledStopPointRef")
    private RefDTO scheduledStopPointRef;

    @JacksonXmlProperty(localName = "Arrival")
    private ArrivalDTO arrival;

    @JacksonXmlProperty(localName = "Departure")
    private DepartureDTO departure;
}
