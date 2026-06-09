package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import it.unicas.omnimove.dto.netex.ScheduledStopPointDTO;
import lombok.Data;

import java.util.List;

@Data
public class SiteFrameDTO {
    @JacksonXmlElementWrapper(localName = "stopPoints")
    @JacksonXmlProperty(localName = "ScheduledStopPoint")
    private List<ScheduledStopPointDTO> stopPoints;
}

