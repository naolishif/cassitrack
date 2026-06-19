package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class DataObjects {
    @JacksonXmlProperty(localName = "CompositeFrame")
    private CompositeFrameDTO compositeFrame;
}