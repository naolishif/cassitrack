package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import it.unicas.omnimove.dto.netex.RefDTO;
import lombok.Data;

@Data
public class CallDTO {
    @JacksonXmlProperty(localName = "Order")
    private Integer order; // Mappa la colonna scheduled_stops.stop_sequence

    // Riferimento all'ID della fermata (stop_id)
    @JacksonXmlProperty(localName = "ScheduledStopPointRef")
    private RefDTO scheduledStopPointRef;

    @JacksonXmlProperty(localName = "ArrivalSeconds")
    private Integer arrivalSeconds;
}