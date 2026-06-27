package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class ServiceJourneyExtensionsDTO {

    @JacksonXmlProperty(localName = "VehicleRef")
    private String vehicleRef;
}
