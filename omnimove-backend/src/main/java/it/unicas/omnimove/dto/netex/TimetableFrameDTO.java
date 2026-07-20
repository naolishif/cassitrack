package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

/**
 * NeTEx TimetableFrame — contiene le corse orarie (ServiceJourney).
 * Nello standard NeTEx le ServiceJourney stanno qui, non nel ServiceFrame.
 */
@Data
public class TimetableFrameDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id;

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version;

    @JacksonXmlElementWrapper(localName = "vehicleJourneys")
    @JacksonXmlProperty(localName = "ServiceJourney")
    private List<ServiceJourneyDTO> serviceJourneys;
}
