package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * NeTEx ResourceFrame — contiene le risorse operative (veicoli).
 * In NeTEx i veicoli non appartengono al ServiceFrame ma al ResourceFrame.
 */
@Data
public class ResourceFrameDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id = "CASSITRACK:ResourceFrame:1";

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    @JacksonXmlElementWrapper(localName = "vehicles")
    @JacksonXmlProperty(localName = "Vehicle")
    private List<VehicleDTO> vehicles;
}
