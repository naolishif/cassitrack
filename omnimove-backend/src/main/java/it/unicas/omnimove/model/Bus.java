package it.unicas.omnimove.model;

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
    private String license_plate;

    @Column(name = "number_seats", nullable = false)
    private Integer numberSeats;

    @Column(name = "place_disable_people", nullable = false)
    private Boolean placeDisablePeople;

    @Column(nullable = false)
    private Boolean available;

    @Column(name = "current_vehicle_id", unique = true)
    private String currentVehicleId;
}