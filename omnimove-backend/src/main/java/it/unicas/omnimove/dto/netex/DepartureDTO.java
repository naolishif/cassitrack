package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/** Wrapper NeTEx per l'orario di partenza da una fermata */
@Data
public class DepartureDTO {

    @JacksonXmlProperty(localName = "Time")
    private String time; // formato HH:mm:ss

    public DepartureDTO() {}
    public DepartureDTO(String time) { this.time = time; }
}
