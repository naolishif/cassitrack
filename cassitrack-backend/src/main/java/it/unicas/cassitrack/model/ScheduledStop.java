package it.unicas.cassitrack.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents one entry in the bus timetable.
 * "Route LINEA-16 is scheduled to reach stop
 *  FOLCARA-CAMPUS at 08:45 on weekdays."
 *
 * This is the data from the paper schedule
 * you picked up at the bus stop.
 */
@Entity
@Table(name = "scheduled_stops")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which route — e.g. "LINEA-16" */
    @Column(name = "route_id", nullable = false)
    private String routeId;

    /** Which stop — e.g. "FOLCARA-CAMPUS" */
    @Column(name = "stop_id", nullable = false)
    private String stopId;

    /** Order of this stop on the route */
    @Column(name = "stop_sequence", nullable = false)
    private Integer stopSequence;

    /**
     * Scheduled arrival time expressed as
     * SECONDS after midnight.
     *
     * Example: 08:45 = 8*3600 + 45*60 = 31500
     *
     * We store it this way so we can do
     * simple arithmetic to compute delays.
     */
    @Column(name = "arrival_seconds", nullable = false)
    private Integer arrivalSeconds;

    /**
     * Which days this trip runs.
     * "WEEKDAY", "SATURDAY", "SUNDAY"
     */
    @Column(name = "service_type", nullable = false)
    private String serviceType;
}