package it.unicas.omnimove.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class RefDTO {
    @JacksonXmlProperty(isAttribute = true)
    private String ref;

    // Costruttore vuoto per Jackson
    public RefDTO() {}

    // Costruttore per comodità nostra
    public RefDTO(String ref) {
        this.ref = ref;
    }
}