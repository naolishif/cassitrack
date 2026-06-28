package it.unicas.cassitrack.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "buses")
@Data
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bus_id")
    private Integer busId;

    @Column(nullable = false, unique = true)
    private String targa;

    @Column(name = "numero_posti", nullable = false)
    private Integer numeroPosti;

    @Column(name = "wheelchair_accessible", nullable = false)
    private Boolean wheelchairAccessible;

    @Column(nullable = false)
    private Boolean disponibile;

    @Column(name = "current_vehicle_id", unique = true)
    private String currentVehicleId;
}