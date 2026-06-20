package it.unicas.omnimove.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "journey_log")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JourneyLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private String mode;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "cost_euros")
    private Double costEuros;

    @Column(name = "co2_grams")
    private Double co2Grams;

    @Column(name = "green_index")
    private Integer greenIndex;

    @Column(name = "origin_name")
    private String originName;

    @Column(name = "dest_name")
    private String destName;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;
}