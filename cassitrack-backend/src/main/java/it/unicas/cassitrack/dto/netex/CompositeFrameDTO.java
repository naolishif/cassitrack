package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

@Data
public class CompositeFrameDTO {
    @JacksonXmlProperty(isAttribute = true)
    private String id = "Cassitrack:Frame:1";

    // Contenitore per le fermate (Stops)
    @JacksonXmlElementWrapper(localName = "siteFrames")
    @JacksonXmlProperty(localName = "SiteFrame")
    private List<SiteFrameDTO> siteFrames;

    // Contenitore per rotte e corse (Routes, Trips, Lines)
    @JacksonXmlElementWrapper(localName = "serviceFrames")
    @JacksonXmlProperty(localName = "ServiceFrame")
    private List<ServiceFrameDTO> serviceFrames;
}