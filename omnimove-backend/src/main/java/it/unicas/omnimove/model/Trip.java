package it.unicas.omnimove.model;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trip {

    @Id
    @Column(name = "id", length = 255)
    private String id; // Chiave primaria (Stringa), es: "T-16-0800"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route; // Relazione Molti-a-Uno verso la tabella 'routes'

    //@Column(name = "service_type", nullable = false)
    //private String serviceType; // Es: "WEEKDAY"

    //@Column(name = "headsign")
    //private String headsign; // Es: "Campus Folcara"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;
}