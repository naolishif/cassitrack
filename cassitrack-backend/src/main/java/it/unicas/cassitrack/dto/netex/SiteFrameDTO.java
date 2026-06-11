package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;
import java.util.List;

@Data
public class SiteFrameDTO {
    @JacksonXmlElementWrapper(localName = "stopPoints")
    @JacksonXmlProperty(localName = "ScheduledStopPoint")
    private List<ScheduledStopPointDTO> stopPoints;
}

