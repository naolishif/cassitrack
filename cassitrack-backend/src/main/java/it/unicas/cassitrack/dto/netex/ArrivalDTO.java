package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/** Wrapper NeTEx per l'orario di arrivo/partenza a una fermata */
@Data
public class ArrivalDTO {

    @JacksonXmlProperty(localName = "Time")
    private String time; // formato HH:mm:ss

    public ArrivalDTO() {}
    public ArrivalDTO(String time) { this.time = time; }
}
