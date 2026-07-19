package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;
import java.time.Instant;

@Data
@JacksonXmlRootElement(localName = "PublicationDelivery")
public class PublicationDeliveryDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "xmlns")
    private String xmlns = "http://www.netex.org.uk/netex";

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1.0";

    @JacksonXmlProperty(localName = "PublicationTimestamp")
    private String publicationTimestamp = Instant.now().toString();

    @JacksonXmlProperty(localName = "ParticipantRef")
    private String participantRef = "CASSITRACK";

    @JacksonXmlProperty(localName = "dataObjects")
    private DataObjects dataObjects;
}
