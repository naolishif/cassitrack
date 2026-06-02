package it.unicas.cassitrack.model;
import jakarta.persistence.*;
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

    // Se nel database la tabella routes ha altre colonne (es. name, type),
    // puoi aggiungerle qui sotto come semplici variabili. Per ora lasciamo solo l'ID.
}