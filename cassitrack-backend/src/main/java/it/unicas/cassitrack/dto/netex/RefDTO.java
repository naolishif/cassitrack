package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class RefDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "ref")
    private String ref;

    public RefDTO() {}
    public RefDTO(String ref) { this.ref = ref; }
}
