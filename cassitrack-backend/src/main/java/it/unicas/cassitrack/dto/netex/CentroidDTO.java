package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/** Wrapper NeTEx: StopPlace > Centroid > Location */
@Data
public class CentroidDTO {

    @JacksonXmlProperty(localName = "Location")
    private LocationDTO location;

    public CentroidDTO() {}
    public CentroidDTO(LocationDTO location) { this.location = location; }
}
