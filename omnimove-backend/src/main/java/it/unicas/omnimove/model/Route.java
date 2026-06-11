package it.unicas.omnimove.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Route {

    @Id
    @Column(name = "id", length = 255)
    private String id; // Es: "LINEA-16"

    private String longName;
    private String shortName;

    private boolean active;


    // Se nel database la tabella routes ha altre colonne (es. name, type),
    // puoi aggiungerle qui sotto come semplici variabili. Per ora lasciamo solo l'ID.
}