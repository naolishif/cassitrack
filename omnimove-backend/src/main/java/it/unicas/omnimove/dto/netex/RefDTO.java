package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class RefDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "ref")
    private String ref;
}
