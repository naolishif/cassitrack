package it.unicas.omnimove.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Entity
@Table(name = "stops")
public class Stop {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;
}