package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/** Wrapper NeTEx per le coordinate geografiche */
@Data
public class LocationDTO {

    @JacksonXmlProperty(localName = "Longitude")
    private Double longitude;

    @JacksonXmlProperty(localName = "Latitude")
    private Double latitude;

    public LocationDTO() {}
    public LocationDTO(Double longitude, Double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }
}
