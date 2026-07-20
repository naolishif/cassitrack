package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * NeTEx TimetableFrame — contiene le corse orarie (ServiceJourney).
 * Nello standard NeTEx le ServiceJourney NON stanno nel ServiceFrame (che descrive
 * la rete: linee, fermate logiche, assegnazioni) ma nel TimetableFrame.
 */
@Data
public class TimetableFrameDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id = "CASSITRACK:TimetableFrame:1";

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    @JacksonXmlElementWrapper(localName = "vehicleJourneys")
    @JacksonXmlProperty(localName = "ServiceJourney")
    private List<ServiceJourneyDTO> serviceJourneys;
}
