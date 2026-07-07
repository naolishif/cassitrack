package it.unicas.cassitrack.dto.netex;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * NeTEx Vehicle — rappresenta un singolo veicolo fisico.
 * I campi operativi non presenti nello standard (targa, disponibilità)
 * sono raccolti in <extensions>.
 */
@Data
public class VehicleDTO {

    @JacksonXmlProperty(isAttribute = true, localName = "id")
    private String id;

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private String version = "1";

    /** ID del veicolo nel sistema MQTT (es. MAGNI-001) */
    @JacksonXmlProperty(localName = "PrivateCode")
    private String privateCode;

    /** Campi non standard NeTEx raccolti in extensions */
    @JacksonXmlProperty(localName = "extensions")
    private VehicleExtensionsDTO extensions;
}
