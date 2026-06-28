package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/** Dati operativi non standard del ServiceJourney */
@Data
public class ServiceJourneyExtensionsDTO {

    /** ID del veicolo fisico (bus) assegnato alla corsa */
    @JacksonXmlProperty(localName = "VehicleRef")
    private String vehicleRef;

    public ServiceJourneyExtensionsDTO() {}
    public ServiceJourneyExtensionsDTO(String vehicleRef) { this.vehicleRef = vehicleRef; }
}
