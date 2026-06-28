package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class ArrivalDTO {

    @JacksonXmlProperty(localName = "Time")
    private String time; // formato HH:mm:ss
}
