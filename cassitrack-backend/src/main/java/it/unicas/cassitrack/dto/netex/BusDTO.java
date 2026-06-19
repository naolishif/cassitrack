package it.unicas.cassitrack.dto.netex;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Data;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class BusDTO {

    @XmlAttribute
    private Integer id;

    @XmlElement(name = "Targa")
    private String targa;

    @XmlElement(name = "NumeroPosti")
    private Integer numeroPosti;

    @XmlElement(name = "PostoDisabili")
    private Boolean postoDisabili;

    @XmlElement(name = "Disponibile")
    private Boolean disponibile;

    @XmlElement(name = "CurrentVehicleId")
    private String currentVehicleId;
}