package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class CompositeFrameDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id = "CASSITRACK:CompositeFrame:1";

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    @JacksonXmlProperty(localName = "frames")
    private FramesDTO frames;
}
