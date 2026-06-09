package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JacksonXmlRootElement(localName = "PublicationDelivery")
public class PublicationDeliveryDTO {

    @JacksonXmlProperty(isAttribute = true)
    private String version = "1.0";

    @JacksonXmlProperty(localName = "PublicationTimestamp")
    private LocalDateTime publicationTimestamp = LocalDateTime.now();

    @JacksonXmlProperty(localName = "ParticipantRef")
    private String participantRef = "CASSITRACK";

    @JacksonXmlProperty(localName = "dataObjects")
    private DataObjects dataObjects;
}